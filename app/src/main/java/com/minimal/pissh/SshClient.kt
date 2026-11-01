package com.minimal.pissh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

sealed interface AuthMethod {
    data class Password(val password: String) : AuthMethod
    data class PublicKey(
        val privateKeyPem: String,
        val passphrase: String? = null
    ) : AuthMethod
}

data class SshConnectionConfig(
    val host: String,
    val port: Int = 22,
    val username: String = "pi",
    val authMethod: AuthMethod,
    val connectTimeoutMs: Int = 10_000,
    val initialCols: Int = 80,
    val initialRows: Int = 24,
    val initialWidthPx: Int = 0,
    val initialHeightPx: Int = 0
)

class SshClient(
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val sessionMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile
    private var session: Session? = null

    @Volatile
    private var channelShell: ChannelShell? = null

    @Volatile
    private var outputStream: OutputStream? = null

    private var readerJob: Job? = null

    private val _incomingBytes = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    val incomingBytes: SharedFlow<ByteArray> = _incomingBytes.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _disconnectReason = MutableSharedFlow<String?>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val disconnectReason: SharedFlow<String?> = _disconnectReason.asSharedFlow()

    suspend fun connect(config: SshConnectionConfig): Result<Unit> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            disconnectInternal()
            runCatching {
                configureAndroidCryptoDefaults()
                val jsch = JSch()
                jsch.removeAllIdentity()

                val sessionConfig = Properties().apply {
                    // Allow seamless LAN IP / Tailscale MagicDNS switching
                    put("StrictHostKeyChecking", "no")
                    put("HashKnownHosts", "yes")
                }

                when (val auth = config.authMethod) {
                    is AuthMethod.PublicKey -> {
                        val normalizedKey = auth.privateKeyPem.trim()
                        require(normalizedKey.isNotEmpty()) { "Private key cannot be empty." }
                        val keyBytes = normalizedKey.toByteArray(Charsets.UTF_8)
                        val passphraseBytes = auth.passphrase
                            ?.takeIf { it.isNotEmpty() }
                            ?.toByteArray(Charsets.UTF_8)
                        try {
                            jsch.addIdentity("pi-ssh-key", keyBytes, null, passphraseBytes)
                        } finally {
                            keyBytes.fill(0)
                            passphraseBytes?.fill(0)
                        }
                        sessionConfig["PreferredAuthentications"] = "publickey,keyboard-interactive,password"
                    }
                    is AuthMethod.Password -> {
                        sessionConfig["PreferredAuthentications"] = "password,keyboard-interactive"
                    }
                }

                val newSession = jsch.getSession(
                    config.username.trim(),
                    config.host.trim(),
                    config.port.coerceIn(1, 65535)
                ).apply {
                    setConfig(sessionConfig)
                    if (config.authMethod is AuthMethod.Password) {
                        setPassword(config.authMethod.password)
                    }
                    setServerAliveInterval(15_000)
                    setServerAliveCountMax(3)
                    timeout = config.connectTimeoutMs
                }

                newSession.connect(config.connectTimeoutMs)

                val shell = (newSession.openChannel("shell") as ChannelShell).apply {
                    setPty(true)
                    setPtyType("xterm-256color")
                    setPtySize(
                        config.initialCols.coerceAtLeast(20),
                        config.initialRows.coerceAtLeast(5),
                        config.initialWidthPx.coerceAtLeast(0),
                        config.initialHeightPx.coerceAtLeast(0)
                    )
                }

                val inputStream = shell.inputStream
                val outStream = shell.outputStream

                shell.connect(config.connectTimeoutMs)

                session = newSession
                channelShell = shell
                outputStream = outStream
                _isConnected.value = true

                startStreamReader(inputStream)
            }.onFailure {
                cleanupResources()
            }
        }
    }

    private fun configureAndroidCryptoDefaults() {
        // Android's JCA does not expose JDK 15's java.security.spec.EdECPoint across API 26-33;
        // wire JSch's lightweight BouncyCastle EdDSA / X25519 implementations so ed25519 keys
        // and curve25519-sha256 KEX work reliably on all Android versions.
        val hasJdk15EdDsa = runCatching {
            Class.forName("java.security.spec.EdECPoint")
        }.isSuccess
        if (!hasJdk15EdDsa) {
            JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
            JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
            JSch.setConfig("ssh-ed448", "com.jcraft.jsch.bc.SignatureEd448")
            JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH")
        }
    }

    private fun startStreamReader(inputStream: InputStream) {
        readerJob = ioScope.launch {
            val buffer = ByteArray(4096)
            var errorMessage: String? = null
            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        _incomingBytes.emit(buffer.copyOfRange(0, bytesRead))
                    }
                }
            } catch (e: Exception) {
                if (_isConnected.value) {
                    errorMessage = e.localizedMessage ?: "SSH stream interrupted"
                }
            } finally {
                val wasConnected = _isConnected.value
                cleanupResources()
                if (wasConnected) {
                    _disconnectReason.tryEmit(errorMessage)
                }
            }
        }
    }

    fun resizePty(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        val safeCols = cols.coerceAtLeast(20)
        val safeRows = rows.coerceAtLeast(5)
        val safeWidth = widthPx.coerceAtLeast(0)
        val safeHeight = heightPx.coerceAtLeast(0)
        ioScope.launch {
            runCatching {
                val shell = channelShell
                if (shell != null && shell.isConnected) {
                    shell.setPtySize(safeCols, safeRows, safeWidth, safeHeight)
                }
            }
        }
    }

    suspend fun sendRaw(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            runCatching {
                val out = outputStream ?: error("SSH stream is not connected")
                out.write(bytes)
                out.flush()
            }
        }
    }

    suspend fun sendCommand(command: String): Result<Unit> {
        val payload = if (command.endsWith("\r") || command.endsWith("\n")) {
            command
        } else {
            "$command\r"
        }
        return sendRaw(payload.toByteArray(Charsets.UTF_8))
    }

    fun disconnect() {
        ioScope.launch {
            disconnectInternal()
        }
    }

    private suspend fun disconnectInternal() {
        val currentReader = readerJob
        readerJob = null
        currentReader?.cancelAndJoin()
        cleanupResources()
    }

    private fun cleanupResources() {
        _isConnected.value = false
        runCatching { outputStream?.close() }
        outputStream = null
        runCatching { channelShell?.disconnect() }
        channelShell = null
        runCatching { session?.disconnect() }
        session = null
    }
}

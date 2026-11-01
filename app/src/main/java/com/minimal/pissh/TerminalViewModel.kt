package com.minimal.pissh

import android.app.Application
import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AuthType {
    KEY,
    PASSWORD
}

data class HostProfile(
    val id: Int,
    val label: String,
    val host: String,
    val port: Int = 22,
    val username: String = "pi",
    val authType: AuthType = AuthType.KEY,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = ""
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class TerminalUiState(
    val profiles: List<HostProfile> = emptyList(),
    val selectedProfileIndex: Int = 0,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val terminalLines: List<String> = listOf(""),
    val fontSizeSp: Float = 12f,
    val ctrlLatched: Boolean = false,
    val keepScreenOn: Boolean = false,
    val ptyCols: Int = 80,
    val ptyRows: Int = 24,
    val ptyWidthPx: Int = 0,
    val ptyHeightPx: Int = 0
) {
    val activeProfile: HostProfile
        get() = profiles.getOrElse(selectedProfileIndex) {
            HostProfile(0, "Pi (Tailscale)", "100.64.0.1", 22, "pi", AuthType.KEY)
        }
}

/**
 * Hardware-backed AES-GCM helper using AndroidKeyStore with 0 KB external library overhead.
 */
object KeyStoreCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "PiSshProfileMasterKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ENCRYPTED_PREFIX = "enc:v1:"

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrDefault(plainText)
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        if (!cipherText.startsWith(ENCRYPTED_PREFIX)) return cipherText
        val rawBase64 = cipherText.removePrefix(ENCRYPTED_PREFIX)
        return runCatching {
            val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
            val ivSize = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivSize)
            val payload = combined.copyOfRange(1 + ivSize, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.Entry
        if (existing is KeyStore.SecretKeyEntry) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}

/**
 * Stateful UTF-8 and VT100/ANSI escape sequence processor.
 * Strips CSI (\e[...) and OSC (\e]...\a) sequences while accurately handling
 * carriage return (\r), backspace (\b), clear-to-EOL (\e[K), and clear-screen (\e[2J).
 */
class AnsiTerminalBuffer(private val maxLines: Int = 2000) {

    private enum class ParserState {
        NORMAL,
        ESC,
        CSI,
        OSC,
        OSC_ESC,
        CHARSET
    }

    private val utf8Decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private var leftoverBytes = ByteArray(0)
    private var state = ParserState.NORMAL
    private val csiParams = StringBuilder(32)

    private val completedLines = ArrayDeque<String>(maxLines)
    private val currentLine = StringBuilder(128)
    private var cursorCol = 0

    @Synchronized
    fun appendBytes(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return snapshot()

        val combined = if (leftoverBytes.isEmpty()) {
            bytes
        } else {
            ByteArray(leftoverBytes.size + bytes.size).also {
                System.arraycopy(leftoverBytes, 0, it, 0, leftoverBytes.size)
                System.arraycopy(bytes, 0, it, leftoverBytes.size, bytes.size)
            }
        }

        val byteBuffer = ByteBuffer.wrap(combined)
        val charBuffer = CharBuffer.allocate(combined.size * 2)
        utf8Decoder.decode(byteBuffer, charBuffer, false)
        charBuffer.flip()

        leftoverBytes = if (byteBuffer.hasRemaining()) {
            ByteArray(byteBuffer.remaining()).also { byteBuffer.get(it) }
        } else {
            ByteArray(0)
        }

        processChars(charBuffer)
        return snapshot()
    }

    @Synchronized
    fun processText(text: String): List<String> {
        processChars(CharBuffer.wrap(text))
        return snapshot()
    }

    @Synchronized
    fun clear(): List<String> {
        completedLines.clear()
        currentLine.setLength(0)
        cursorCol = 0
        state = ParserState.NORMAL
        csiParams.setLength(0)
        leftoverBytes = ByteArray(0)
        utf8Decoder.reset()
        return snapshot()
    }

    @Synchronized
    fun snapshot(): List<String> {
        val result = ArrayList<String>(completedLines.size + 1)
        result.addAll(completedLines)
        result.add(currentLine.toString())
        return result
    }

    private fun processChars(chars: CharSequence) {
        for (i in 0 until chars.length) {
            val ch = chars[i]
            when (state) {
                ParserState.NORMAL -> handleNormalChar(ch)
                ParserState.ESC -> handleEscChar(ch)
                ParserState.CSI -> handleCsiChar(ch)
                ParserState.OSC -> {
                    if (ch == '\u0007') {
                        state = ParserState.NORMAL
                    } else if (ch == '\u001B') {
                        state = ParserState.OSC_ESC
                    }
                }
                ParserState.OSC_ESC -> {
                    state = if (ch == '\\') ParserState.NORMAL else ParserState.OSC
                }
                ParserState.CHARSET -> {
                    state = ParserState.NORMAL
                }
            }
        }
    }

    private fun handleNormalChar(ch: Char) {
        when (ch) {
            '\u001B' -> state = ParserState.ESC
            '\r' -> cursorCol = 0
            '\n' -> {
                completedLines.addLast(currentLine.toString())
                while (completedLines.size >= maxLines) {
                    completedLines.removeFirst()
                }
                currentLine.setLength(0)
                cursorCol = 0
            }
            '\b' -> {
                if (cursorCol > 0) cursorCol--
            }
            '\t' -> {
                val spaces = 4 - (cursorCol % 4)
                repeat(spaces) { writePrintable(' ') }
            }
            '\u0007' -> Unit // Ignore terminal bell
            else -> {
                if (ch >= ' ') {
                    writePrintable(ch)
                }
            }
        }
    }

    private fun writePrintable(ch: Char) {
        while (currentLine.length < cursorCol) {
            currentLine.append(' ')
        }
        if (cursorCol < currentLine.length) {
            currentLine.setCharAt(cursorCol, ch)
        } else {
            currentLine.append(ch)
        }
        cursorCol++
    }

    private fun handleEscChar(ch: Char) {
        when (ch) {
            '[' -> {
                csiParams.setLength(0)
                state = ParserState.CSI
            }
            ']' -> state = ParserState.OSC
            '(', ')', '*', '+' -> state = ParserState.CHARSET
            'c' -> {
                // Full terminal reset (RIS)
                completedLines.clear()
                currentLine.setLength(0)
                cursorCol = 0
                state = ParserState.NORMAL
            }
            else -> state = ParserState.NORMAL
        }
    }

    private fun handleCsiChar(ch: Char) {
        if (ch.code in 0x40..0x7E) {
            val params = csiParams.toString()
            when (ch) {
                'H', 'f' -> {
                    if (params.isEmpty() || params == "1;1" || params == ";") {
                        cursorCol = 0
                    }
                }
                'J' -> {
                    if (params.isEmpty() || params == "0" || params == "2" || params == "3") {
                        completedLines.clear()
                        currentLine.setLength(0)
                        cursorCol = 0
                    }
                }
                'K' -> {
                    val mode = params.ifEmpty { "0" }
                    if (mode == "0" && cursorCol < currentLine.length) {
                        currentLine.setLength(cursorCol)
                    } else if (mode == "2") {
                        currentLine.setLength(0)
                        cursorCol = 0
                    }
                }
                'G' -> {
                    val targetCol = (params.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
                    cursorCol = targetCol
                }
                'C' -> {
                    val delta = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    cursorCol += delta
                }
                'D' -> {
                    val delta = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    cursorCol = (cursorCol - delta).coerceAtLeast(0)
                }
                'P' -> {
                    // Delete N chars at cursor (used by bash readline on backspace)
                    val count = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    if (cursorCol < currentLine.length) {
                        val end = (cursorCol + count).coerceAtMost(currentLine.length)
                        currentLine.delete(cursorCol, end)
                    }
                }
            }
            state = ParserState.NORMAL
        } else {
            if (csiParams.length < 32) {
                csiParams.append(ch)
            }
        }
    }

    companion object {
        /**
         * Converts a typed character to its ASCII control code (char.code - 64) when CTRL is latched.
         */
        fun computeCtrlByte(char: Char): Byte {
            val upperCode = char.uppercaseChar().code
            return if (upperCode in 64..95) {
                (upperCode - 64).toByte()
            } else {
                (char.code and 0x1F).toByte()
            }
        }
    }
}

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val terminalBuffer = AnsiTerminalBuffer(maxLines = 2000)

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var boundService: SshForegroundService? = null
    private var streamCollectJob: Job? = null
    private var disconnectCollectJob: Job? = null

    init {
        val loadedProfiles = loadProfiles()
        val lastSelected = prefs.getInt(KEY_SELECTED_PROFILE, 0).coerceIn(0, loadedProfiles.lastIndex)
        _uiState.update {
            it.copy(
                profiles = loadedProfiles,
                selectedProfileIndex = lastSelected
            )
        }
    }

    fun attachService(service: SshForegroundService) {
        boundService = service
        service.onUserDisconnectAction = {
            onDisconnected("Disconnected from notification")
        }

        streamCollectJob?.cancel()
        streamCollectJob = viewModelScope.launch(Dispatchers.Default) {
            service.sshClient.incomingBytes.collect { bytes ->
                val updatedLines = terminalBuffer.appendBytes(bytes)
                _uiState.update { it.copy(terminalLines = updatedLines) }
            }
        }

        disconnectCollectJob?.cancel()
        disconnectCollectJob = viewModelScope.launch {
            service.sshClient.disconnectReason.collect { reason ->
                onDisconnected(reason ?: "Remote host closed the connection")
            }
        }

        if (service.sshClient.isConnected.value) {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTED) }
        }
    }

    fun selectProfile(index: Int) {
        val profiles = _uiState.value.profiles
        if (index !in profiles.indices) return
        prefs.edit().putInt(KEY_SELECTED_PROFILE, index).apply()
        _uiState.update {
            it.copy(
                selectedProfileIndex = index,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun updateActiveProfile(transform: (HostProfile) -> HostProfile) {
        _uiState.update { state ->
            val currentList = state.profiles.toMutableList()
            val idx = state.selectedProfileIndex
            if (idx in currentList.indices) {
                currentList[idx] = transform(currentList[idx])
            }
            state.copy(profiles = currentList, errorMessage = null)
        }
    }

    fun saveActiveProfile() {
        val state = _uiState.value
        val profile = state.activeProfile
        saveProfileToPrefs(profile)
        _uiState.update { it.copy(statusMessage = "Saved profile \"${profile.label}\"") }
    }

    fun connect() {
        val service = boundService
        if (service == null) {
            _uiState.update { it.copy(errorMessage = "SSH background service is still initializing. Try again.") }
            return
        }

        val state = _uiState.value
        val profile = state.activeProfile
        if (profile.host.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Hostname or Tailscale IP is required.") }
            return
        }
        if (profile.username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Username is required.") }
            return
        }

        val authMethod = when (profile.authType) {
            AuthType.KEY -> {
                if (profile.privateKey.isBlank()) {
                    _uiState.update { it.copy(errorMessage = "Please provide an OpenSSH private key (ed25519 or rsa).") }
                    return
                }
                AuthMethod.PublicKey(
                    privateKeyPem = profile.privateKey,
                    passphrase = profile.passphrase.takeIf { it.isNotEmpty() }
                )
            }
            AuthType.PASSWORD -> {
                if (profile.password.isEmpty()) {
                    _uiState.update { it.copy(errorMessage = "Please enter a password.") }
                    return
                }
                AuthMethod.Password(profile.password)
            }
        }

        // Persist profile on connect
        saveProfileToPrefs(profile)

        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.CONNECTING,
                errorMessage = null,
                statusMessage = "Connecting to ${profile.username}@${profile.host}:${profile.port}...",
                terminalLines = terminalBuffer.clear()
            )
        }

        val config = SshConnectionConfig(
            host = profile.host,
            port = profile.port,
            username = profile.username,
            authMethod = authMethod,
            initialCols = state.ptyCols,
            initialRows = state.ptyRows,
            initialWidthPx = state.ptyWidthPx,
            initialHeightPx = state.ptyHeightPx
        )

        viewModelScope.launch {
            val result = service.sshClient.connect(config)
            result.onSuccess {
                val appContext = getApplication<Application>()
                val startIntent = Intent(appContext, SshForegroundService::class.java).apply {
                    action = SshForegroundService.ACTION_START_SESSION
                    putExtra(SshForegroundService.EXTRA_HOSTNAME, "${profile.label} (${profile.host})")
                }
                ContextCompat.startForegroundService(appContext, startIntent)
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        statusMessage = null,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        statusMessage = null,
                        errorMessage = "Connection failed: ${err.localizedMessage ?: err.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun disconnect() {
        boundService?.sshClient?.disconnect()
        boundService?.stopSessionForeground()
        onDisconnected(null)
    }

    private fun onDisconnected(reason: String?) {
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                ctrlLatched = false,
                statusMessage = null,
                errorMessage = reason
            )
        }
    }

    fun onTerminalGeometryChanged(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        val safeCols = cols.coerceIn(20, 500)
        val safeRows = rows.coerceIn(5, 200)
        val current = _uiState.value
        if (current.ptyCols == safeCols &&
            current.ptyRows == safeRows &&
            current.ptyWidthPx == widthPx &&
            current.ptyHeightPx == heightPx
        ) {
            return
        }
        _uiState.update {
            it.copy(
                ptyCols = safeCols,
                ptyRows = safeRows,
                ptyWidthPx = widthPx,
                ptyHeightPx = heightPx
            )
        }
        boundService?.sshClient?.resizePty(safeCols, safeRows, widthPx, heightPx)
    }

    fun updateFontSize(newSizeSp: Float) {
        val clamped = newSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        _uiState.update { it.copy(fontSizeSp = clamped) }
    }

    fun toggleKeepScreenOn() {
        _uiState.update { it.copy(keepScreenOn = !it.keepScreenOn) }
    }

    fun toggleCtrlLatch() {
        _uiState.update { it.copy(ctrlLatched = !it.ctrlLatched) }
    }

    /**
     * Handles single character input from soft or hardware keyboard.
     * If CTRL is latched, sends (char.code - 64) and automatically unlatches CTRL.
     */
    fun onCharacterTyped(char: Char) {
        val wasCtrlLatched = _uiState.value.ctrlLatched
        if (wasCtrlLatched) {
            _uiState.update { it.copy(ctrlLatched = false) }
            val ctrlByte = AnsiTerminalBuffer.computeCtrlByte(char)
            sendRawBytes(byteArrayOf(ctrlByte))
        } else {
            sendRawBytes(char.toString().toByteArray(Charsets.UTF_8))
        }
    }

    fun sendCtrlSignal(letter: Char) {
        _uiState.update { it.copy(ctrlLatched = false) }
        val ctrlByte = AnsiTerminalBuffer.computeCtrlByte(letter)
        sendRawBytes(byteArrayOf(ctrlByte))
    }

    fun sendVt100Sequence(sequence: String) {
        _uiState.update { it.copy(ctrlLatched = false) }
        sendRawBytes(sequence.toByteArray(Charsets.UTF_8))
    }

    fun sendRawBytes(bytes: ByteArray) {
        val client = boundService?.sshClient ?: return
        viewModelScope.launch {
            client.sendRaw(bytes)
        }
    }

    fun sendCommand(command: String) {
        val client = boundService?.sshClient ?: return
        viewModelScope.launch {
            client.sendCommand(command)
        }
    }

    fun onPasteText(text: String) {
        if (text.isEmpty()) return
        val normalized = text.replace("\r\n", "\r").replace("\n", "\r")
        sendRawBytes(normalized.toByteArray(Charsets.UTF_8))
    }

    private fun loadProfiles(): List<HostProfile> {
        val defaults = listOf(
            HostProfile(0, "Pi (Tailscale)", "raspberrypi", 22, "pi", AuthType.KEY),
            HostProfile(1, "Pi (Local LAN)", "192.168.1.100", 22, "pi", AuthType.KEY),
            HostProfile(2, "Pi #2 (Tailscale)", "100.100.100.100", 22, "pi", AuthType.PASSWORD),
            HostProfile(3, "Custom Host", "", 22, "pi", AuthType.KEY)
        )
        return defaults.map { def ->
            val prefix = "profile_${def.id}_"
            val authName = prefs.getString("${prefix}authType", def.authType.name) ?: def.authType.name
            val authType = runCatching { AuthType.valueOf(authName) }.getOrDefault(def.authType)
            HostProfile(
                id = def.id,
                label = prefs.getString("${prefix}label", def.label) ?: def.label,
                host = prefs.getString("${prefix}host", def.host) ?: def.host,
                port = prefs.getInt("${prefix}port", def.port),
                username = prefs.getString("${prefix}username", def.username) ?: def.username,
                authType = authType,
                password = KeyStoreCrypto.decrypt(prefs.getString("${prefix}password", "") ?: ""),
                privateKey = KeyStoreCrypto.decrypt(prefs.getString("${prefix}privateKey", "") ?: ""),
                passphrase = KeyStoreCrypto.decrypt(prefs.getString("${prefix}passphrase", "") ?: "")
            )
        }
    }

    private fun saveProfileToPrefs(profile: HostProfile) {
        val prefix = "profile_${profile.id}_"
        prefs.edit()
            .putString("${prefix}label", profile.label.trim())
            .putString("${prefix}host", profile.host.trim())
            .putInt("${prefix}port", profile.port.coerceIn(1, 65535))
            .putString("${prefix}username", profile.username.trim())
            .putString("${prefix}authType", profile.authType.name)
            .putString("${prefix}password", KeyStoreCrypto.encrypt(profile.password))
            .putString("${prefix}privateKey", KeyStoreCrypto.encrypt(profile.privateKey))
            .putString("${prefix}passphrase", KeyStoreCrypto.encrypt(profile.passphrase))
            .putInt(KEY_SELECTED_PROFILE, profile.id)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pissh_host_profiles"
        private const val KEY_SELECTED_PROFILE = "selected_profile_index"
        const val MIN_FONT_SIZE_SP = 9f
        const val MAX_FONT_SIZE_SP = 15f
    }
}

package com.minimal.pissh

import android.app.Application
import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val isEditorOpen: Boolean = false,
    val isCreatingNewProfile: Boolean = false,
    val editingProfile: HostProfile? = null,
    val connectingProfileId: Int? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val saveSuccessMessage: String? = null,
    val terminalLines: List<AnnotatedString> = listOf(AnnotatedString("")),
    val cursorRow: Int = 0,
    val cursorCol: Int = 0,
    val cursorVisible: Boolean = true,
    val fontSizeSp: Float = 12f,
    val ctrlLatched: Boolean = false,
    val keepScreenOn: Boolean = false,
    val ptyCols: Int = 80,
    val ptyRows: Int = 24,
    val ptyWidthPx: Int = 0,
    val ptyHeightPx: Int = 0
) {
    val activeProfile: HostProfile
        get() = editingProfile ?: profiles.getOrElse(selectedProfileIndex) {
            HostProfile(0, "New Connection", "", 22, "pi", AuthType.KEY)
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
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
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

val DefaultTerminalFgColor = Color(0xFFE6EDF3)
val FaintTerminalFgColor = Color(0xFF6E7681) // Grey for zsh autosuggestions

data class AnsiStyle(
    val color: Color = DefaultTerminalFgColor,
    val bold: Boolean = false,
    val faint: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false
)

class TerminalLine(
    val chars: StringBuilder = StringBuilder(128),
    val styles: ArrayList<AnsiStyle> = ArrayList(128),
    var isDirty: Boolean = true,
    var cachedAnnotatedString: AnnotatedString? = null
) {
    fun markDirty() {
        isDirty = true
    }

    fun getAnnotatedString(): AnnotatedString {
        if (isDirty || cachedAnnotatedString == null) {
            cachedAnnotatedString = AnsiTerminalBuffer.buildAnnotatedString(chars, styles)
            isDirty = false
        }
        return cachedAnnotatedString!!
    }
}

/**
 * Stateful UTF-8 and VT100/ANSI escape sequence processor.
 * Accurately processes 16-color, 256-color, bold, and faint text styling (used by zsh-autosuggestions),
 * carriage returns, backspaces, screen redrawing without wiping scrollback, and cursor position tracking.
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

    private val lines = ArrayList<TerminalLine>(maxLines).apply {
        add(TerminalLine())
    }
    private var cursorRow = 0
    private var cursorCol = 0
    private var cursorVisible = true
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var currentStyle = AnsiStyle()

    val currentCursorRow: Int
        @Synchronized get() = cursorRow

    val currentCursorCol: Int
        @Synchronized get() = cursorCol

    val isCursorVisible: Boolean
        @Synchronized get() = cursorVisible

    private fun getOrCreateLine(row: Int): TerminalLine {
        while (lines.size <= row) {
            lines.add(TerminalLine())
        }
        return lines[row]
    }

    @Synchronized
    fun appendBytes(bytes: ByteArray): List<AnnotatedString> {
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
    fun processText(text: String): List<AnnotatedString> {
        processChars(CharBuffer.wrap(text))
        return snapshot()
    }

    @Synchronized
    fun clear(): List<AnnotatedString> {
        lines.clear()
        lines.add(TerminalLine())
        cursorRow = 0
        cursorCol = 0
        cursorVisible = true
        savedCursorRow = 0
        savedCursorCol = 0
        currentStyle = AnsiStyle()
        state = ParserState.NORMAL
        csiParams.setLength(0)
        leftoverBytes = ByteArray(0)
        utf8Decoder.reset()
        return snapshot()
    }

    @Synchronized
    fun snapshot(): List<AnnotatedString> {
        if (lines.isEmpty()) return listOf(AnnotatedString(""))
        val result = ArrayList<AnnotatedString>(lines.size)
        for (i in 0 until lines.size) {
            result.add(lines[i].getAnnotatedString())
        }
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
                cursorRow++
                getOrCreateLine(cursorRow)
                while (lines.size > maxLines) {
                    lines.removeAt(0)
                    cursorRow = (cursorRow - 1).coerceAtLeast(0)
                    savedCursorRow = (savedCursorRow - 1).coerceAtLeast(0)
                }
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
        val line = getOrCreateLine(cursorRow)
        line.markDirty()
        while (line.chars.length < cursorCol) {
            line.chars.append(' ')
            line.styles.add(AnsiStyle())
        }
        if (cursorCol < line.chars.length) {
            line.chars.setCharAt(cursorCol, ch)
            line.styles[cursorCol] = currentStyle
        } else {
            line.chars.append(ch)
            line.styles.add(currentStyle)
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
            '7' -> {
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
                state = ParserState.NORMAL
            }
            '8' -> {
                cursorRow = savedCursorRow.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
                cursorCol = savedCursorCol.coerceAtLeast(0)
                state = ParserState.NORMAL
            }
            'c' -> {
                // Full terminal reset (RIS)
                lines.clear()
                lines.add(TerminalLine())
                cursorRow = 0
                cursorCol = 0
                cursorVisible = true
                savedCursorRow = 0
                savedCursorCol = 0
                currentStyle = AnsiStyle()
                state = ParserState.NORMAL
            }
            else -> state = ParserState.NORMAL
        }
    }

    private fun handleCsiChar(ch: Char) {
        if (ch.code in 0x40..0x7E) {
            val params = csiParams.toString()
            when (ch) {
                'm' -> handleSgr(params)
                'A' -> {
                    val count = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    cursorRow = (cursorRow - count).coerceAtLeast(0)
                }
                'B' -> {
                    val count = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    cursorRow = (cursorRow + count).coerceAtMost((lines.size - 1).coerceAtLeast(0))
                }
                'C' -> {
                    val count = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    cursorCol += count
                }
                'D' -> {
                    val count = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    cursorCol = (cursorCol - count).coerceAtLeast(0)
                }
                'E' -> {
                    val count = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    cursorRow = (cursorRow + count).coerceAtMost((lines.size - 1).coerceAtLeast(0))
                    cursorCol = 0
                }
                'F' -> {
                    val count = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    cursorRow = (cursorRow - count).coerceAtLeast(0)
                    cursorCol = 0
                }
                'G' -> {
                    cursorCol = (params.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
                }
                'd' -> {
                    val targetRow = (params.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
                    getOrCreateLine(targetRow)
                    cursorRow = targetRow
                }
                'H', 'f' -> {
                    if (params.isEmpty() || params == ";") {
                        cursorRow = 0
                        cursorCol = 0
                    } else {
                        val parts = params.split(';')
                        val row = (parts.getOrNull(0)?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
                        val col = (parts.getOrNull(1)?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
                        getOrCreateLine(row)
                        cursorRow = row
                        cursorCol = col
                    }
                }
                'J' -> {
                    when (params) {
                        "2", "3" -> {
                            // Clear visible screen / scrollback
                            lines.clear()
                            lines.add(TerminalLine())
                            cursorRow = 0
                            cursorCol = 0
                        }
                        else -> {
                            // "0" or empty: Erase from cursor to end of screen.
                            if (cursorRow < lines.size) {
                                val line = lines[cursorRow]
                                line.markDirty()
                                if (cursorCol < line.chars.length) {
                                    line.chars.setLength(cursorCol)
                                    while (line.styles.size > cursorCol) {
                                        line.styles.removeAt(line.styles.size - 1)
                                    }
                                }
                                while (lines.size > cursorRow + 1) {
                                    lines.removeAt(lines.size - 1)
                                }
                            }
                        }
                    }
                }
                'K' -> {
                    val mode = params.ifEmpty { "0" }
                    if (cursorRow < lines.size) {
                        val line = lines[cursorRow]
                        line.markDirty()
                        when (mode) {
                            "0" -> {
                                if (cursorCol < line.chars.length) {
                                    line.chars.setLength(cursorCol)
                                    while (line.styles.size > cursorCol) {
                                        line.styles.removeAt(line.styles.size - 1)
                                    }
                                }
                            }
                            "1" -> {
                                for (col in 0 until cursorCol.coerceAtMost(line.chars.length)) {
                                    line.chars.setCharAt(col, ' ')
                                    if (col < line.styles.size) {
                                        line.styles[col] = AnsiStyle()
                                    }
                                }
                            }
                            "2" -> {
                                line.chars.setLength(0)
                                line.styles.clear()
                                cursorCol = 0
                            }
                        }
                    }
                }
                'P' -> {
                    val count = (params.toIntOrNull() ?: 1).coerceAtLeast(1)
                    if (cursorRow < lines.size) {
                        val line = lines[cursorRow]
                        line.markDirty()
                        if (cursorCol < line.chars.length) {
                            val end = (cursorCol + count).coerceAtMost(line.chars.length)
                            line.chars.delete(cursorCol, end)
                            for (k in cursorCol until end) {
                                if (cursorCol < line.styles.size) {
                                    line.styles.removeAt(cursorCol)
                                }
                            }
                        }
                    }
                }
                's' -> {
                    savedCursorRow = cursorRow
                    savedCursorCol = cursorCol
                }
                'u' -> {
                    cursorRow = savedCursorRow.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
                    cursorCol = savedCursorCol.coerceAtLeast(0)
                }
                'h' -> {
                    if (params == "?25") cursorVisible = true
                }
                'l' -> {
                    if (params == "?25") cursorVisible = false
                }
            }
            state = ParserState.NORMAL
        } else {
            if (csiParams.length < 32) {
                csiParams.append(ch)
            }
        }
    }

    private fun handleSgr(params: String) {
        if (params.isEmpty() || params == "0") {
            currentStyle = AnsiStyle()
            return
        }
        val tokens = params.split(';')
        var i = 0
        while (i < tokens.size) {
            val code = tokens[i].toIntOrNull() ?: 0
            when (code) {
                0 -> currentStyle = AnsiStyle()
                1 -> currentStyle = currentStyle.copy(bold = true)
                2 -> currentStyle = currentStyle.copy(faint = true, color = FaintTerminalFgColor)
                3 -> currentStyle = currentStyle.copy(italic = true)
                4 -> currentStyle = currentStyle.copy(underline = true)
                22 -> currentStyle = currentStyle.copy(bold = false, faint = false)
                23 -> currentStyle = currentStyle.copy(italic = false)
                24 -> currentStyle = currentStyle.copy(underline = false)
                30 -> currentStyle = currentStyle.copy(color = Color(0xFF484F58))
                31 -> currentStyle = currentStyle.copy(color = Color(0xFFF85149))
                32 -> currentStyle = currentStyle.copy(color = Color(0xFF3FB950))
                33 -> currentStyle = currentStyle.copy(color = Color(0xFFD29922))
                34 -> currentStyle = currentStyle.copy(color = Color(0xFF58A6FF))
                35 -> currentStyle = currentStyle.copy(color = Color(0xFFBC8CFF))
                36 -> currentStyle = currentStyle.copy(color = Color(0xFF39C5CF))
                37 -> currentStyle = currentStyle.copy(color = Color(0xFFE6EDF3))
                39 -> currentStyle = currentStyle.copy(color = DefaultTerminalFgColor)
                90 -> currentStyle = currentStyle.copy(color = FaintTerminalFgColor) // Bright Black / Grey (zsh-autosuggestions)
                91 -> currentStyle = currentStyle.copy(color = Color(0xFFFF7B72))
                92 -> currentStyle = currentStyle.copy(color = Color(0xFF56D364))
                93 -> currentStyle = currentStyle.copy(color = Color(0xFFE3B341))
                94 -> currentStyle = currentStyle.copy(color = Color(0xFF79C0FF))
                95 -> currentStyle = currentStyle.copy(color = Color(0xFFD2A8FF))
                96 -> currentStyle = currentStyle.copy(color = Color(0xFF56D4DD))
                97 -> currentStyle = currentStyle.copy(color = Color(0xFFFFFFFF))
                38 -> {
                    if (i + 2 < tokens.size && tokens[i + 1] == "5") {
                        val colorIdx = tokens[i + 2].toIntOrNull() ?: 0
                        currentStyle = currentStyle.copy(color = parse256Color(colorIdx))
                        i += 2
                    }
                }
            }
            i++
        }
    }

    private fun parse256Color(n: Int): Color {
        return when (n) {
            0 -> Color(0xFF0D1117)
            1 -> Color(0xFFF85149)
            2 -> Color(0xFF3FB950)
            3 -> Color(0xFFD29922)
            4 -> Color(0xFF58A6FF)
            5 -> Color(0xFFBC8CFF)
            6 -> Color(0xFF39C5CF)
            7 -> Color(0xFFE6EDF3)
            8 -> FaintTerminalFgColor // Color 8 = Grey (zsh-autosuggestions)
            9 -> Color(0xFFFF7B72)
            10 -> Color(0xFF56D364)
            11 -> Color(0xFFE3B341)
            12 -> Color(0xFF79C0FF)
            13 -> Color(0xFFD2A8FF)
            14 -> Color(0xFF56D4DD)
            15 -> Color(0xFFFFFFFF)
            in 16..231 -> {
                val idx = n - 16
                val r = ((idx / 36) * 51).coerceIn(0, 255)
                val g = (((idx % 36) / 6) * 51).coerceIn(0, 255)
                val b = ((idx % 6) * 51).coerceIn(0, 255)
                Color(r, g, b)
            }
            in 232..255 -> {
                val gray = (8 + (n - 232) * 10).coerceIn(0, 255)
                Color(gray, gray, gray)
            }
            else -> DefaultTerminalFgColor
        }
    }

    companion object {
        fun buildAnnotatedString(chars: CharSequence, styles: List<AnsiStyle>): AnnotatedString {
            if (chars.isEmpty()) return AnnotatedString("")
            val builder = AnnotatedString.Builder(chars.toString())
            var spanStart = 0
            var currentSpanStyle: AnsiStyle? = null

            for (i in 0 until chars.length) {
                val style = if (i < styles.size) styles[i] else AnsiStyle()
                if (style != currentSpanStyle) {
                    if (currentSpanStyle != null && currentSpanStyle != AnsiStyle()) {
                        builder.addStyle(
                            toSpanStyle(currentSpanStyle),
                            spanStart,
                            i
                        )
                    }
                    spanStart = i
                    currentSpanStyle = style
                }
            }
            if (currentSpanStyle != null && currentSpanStyle != AnsiStyle()) {
                builder.addStyle(
                    toSpanStyle(currentSpanStyle),
                    spanStart,
                    chars.length
                )
            }
            return builder.toAnnotatedString()
        }

        private fun toSpanStyle(style: AnsiStyle): SpanStyle {
            val effectiveColor = if (style.faint && style.color == DefaultTerminalFgColor) {
                FaintTerminalFgColor
            } else {
                style.color
            }
            return SpanStyle(
                color = effectiveColor,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (style.underline) TextDecoration.Underline else TextDecoration.None
            )
        }

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
        val lastSelectedId = prefs.getInt(KEY_SELECTED_PROFILE, 0)
        val selectedIdx = loadedProfiles.indexOfFirst { it.id == lastSelectedId }.let { if (it >= 0) it else 0 }
        _uiState.update {
            it.copy(
                profiles = loadedProfiles,
                selectedProfileIndex = selectedIdx
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
                _uiState.update {
                    it.copy(
                        terminalLines = updatedLines,
                        cursorRow = terminalBuffer.currentCursorRow,
                        cursorCol = terminalBuffer.currentCursorCol,
                        cursorVisible = terminalBuffer.isCursorVisible
                    )
                }
            }
        }

        disconnectCollectJob?.cancel()
        disconnectCollectJob = viewModelScope.launch {
            service.sshClient.disconnectReason.collect { reason ->
                onDisconnected(reason ?: "Remote host closed the connection")
            }
        }

        if (service.sshClient.isConnected.value) {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.CONNECTED,
                    terminalLines = terminalBuffer.snapshot(),
                    cursorRow = terminalBuffer.currentCursorRow,
                    cursorCol = terminalBuffer.currentCursorCol,
                    cursorVisible = terminalBuffer.isCursorVisible
                )
            }
        }
    }

    private var saveSuccessJob: Job? = null

    fun startCreateProfile() {
        val nextId = getNextProfileId()
        val newProfile = HostProfile(
            id = nextId,
            label = "",
            host = "",
            port = 22,
            username = "pi",
            authType = AuthType.KEY
        )
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                isCreatingNewProfile = true,
                editingProfile = newProfile,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun startEditProfile(profileId: Int) {
        val profiles = _uiState.value.profiles
        val index = profiles.indexOfFirst { it.id == profileId }
        val targetProfile = if (index >= 0) profiles[index] else return
        saveSuccessJob?.cancel()
        _uiState.update {
            it.copy(
                selectedProfileIndex = index,
                isEditorOpen = true,
                isCreatingNewProfile = false,
                editingProfile = targetProfile.copy(),
                errorMessage = null,
                statusMessage = null,
                saveSuccessMessage = null
            )
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                isEditorOpen = false,
                isCreatingNewProfile = false,
                editingProfile = null,
                errorMessage = null
            )
        }
    }

    fun deleteProfile(profileId: Int) {
        deleteProfileFromPrefs(profileId)
        _uiState.update { state ->
            val updatedList = state.profiles.filter { it.id != profileId }
            val newIndex = if (updatedList.isEmpty()) 0 else state.selectedProfileIndex.coerceIn(0, updatedList.lastIndex)
            state.copy(
                profiles = updatedList,
                selectedProfileIndex = newIndex,
                isEditorOpen = false,
                isCreatingNewProfile = false,
                editingProfile = null,
                statusMessage = "Connection deleted",
                errorMessage = null
            )
        }
    }

    fun moveProfile(fromIndex: Int, toIndex: Int) {
        val currentList = _uiState.value.profiles.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        prefs.edit().putString(KEY_PROFILE_IDS, currentList.joinToString(",") { it.id.toString() }).apply()
        _uiState.update { state ->
            val targetId = state.activeProfile.id
            val newSelected = currentList.indexOfFirst { it.id == targetId }.let {
                if (it >= 0) it else 0
            }
            state.copy(
                profiles = currentList,
                selectedProfileIndex = newSelected
            )
        }
    }

    fun moveProfileUp(profileId: Int) {
        val list = _uiState.value.profiles
        val idx = list.indexOfFirst { it.id == profileId }
        if (idx > 0) {
            moveProfile(idx, idx - 1)
        }
    }

    fun moveProfileDown(profileId: Int) {
        val list = _uiState.value.profiles
        val idx = list.indexOfFirst { it.id == profileId }
        if (idx in 0 until list.lastIndex) {
            moveProfile(idx, idx + 1)
        }
    }

    fun connectProfile(profileId: Int) {
        val profiles = _uiState.value.profiles
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index >= 0) {
            _uiState.update {
                it.copy(
                    selectedProfileIndex = index,
                    connectingProfileId = profileId
                )
            }
            connect()
        }
    }

    fun selectProfile(index: Int) {
        val profiles = _uiState.value.profiles
        if (index !in profiles.indices) return
        saveSuccessJob?.cancel()
        prefs.edit().putInt(KEY_SELECTED_PROFILE, profiles[index].id).apply()
        _uiState.update {
            it.copy(
                selectedProfileIndex = index,
                errorMessage = null,
                statusMessage = null,
                saveSuccessMessage = null
            )
        }
    }

    fun updateActiveProfile(transform: (HostProfile) -> HostProfile) {
        _uiState.update { state ->
            if (state.editingProfile != null) {
                state.copy(editingProfile = transform(state.editingProfile), errorMessage = null)
            } else {
                val currentList = state.profiles.toMutableList()
                val idx = state.selectedProfileIndex
                if (idx in currentList.indices) {
                    currentList[idx] = transform(currentList[idx])
                }
                state.copy(profiles = currentList, errorMessage = null)
            }
        }
    }

    fun saveActiveProfile(): Boolean {
        return try {
            val state = _uiState.value
            val profileToSave = state.editingProfile ?: state.activeProfile
            saveProfileToPrefs(profileToSave)
            val updatedList = state.profiles.toMutableList()
            val existingIndex = updatedList.indexOfFirst { it.id == profileToSave.id }
            if (existingIndex >= 0) {
                updatedList[existingIndex] = profileToSave
            } else {
                updatedList.add(profileToSave)
            }
            val newIndex = updatedList.indexOfFirst { it.id == profileToSave.id }.coerceAtLeast(0)
            val labelText = profileToSave.label.ifBlank { "Host ${profileToSave.id + 1}" }
            _uiState.update {
                it.copy(
                    profiles = updatedList,
                    selectedProfileIndex = newIndex,
                    isEditorOpen = false,
                    isCreatingNewProfile = false,
                    editingProfile = null,
                    saveSuccessMessage = "Saved profile \"$labelText\"",
                    errorMessage = null
                )
            }
            saveSuccessJob?.cancel()
            saveSuccessJob = viewModelScope.launch {
                delay(2500)
                _uiState.update { it.copy(saveSuccessMessage = null) }
            }
            true
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to save profile: ${e.message}") }
            false
        }
    }

    fun dismissSaveSuccess() {
        saveSuccessJob?.cancel()
        _uiState.update { it.copy(saveSuccessMessage = null) }
    }

    fun connect() {
        val service = boundService
        if (service == null) {
            _uiState.update {
                it.copy(
                    connectingProfileId = null,
                    errorMessage = "SSH background service is still initializing. Try again."
                )
            }
            return
        }

        val state = _uiState.value
        val profile = state.editingProfile ?: state.activeProfile
        if (profile.host.isBlank()) {
            _uiState.update {
                it.copy(
                    connectingProfileId = null,
                    errorMessage = "Hostname or Tailscale IP is required."
                )
            }
            return
        }
        if (profile.username.isBlank()) {
            _uiState.update {
                it.copy(
                    connectingProfileId = null,
                    errorMessage = "Username is required."
                )
            }
            return
        }

        val authMethod = when (profile.authType) {
            AuthType.KEY -> {
                if (profile.privateKey.isBlank()) {
                    _uiState.update {
                        it.copy(
                            connectingProfileId = null,
                            errorMessage = "Please provide an OpenSSH private key (ed25519 or rsa)."
                        )
                    }
                    return
                }
                AuthMethod.PublicKey(
                    privateKeyPem = profile.privateKey,
                    passphrase = profile.passphrase.takeIf { it.isNotEmpty() }
                )
            }
            AuthType.PASSWORD -> {
                if (profile.password.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            connectingProfileId = null,
                            errorMessage = "Please enter a password."
                        )
                    }
                    return
                }
                AuthMethod.Password(profile.password)
            }
        }

        // Persist profile on connect
        saveProfileToPrefs(profile)
        val updatedProfiles = state.profiles.toMutableList()
        val existingIndex = updatedProfiles.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            updatedProfiles[existingIndex] = profile
        } else {
            updatedProfiles.add(profile)
        }

        _uiState.update {
            it.copy(
                profiles = updatedProfiles,
                isEditorOpen = false,
                isCreatingNewProfile = false,
                editingProfile = null,
                connectingProfileId = profile.id,
                connectionStatus = ConnectionStatus.CONNECTING,
                errorMessage = null,
                statusMessage = "Connecting to ${profile.username}@${profile.host}:${profile.port}...",
                terminalLines = terminalBuffer.clear(),
                cursorRow = terminalBuffer.currentCursorRow,
                cursorCol = terminalBuffer.currentCursorCol,
                cursorVisible = terminalBuffer.isCursorVisible
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
                        connectingProfileId = null,
                        statusMessage = null,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        connectingProfileId = null,
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
                connectingProfileId = null,
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

    private fun getNextProfileId(): Int {
        val currentMax = _uiState.value.profiles.maxOfOrNull { it.id } ?: -1
        val storedNext = prefs.getInt(KEY_NEXT_PROFILE_ID, currentMax + 1)
        val nextId = maxOf(currentMax + 1, storedNext)
        prefs.edit().putInt(KEY_NEXT_PROFILE_ID, nextId + 1).apply()
        return nextId
    }

    private fun getStoredProfileIds(): List<Int> {
        val raw = prefs.getString(KEY_PROFILE_IDS, null) ?: return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun loadProfiles(): List<HostProfile> {
        val storedIds = getStoredProfileIds()
        if (prefs.contains(KEY_PROFILE_IDS)) {
            return storedIds.mapNotNull { id ->
                loadProfileById(id)
            }
        }

        // Migration or first launch
        val defaults = listOf(
            HostProfile(0, "Pi (Tailscale)", "raspberrypi", 22, "pi", AuthType.KEY),
            HostProfile(1, "Pi (Local LAN)", "192.168.1.100", 22, "pi", AuthType.KEY),
            HostProfile(2, "Pi #2 (Tailscale)", "100.100.100.100", 22, "pi", AuthType.PASSWORD),
            HostProfile(3, "Custom Host", "", 22, "pi", AuthType.KEY)
        )

        val loaded = defaults.mapNotNull { def ->
            val prefix = "profile_${def.id}_"
            val hasCustomData = prefs.contains("${prefix}host") || prefs.contains("${prefix}label")
            val host = prefs.getString("${prefix}host", def.host) ?: def.host
            val label = prefs.getString("${prefix}label", def.label) ?: def.label

            // Filter out blank custom host if user never configured it
            if (!hasCustomData && def.id == 3 && host.isBlank()) {
                null
            } else {
                val authName = prefs.getString("${prefix}authType", def.authType.name) ?: def.authType.name
                val authType = runCatching { AuthType.valueOf(authName) }.getOrDefault(def.authType)
                HostProfile(
                    id = def.id,
                    label = label,
                    host = host,
                    port = prefs.getInt("${prefix}port", def.port),
                    username = prefs.getString("${prefix}username", def.username) ?: def.username,
                    authType = authType,
                    password = KeyStoreCrypto.decrypt(prefs.getString("${prefix}password", "") ?: ""),
                    privateKey = KeyStoreCrypto.decrypt(prefs.getString("${prefix}privateKey", "") ?: ""),
                    passphrase = KeyStoreCrypto.decrypt(prefs.getString("${prefix}passphrase", "") ?: "")
                )
            }
        }

        val ids = loaded.map { it.id }
        prefs.edit()
            .putString(KEY_PROFILE_IDS, ids.joinToString(","))
            .putInt(KEY_NEXT_PROFILE_ID, (ids.maxOrNull() ?: -1) + 1)
            .apply()

        return loaded
    }

    private fun loadProfileById(id: Int): HostProfile? {
        val prefix = "profile_${id}_"
        val hasCustomData = prefs.contains("${prefix}host") || prefs.contains("${prefix}label")
        if (!hasCustomData && id !in 0..2) {
            return null
        }
        val defaultDef = when (id) {
            0 -> HostProfile(0, "Pi (Tailscale)", "raspberrypi", 22, "pi", AuthType.KEY)
            1 -> HostProfile(1, "Pi (Local LAN)", "192.168.1.100", 22, "pi", AuthType.KEY)
            2 -> HostProfile(2, "Pi #2 (Tailscale)", "100.100.100.100", 22, "pi", AuthType.PASSWORD)
            else -> HostProfile(id, "Host ${id + 1}", "", 22, "pi", AuthType.KEY)
        }

        val authName = prefs.getString("${prefix}authType", defaultDef.authType.name) ?: defaultDef.authType.name
        val authType = runCatching { AuthType.valueOf(authName) }.getOrDefault(defaultDef.authType)

        return HostProfile(
            id = id,
            label = prefs.getString("${prefix}label", defaultDef.label) ?: defaultDef.label,
            host = prefs.getString("${prefix}host", defaultDef.host) ?: defaultDef.host,
            port = prefs.getInt("${prefix}port", defaultDef.port),
            username = prefs.getString("${prefix}username", defaultDef.username) ?: defaultDef.username,
            authType = authType,
            password = KeyStoreCrypto.decrypt(prefs.getString("${prefix}password", "") ?: ""),
            privateKey = KeyStoreCrypto.decrypt(prefs.getString("${prefix}privateKey", "") ?: ""),
            passphrase = KeyStoreCrypto.decrypt(prefs.getString("${prefix}passphrase", "") ?: "")
        )
    }

    private fun saveProfileToPrefs(profile: HostProfile) {
        val prefix = "profile_${profile.id}_"
        val editor = prefs.edit()
            .putString("${prefix}label", profile.label.trim())
            .putString("${prefix}host", profile.host.trim())
            .putInt("${prefix}port", profile.port.coerceIn(1, 65535))
            .putString("${prefix}username", profile.username.trim())
            .putString("${prefix}authType", profile.authType.name)
            .putString("${prefix}password", KeyStoreCrypto.encrypt(profile.password))
            .putString("${prefix}privateKey", KeyStoreCrypto.encrypt(profile.privateKey))
            .putString("${prefix}passphrase", KeyStoreCrypto.encrypt(profile.passphrase))
            .putInt(KEY_SELECTED_PROFILE, profile.id)

        val currentIds = getStoredProfileIds().toMutableList()
        if (!currentIds.contains(profile.id)) {
            currentIds.add(profile.id)
            editor.putString(KEY_PROFILE_IDS, currentIds.joinToString(","))
        }
        editor.apply()
    }

    private fun deleteProfileFromPrefs(profileId: Int) {
        val prefix = "profile_${profileId}_"
        val editor = prefs.edit()
        editor.remove("${prefix}label")
        editor.remove("${prefix}host")
        editor.remove("${prefix}port")
        editor.remove("${prefix}username")
        editor.remove("${prefix}authType")
        editor.remove("${prefix}password")
        editor.remove("${prefix}privateKey")
        editor.remove("${prefix}passphrase")

        val currentIds = getStoredProfileIds().filter { it != profileId }
        editor.putString(KEY_PROFILE_IDS, currentIds.joinToString(","))
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "pissh_host_profiles"
        private const val KEY_SELECTED_PROFILE = "selected_profile_index"
        private const val KEY_PROFILE_IDS = "profile_ids"
        private const val KEY_NEXT_PROFILE_ID = "next_profile_id"
        const val MIN_FONT_SIZE_SP = 9f
        const val MAX_FONT_SIZE_SP = 15f
    }
}

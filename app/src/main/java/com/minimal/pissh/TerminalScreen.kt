package com.minimal.pissh

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    uiState: TerminalUiState,
    onToggleKeepScreenOn: () -> Unit,
    onToggleCtrlLatch: () -> Unit,
    onUpdateFontSize: (Float) -> Unit,
    onTerminalGeometryChanged: (cols: Int, rows: Int, widthPx: Int, heightPx: Int) -> Unit,
    onCharacterTyped: (Char) -> Unit,
    onSendVt100: (String) -> Unit,
    onSendCtrlSignal: (Char) -> Unit,
    onSendRawBytes: (ByteArray) -> Unit,
    onPasteText: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val textMeasurer = rememberTextMeasurer()

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    // Sentinel TextFieldValue with a leading zero-width space and cursor fixed at index 1.
    // Uses neverEqualPolicy() so resetting to the sentinel always resets the IME buffer.
    var softInputValue by remember {
        mutableStateOf(
            TextFieldValue(text = "\u200B", selection = TextRange(1)),
            policy = neverEqualPolicy()
        )
    }

    val lineHeightSp = (uiState.fontSizeSp * 1.28f)
    val terminalTextStyle = remember(uiState.fontSizeSp) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = uiState.fontSizeSp.sp,
            lineHeight = lineHeightSp.sp,
            color = Color(0xFFE6EDF3)
        )
    }

    // Dynamically measure monospace cell dimensions and notify SSH PTY (SIGWINCH)
    LaunchedEffect(viewportSize, uiState.fontSizeSp) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            val sampleLayout = textMeasurer.measure(
                text = "MMMMMMMMMM",
                style = terminalTextStyle
            )
            val charWidthPx = (sampleLayout.size.width / 10f).coerceAtLeast(4f)
            val charHeightPx = sampleLayout.size.height.toFloat().coerceAtLeast(8f)

            val cols = (viewportSize.width / charWidthPx).toInt().coerceAtLeast(20)
            val rows = (viewportSize.height / charHeightPx).toInt().coerceAtLeast(5)
            onTerminalGeometryChanged(cols, rows, viewportSize.width, viewportSize.height)
        }
    }

    // Auto-scroll to bottom when new terminal lines arrive
    LaunchedEffect(uiState.terminalLines.size, uiState.terminalLines.lastOrNull()) {
        if (uiState.terminalLines.isNotEmpty()) {
            listState.scrollToItem(uiState.terminalLines.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .imePadding()
            .navigationBarsPadding()
            .onPreviewKeyEvent { event ->
                handleHardwareKeyEvent(
                    event = event,
                    onCharacterTyped = onCharacterTyped,
                    onSendVt100 = onSendVt100,
                    onSendCtrlSignal = onSendCtrlSignal,
                    onSendRawBytes = onSendRawBytes
                )
            }
    ) {
        // Minimal Top Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = uiState.activeProfile.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "${uiState.activeProfile.username}@${uiState.activeProfile.host} • ${uiState.ptyCols}×${uiState.ptyRows} • ${uiState.fontSizeSp.roundToInt()}sp",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            },
            actions = {
                // Keep Screen On Toggle (FLAG_KEEP_SCREEN_ON)
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleKeepScreenOn()
                    }
                ) {
                    Text(
                        text = if (uiState.keepScreenOn) "Awake: ON" else "Awake: OFF",
                        color = if (uiState.keepScreenOn) {
                            Color(0xFF3FB950)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Disconnect Action
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDisconnect()
                    }
                ) {
                    Text(
                        text = "Disconnect",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF161B22)
            )
        )

        // Monospace Terminal Output Canvas with Pinch-to-Zoom (9sp..15sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .onSizeChanged { viewportSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            val updated = (uiState.fontSizeSp * zoom).coerceIn(
                                TerminalViewModel.MIN_FONT_SIZE_SP,
                                TerminalViewModel.MAX_FONT_SIZE_SP
                            )
                            onUpdateFontSize(updated)
                        }
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(uiState.terminalLines) { _, line ->
                    Text(
                        text = line,
                        style = terminalTextStyle,
                        softWrap = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Invisible BasicTextField to capture soft-keyboard characters & Backspace
            BasicTextField(
                value = softInputValue,
                onValueChange = { newValue ->
                    val currentText = newValue.text
                    if (!currentText.contains('\u200B')) {
                        // Soft keyboard Backspace deleted the sentinel character
                        onSendRawBytes(byteArrayOf(0x7F))
                    } else {
                        val added = currentText.replace("\u200B", "")
                        for (ch in added) {
                            if (ch == '\n' || ch == '\r') {
                                onSendRawBytes(byteArrayOf(0x0D))
                            } else {
                                onCharacterTyped(ch)
                            }
                        }
                    }
                    softInputValue = TextFieldValue(text = "\u200B", selection = TextRange(1))
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSendRawBytes(byteArrayOf(0x0D)) },
                    onDone = { onSendRawBytes(byteArrayOf(0x0D)) }
                ),
                cursorBrush = SolidColor(Color.Transparent),
                textStyle = TextStyle(fontSize = 1.sp, color = Color.Transparent),
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        // Touch Accessory Bar above Soft Keyboard
        TouchAccessoryBar(
            ctrlLatched = uiState.ctrlLatched,
            onToggleCtrl = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleCtrlLatch()
            },
            onSendVt100 = { seq ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSendVt100(seq)
            },
            onSendCtrlSignal = { sig ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSendCtrlSignal(sig)
            },
            onPaste = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val pasted = clipboard?.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                if (!pasted.isNullOrEmpty()) {
                    onPasteText(pasted)
                }
            }
        )
    }
}

@Composable
private fun TouchAccessoryBar(
    ctrlLatched: Boolean,
    onToggleCtrl: () -> Unit,
    onSendVt100: (String) -> Unit,
    onSendCtrlSignal: (Char) -> Unit,
    onPaste: () -> Unit
) {
    Surface(
        color = Color(0xFF161B22),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccessoryKeyButton(
                label = "CTRL",
                isHighlighted = ctrlLatched,
                onClick = onToggleCtrl
            )
            AccessoryKeyButton(label = "ESC", onClick = { onSendVt100("\u001B") })
            AccessoryKeyButton(label = "TAB", onClick = { onSendVt100("\t") })

            // VT100 Arrow Navigation Keys
            AccessoryKeyButton(label = "▲", onClick = { onSendVt100("\u001B[A") })
            AccessoryKeyButton(label = "▼", onClick = { onSendVt100("\u001B[B") })
            AccessoryKeyButton(label = "◄", onClick = { onSendVt100("\u001B[D") })
            AccessoryKeyButton(label = "►", onClick = { onSendVt100("\u001B[C") })

            // Common Signals
            AccessoryKeyButton(label = "Ctrl+C", onClick = { onSendCtrlSignal('C') })
            AccessoryKeyButton(label = "Ctrl+D", onClick = { onSendCtrlSignal('D') })
            AccessoryKeyButton(label = "Ctrl+Z", onClick = { onSendCtrlSignal('Z') })

            // Clipboard Paste
            AccessoryKeyButton(label = "Paste", onClick = onPaste)
        }
    }
}

@Composable
private fun AccessoryKeyButton(
    label: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isHighlighted) Color(0xFF1F6FEB) else Color(0xFF21262D)
    val borderColor = if (isHighlighted) Color(0xFF58A6FF) else Color(0xFF30363D)
    val textColor = if (isHighlighted) Color.White else Color(0xFFC9D1D9)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

private fun handleHardwareKeyEvent(
    event: KeyEvent,
    onCharacterTyped: (Char) -> Unit,
    onSendVt100: (String) -> Unit,
    onSendCtrlSignal: (Char) -> Unit,
    onSendRawBytes: (ByteArray) -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    when (event.key) {
        Key.DirectionUp -> {
            onSendVt100("\u001B[A")
            return true
        }
        Key.DirectionDown -> {
            onSendVt100("\u001B[B")
            return true
        }
        Key.DirectionRight -> {
            onSendVt100("\u001B[C")
            return true
        }
        Key.DirectionLeft -> {
            onSendVt100("\u001B[D")
            return true
        }
        Key.Tab -> {
            onSendVt100("\t")
            return true
        }
        Key.Escape -> {
            onSendVt100("\u001B")
            return true
        }
        Key.Enter, Key.NumPadEnter -> {
            onSendRawBytes(byteArrayOf(0x0D))
            return true
        }
        Key.Backspace -> {
            onSendRawBytes(byteArrayOf(0x7F))
            return true
        }
        Key.Delete -> {
            onSendVt100("\u001B[3~")
            return true
        }
        Key.MoveHome -> {
            onSendVt100("\u001B[H")
            return true
        }
        Key.MoveEnd -> {
            onSendVt100("\u001B[F")
            return true
        }
        Key.PageUp -> {
            onSendVt100("\u001B[5~")
            return true
        }
        Key.PageDown -> {
            onSendVt100("\u001B[6~")
            return true
        }
    }

    val codePoint = event.utf16CodePoint
    if (event.isCtrlPressed && codePoint != 0) {
        onSendCtrlSignal(codePoint.toChar())
        return true
    }

    if (codePoint >= 32) {
        onCharacterTyped(codePoint.toChar())
        return true
    }

    return false
}

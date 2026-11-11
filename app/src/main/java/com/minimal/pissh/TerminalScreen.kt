package com.minimal.pissh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
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
    var selection by remember { mutableStateOf<TerminalSelection?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    // Sentinel TextFieldValue with a leading zero-width space and cursor fixed at index 1.
    // Uses neverEqualPolicy() so resetting to the sentinel always resets the IME buffer.
    var softInputValue by remember {
        mutableStateOf(
            TextFieldValue(text = "\u200B", selection = TextRange(1)),
            policy = neverEqualPolicy()
        )
    }

    // Terminal Cursor Blink: blinks at 500ms when idle, resets to solid immediately on movement or typing
    var cursorBlinkVisible by remember { mutableStateOf(true) }

    LaunchedEffect(
        uiState.cursorRow,
        uiState.cursorCol,
        uiState.terminalLines.size,
        uiState.terminalLines.lastOrNull()
    ) {
        cursorBlinkVisible = true
        while (true) {
            delay(500)
            cursorBlinkVisible = !cursorBlinkVisible
        }
    }

    val density = LocalDensity.current
    val lineHeightSp = (uiState.fontSizeSp * 1.28f)
    val terminalTextStyle = remember(uiState.fontSizeSp) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = uiState.fontSizeSp.sp,
            lineHeight = lineHeightSp.sp,
            color = Color(0xFFE6EDF3)
        )
    }

    val cellMetrics = remember(uiState.fontSizeSp, density) {
        val sampleLayout = textMeasurer.measure(
            text = "M".repeat(100),
            style = terminalTextStyle,
            softWrap = false,
            maxLines = 1
        )
        val widthPx = (sampleLayout.size.width / 100f).coerceAtLeast(4f)
        val heightPx = sampleLayout.size.height.toFloat().coerceAtLeast(8f)
        widthPx to heightPx
    }
    val charWidthPx = cellMetrics.first
    val charHeightPx = cellMetrics.second
    val charHeightDp = with(density) { ceil(charHeightPx).toDp() }

    var hasReportedInitialGeometry by remember { mutableStateOf(false) }

    // Dynamically measure monospace cell dimensions and notify SSH PTY (SIGWINCH)
    LaunchedEffect(viewportSize, charWidthPx, charHeightPx) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            if (hasReportedInitialGeometry) {
                // Debounce rapid resize events during soft keyboard (IME) animations
                delay(100)
            }
            val cols = (viewportSize.width / charWidthPx).toInt().coerceAtLeast(20)
            val rows = (viewportSize.height / charHeightPx).toInt().coerceAtLeast(5)
            hasReportedInitialGeometry = true
            onTerminalGeometryChanged(cols, rows, viewportSize.width, viewportSize.height)
        }
    }

    // Keep the active terminal viewport anchored without jumping when TUI tools move the cursor.
    // Auto-scroll is paused while a text selection is active so incoming output does not yank the viewport.
    LaunchedEffect(
        uiState.terminalLines.size,
        uiState.terminalLines.lastOrNull(),
        uiState.cursorRow,
        viewportSize.height,
        selection == null
    ) {
        if (selection != null) return@LaunchedEffect
        if (uiState.terminalLines.isNotEmpty()) {
            val lastIdx = uiState.terminalLines.lastIndex
            val activeViewportStart = (uiState.terminalLines.size - uiState.ptyRows).coerceAtLeast(0)
            val targetIndex = uiState.cursorRow.coerceIn(0, lastIdx)
            if (targetIndex >= activeViewportStart) {
                listState.scrollToItem(lastIdx)
            } else {
                val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
                if (visible.isNotEmpty() && targetIndex !in visible) {
                    listState.scrollToItem(targetIndex)
                }
            }
        }
    }

    // Clear selection if terminal buffer is cleared or shrinks below the selected range
    LaunchedEffect(uiState.terminalLines.size) {
        val currentSelection = selection ?: return@LaunchedEffect
        if (uiState.terminalLines.isEmpty() || currentSelection.fromRow > uiState.terminalLines.lastIndex) {
            selection = null
            showContextMenu = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val currentUiState by rememberUpdatedState(uiState)
    val currentCharWidthPx by rememberUpdatedState(charWidthPx)
    val currentOnUpdateFontSize by rememberUpdatedState(onUpdateFontSize)

    // Anchor coordinates for the initial word selected on long-press
    var dragAnchorRow by remember { mutableStateOf(0) }
    var dragAnchorWordStart by remember { mutableStateOf(0) }
    var dragAnchorWordEnd by remember { mutableStateOf(0) }

    BackHandler(enabled = selection != null || showContextMenu) {
        showContextMenu = false
        selection = null
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
                    onSendRawBytes = onSendRawBytes,
                    hasSelection = selection != null,
                    onCopySelection = {
                        val text = selection?.extractText(uiState.terminalLines)
                        if (!text.isNullOrEmpty()) {
                            copyToClipboard(context, text)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            selection = null
                            showContextMenu = false
                        }
                    },
                    onPaste = {
                        val pasted = getClipboardText(context)
                        if (!pasted.isNullOrEmpty()) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPasteText(pasted)
                            selection = null
                            showContextMenu = false
                        }
                    }
                )
            }
    ) {
        // Top Bar: Transforms into Material 3 Selection Action Bar when text is selected
        if (selection != null) {
            val selectedText = remember(selection, uiState.terminalLines) {
                selection?.extractText(uiState.terminalLines) ?: ""
            }
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { selection = null; showContextMenu = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel selection",
                            tint = Color(0xFFC9D1D9)
                        )
                    }
                },
                title = {
                    Text(
                        text = "${selectedText.length} chars selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE6EDF3)
                    )
                },
                actions = {
                    // Copy selection action
                    IconButton(
                        onClick = {
                            if (selectedText.isNotEmpty()) {
                                copyToClipboard(context, selectedText)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                selection = null
                                showContextMenu = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF58A6FF)
                        )
                    }
                    // Select All action
                    IconButton(
                        onClick = {
                            if (uiState.terminalLines.isNotEmpty()) {
                                val lastLineIdx = uiState.terminalLines.lastIndex
                                val lastCol = (uiState.terminalLines[lastLineIdx].text.length - 1).coerceAtLeast(0)
                                selection = TerminalSelection(0, 0, lastLineIdx, lastCol)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "Select All",
                            tint = Color(0xFFC9D1D9)
                        )
                    }
                    // Paste action
                    val clipboardText = getClipboardText(context)
                    if (!clipboardText.isNullOrEmpty()) {
                        IconButton(
                            onClick = {
                                onPasteText(clipboardText)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selection = null
                                showContextMenu = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = Color(0xFF3FB950)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF161B22)
                )
            )
        } else {
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
        }

        // Monospace Terminal Output Canvas with Gestures (Pinch-to-Zoom, Long-Press Selection, Drag)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .onSizeChanged { viewportSize = it }
                .pointerInput(Unit) {
                    detectTerminalGestures(
                        onZoom = { zoom ->
                            if (zoom != 1f) {
                                val updated = (currentUiState.fontSizeSp * zoom).coerceIn(
                                    TerminalViewModel.MIN_FONT_SIZE_SP,
                                    TerminalViewModel.MAX_FONT_SIZE_SP
                                )
                                currentOnUpdateFontSize(updated)
                            }
                        },
                        onTap = {
                            if (showContextMenu) {
                                showContextMenu = false
                            } else if (selection != null) {
                                selection = null
                            } else {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        },
                        onLongPressStart = { offset ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            keyboardController?.hide()
                            val lines = currentUiState.terminalLines
                            val pos = findPositionFromOffset(offset, listState, currentCharWidthPx, lines.size)
                            if (pos != null) {
                                val (row, col) = pos
                                val lineText = lines.getOrNull(row)?.text ?: ""
                                if (lineText.isNotBlank()) {
                                    val (wStart, wEnd) = findWordBoundary(lineText, col)
                                    dragAnchorRow = row
                                    dragAnchorWordStart = wStart
                                    dragAnchorWordEnd = wEnd
                                    selection = TerminalSelection(row, wStart, row, wEnd)
                                } else {
                                    dragAnchorRow = row
                                    dragAnchorWordStart = col
                                    dragAnchorWordEnd = col
                                    selection = null
                                }
                            }
                        },
                        onDrag = { offset ->
                            val lines = currentUiState.terminalLines
                            val pos = findPositionFromOffset(offset, listState, currentCharWidthPx, lines.size)
                            if (pos != null) {
                                val (row, col) = pos
                                selection = extendWordSelection(
                                    anchorRow = dragAnchorRow,
                                    anchorWordStart = dragAnchorWordStart,
                                    anchorWordEnd = dragAnchorWordEnd,
                                    dragRow = row,
                                    dragCol = col
                                )
                            }
                        },
                        onDragEnd = { offset ->
                            val xDp = with(density) { offset.x.toDp() }
                            val yDp = with(density) { offset.y.toDp() }
                            contextMenuOffset = DpOffset(xDp, yDp)
                            showContextMenu = true
                        }
                    )
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 2.dp)
            ) {
                itemsIndexed(uiState.terminalLines) { index, line ->
                    val isCursorLine = (index == uiState.cursorRow) && uiState.cursorVisible && cursorBlinkVisible
                    val displayLine = if (isCursorLine) {
                        buildLineWithCursor(
                            line = line,
                            cursorCol = uiState.cursorCol,
                            cursorColor = Color(0xFF58A6FF),
                            cursorTextColor = Color(0xFF0D1117)
                        )
                    } else {
                        line
                    }

                    val selectedColRange = selection?.getSelectedColRange(index, line.text.length)

                    TerminalLineRow(
                        line = displayLine,
                        textMeasurer = textMeasurer,
                        textStyle = terminalTextStyle,
                        charWidthPx = charWidthPx,
                        charHeightDp = charHeightDp,
                        selectedColRange = selectedColRange
                    )
                }
            }

            // Floating Context Menu on Long Press
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = contextMenuOffset
            ) {
                if (selection != null) {
                    val textToCopy = remember(selection, uiState.terminalLines) {
                        selection?.extractText(uiState.terminalLines) ?: ""
                    }
                    if (textToCopy.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Copy (${textToCopy.length})") },
                            onClick = {
                                copyToClipboard(context, textToCopy)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                selection = null
                                showContextMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy"
                                )
                            }
                        )
                    }
                }

                val clipboardText = remember(showContextMenu) { getClipboardText(context) }
                if (!clipboardText.isNullOrEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Paste") },
                        onClick = {
                            onPasteText(clipboardText)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selection = null
                            showContextMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste"
                            )
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Select All") },
                    onClick = {
                        if (uiState.terminalLines.isNotEmpty()) {
                            val lastLineIdx = uiState.terminalLines.lastIndex
                            val lastCol = (uiState.terminalLines[lastLineIdx].text.length - 1).coerceAtLeast(0)
                            selection = TerminalSelection(0, 0, lastLineIdx, lastCol)
                        }
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "Select All"
                        )
                    }
                )
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
            hasSelection = selection != null,
            onToggleCtrl = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleCtrlLatch()
            },
            onCopyOrSelect = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (selection != null) {
                    val text = selection?.extractText(uiState.terminalLines) ?: ""
                    if (text.isNotEmpty()) {
                        copyToClipboard(context, text)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }
                    selection = null
                    showContextMenu = false
                } else {
                    keyboardController?.hide()
                    val lines = uiState.terminalLines
                    val targetRow = (uiState.cursorRow.coerceIn(0, lines.lastIndex) downTo 0)
                        .firstOrNull { lines[it].text.isNotBlank() }
                        ?: uiState.cursorRow.coerceIn(0, lines.lastIndex)
                    val lineText = lines.getOrNull(targetRow)?.text ?: ""
                    if (lineText.isNotBlank()) {
                        val firstNonSpace = lineText.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                        val lastNonSpace = lineText.indexOfLast { !it.isWhitespace() }.coerceAtLeast(firstNonSpace)
                        selection = TerminalSelection(targetRow, firstNonSpace, targetRow, lastNonSpace)
                    } else {
                        selection = TerminalSelection(targetRow, 0, targetRow, 0)
                    }
                }
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
                val pasted = getClipboardText(context)
                if (!pasted.isNullOrEmpty()) {
                    onPasteText(pasted)
                    selection = null
                    showContextMenu = false
                }
            }
        )
    }
}

@Composable
private fun TouchAccessoryBar(
    ctrlLatched: Boolean,
    hasSelection: Boolean,
    onToggleCtrl: () -> Unit,
    onCopyOrSelect: () -> Unit,
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

            // Selection & Copy
            AccessoryKeyButton(
                label = if (hasSelection) "Copy" else "Select",
                isHighlighted = hasSelection,
                onClick = onCopyOrSelect
            )

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
    onSendRawBytes: (ByteArray) -> Unit,
    hasSelection: Boolean = false,
    onCopySelection: (() -> Unit)? = null,
    onPaste: (() -> Unit)? = null
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    // Hardware shortcuts for Copy & Paste
    if (event.isCtrlPressed && event.isShiftPressed && event.key == Key.C) {
        onCopySelection?.invoke()
        return true
    }
    if (event.isCtrlPressed && event.isShiftPressed && event.key == Key.V) {
        onPaste?.invoke()
        return true
    }
    if (hasSelection && event.isCtrlPressed && event.key == Key.C) {
        onCopySelection?.invoke()
        return true
    }

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

/**
 * Renders the terminal cursor at the specified [cursorCol] on the given [line].
 * If [cursorCol] falls on an existing non-space character, that character's colors are inverted
 * with [cursorColor] background and [cursorTextColor] foreground.
 * If [cursorCol] falls on a space or at/past the end of the line, a solid block character '█'
 * styled in [cursorColor] is rendered to guarantee visibility regardless of font whitespace trimming.
 */
fun buildLineWithCursor(
    line: AnnotatedString,
    cursorCol: Int,
    cursorColor: Color = Color(0xFF58A6FF),
    cursorTextColor: Color = Color(0xFF0D1117)
): AnnotatedString {
    val text = line.text
    val safeCol = cursorCol.coerceAtLeast(0)

    if (safeCol < text.length) {
        val charAtCursor = text[safeCol]
        if (charAtCursor == ' ') {
            val prefix = if (safeCol > 0) line.subSequence(0, safeCol) else AnnotatedString("")
            val suffix = if (safeCol + 1 < text.length) line.subSequence(safeCol + 1, text.length) else AnnotatedString("")
            val blockBuilder = AnnotatedString.Builder()
            blockBuilder.append(prefix)
            blockBuilder.pushStyle(SpanStyle(color = cursorColor))
            blockBuilder.append('█')
            blockBuilder.pop()
            blockBuilder.append(suffix)
            return blockBuilder.toAnnotatedString()
        } else {
            val builder = AnnotatedString.Builder(line)
            builder.addStyle(
                SpanStyle(
                    background = cursorColor,
                    color = cursorTextColor,
                    fontWeight = FontWeight.Bold
                ),
                start = safeCol,
                end = safeCol + 1
            )
            return builder.toAnnotatedString()
        }
    } else {
        val builder = AnnotatedString.Builder(line)
        if (safeCol > text.length) {
            builder.append(" ".repeat(safeCol - text.length))
        }
        builder.pushStyle(SpanStyle(color = cursorColor))
        builder.append('█')
        builder.pop()
        return builder.toAnnotatedString()
    }
}

/**
 * Renders a single terminal row locked to a strict monospace character grid ([charWidthPx] x [charHeightDp]).
 * - Background spans fill exact cell bounds edge-to-edge without vertical line-height gaps.
 * - Box-drawing (U+2500..U+257F) and Block Element (U+2580..U+259F) characters are drawn geometrically
 *   inside their exact cell so fallback font widths never stretch lines or skew TUI borders/logos.
 * - Non-ASCII symbols are anchored to their exact column offset so subsequent columns never drift.
 */
@Composable
private fun TerminalLineRow(
    line: AnnotatedString,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    charWidthPx: Float,
    charHeightDp: Dp,
    selectedColRange: IntRange? = null
) {
    if (line.text.isEmpty() && line.spanStyles.isEmpty() && selectedColRange == null) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(charHeightDp)
        )
        return
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(charHeightDp)
    ) {
        val rowHeight = size.height
        val text = line.text

        // 1. Draw cell background spans edge-to-edge
        for (span in line.spanStyles) {
            val bg = span.item.background
            if (bg != Color.Unspecified && bg != Color.Transparent && span.end > span.start) {
                val left = span.start * charWidthPx
                val width = (span.end - span.start) * charWidthPx
                if (left < size.width) {
                    drawRect(
                        color = bg,
                        topLeft = Offset(left, 0f),
                        size = Size(width.coerceAtMost(size.width - left), rowHeight)
                    )
                }
            }
        }

        // 2. Draw foreground glyphs and geometric box/block elements locked to column cells
        if (text.isNotEmpty()) {
            var col = 0
            val len = text.length
            while (col < len) {
                val x = col * charWidthPx
                if (x >= size.width) break

                val ch = text[col]
                val code = ch.code
                if (code in 0x2500..0x259F) {
                    val fgColor = resolveFgColorAt(line, col, textStyle.color)
                    drawBoxOrBlockChar(
                        ch = ch,
                        left = x,
                        top = 0f,
                        width = charWidthPx,
                        height = rowHeight,
                        color = fgColor
                    )
                    col++
                } else if (code in 0x20..0x7E) {
                    var runEnd = col + 1
                    var allSpaces = (ch == ' ')
                    while (runEnd < len) {
                        val c = text[runEnd]
                        if (c.code !in 0x20..0x7E) break
                        if (c != ' ') allSpaces = false
                        runEnd++
                    }
                    val hasDecorations = line.spanStyles.any {
                        it.start < runEnd && it.end > col &&
                            it.item.textDecoration != null &&
                            it.item.textDecoration != TextDecoration.None
                    }
                    if (!allSpaces || hasDecorations) {
                        val sub = sliceWithoutBackground(line, col, runEnd)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = sub,
                            topLeft = Offset(x, 0f),
                            style = textStyle,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                    col = runEnd
                } else {
                    val charCount = if (Character.isHighSurrogate(ch) && col + 1 < len && Character.isLowSurrogate(text[col + 1])) {
                        2
                    } else {
                        1
                    }
                    val sub = sliceWithoutBackground(line, col, col + charCount)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = sub,
                        topLeft = Offset(x, 0f),
                        style = textStyle,
                        softWrap = false,
                        maxLines = 1
                    )
                    col += charCount
                }
            }
        }

        // 3. Draw selection highlight over the line
        if (selectedColRange != null && selectedColRange.first <= selectedColRange.last) {
            val selStart = selectedColRange.first.coerceAtLeast(0)
            val selEnd = (selectedColRange.last + 1).coerceAtLeast(selStart)
            val selLeft = selStart * charWidthPx
            val selWidth = (selEnd - selStart) * charWidthPx
            if (selWidth > 0 && selLeft < size.width) {
                drawRect(
                    color = Color(0x66388BFD), // Translucent high-visibility selection blue
                    topLeft = Offset(selLeft, 0f),
                    size = Size(selWidth.coerceAtMost(size.width - selLeft), rowHeight)
                )
            }
        }
    }
}

private fun resolveFgColorAt(line: AnnotatedString, index: Int, defaultColor: Color): Color {
    var resolved = defaultColor
    for (span in line.spanStyles) {
        if (index in span.start until span.end && span.item.color != Color.Unspecified) {
            resolved = span.item.color
        }
    }
    return resolved
}

private fun sliceWithoutBackground(line: AnnotatedString, start: Int, end: Int): AnnotatedString {
    val subText = line.text.substring(start, end)
    val builder = AnnotatedString.Builder(subText)
    for (span in line.spanStyles) {
        if (span.start < end && span.end > start) {
            val clampedStart = (span.start - start).coerceIn(0, subText.length)
            val clampedEnd = (span.end - start).coerceIn(0, subText.length)
            if (clampedEnd > clampedStart) {
                builder.addStyle(
                    span.item.copy(background = Color.Unspecified),
                    clampedStart,
                    clampedEnd
                )
            }
        }
    }
    return builder.toAnnotatedString()
}

private fun DrawScope.drawBoxOrBlockChar(
    ch: Char,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color
) {
    val code = ch.code
    if (code in 0x2580..0x259F) {
        val hw = width * 0.5f
        val hh = height * 0.5f
        when (code) {
            0x2580 -> drawRect(color, Offset(left, top), Size(width, hh)) // ▀ Upper half
            in 0x2581..0x2587 -> { // ▁▂▃▄▅▆▇ Lower 1/8..7/8
                val frac = (code - 0x2580) / 8f
                val h = height * frac
                drawRect(color, Offset(left, top + height - h), Size(width, h))
            }
            0x2588 -> drawRect(color, Offset(left, top), Size(width, height)) // █ Full block
            in 0x2589..0x258F -> { // ▉▊▋▌▍▎▏ Left 7/8..1/8
                val frac = (0x2590 - code) / 8f
                drawRect(color, Offset(left, top), Size(width * frac, height))
            }
            0x2590 -> drawRect(color, Offset(left + hw, top), Size(hw, height)) // ▐ Right half
            0x2591 -> drawRect(color.copy(alpha = color.alpha * 0.25f), Offset(left, top), Size(width, height)) // ░
            0x2592 -> drawRect(color.copy(alpha = color.alpha * 0.50f), Offset(left, top), Size(width, height)) // ▒
            0x2593 -> drawRect(color.copy(alpha = color.alpha * 0.75f), Offset(left, top), Size(width, height)) // ▓
            0x2594 -> drawRect(color, Offset(left, top), Size(width, height * 0.125f)) // ▔ Upper 1/8
            0x2595 -> drawRect(color, Offset(left + width * 0.875f, top), Size(width * 0.125f, height)) // ▕ Right 1/8
            in 0x2596..0x259F -> { // Quadrant blocks ▖▗▘▙▚▛▜▝▞▟ (bit 0=UL, 1=UR, 2=LL, 3=LR)
                val mask = when (code) {
                    0x2596 -> 0b0100
                    0x2597 -> 0b1000
                    0x2598 -> 0b0001
                    0x2599 -> 0b1101
                    0x259A -> 0b1001
                    0x259B -> 0b0111
                    0x259C -> 0b1011
                    0x259D -> 0b0010
                    0x259E -> 0b0110
                    0x259F -> 0b1110
                    else -> 0
                }
                if ((mask and 1) != 0) drawRect(color, Offset(left, top), Size(hw, hh))
                if ((mask and 2) != 0) drawRect(color, Offset(left + hw, top), Size(width - hw, hh))
                if ((mask and 4) != 0) drawRect(color, Offset(left, top + hh), Size(hw, height - hh))
                if ((mask and 8) != 0) drawRect(color, Offset(left + hw, top + hh), Size(width - hw, height - hh))
            }
        }
        return
    }

    // Box Drawing (U+2500..U+257F)
    val cx = left + width * 0.5f
    val cy = top + height * 0.5f
    val right = left + width
    val bottom = top + height
    val light = (width * 0.10f).coerceAtLeast(1.5f)
    val heavy = (width * 0.20f).coerceAtLeast(3.0f)

    when (code) {
        0x2571 -> { // ╱
            drawLine(color, Offset(right, top), Offset(left, bottom), strokeWidth = light)
            return
        }
        0x2572 -> { // ╲
            drawLine(color, Offset(left, top), Offset(right, bottom), strokeWidth = light)
            return
        }
        0x2573 -> { // ╳
            drawLine(color, Offset(right, top), Offset(left, bottom), strokeWidth = light)
            drawLine(color, Offset(left, top), Offset(right, bottom), strokeWidth = light)
            return
        }
    }

    // Segment weights: 0 = none, 1 = light, 2 = heavy
    var l = 0
    var r = 0
    var u = 0
    var d = 0

    when (code) {
        0x2500, 0x2504, 0x2508, 0x254C -> { l = 1; r = 1 }
        0x2501, 0x2505, 0x2509, 0x254D, 0x2550 -> { l = 2; r = 2 }
        0x2502, 0x2506, 0x250A, 0x254E -> { u = 1; d = 1 }
        0x2503, 0x2507, 0x250B, 0x254F, 0x2551 -> { u = 2; d = 2 }
        0x250C, 0x256D -> { r = 1; d = 1 } // ┌ ╭
        0x250D, 0x250E, 0x250F, 0x2552, 0x2553, 0x2554 -> { r = 2; d = 2 } // ┏ ╔
        0x2510, 0x256E -> { l = 1; d = 1 } // ┐ ╮
        0x2511, 0x2512, 0x2513, 0x2555, 0x2556, 0x2557 -> { l = 2; d = 2 } // ┓ ╗
        0x2514, 0x2570 -> { r = 1; u = 1 } // └ ╰
        0x2515, 0x2516, 0x2517, 0x2558, 0x2559, 0x255A -> { r = 2; u = 2 } // ┗ ╚
        0x2518, 0x256F -> { l = 1; u = 1 } // ┘ ╯
        0x2519, 0x251A, 0x251B, 0x255B, 0x255C, 0x255D -> { l = 2; u = 2 } // ┛ ╝
        0x251C -> { u = 1; d = 1; r = 1 } // ├
        in 0x251D..0x2523, in 0x255E..0x2560 -> { u = 2; d = 2; r = 2 } // ┣ ╠
        0x2524 -> { u = 1; d = 1; l = 1 } // ┤
        in 0x2525..0x252B, in 0x2561..0x2563 -> { u = 2; d = 2; l = 2 } // ┫ ╣
        0x252C -> { l = 1; r = 1; d = 1 } // ┬
        in 0x252D..0x2533, in 0x2564..0x2566 -> { l = 2; r = 2; d = 2 } // ┳ ╦
        0x2534 -> { l = 1; r = 1; u = 1 } // ┴
        in 0x2535..0x253B, in 0x2567..0x2569 -> { l = 2; r = 2; u = 2 } // ┻ ╩
        0x253C -> { l = 1; r = 1; u = 1; d = 1 } // ┼
        in 0x253D..0x254B, in 0x256A..0x256C -> { l = 2; r = 2; u = 2; d = 2 } // ╋ ╬
        0x2574 -> l = 1
        0x2575 -> u = 1
        0x2576 -> r = 1
        0x2577 -> d = 1
        0x2578 -> l = 2
        0x2579 -> u = 2
        0x257A -> r = 2
        0x257B -> d = 2
        0x257C -> { l = 1; r = 2 }
        0x257D -> { u = 1; d = 2 }
        0x257E -> { l = 2; r = 1 }
        0x257F -> { u = 2; d = 1 }
        else -> { l = 1; r = 1 }
    }

    if (l > 0) {
        drawLine(color, Offset(left, cy), Offset(cx, cy), strokeWidth = if (l == 2) heavy else light, cap = StrokeCap.Square)
    }
    if (r > 0) {
        drawLine(color, Offset(cx, cy), Offset(right, cy), strokeWidth = if (r == 2) heavy else light, cap = StrokeCap.Square)
    }
    if (u > 0) {
        drawLine(color, Offset(cx, top), Offset(cx, cy), strokeWidth = if (u == 2) heavy else light, cap = StrokeCap.Square)
    }
    if (d > 0) {
        drawLine(color, Offset(cx, cy), Offset(cx, bottom), strokeWidth = if (d == 2) heavy else light, cap = StrokeCap.Square)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("PiSSH Terminal", text)
    clipboard.setPrimaryClip(clip)
}

private fun getClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboard?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}

private fun findPositionFromOffset(
    offset: Offset,
    listState: LazyListState,
    charWidthPx: Float,
    lineCount: Int
): Pair<Int, Int>? {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty() || lineCount <= 0) return null
    val y = offset.y
    val x = offset.x

    val matchedItem = visibleItems.firstOrNull { item ->
        y >= item.offset && y < (item.offset + item.size)
    } ?: run {
        if (y < visibleItems.first().offset) {
            visibleItems.first()
        } else {
            visibleItems.last()
        }
    }

    val row = matchedItem.index.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
    val col = if (charWidthPx > 0f) (x / charWidthPx).toInt().coerceAtLeast(0) else 0
    return row to col
}

private suspend fun PointerInputScope.detectTerminalGestures(
    onZoom: (Float) -> Unit,
    onTap: (Offset) -> Unit,
    onLongPressStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val initialId = down.id
        val initialPos = down.position
        val touchSlop = viewConfiguration.touchSlop
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis

        var previousDistance = 0f
        var lastPointerPos = initialPos

        val gestureType = withTimeoutOrNull(longPressTimeout) {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.size >= 2) {
                    return@withTimeoutOrNull "ZOOM"
                }
                val change = event.changes.firstOrNull { it.id == initialId } ?: return@withTimeoutOrNull "CANCEL"
                if (!change.pressed) {
                    return@withTimeoutOrNull "TAP"
                }
                val dist = (change.position - initialPos).getDistance()
                if (dist > touchSlop) {
                    return@withTimeoutOrNull "SCROLL"
                }
            }
        }

        when (gestureType) {
            "TAP" -> {
                onTap(initialPos)
            }
            "CANCEL" -> Unit
            "SCROLL", "ZOOM" -> {
                // Single-finger scroll passes through unconsumed to LazyColumn;
                // if a second finger touches down at any point, handle pinch-to-zoom.
                while (true) {
                    val event = awaitPointerEvent()
                    val activePointers = event.changes.filter { it.pressed }
                    if (activePointers.isEmpty()) break
                    if (activePointers.size >= 2) {
                        val p1 = activePointers[0].position
                        val p2 = activePointers[1].position
                        val currentDistance = (p1 - p2).getDistance()
                        if (previousDistance > 0f && currentDistance > 0f) {
                            onZoom(currentDistance / previousDistance)
                        }
                        previousDistance = currentDistance
                        activePointers.forEach { it.consume() }
                    } else {
                        previousDistance = 0f
                    }
                }
            }
            null -> {
                // Long press triggered (timeout expired while stationary)
                onLongPressStart(initialPos)
                var hasStartedDragging = false

                // Drag loop: adjust selection while pointer remains down
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == initialId } ?: break
                    if (!change.pressed) {
                        lastPointerPos = change.position
                        break
                    }
                    change.consume()
                    lastPointerPos = change.position
                    if (!hasStartedDragging && (change.position - initialPos).getDistance() > touchSlop) {
                        hasStartedDragging = true
                    }
                    if (hasStartedDragging) {
                        onDrag(change.position)
                    }
                }
                onDragEnd(lastPointerPos)
            }
        }
    }
}

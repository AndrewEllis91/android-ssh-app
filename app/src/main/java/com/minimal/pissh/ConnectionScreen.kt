package com.minimal.pissh

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    uiState: TerminalUiState,
    onSelectProfile: (Int) -> Unit,
    onUpdateProfile: ((HostProfile) -> HostProfile) -> Unit,
    onSaveProfile: () -> Unit,
    onConnect: () -> Unit,
    onToggleKeepScreenOn: () -> Unit = {},
    onDismissSaveSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val profile = uiState.activeProfile
    val isConnecting = uiState.connectionStatus == ConnectionStatus.CONNECTING

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showPassword by remember(profile.id) { mutableStateOf(false) }
    var showPassphrase by remember(profile.id) { mutableStateOf(false) }
    var portText by remember(profile.id) { mutableStateOf(profile.port.toString()) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showEmulationMenu by remember { mutableStateOf(false) }
    var selectedEmulation by remember { mutableStateOf("xterm-256color") }

    val keyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader(Charsets.UTF_8).readText()
                    onUpdateProfile { it.copy(privateKey = content) }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = profile.label.ifBlank { "New Connection" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* Home or root */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val isSaved = !uiState.saveSuccessMessage.isNullOrBlank()
                    TextButton(
                        onClick = {
                            keyboardController?.hide()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSaveProfile()
                        },
                        enabled = !isConnecting
                    ) {
                        if (isSaved) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Saved",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        } else {
                            Text(
                                text = "Save",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showOverflowMenu = !showOverflowMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save Profile") },
                                onClick = {
                                    showOverflowMenu = false
                                    keyboardController?.hide()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSaveProfile()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Host & Port") },
                                onClick = {
                                    showOverflowMenu = false
                                    portText = "22"
                                    onUpdateProfile { it.copy(host = "", port = 22) }
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isSaved = !uiState.saveSuccessMessage.isNullOrBlank()
                    OutlinedButton(
                        onClick = {
                            keyboardController?.hide()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSaveProfile()
                        },
                        enabled = !isConnecting,
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSaved) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(0.40f)
                            .height(56.dp)
                    ) {
                        if (isSaved) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Saved!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4CAF50)
                            )
                        } else {
                            Text(
                                text = "Save",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConnect()
                        },
                        enabled = !isConnecting && profile.host.isNotBlank() && profile.username.isNotBlank(),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(0.60f)
                            .height(56.dp)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Connect",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(8.dp))
                                // Terminal prompt badge icon matching screenshot
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = ">_",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Saved Profiles Carousel (Single-tap switching across 4 slots)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.profiles.forEachIndexed { index, item ->
                    val isSelected = index == uiState.selectedProfileIndex
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectProfile(index)
                        },
                        enabled = !isConnecting,
                        shape = RoundedCornerShape(20.dp),
                        label = {
                            Text(
                                text = item.label.ifBlank { "Host ${index + 1}" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Outlined.Dns, contentDescription = null, Modifier.size(16.dp))
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // 1. General Card (Target Host & Port)
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "General",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    MaterialYouPillTextField(
                        value = profile.label,
                        onValueChange = { value -> onUpdateProfile { it.copy(label = value) } },
                        label = "Connection Name",
                        placeholder = "e.g. Home Raspberry Pi",
                        enabled = !isConnecting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MaterialYouPillTextField(
                            value = profile.host,
                            onValueChange = { value -> onUpdateProfile { it.copy(host = value) } },
                            label = "Host",
                            placeholder = "192.168.1.100",
                            isMonospace = true,
                            enabled = !isConnecting,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Uri
                            ),
                            modifier = Modifier.weight(0.72f)
                        )

                        MaterialYouPillTextField(
                            value = portText,
                            onValueChange = { value ->
                                val digitsOnly = value.filter { it.isDigit() }.take(5)
                                portText = digitsOnly
                                if (digitsOnly.isEmpty()) {
                                    onUpdateProfile { it.copy(port = 22) }
                                } else {
                                    val parsed = digitsOnly.toIntOrNull()
                                    if (parsed != null && parsed in 1..65535) {
                                        onUpdateProfile { it.copy(port = parsed) }
                                    }
                                }
                            },
                            label = "Port",
                            placeholder = "22",
                            isMonospace = true,
                            enabled = !isConnecting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.28f)
                        )
                    }
                }
            }

            // 2. Authentication Card
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Authentication",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Material You Choice Pills: Password vs Private Key
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isPassword = profile.authType == AuthType.PASSWORD
                        FilterChip(
                            selected = isPassword,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateProfile { it.copy(authType = AuthType.PASSWORD) }
                            },
                            enabled = !isConnecting,
                            shape = RoundedCornerShape(20.dp),
                            label = { Text("Password", style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = if (isPassword) {
                                { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                            } else null,
                            border = if (!isPassword) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        val isKey = profile.authType == AuthType.KEY
                        FilterChip(
                            selected = isKey,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateProfile { it.copy(authType = AuthType.KEY) }
                            },
                            enabled = !isConnecting,
                            shape = RoundedCornerShape(20.dp),
                            label = { Text("Private Key", style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = if (isKey) {
                                { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                            } else null,
                            border = if (!isKey) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    MaterialYouPillTextField(
                        value = profile.username,
                        onValueChange = { value -> onUpdateProfile { it.copy(username = value) } },
                        label = "Username",
                        placeholder = "pi",
                        isMonospace = true,
                        enabled = !isConnecting,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect = false,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (profile.authType == AuthType.PASSWORD) {
                        MaterialYouPillTextField(
                            value = profile.password,
                            onValueChange = { value -> onUpdateProfile { it.copy(password = value) } },
                            label = "Password",
                            placeholder = "••••••••••••",
                            isPassword = true,
                            passwordVisible = showPassword,
                            onTogglePasswordVisibility = { showPassword = !showPassword },
                            enabled = !isConnecting,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        FilledTonalButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                keyFilePicker.launch(arrayOf("*/*"))
                            },
                            enabled = !isConnecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Outlined.Key, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (profile.privateKey.isNotBlank()) {
                                    "Change OpenSSH Key (${profile.privateKey.trim().lines().size} lines)"
                                } else {
                                    "Import OpenSSH Key (.pem / .id_rsa)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (profile.privateKey.isNotBlank()) {
                            TextField(
                                value = profile.privateKey,
                                onValueChange = { value -> onUpdateProfile { it.copy(privateKey = value) } },
                                label = { Text("OpenSSH Private Key") },
                                enabled = !isConnecting,
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(20.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { onUpdateProfile { it.copy(privateKey = "") } }) {
                                        Icon(Icons.Outlined.Clear, contentDescription = "Clear Key")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        MaterialYouPillTextField(
                            value = profile.passphrase,
                            onValueChange = { value -> onUpdateProfile { it.copy(passphrase = value) } },
                            label = "Key Passphrase (Optional)",
                            placeholder = "••••••••",
                            isPassword = true,
                            passwordVisible = showPassphrase,
                            onTogglePasswordVisibility = { showPassphrase = !showPassphrase },
                            enabled = !isConnecting,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Behavior & Terminal Card
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Behavior & Terminal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keep Alive",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Maintains terminal session when app is minimized",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.keepScreenOn,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleKeepScreenOn()
                            },
                            thumbContent = if (uiState.keepScreenOn) {
                                { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(12.dp)) }
                            } else null
                        )
                    }

                    // Terminal Emulation Dropdown Pill matching screenshot
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEmulationMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Terminal,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Terminal emulation ($selectedEmulation)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select terminal emulation",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showEmulationMenu,
                            onDismissRequest = { showEmulationMenu = false }
                        ) {
                            listOf("xterm-256color", "xterm", "vt100", "ansi", "linux").forEach { emu ->
                                DropdownMenuItem(
                                    text = { Text(emu) },
                                    onClick = {
                                        selectedEmulation = emu
                                        showEmulationMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Status / Error Banners
            if (!uiState.errorMessage.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else if (!uiState.statusMessage.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = uiState.statusMessage,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Green Pop-up Dialogue / Floating Confirmation Pill
        AnimatedVisibility(
            visible = !uiState.saveSuccessMessage.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                animationSpec = tween(250),
                initialOffsetY = { fullHeight -> fullHeight / 2 }
            ),
            exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(
                animationSpec = tween(250),
                targetOffsetY = { fullHeight -> fullHeight / 2 }
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            SaveSuccessPopUp(
                message = uiState.saveSuccessMessage.orEmpty(),
                onDismiss = onDismissSaveSuccess
            )
        }
    }
}
}

/**
 * Green pop-up dialogue / floating confirmation pill shown when a profile is saved.
 * Features a high-contrast Material green container, subtle border, drop shadow,
 * check icon, bold title, message, and tap-to-dismiss support.
 */
@Composable
private fun SaveSuccessPopUp(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E4620), // Rich forest emerald green
        contentColor = Color.White,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.5.dp, Color(0xFF4CAF50)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onDismiss() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2E7D32),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Saved Successfully",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA5D6A7),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = "Dismiss",
                    tint = Color(0xFFA5D6A7),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Reusable Material You Pill-styled TextField matching Android 14/15 system settings inputs.
 * Uses continuous 20dp rounding with a soft tonal surfaceContainerHighest fill and transparent indicator lines.
 */
@Composable
private fun MaterialYouPillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    isMonospace: Boolean = false,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        textStyle = if (isMonospace) {
            LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
        } else {
            LocalTextStyle.current
        },
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (isPassword && onTogglePasswordVisibility != null) {
            {
                IconButton(onClick = onTogglePasswordVisibility, enabled = enabled) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        } else null,
        keyboardOptions = keyboardOptions,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
    )
}

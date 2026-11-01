package com.minimal.pissh

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Sleek dark palette tokens
private val DarkCardBg = Color(0xFF161B22)
private val DarkCardBorder = Color(0xFF30363D)
private val DarkInputBg = Color(0xFF0D1117)
private val AccentPrimary = Color(0xFF58A6FF)
private val AccentElectric = Color(0xFF1F6FEB)
private val AccentSuccess = Color(0xFF3FB950)
private val AccentWarning = Color(0xFFD29922)
private val AccentError = Color(0xFFF85149)
private val TextHighContrast = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF8B949E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    uiState: TerminalUiState,
    onSelectProfile: (Int) -> Unit,
    onUpdateProfile: ((HostProfile) -> HostProfile) -> Unit,
    onSaveProfile: () -> Unit,
    onConnect: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val profile = uiState.activeProfile
    val isConnecting = uiState.connectionStatus == ConnectionStatus.CONNECTING

    var showPassword by remember(profile.id) { mutableStateOf(false) }
    var showPassphrase by remember(profile.id) { mutableStateOf(false) }
    var portText by remember(profile.id) { mutableStateOf(profile.port.toString()) }

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
        containerColor = DarkInputBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // App Logo Glyph container
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            AccentElectric,
                                            Color(0xFF114290)
                                        )
                                    )
                                )
                                .border(1.dp, AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = ">_",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "PiSSH",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextHighContrast
                                )
                                Surface(
                                    color = AccentElectric.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, AccentPrimary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "SSH2",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentPrimary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Minimal LAN & Tailscale SSH Client",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        color = DarkCardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (isConnecting) AccentWarning else AccentSuccess, CircleShape)
                            )
                            Text(
                                text = if (isConnecting) "LINKING" else "READY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextHighContrast
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkInputBg
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Host Profile Carousel Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Dns,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "HOST PROFILES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                }

                Text(
                    text = "SLOT ${uiState.selectedProfileIndex + 1} OF ${uiState.profiles.size}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentPrimary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.profiles.forEachIndexed { index, item ->
                    ProfileCard(
                        slotNumber = index + 1,
                        profile = item,
                        isSelected = index == uiState.selectedProfileIndex,
                        enabled = !isConnecting,
                        onClick = { onSelectProfile(index) }
                    )
                }
            }

            // 2. Target Server Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SERVER ENDPOINT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = TextHighContrast
                        )
                        Surface(
                            color = DarkInputBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Text(
                                text = "PORT ${profile.port}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = profile.label,
                        onValueChange = { value -> onUpdateProfile { it.copy(label = value) } },
                        label = { Text("Profile Label") },
                        placeholder = { Text("e.g. Pi (Tailscale)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        enabled = !isConnecting,
                        shape = RoundedCornerShape(12.dp),
                        colors = modernTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = profile.host,
                            onValueChange = { value -> onUpdateProfile { it.copy(host = value) } },
                            label = { Text("Host / IP Address") },
                            placeholder = { Text("100.x.y.z or raspberrypi") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Dns,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            enabled = !isConnecting,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = modernTextFieldColors(),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Uri
                            ),
                            modifier = Modifier.weight(0.72f)
                        )

                        OutlinedTextField(
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
                            label = { Text("Port") },
                            placeholder = { Text("22") },
                            singleLine = true,
                            enabled = !isConnecting,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = modernTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.28f)
                        )
                    }

                    OutlinedTextField(
                        value = profile.username,
                        onValueChange = { value -> onUpdateProfile { it.copy(username = value) } },
                        label = { Text("SSH Username") },
                        placeholder = { Text("pi") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        enabled = !isConnecting,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = modernTextFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect = false,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Authentication Credentials Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "AUTHENTICATION CREDENTIALS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextHighContrast
                    )

                    // Full-width Segmented Tab Switcher (eliminates chip text wrapping)
                    AuthSegmentedControl(
                        selectedType = profile.authType,
                        enabled = !isConnecting,
                        onSelect = { newType -> onUpdateProfile { it.copy(authType = newType) } }
                    )

                    if (profile.authType == AuthType.KEY) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (profile.privateKey.isNotBlank()) {
                                val lineCount = profile.privateKey.trim().lines().size
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Key loaded ($lineCount lines)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = AccentSuccess
                                    )
                                }
                            } else {
                                Text(
                                    text = "OpenSSH Private Key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    keyFilePicker.launch(arrayOf("*/*"))
                                },
                                enabled = !isConnecting,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, DarkCardBorder),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = AccentPrimary
                                ),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Import File",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = profile.privateKey,
                            onValueChange = { value -> onUpdateProfile { it.copy(privateKey = value) } },
                            placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----\n...") },
                            enabled = !isConnecting,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(12.dp),
                            colors = modernTextFieldColors(),
                            trailingIcon = {
                                if (profile.privateKey.isNotBlank()) {
                                    IconButton(
                                        onClick = { onUpdateProfile { it.copy(privateKey = "") } },
                                        enabled = !isConnecting
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Clear,
                                            contentDescription = "Clear Key",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Ascii
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = profile.passphrase,
                            onValueChange = { value -> onUpdateProfile { it.copy(passphrase = value) } },
                            label = { Text("Key Passphrase (Optional)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            enabled = !isConnecting,
                            shape = RoundedCornerShape(12.dp),
                            colors = modernTextFieldColors(),
                            visualTransformation = if (showPassphrase) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                    Icon(
                                        imageVector = if (showPassphrase) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Toggle Passphrase Visibility",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = profile.password,
                            onValueChange = { value -> onUpdateProfile { it.copy(password = value) } },
                            label = { Text("SSH Password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            enabled = !isConnecting,
                            shape = RoundedCornerShape(12.dp),
                            colors = modernTextFieldColors(),
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Toggle Password Visibility",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 4. Hardware Security Guarantee Badge
            Surface(
                color = DarkCardBg.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF21262D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = AccentSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Encrypted at rest via AndroidKeyStore AES-256 GCM",
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 5. Status / Error Banner
            if (!uiState.errorMessage.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF2D1616),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentError.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = AccentError,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = uiState.errorMessage,
                            color = Color(0xFFFF7B72),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else if (!uiState.statusMessage.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF132338),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = uiState.statusMessage,
                            color = Color(0xFF79C0FF),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 6. Modernized Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSaveProfile()
                    },
                    enabled = !isConnecting,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextHighContrast
                    ),
                    modifier = Modifier
                        .weight(0.36f)
                        .height(52.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConnect()
                    },
                    enabled = !isConnecting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentElectric,
                        contentColor = Color.White,
                        disabledContainerColor = AccentElectric.copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .weight(0.64f)
                        .height(52.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connecting...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Connect SSH",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ProfileCard(
    slotNumber: Int,
    profile: HostProfile,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val borderColor = if (isSelected) AccentPrimary else DarkCardBorder
    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    val bgModifier = if (isSelected) {
        Modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF1E2838),
                    DarkCardBg
                )
            )
        )
    } else {
        Modifier.background(DarkCardBg.copy(alpha = 0.65f))
    }

    Box(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(bgModifier)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SLOT 0$slotNumber",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) AccentPrimary else TextMuted
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (profile.authType == AuthType.KEY) Icons.Outlined.VpnKey else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (isSelected) AccentPrimary.copy(alpha = 0.85f) else TextMuted.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(AccentSuccess, CircleShape)
                        )
                    }
                }
            }

            Text(
                text = profile.label.ifBlank { "Slot $slotNumber" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) TextHighContrast else Color(0xFFC9D1D9),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = profile.host.ifBlank { "Unconfigured" },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (isSelected) Color(0xFF9FB2C8) else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AuthSegmentedControl(
    selectedType: AuthType,
    enabled: Boolean,
    onSelect: (AuthType) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        color = DarkInputBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AuthTabButton(
                title = "SSH Key",
                icon = Icons.Outlined.VpnKey,
                isSelected = selectedType == AuthType.KEY,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(AuthType.KEY)
                }
            )

            AuthTabButton(
                title = "Password",
                icon = Icons.Outlined.Lock,
                isSelected = selectedType == AuthType.PASSWORD,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(AuthType.PASSWORD)
                }
            )
        }
    }
}

@Composable
private fun AuthTabButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgModifier = if (isSelected) {
        Modifier.background(AccentElectric, RoundedCornerShape(8.dp))
    } else {
        Modifier.background(Color.Transparent, RoundedCornerShape(8.dp))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(bgModifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun modernTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentPrimary,
    unfocusedBorderColor = DarkCardBorder,
    disabledBorderColor = DarkCardBorder.copy(alpha = 0.5f),
    focusedContainerColor = DarkInputBg,
    unfocusedContainerColor = DarkInputBg,
    disabledContainerColor = DarkInputBg.copy(alpha = 0.5f),
    cursorColor = AccentPrimary,
    focusedLabelColor = AccentPrimary,
    unfocusedLabelColor = TextMuted,
    disabledLabelColor = TextMuted.copy(alpha = 0.6f),
    focusedPlaceholderColor = TextMuted.copy(alpha = 0.55f),
    unfocusedPlaceholderColor = TextMuted.copy(alpha = 0.55f),
    focusedLeadingIconColor = AccentPrimary,
    unfocusedLeadingIconColor = TextMuted,
    disabledLeadingIconColor = TextMuted.copy(alpha = 0.5f),
    focusedTrailingIconColor = AccentPrimary,
    unfocusedTrailingIconColor = TextMuted,
    disabledTrailingIconColor = TextMuted.copy(alpha = 0.5f),
    focusedTextColor = TextHighContrast,
    unfocusedTextColor = TextHighContrast,
    disabledTextColor = TextMuted
)

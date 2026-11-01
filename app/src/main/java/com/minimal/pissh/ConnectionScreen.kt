package com.minimal.pissh

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val profile = uiState.activeProfile
    val isConnecting = uiState.connectionStatus == ConnectionStatus.CONNECTING

    var showPassword by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PiSSH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Minimal LAN & Tailscale SSH Client",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Host Profile Switcher (4 predefined slots)
            Text(
                text = "Host Profiles",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.profiles.forEachIndexed { index, item ->
                    FilterChip(
                        selected = index == uiState.selectedProfileIndex,
                        onClick = { onSelectProfile(index) },
                        enabled = !isConnecting,
                        label = {
                            Text(
                                text = item.label.ifBlank { "Slot ${index + 1}" },
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // Connection Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = profile.label,
                        onValueChange = { value -> onUpdateProfile { it.copy(label = value) } },
                        label = { Text("Profile Label") },
                        placeholder = { Text("Pi (Tailscale)") },
                        singleLine = true,
                        enabled = !isConnecting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = profile.host,
                            onValueChange = { value -> onUpdateProfile { it.copy(host = value) } },
                            label = { Text("Hostname / Tailscale or LAN IP") },
                            placeholder = { Text("100.x.y.z or raspberrypi") },
                            singleLine = true,
                            enabled = !isConnecting,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Uri
                            ),
                            modifier = Modifier.weight(0.72f)
                        )

                        OutlinedTextField(
                            value = portText,
                            onValueChange = { value ->
                                val digitsOnly = value.filter { it.isDigit() }.take(5)
                                portText = digitsOnly
                                val parsed = digitsOnly.toIntOrNull()
                                if (parsed != null && parsed in 1..65535) {
                                    onUpdateProfile { it.copy(port = parsed) }
                                }
                            },
                            label = { Text("Port") },
                            placeholder = { Text("22") },
                            singleLine = true,
                            enabled = !isConnecting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.28f)
                        )
                    }

                    OutlinedTextField(
                        value = profile.username,
                        onValueChange = { value -> onUpdateProfile { it.copy(username = value) } },
                        label = { Text("Username") },
                        placeholder = { Text("pi") },
                        singleLine = true,
                        enabled = !isConnecting,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Authentication Method Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Auth Type:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FilterChip(
                            selected = profile.authType == AuthType.KEY,
                            onClick = { onUpdateProfile { it.copy(authType = AuthType.KEY) } },
                            enabled = !isConnecting,
                            label = { Text("SSH Key (Ed25519 / RSA)") }
                        )
                        FilterChip(
                            selected = profile.authType == AuthType.PASSWORD,
                            onClick = { onUpdateProfile { it.copy(authType = AuthType.PASSWORD) } },
                            enabled = !isConnecting,
                            label = { Text("Password") }
                        )
                    }

                    if (profile.authType == AuthType.KEY) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OpenSSH Private Key",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { keyFilePicker.launch(arrayOf("*/*")) },
                                enabled = !isConnecting
                            ) {
                                Text("Import Key File")
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
                            minLines = 4,
                            maxLines = 7,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = profile.passphrase,
                            onValueChange = { value -> onUpdateProfile { it.copy(passphrase = value) } },
                            label = { Text("Key Passphrase (Optional)") },
                            singleLine = true,
                            enabled = !isConnecting,
                            visualTransformation = if (showPassphrase) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                TextButton(onClick = { showPassphrase = !showPassphrase }) {
                                    Text(if (showPassphrase) "Hide" else "Show")
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = profile.password,
                            onValueChange = { value -> onUpdateProfile { it.copy(password = value) } },
                            label = { Text("SSH Password") },
                            singleLine = true,
                            enabled = !isConnecting,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                TextButton(onClick = { showPassword = !showPassword }) {
                                    Text(if (showPassword) "Hide" else "Show")
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Status / Error Banner
            if (!uiState.errorMessage.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else if (!uiState.statusMessage.isNullOrBlank()) {
                Text(
                    text = uiState.statusMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveProfile,
                    enabled = !isConnecting,
                    modifier = Modifier.weight(0.38f)
                ) {
                    Text("Save Profile")
                }

                Button(
                    onClick = onConnect,
                    enabled = !isConnecting,
                    modifier = Modifier.weight(0.62f)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...")
                    } else {
                        Text("Connect SSH")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

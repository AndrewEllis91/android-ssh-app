package com.minimal.pissh

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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

@Composable
fun ConnectionScreen(
    uiState: TerminalUiState,
    onAddProfile: () -> Unit = {},
    onEditProfile: (Int) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDeleteProfile: (Int) -> Unit = {},
    onMoveProfileUp: (Int) -> Unit = {},
    onMoveProfileDown: (Int) -> Unit = {},
    onUpdateProfile: ((HostProfile) -> HostProfile) -> Unit,
    onSaveProfile: () -> Unit,
    onConnect: () -> Unit,
    onConnectProfile: (Int) -> Unit = {},
    onToggleKeepScreenOn: () -> Unit = {},
    onDismissSaveSuccess: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isEditorOpen) {
            ConnectionEditorScreen(
                uiState = uiState,
                onCancelEdit = onCancelEdit,
                onDeleteProfile = onDeleteProfile,
                onUpdateProfile = onUpdateProfile,
                onSaveProfile = onSaveProfile,
                onConnect = onConnect,
                onToggleKeepScreenOn = onToggleKeepScreenOn
            )
        } else {
            ConnectionListScreen(
                uiState = uiState,
                onAddProfile = onAddProfile,
                onEditProfile = onEditProfile,
                onConnectProfile = onConnectProfile,
                onDeleteProfile = onDeleteProfile,
                onMoveProfileUp = onMoveProfileUp,
                onMoveProfileDown = onMoveProfileDown
            )
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

/**
 * Default State: Vertical list of saved SSH host connections with per-item Edit and Connect buttons,
 * reorder controls, delete confirmation dialogs, and a bottom-right Floating Action Button (+) to add new connections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionListScreen(
    uiState: TerminalUiState,
    onAddProfile: () -> Unit,
    onEditProfile: (Int) -> Unit,
    onConnectProfile: (Int) -> Unit,
    onDeleteProfile: (Int) -> Unit,
    onMoveProfileUp: (Int) -> Unit,
    onMoveProfileDown: (Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var isReorderMode by remember { mutableStateOf(false) }
    var profilePendingDelete by remember { mutableStateOf<HostProfile?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = if (isReorderMode) "Reorder Connections" else "Connections",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (uiState.profiles.size > 1) {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isReorderMode = !isReorderMode
                        }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = if (isReorderMode) "Exit Reorder Mode" else "Reorder Connections",
                                tint = if (isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAddProfile()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Connection")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status / Error Banners
            if (!uiState.errorMessage.isNullOrBlank()) {
                item(key = "error_banner") {
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
                }
            } else if (!uiState.statusMessage.isNullOrBlank()) {
                item(key = "status_banner") {
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
            }

            if (uiState.profiles.isEmpty()) {
                item(key = "empty_state") {
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Dns,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = "No Saved Connections",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap the + button below to add your first SSH connection.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = uiState.profiles,
                    key = { _, profile -> profile.id }
                ) { index, profile ->
                    val isThisConnecting = uiState.connectionStatus == ConnectionStatus.CONNECTING &&
                        uiState.connectingProfileId == profile.id
                    val isAnyConnecting = uiState.connectionStatus == ConnectionStatus.CONNECTING

                    ConnectionListItemCard(
                        profile = profile,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.profiles.lastIndex,
                        isReorderMode = isReorderMode,
                        isConnecting = isThisConnecting,
                        isAnyConnecting = isAnyConnecting,
                        onEdit = { onEditProfile(profile.id) },
                        onConnect = { onConnectProfile(profile.id) },
                        onMoveUp = { onMoveProfileUp(profile.id) },
                        onMoveDown = { onMoveProfileDown(profile.id) },
                        onDelete = { profilePendingDelete = profile }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (profilePendingDelete != null) {
        val target = profilePendingDelete!!
        AlertDialog(
            onDismissRequest = { profilePendingDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(text = "Delete Connection?")
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${target.label.ifBlank { "Host ${target.id + 1}" }}\"? This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        profilePendingDelete = null
                        onDeleteProfile(id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Vertical list item card showing connection title, monospace user@host:port, auth badge,
 * overflow menu (Move Up, Move Down, Delete), reorder buttons, and "Edit" / "Connect" buttons.
 */
@Composable
private fun ConnectionListItemCard(
    profile: HostProfile,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isReorderMode: Boolean,
    isConnecting: Boolean,
    isAnyConnecting: Boolean,
    onEdit: () -> Unit,
    onConnect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = profile.label.ifBlank { "Host ${profile.id + 1}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.username}@${if (profile.host.isNotBlank()) profile.host else "no host"}:${profile.port}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Auth Pill Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (profile.authType == AuthType.KEY) Icons.Outlined.Key else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (profile.authType == AuthType.KEY) "Key" else "Password",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Three-dot Options Menu for Move Up, Move Down, Delete
                Box {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Connection Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            enabled = canMoveUp,
                            onClick = {
                                showMenu = false
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoveUp()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            enabled = canMoveDown,
                            onClick = {
                                showMenu = false
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoveDown()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Quick Reorder Toolbar Row (visible when Reorder Mode is toggled on)
            if (isReorderMode) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Position Order",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onMoveUp()
                                },
                                enabled = canMoveUp,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move Up",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Up", style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onMoveDown()
                                },
                                enabled = canMoveDown,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move Down",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Down", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Action Buttons: Edit and Connect
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEdit()
                    },
                    enabled = !isAnyConnecting,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(0.40f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConnect()
                    },
                    enabled = !isAnyConnecting && profile.host.isNotBlank() && profile.username.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(0.60f)
                        .height(44.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Connecting...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Connect",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = ">_",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Add / Edit Connection View: Opens when the floating + button or an Edit button is pressed.
 * Provides complete form inputs for General, Authentication, and Behavior/Terminal settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionEditorScreen(
    uiState: TerminalUiState,
    onCancelEdit: () -> Unit,
    onDeleteProfile: (Int) -> Unit,
    onUpdateProfile: ((HostProfile) -> HostProfile) -> Unit,
    onSaveProfile: () -> Unit,
    onConnect: () -> Unit,
    onToggleKeepScreenOn: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val profile = uiState.activeProfile
    val isConnecting = uiState.connectionStatus == ConnectionStatus.CONNECTING

    BackHandler(enabled = !isConnecting) {
        onCancelEdit()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showPassword by remember(profile.id) { mutableStateOf(false) }
    var showPassphrase by remember(profile.id) { mutableStateOf(false) }
    var portText by remember(profile.id) { mutableStateOf(profile.port.toString()) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showEmulationMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
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
                        text = if (uiState.isCreatingNewProfile) {
                            "New Connection"
                        } else {
                            profile.label.ifBlank { "Edit Connection" }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCancelEdit,
                        enabled = !isConnecting
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            if (!uiState.isCreatingNewProfile) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Delete Connection",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

                    // Terminal Emulation Dropdown Pill
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
    }

    // Delete Confirmation Dialog for Editor Screen
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(text = "Delete Connection?")
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${profile.label.ifBlank { "Host ${profile.id + 1}" }}\"? This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteProfile(profile.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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

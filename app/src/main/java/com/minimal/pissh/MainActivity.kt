package com.minimal.pissh

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val TerminalDarkColorScheme = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF0D1117),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF161B22),
    onSurfaceVariant = Color(0xFF8B949E),
    error = Color(0xFFF85149)
)

class MainActivity : ComponentActivity() {

    private val viewModel: TerminalViewModel by viewModels()
    private var isServiceBound = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Ongoing service still runs even if notification permission is declined */ }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? SshForegroundService.LocalBinder ?: return
            isServiceBound = true
            viewModel.attachService(localBinder.getService())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        Intent(this, SshForegroundService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Dynamically toggle FLAG_KEEP_SCREEN_ON for long-running Pi builds/scripts
            LaunchedEffect(uiState.keepScreenOn) {
                if (uiState.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            MaterialTheme(colorScheme = TerminalDarkColorScheme) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    if (uiState.connectionStatus == ConnectionStatus.CONNECTED) {
                        TerminalScreen(
                            uiState = uiState,
                            onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
                            onToggleCtrlLatch = viewModel::toggleCtrlLatch,
                            onUpdateFontSize = viewModel::updateFontSize,
                            onTerminalGeometryChanged = viewModel::onTerminalGeometryChanged,
                            onCharacterTyped = viewModel::onCharacterTyped,
                            onSendVt100 = viewModel::sendVt100Sequence,
                            onSendCtrlSignal = viewModel::sendCtrlSignal,
                            onSendRawBytes = viewModel::sendRawBytes,
                            onPasteText = viewModel::onPasteText,
                            onDisconnect = viewModel::disconnect
                        )
                    } else {
                        ConnectionScreen(
                            uiState = uiState,
                            onSelectProfile = viewModel::selectProfile,
                            onUpdateProfile = viewModel::updateActiveProfile,
                            onSaveProfile = viewModel::saveActiveProfile,
                            onConnect = viewModel::connect
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        // If no SSH session is active when exiting, ensure service is stopped
        if (viewModel.uiState.value.connectionStatus != ConnectionStatus.CONNECTED) {
            stopService(Intent(this, SshForegroundService::class.java))
        }
        super.onDestroy()
    }
}

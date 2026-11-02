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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

// Material You fallback color schemes for pre-Android 12 devices
private val MaterialYouDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90D5A3),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF1E522E),
    onPrimaryContainer = Color(0xFFAEF2BF),
    secondary = Color(0xFFB9CCB8),
    onSecondary = Color(0xFF243427),
    secondaryContainer = Color(0xFF3B4B3C),
    onSecondaryContainer = Color(0xFFD5E8D4),
    tertiary = Color(0xFFA1CED6),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF1F4D54),
    onTertiaryContainer = Color(0xFFBCEBF2),
    surface = Color(0xFF111411),
    onSurface = Color(0xFFE1E4DE),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BE),
    surfaceContainer = Color(0xFF1D211D),
    surfaceContainerHigh = Color(0xFF282B27),
    surfaceContainerHighest = Color(0xFF333632),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE1E4DE),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414941),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val MaterialYouLightColorScheme = lightColorScheme(
    primary = Color(0xFF2B6B40),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFAEF2BF),
    onPrimaryContainer = Color(0xFF00210C),
    secondary = Color(0xFF516353),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E8D4),
    onSecondaryContainer = Color(0xFF0F1F13),
    surface = Color(0xFFF7FAF4),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainer = Color(0xFFECEFE9),
    surfaceContainerHigh = Color(0xFFE6E9E3),
    surfaceContainerHighest = Color(0xFFE0E4DD),
    background = Color(0xFFF7FAF4),
    onBackground = Color(0xFF191C19),
    outline = Color(0xFF717970),
    outlineVariant = Color(0xFFC1C9BE)
)

class MainActivity : ComponentActivity() {

    private val viewModel: TerminalViewModel by viewModels()
    private var isServiceBound = false

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

        // Capture any uncaught crash to logcat and local file for immediate diagnostics
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("PiSSH_Crash", "Fatal crash: ${throwable.message}", throwable)
            runCatching {
                File(filesDir, "last_crash.txt").writeText(throwable.stackTraceToString())
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Intent(this, SshForegroundService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            val context = LocalContext.current
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isDark = isSystemInDarkTheme()

            // Dynamic Monet Palette on Android 12+ (adapts to user wallpaper), with Material You fallback on older versions
            val colorScheme = remember(isDark) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (isDark) MaterialYouDarkColorScheme else MaterialYouLightColorScheme
                }
            }

            // Safe lifecycle-aware notification permission request for Android 13+ (Pixel 7 Pro)
            val notificationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Handled gracefully */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Dynamically toggle FLAG_KEEP_SCREEN_ON for long-running Pi builds/scripts
            LaunchedEffect(uiState.keepScreenOn) {
                if (uiState.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
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
                            onAddProfile = viewModel::startCreateProfile,
                            onEditProfile = viewModel::startEditProfile,
                            onCancelEdit = viewModel::closeEditor,
                            onDeleteProfile = viewModel::deleteProfile,
                            onMoveProfileUp = viewModel::moveProfileUp,
                            onMoveProfileDown = viewModel::moveProfileDown,
                            onUpdateProfile = viewModel::updateActiveProfile,
                            onSaveProfile = viewModel::saveActiveProfile,
                            onDismissSaveSuccess = viewModel::dismissSaveSuccess,
                            onConnectProfile = viewModel::connectProfile,
                            onConnect = viewModel::connect,
                            onToggleKeepScreenOn = viewModel::toggleKeepScreenOn
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        if (viewModel.uiState.value.connectionStatus != ConnectionStatus.CONNECTED) {
            stopService(Intent(this, SshForegroundService::class.java))
        }
        super.onDestroy()
    }
}

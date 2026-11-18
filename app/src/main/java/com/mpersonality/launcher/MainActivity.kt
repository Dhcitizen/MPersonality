package com.mpersonality.launcher

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var launcher: Launcher

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras do sistema transparentes, com ícones claros (por cima do papel de parede).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        launcher = Launcher(this)
        launcher.start()

        // Com papel de parede próprio, o sistema não precisa desenhar o dele por trás (economiza GPU).
        lifecycleScope.launch {
            snapshotFlow { launcher.wallpaper.image != null }.collect { custom -> useSystemWallpaper(!custom) }
        }

        setContent { LauncherRoot(launcher) }
    }

    private fun useSystemWallpaper(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        }
    }

    // Botão "Início" com o launcher aberto: volta para a tela inicial (e para a 1ª página).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launcher.onHomePressed()
    }

    override fun onDestroy() {
        launcher.dispose()
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherRoot(launcher: Launcher) {
    val view = LocalView.current
    SideEffect {
        launcher.haptic = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val screen = launcher.screen
    LaunchedEffect(screen) {
        if (screen != Screen.DRAWER) keyboard?.hide()
    }
    BackHandler { launcher.onBack() }

    val pagerState = rememberPagerState { launcher.pagerPageCount }

    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { launcher.wallpaper.rootSize = it }
                    .launcherGestures(launcher),
            ) {
                if (launcher.wallpaper.image != null) {
                    // Camada própria: desenhada uma vez e reaproveitada.
                    Box(Modifier.fillMaxSize().graphicsLayer().wallpaperBehind(launcher.wallpaper, blurred = false))
                }

                HomeScreen(launcher, pagerState)

                AnimatedVisibility(
                    visible = screen == Screen.DRAWER,
                    enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { it / 10 },
                    exit = fadeOut(tween(90)),
                ) {
                    AppDrawer(launcher)
                }

                when (screen) {
                    Screen.SETTINGS -> SettingsScreen(launcher)
                    Screen.CARD_EDITOR -> launcher.editingCardId?.let { CardEditorScreen(launcher, it) }
                    else -> Unit
                }

                DragOverlay(launcher)
                MenuOverlay(launcher)
            }
        }
    }
}

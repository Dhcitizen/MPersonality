package com.mpersonality.launcher

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Screen { HOME, DRAWER, SETTINGS }

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf(Screen.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras do sistema transparentes, com ícones claros (por cima do papel de parede).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val settings = ClockSettings(applicationContext)
        setContent {
            LauncherApp(settings, screen) { screen = it }
        }
    }

    // Botão "Início" apertado com o launcher já aberto: volta para a tela inicial.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screen = Screen.HOME
    }
}

@Composable
private fun LauncherApp(
    settings: ClockSettings,
    screen: Screen,
    onScreenChange: (Screen) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

    // Carrega a lista ao iniciar e atualiza toda vez que a gaveta abre
    // (assim apps recém-instalados aparecem).
    val drawerOpen = screen == Screen.DRAWER
    LaunchedEffect(drawerOpen) {
        if (drawerOpen || apps.isEmpty()) {
            apps = withContext(Dispatchers.Default) { AppRepository.load(context.applicationContext) }
        }
    }

    val goHome = {
        keyboard?.hide()
        onScreenChange(Screen.HOME)
    }

    // "Voltar" fecha a gaveta/configurações; na tela inicial não faz nada.
    BackHandler { if (screen != Screen.HOME) goHome() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                settings = settings,
                onOpenDrawer = { onScreenChange(Screen.DRAWER) },
                onOpenSettings = { onScreenChange(Screen.SETTINGS) },
            )

            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 8 },
                exit = fadeOut(tween(100)),
            ) {
                AppDrawer(
                    apps = apps,
                    onLaunch = { app ->
                        AppRepository.launch(context, app)
                        goHome()
                    },
                    onAppInfo = { app -> AppRepository.openDetails(context, app) },
                    onOpenSettings = {
                        keyboard?.hide()
                        onScreenChange(Screen.SETTINGS)
                    },
                    onClose = goHome,
                )
            }

            if (screen == Screen.SETTINGS) {
                SettingsScreen(settings, onBack = goHome)
            }
        }
    }
}

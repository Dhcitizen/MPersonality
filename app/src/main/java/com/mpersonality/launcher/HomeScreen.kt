package com.mpersonality.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Tela inicial: papel de parede do sistema (vem do tema), relógio no topo.
 * - Deslizar para cima: abre a gaveta de apps.
 * - Segurar o dedo: abre as configurações.
 */
@Composable
fun HomeScreen(
    settings: ClockSettings,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val openDistance = with(LocalDensity.current) { 60.dp.toPx() }
    val currentOnOpenDrawer by rememberUpdatedState(onOpenDrawer)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -openDistance) currentOnOpenDrawer() },
                ) { change, dragAmount ->
                    change.consume()
                    total += dragAmount
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { currentOnOpenSettings() })
            }
            .systemBarsPadding(),
    ) {
        ClockView(
            style = settings.style,
            scale = settings.size,
            color = Color(settings.color),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
        )

        // Pequena barra no rodapé indicando "deslize para cima".
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .size(width = 40.dp, height = 5.dp)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)),
        )
    }
}

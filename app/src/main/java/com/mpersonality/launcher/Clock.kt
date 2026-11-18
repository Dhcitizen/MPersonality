package com.mpersonality.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hora atual, atualizada só uma vez por minuto e só enquanto a tela está visível.
 * Isso economiza bateria.
 */
@Composable
fun rememberCurrentTime(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000L - now % 60_000L)
            }
        }
    }
    return now
}

@Composable
fun ClockView(
    style: ClockStyle,
    scale: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val now = rememberCurrentTime()
    when (style) {
        ClockStyle.DIGITAL_GRANDE -> DigitalBigClock(now, scale, color, modifier)
        ClockStyle.DIGITAL_FINO -> DigitalThinClock(now, scale, color, modifier)
        ClockStyle.ANALOGICO -> AnalogClock(now, scale, color, modifier)
    }
}

/** Sombra leve para o relógio ficar legível em qualquer papel de parede. */
private fun shadowFor(color: Color): Shadow = Shadow(
    color = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f),
    offset = Offset(0f, 2f),
    blurRadius = 8f,
)

@Composable
private fun timeText(now: Long): String {
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val pattern = if (is24h) "HH:mm" else "h:mm"
    return remember(now, pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)) }
}

@Composable
private fun DigitalBigClock(now: Long, scale: Float, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Text(
            text = timeText(now),
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                color = color,
                fontSize = (96 * scale).sp,
                fontWeight = FontWeight.Bold,
                shadow = shadowFor(color),
            ),
        )
    }
}

@Composable
private fun DigitalThinClock(now: Long, scale: Float, color: Color, modifier: Modifier) {
    val dateText = remember(now) {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        SimpleDateFormat(pattern, locale).format(Date(now)).replaceFirstChar { it.titlecase(locale) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Text(
            text = timeText(now),
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                color = color,
                fontSize = (80 * scale).sp,
                fontWeight = FontWeight.Thin,
                shadow = shadowFor(color),
            ),
        )
        androidx.compose.material3.Text(
            text = dateText,
            maxLines = 1,
            style = TextStyle(
                color = color,
                fontSize = (20 * scale).sp,
                fontWeight = FontWeight.Light,
                shadow = shadowFor(color),
            ),
        )
    }
}

@Composable
private fun AnalogClock(now: Long, scale: Float, color: Color, modifier: Modifier) {
    val calendar = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val minutes = calendar.get(Calendar.MINUTE)
    val hours = calendar.get(Calendar.HOUR) + minutes / 60f
    val backdrop = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.25f)

    Canvas(modifier.size((220 * scale).dp)) {
        val radius = size.minDimension / 2f
        val c = center
        val stroke = radius * 0.04f

        drawCircle(backdrop, radius)
        drawCircle(color, radius - stroke / 2f, style = Stroke(stroke))

        // Marcações das horas (as de 3, 6, 9 e 12 são maiores).
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0)
            val major = i % 3 == 0
            drawLine(
                color = color,
                start = c + polar(angle, radius * if (major) 0.70f else 0.80f),
                end = c + polar(angle, radius * 0.88f),
                strokeWidth = if (major) stroke * 1.2f else stroke * 0.6f,
                cap = StrokeCap.Round,
            )
        }

        // Ponteiro das horas.
        drawLine(color, c, c + polar(Math.toRadians(hours * 30.0), radius * 0.5f), stroke * 2f, StrokeCap.Round)
        // Ponteiro dos minutos.
        drawLine(color, c, c + polar(Math.toRadians(minutes * 6.0), radius * 0.75f), stroke * 1.3f, StrokeCap.Round)
        drawCircle(color, stroke * 1.8f, c)
    }
}

/** Ponto a partir do centro; ângulo 0 = 12 horas, girando no sentido horário. */
private fun polar(angle: Double, r: Float): Offset =
    Offset((sin(angle) * r).toFloat(), (-cos(angle) * r).toFloat())

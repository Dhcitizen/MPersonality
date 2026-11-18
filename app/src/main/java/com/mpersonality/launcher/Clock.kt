package com.mpersonality.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Hora atual, atualizada uma vez por minuto e só enquanto a tela está visível.
 * Quem lê o valor é só o texto/desenho do relógio, então o resto da tela não é refeito.
 */
@Composable
fun rememberClockTime(): State<Long> {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                state.longValue = now
                delay(60_000L - now % 60_000L)
            }
        }
    }
    return state
}

private class ClockSpec(
    val scale: Float,
    val color: Color,
    val family: FontFamily,
    val shadow: ClockShadow,
    val horizontal: Alignment.Horizontal,
    val textAlign: TextAlign,
    val is24h: Boolean,
)

@Composable
fun ClockView(settings: ClockSettings, modifier: Modifier = Modifier) {
    val time = rememberClockTime()
    val (horizontal, textAlign) = when (settings.align) {
        ClockAlign.ESQUERDA -> Alignment.Start to TextAlign.Start
        ClockAlign.CENTRO -> Alignment.CenterHorizontally to TextAlign.Center
        ClockAlign.DIREITA -> Alignment.End to TextAlign.End
    }
    val spec = ClockSpec(
        scale = settings.size,
        color = Color(settings.color),
        family = settings.font.family,
        shadow = settings.shadow,
        horizontal = horizontal,
        textAlign = textAlign,
        is24h = DateFormat.is24HourFormat(LocalContext.current),
    )
    // graphicsLayer: o relógio é desenhado numa camada própria. Quando o minuto
    // muda, só essa camada é redesenhada, não a tela inteira.
    Box(modifier.graphicsLayer()) {
        when (settings.style) {
            ClockStyle.GIGANTE -> GiantClock(time, spec)
            ClockStyle.EMPILHADO -> StackedClock(time, spec)
            ClockStyle.ELEGANTE -> ElegantClock(time, spec)
            ClockStyle.CONTORNO -> OutlineClock(time, spec)
            ClockStyle.BRILHO -> GlowClock(time, spec)
            ClockStyle.ANALOGICO_MINIMAL -> MinimalAnalogClock(time, spec)
            ClockStyle.ANALOGICO_CLASSICO -> ClassicAnalogClock(time, spec)
        }
    }
}

// ---------- Peças comuns ----------

private fun shadowColorFor(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White

private fun textShadow(spec: ClockSpec): Shadow? = when (spec.shadow) {
    ClockShadow.NENHUMA -> null
    ClockShadow.SUAVE -> Shadow(shadowColorFor(spec.color).copy(alpha = 0.45f), Offset(0f, 2f), 10f)
    ClockShadow.FORTE -> Shadow(shadowColorFor(spec.color).copy(alpha = 0.85f), Offset(0f, 4f), 18f)
}

private fun baseStyle(spec: ClockSpec) = TextStyle(
    color = spec.color,
    fontFamily = spec.family,
    textAlign = spec.textAlign,
    shadow = textShadow(spec),
)

private fun hourMinutePattern(spec: ClockSpec) = if (spec.is24h) "HH:mm" else "h:mm"

/** Linha de texto com a hora. Só esta função é refeita a cada minuto. */
@Composable
private fun TimeLine(
    time: State<Long>,
    pattern: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    uppercase: Boolean = false,
) {
    val format = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    val text = format.format(Date(time.value))
    Text(
        text = if (uppercase) text.uppercase(Locale.getDefault()) else text,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** Reduz o espaço vazio em cima e embaixo de textos muito grandes. */
private fun Modifier.tight(fraction: Float) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * fraction).roundToInt()
    layout(placeable.width, height) { placeable.place(0, -(placeable.height - height) / 2) }
}

// ---------- Estilos digitais ----------

@Composable
private fun GiantClock(time: State<Long>, spec: ClockSpec) {
    Column(horizontalAlignment = spec.horizontal) {
        TimeLine(
            time,
            hourMinutePattern(spec),
            baseStyle(spec).copy(
                fontSize = (112 * spec.scale).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.04).em,
            ),
            Modifier.tight(0.86f),
        )
    }
}

@Composable
private fun StackedClock(time: State<Long>, spec: ClockSpec) {
    val size = (124 * spec.scale).sp
    Column(horizontalAlignment = spec.horizontal) {
        TimeLine(
            time,
            if (spec.is24h) "HH" else "hh",
            baseStyle(spec).copy(fontSize = size, fontWeight = FontWeight.Black, letterSpacing = (-0.03).em),
            Modifier.tight(0.78f),
        )
        TimeLine(
            time,
            "mm",
            baseStyle(spec).copy(
                fontSize = size,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.03).em,
                color = spec.color.copy(alpha = 0.85f),
            ),
            Modifier.tight(0.78f),
        )
    }
}

@Composable
private fun ElegantClock(time: State<Long>, spec: ClockSpec) {
    val datePattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM") }
    Column(horizontalAlignment = spec.horizontal) {
        TimeLine(
            time,
            hourMinutePattern(spec),
            baseStyle(spec).copy(fontSize = (84 * spec.scale).sp, fontWeight = FontWeight.Thin, letterSpacing = 0.02.em),
        )
        TimeLine(
            time,
            datePattern,
            baseStyle(spec).copy(
                fontSize = (14 * spec.scale).sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.22.em,
                color = spec.color.copy(alpha = 0.9f),
            ),
            uppercase = true,
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun OutlineClock(time: State<Long>, spec: ClockSpec) {
    val strokePx = with(LocalDensity.current) { (2.5f * spec.scale).coerceAtLeast(1f).dp.toPx() }
    Column(horizontalAlignment = spec.horizontal) {
        TimeLine(
            time,
            hourMinutePattern(spec),
            baseStyle(spec).copy(
                fontSize = (112 * spec.scale).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.02).em,
                drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
            ),
            Modifier.tight(0.86f),
        )
    }
}

@Composable
private fun GlowClock(time: State<Long>, spec: ClockSpec) {
    val glowPx = with(LocalDensity.current) { (16 * spec.scale).dp.toPx() }
    Column(horizontalAlignment = spec.horizontal) {
        TimeLine(
            time,
            hourMinutePattern(spec),
            baseStyle(spec).copy(
                fontSize = (100 * spec.scale).sp,
                fontWeight = FontWeight.Normal,
                shadow = Shadow(spec.color.copy(alpha = 0.9f), Offset.Zero, glowPx),
            ),
            Modifier.tight(0.9f),
        )
    }
}

// ---------- Estilos analógicos (o tempo é lido só no desenho) ----------

private fun backdropAlpha(shadow: ClockShadow) = when (shadow) {
    ClockShadow.NENHUMA -> 0f
    ClockShadow.SUAVE -> 0.18f
    ClockShadow.FORTE -> 0.35f
}

@Composable
private fun MinimalAnalogClock(time: State<Long>, spec: ClockSpec) {
    val color = spec.color
    val backdrop = shadowColorFor(color).copy(alpha = backdropAlpha(spec.shadow))
    Canvas(Modifier.size((200 * spec.scale).dp)) {
        val calendar = Calendar.getInstance().apply { timeInMillis = time.value }
        val minutes = calendar.get(Calendar.MINUTE)
        val hours = calendar.get(Calendar.HOUR) + minutes / 60f
        val r = size.minDimension / 2f
        val c = center
        if (backdrop.alpha > 0f) drawCircle(backdrop, r)
        for (i in 0 until 12) {
            val major = i % 3 == 0
            drawCircle(
                color = color.copy(alpha = if (major) 1f else 0.5f),
                radius = r * if (major) 0.035f else 0.02f,
                center = c + polar(Math.toRadians(i * 30.0), r * 0.86f),
            )
        }
        val hourAngle = Math.toRadians(hours * 30.0)
        val minuteAngle = Math.toRadians(minutes * 6.0)
        drawLine(color, c - polar(hourAngle, r * 0.08f), c + polar(hourAngle, r * 0.48f), r * 0.075f, StrokeCap.Round)
        drawLine(color.copy(alpha = 0.9f), c - polar(minuteAngle, r * 0.08f), c + polar(minuteAngle, r * 0.72f), r * 0.04f, StrokeCap.Round)
        drawCircle(color, r * 0.06f, c)
    }
}

@Composable
private fun ClassicAnalogClock(time: State<Long>, spec: ClockSpec) {
    val color = spec.color
    val backdrop = shadowColorFor(color).copy(alpha = maxOf(0.12f, backdropAlpha(spec.shadow)))
    Canvas(Modifier.size((220 * spec.scale).dp)) {
        val calendar = Calendar.getInstance().apply { timeInMillis = time.value }
        val minutes = calendar.get(Calendar.MINUTE)
        val hours = calendar.get(Calendar.HOUR) + minutes / 60f
        val r = size.minDimension / 2f
        val c = center
        val stroke = r * 0.04f
        drawCircle(backdrop, r)
        drawCircle(color, r - stroke / 2f, style = Stroke(stroke))
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0)
            val major = i % 3 == 0
            drawLine(
                color = color,
                start = c + polar(angle, r * if (major) 0.70f else 0.80f),
                end = c + polar(angle, r * 0.88f),
                strokeWidth = if (major) stroke * 1.2f else stroke * 0.6f,
                cap = StrokeCap.Round,
            )
        }
        drawLine(color, c, c + polar(Math.toRadians(hours * 30.0), r * 0.5f), stroke * 2f, StrokeCap.Round)
        drawLine(color, c, c + polar(Math.toRadians(minutes * 6.0), r * 0.75f), stroke * 1.3f, StrokeCap.Round)
        drawCircle(color, stroke * 1.8f, c)
    }
}

/** Ponto a partir do centro; ângulo 0 = 12 horas, girando no sentido horário. */
private fun polar(angle: Double, r: Float): Offset =
    Offset((sin(angle) * r).toFloat(), (-cos(angle) * r).toFloat())

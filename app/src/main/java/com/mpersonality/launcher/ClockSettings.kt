package com.mpersonality.launcher

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily

enum class ClockStyle(val label: String) {
    GIGANTE("Hora gigante (fonte grossa)"),
    EMPILHADO("Hora empilhada"),
    ELEGANTE("Fina e elegante com data"),
    CONTORNO("Contorno sem preenchimento"),
    BRILHO("Brilho suave"),
    ANALOGICO_MINIMAL("Analógico minimalista"),
    ANALOGICO_CLASSICO("Analógico clássico"),
}

enum class ClockFont(val label: String) {
    PADRAO("Padrão"),
    CONDENSADA("Condensada"),
    SERIFADA("Serifada"),
    MONO("Mono"),
    MANUSCRITA("Manuscrita");

    val family: FontFamily by lazy {
        when (this) {
            PADRAO -> FontFamily.SansSerif
            CONDENSADA -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
            SERIFADA -> FontFamily.Serif
            MONO -> FontFamily.Monospace
            MANUSCRITA -> FontFamily.Cursive
        }
    }
}

enum class ClockShadow(val label: String) {
    NENHUMA("Sem sombra"),
    SUAVE("Suave"),
    FORTE("Forte"),
}

enum class ClockAlign(val label: String) {
    ESQUERDA("Esquerda"),
    CENTRO("Centro"),
    DIREITA("Direita"),
}

/** Configurações do relógio, salvas no próprio celular. */
class ClockSettings(context: Context) {

    private val prefs = context.getSharedPreferences("mpersonality_prefs", Context.MODE_PRIVATE)

    var style by mutableStateOf(readEnum(KEY_STYLE, ClockStyle.GIGANTE))
        private set
    var font by mutableStateOf(readEnum(KEY_FONT, ClockFont.PADRAO))
        private set
    var shadow by mutableStateOf(readEnum(KEY_SHADOW, ClockShadow.SUAVE))
        private set
    var align by mutableStateOf(readEnum(KEY_ALIGN, ClockAlign.CENTRO))
        private set
    var size by mutableFloatStateOf(prefs.getFloat(KEY_SIZE, 1f).coerceIn(MIN_SIZE, MAX_SIZE))
        private set
    var topOffset by mutableFloatStateOf(prefs.getFloat(KEY_TOP, 32f).coerceIn(0f, MAX_TOP))
        private set
    var color by mutableIntStateOf(prefs.getInt(KEY_COLOR, 0xFFFFFFFF.toInt()))
        private set

    fun changeStyle(value: ClockStyle) {
        style = value
        prefs.edit().putString(KEY_STYLE, value.name).apply()
    }

    fun changeFont(value: ClockFont) {
        font = value
        prefs.edit().putString(KEY_FONT, value.name).apply()
    }

    fun changeShadow(value: ClockShadow) {
        shadow = value
        prefs.edit().putString(KEY_SHADOW, value.name).apply()
    }

    fun changeAlign(value: ClockAlign) {
        align = value
        prefs.edit().putString(KEY_ALIGN, value.name).apply()
    }

    fun changeColor(value: Int) {
        color = value
        prefs.edit().putInt(KEY_COLOR, value).apply()
    }

    /** Muda na tela enquanto arrasta; [saveSliders] grava quando solta. */
    fun changeSize(value: Float) {
        size = value.coerceIn(MIN_SIZE, MAX_SIZE)
    }

    fun changeTopOffset(value: Float) {
        topOffset = value.coerceIn(0f, MAX_TOP)
    }

    fun saveSliders() {
        prefs.edit().putFloat(KEY_SIZE, size).putFloat(KEY_TOP, topOffset).apply()
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T {
        val saved = prefs.getString(key, null)
        return enumValues<T>().firstOrNull { it.name == saved } ?: default
    }

    companion object {
        const val MIN_SIZE = 0.4f
        const val MAX_SIZE = 1.6f
        const val MAX_TOP = 240f

        private const val KEY_STYLE = "clock_style_v2"
        private const val KEY_FONT = "clock_font"
        private const val KEY_SHADOW = "clock_shadow"
        private const val KEY_ALIGN = "clock_align"
        private const val KEY_SIZE = "clock_size"
        private const val KEY_TOP = "clock_top"
        private const val KEY_COLOR = "clock_color"
    }
}

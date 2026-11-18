package com.mpersonality.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ClockStyle(val label: String) {
    DIGITAL_GRANDE("Digital grande"),
    DIGITAL_FINO("Digital fino com data"),
    ANALOGICO("Analógico"),
}

/**
 * Configurações do relógio, salvas no próprio celular (SharedPreferences).
 * Nada vai para a internet.
 */
class ClockSettings(context: Context) {

    private val prefs = context.getSharedPreferences("mpersonality_prefs", Context.MODE_PRIVATE)

    var style by mutableStateOf(readStyle())
        private set

    var size by mutableFloatStateOf(prefs.getFloat(KEY_SIZE, 1f).coerceIn(MIN_SIZE, MAX_SIZE))
        private set

    var color by mutableIntStateOf(prefs.getInt(KEY_COLOR, DEFAULT_COLOR))
        private set

    fun changeStyle(value: ClockStyle) {
        style = value
        prefs.edit().putString(KEY_STYLE, value.name).apply()
    }

    /** Muda o tamanho na tela; [saveSize] grava quando o usuário solta o controle. */
    fun changeSize(value: Float) {
        size = value.coerceIn(MIN_SIZE, MAX_SIZE)
    }

    fun saveSize() {
        prefs.edit().putFloat(KEY_SIZE, size).apply()
    }

    fun changeColor(value: Int) {
        color = value
        prefs.edit().putInt(KEY_COLOR, value).apply()
    }

    private fun readStyle(): ClockStyle {
        val saved = prefs.getString(KEY_STYLE, null)
        return ClockStyle.entries.firstOrNull { it.name == saved } ?: ClockStyle.DIGITAL_GRANDE
    }

    companion object {
        const val MIN_SIZE = 0.5f
        const val MAX_SIZE = 1.5f

        private const val KEY_STYLE = "clock_style"
        private const val KEY_SIZE = "clock_size"
        private const val KEY_COLOR = "clock_color"

        val DEFAULT_COLOR: Int = 0xFFFFFFFF.toInt()

        val PRESET_COLORS: List<Int> = listOf(
            0xFFFFFFFF, // branco
            0xFF000000, // preto
            0xFFFFEB3B, // amarelo
            0xFFFF9800, // laranja
            0xFFF44336, // vermelho
            0xFFE91E63, // rosa
            0xFF9C27B0, // roxo
            0xFF2196F3, // azul
            0xFF00BCD4, // ciano
            0xFF4CAF50, // verde
        ).map { it.toInt() }
    }
}

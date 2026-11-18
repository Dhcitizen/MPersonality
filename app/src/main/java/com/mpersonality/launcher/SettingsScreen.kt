package com.mpersonality.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(settings: ClockSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Configurações", style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)

        // Prévia do relógio.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .background(Color(0xFF2B2B33), RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            ClockView(settings.style, settings.size, Color(settings.color))
        }

        SectionTitle("Estilo do relógio")
        ClockStyle.entries.forEach { style ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = settings.style == style,
                        onClick = { settings.changeStyle(style) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = settings.style == style, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(style.label, color = colors.onBackground)
            }
        }

        SectionTitle("Tamanho: ${(settings.size * 100).roundToInt()}%")
        Slider(
            value = settings.size,
            onValueChange = { settings.changeSize(it) },
            onValueChangeFinished = { settings.saveSize() },
            valueRange = ClockSettings.MIN_SIZE..ClockSettings.MAX_SIZE,
        )

        SectionTitle("Cor")
        ClockSettings.PRESET_COLORS.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { argb ->
                    ColorSwatch(
                        color = Color(argb),
                        selected = settings.color == argb,
                        onClick = { settings.changeColor(argb) },
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        OutlinedButton(onClick = { openWallpaperPicker(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Trocar papel de parede")
        }
        OutlinedButton(onClick = { openHomeSettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Definir como tela inicial padrão")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar")
        }

        Text(
            "Dica: na tela inicial, deslize para cima para ver os apps. " +
                "Segure o dedo em um espaço vazio para voltar a estas configurações.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape,
            )
            .padding(5.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick),
    )
}

private fun openWallpaperPicker(context: Context) {
    try {
        val intent = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Escolha o papel de parede")
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Não foi possível abrir o seletor de papel de parede", Toast.LENGTH_SHORT).show()
    }
}

private fun openHomeSettings(context: Context) {
    val options = listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (intent in options) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // tenta a próxima opção
        }
    }
}

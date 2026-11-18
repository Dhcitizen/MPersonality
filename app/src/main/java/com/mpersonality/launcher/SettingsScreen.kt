package com.mpersonality.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(launcher: Launcher) {
    val context = LocalContext.current
    val clock = launcher.clock
    val wallpaper = launcher.wallpaper
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) launcher.importWallpaper(uri)
    }

    SettingsPage("Configurações") {
        // Prévia do relógio sobre o papel de parede.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2B2B33))
                .wallpaperBehind(wallpaper, blurred = false)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            ClockView(clock)
        }

        SectionTitle("Estilo do relógio")
        ClockStyle.entries.forEach { style ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = clock.style == style, onClick = { clock.changeStyle(style) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = clock.style == style, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(style.label)
            }
        }

        SectionTitle("Fonte")
        OptionChips(ClockFont.entries, clock.font, { it.label }) { clock.changeFont(it) }

        SectionTitle("Tamanho e cor")
        LabeledSlider(
            label = "Tamanho: ${(clock.size * 100).roundToInt()}%",
            value = clock.size,
            range = ClockSettings.MIN_SIZE..ClockSettings.MAX_SIZE,
            onChange = { clock.changeSize(it) },
            onFinished = { clock.saveSliders() },
        )
        ColorPalette(clock.color) { clock.changeColor(it) }

        SectionTitle("Sombra")
        OptionChips(ClockShadow.entries, clock.shadow, { it.label }) { clock.changeShadow(it) }

        SectionTitle("Posição na tela")
        OptionChips(ClockAlign.entries, clock.align, { it.label }) { clock.changeAlign(it) }
        LabeledSlider(
            label = "Distância do topo: ${clock.topOffset.roundToInt()}",
            value = clock.topOffset,
            range = 0f..ClockSettings.MAX_TOP,
            onChange = { clock.changeTopOffset(it) },
            onFinished = { clock.saveSliders() },
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionTitle("Papel de parede")
        Button(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Escolher imagem da galeria") }
        HintText(
            "A imagem é reduzida para a resolução da tela (fica leve) e permite o efeito de desfoque nos cards e na gaveta.",
        )
        if (wallpaper.image != null) {
            OutlinedButton(onClick = { wallpaper.clear() }, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar a usar o papel de parede do sistema")
            }
        } else {
            OutlinedButton(onClick = { openSystemWallpaperPicker(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Trocar papel de parede do sistema")
            }
        }

        SectionTitle("Tela inicial")
        OutlinedButton(onClick = { openHomeSettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Definir como tela inicial padrão")
        }

        Button(onClick = { launcher.goHome() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Voltar")
        }

        HintText(
            "Dicas: deslize para cima para ver os apps. Segure um app na gaveta e arraste para a tela inicial, " +
                "para o dock ou para um card. Segure um espaço vazio da tela inicial para criar um card.",
        )
    }
}

private fun openSystemWallpaperPicker(context: Context) {
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Escolha o papel de parede"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Não foi possível abrir o seletor de papel de parede", Toast.LENGTH_SHORT).show()
    }
}

private fun openHomeSettings(context: Context) {
    for (intent in listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // tenta a próxima opção
        }
    }
}

package com.mpersonality.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CardEditorScreen(launcher: Launcher, cardId: Long) {
    val layout = launcher.layout
    val card = layout.card(cardId)
    if (card == null) {
        LaunchedEffect(Unit) { launcher.goHome() }
        return
    }

    var picking by remember { mutableStateOf(false) }
    if (picking) {
        BackHandler { picking = false }
        CardAppPicker(launcher, card, onDone = { picking = false })
        return
    }

    val s = card.style
    val update: ((CardStyle) -> CardStyle) -> Unit = { change ->
        layout.updateCard(cardId) { it.copy(style = change(it.style)) }
        layout.save()
    }

    SettingsPage("Editar card") {
        // Prévia sobre o papel de parede.
        Box(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2B2B33))
                .wallpaperBehind(launcher.wallpaper, blurred = false)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppCard(launcher, card, Modifier.fillMaxWidth().height(96.dp), iconSize = 44.dp, interactive = false)
        }

        SectionTitle("Título")
        SwitchRow("Mostrar título em cima", s.showTitle) { v -> update { it.copy(showTitle = v) } }
        if (s.showTitle) {
            OutlinedTextField(
                value = s.title,
                onValueChange = { v -> update { it.copy(title = v.take(40)) } },
                placeholder = { Text("Ex.: Redes sociais") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionTitle("Borda")
        SwitchRow("Mostrar borda", s.showBorder) { v -> update { it.copy(showBorder = v) } }
        if (s.showBorder) {
            LabeledSlider(
                label = "Grossura: ${"%.1f".format(s.borderWidth)}",
                value = s.borderWidth,
                range = 0.5f..6f,
                onChange = { v -> update { it.copy(borderWidth = v) } },
            )
            ColorPalette(s.borderColor) { c -> update { it.copy(borderColor = c) } }
        }

        LabeledSlider(
            label = "Cantos arredondados: ${s.cornerRadius.roundToInt()}",
            value = s.cornerRadius,
            range = 0f..40f,
            onChange = { v -> update { it.copy(cornerRadius = v) } },
        )

        SectionTitle("Fundo")
        OptionChips(CardBackground.entries, s.background, { it.label }) { b -> update { it.copy(background = b) } }
        if (s.background != CardBackground.TRANSPARENT) {
            LabeledSlider(
                label = "Opacidade da cor: ${(s.backgroundAlpha * 100).roundToInt()}%",
                value = s.backgroundAlpha,
                range = 0f..1f,
                onChange = { v -> update { it.copy(backgroundAlpha = v) } },
            )
            ColorPalette(s.backgroundColor) { c -> update { it.copy(backgroundColor = c) } }
        }
        if (s.background == CardBackground.BLUR && launcher.wallpaper.blurred == null) {
            HintText(
                "O desfoque aparece quando o papel de parede é escolhido em Configurações → " +
                    "\"Escolher imagem da galeria\". Até lá, o card usa um fundo escuro.",
            )
        }

        LabeledSlider(
            label = "Espaço entre os ícones: ${s.spacing.roundToInt()}",
            value = s.spacing,
            range = 0f..32f,
            onChange = { v -> update { it.copy(spacing = v) } },
        )

        SectionTitle("Apps no card (${card.apps.size})")
        if (card.apps.isEmpty()) HintText("Nenhum app ainda. Toque em \"Adicionar apps\" ou arraste apps para o card.")
        card.apps.forEach { key ->
            val app = launcher.apps.byKey[key]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (app != null) AppIcon(app, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(app?.label ?: "App não encontrado", Modifier.weight(1f))
                TextButton(onClick = {
                    layout.updateCard(cardId) { it.copy(apps = it.apps - key) }
                    layout.save()
                }) { Text("Remover") }
            }
        }
        Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar apps") }

        HintText("Para mudar o card de lugar: na tela inicial, segure o card e arraste.")

        OutlinedButton(
            onClick = {
                layout.remove(cardId)
                layout.normalizePages()
                layout.save()
                launcher.goHome()
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Excluir este card") }
        Button(onClick = { launcher.goHome() }, modifier = Modifier.fillMaxWidth()) { Text("Concluir") }
    }
}

@Composable
private fun CardAppPicker(launcher: Launcher, card: CardItem, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = launcher.apps.apps
    val filtered = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.label.contains(q, ignoreCase = true) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Escolha os apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Pronto") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar app") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.key }) { app ->
                val checked = app.key in card.apps
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            launcher.layout.updateCard(card.id) { c ->
                                c.copy(apps = if (app.key in c.apps) c.apps - app.key else c.apps + app.key)
                            }
                            launcher.layout.save()
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, Modifier.weight(1f))
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}

package com.mpersonality.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gaveta de apps. Tocar abre; segurar mostra o menu e, se arrastar, leva o app
 * para a tela inicial, o dock ou um card.
 */
@Composable
fun AppDrawer(launcher: Launcher) {
    val apps = launcher.apps.apps
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val closeDistance = with(LocalDensity.current) { 80.dp.toPx() }

    // Fecha ao puxar para baixo quando a lista já está no topo.
    val pullDownToClose = remember(launcher, closeDistance) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0) pulled = 0f
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pulled += available.y
                    if (pulled > closeDistance) {
                        pulled = 0f
                        launcher.goHome()
                    }
                }
                return Offset.Zero
            }
        }
    }

    val hasWallpaper = launcher.wallpaper.blurred != null
    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (hasWallpaper) {
                    Modifier.wallpaperBehind(launcher.wallpaper, blurred = true).background(Color(0xB3101014))
                } else {
                    Modifier.background(Color(0xEE101014))
                },
            )
            .pointerInput(launcher) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > closeDistance) launcher.goHome() },
                ) { change, dragAmount ->
                    change.consume()
                    total += dragAmount
                }
            }
            .systemBarsPadding()
            .imePadding(),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 40.dp, height = 5.dp).background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(50)))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar app") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let { launcher.launch(it) } }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { launcher.openSettings() }) { Text("Ajustes") }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            modifier = Modifier.fillMaxSize().nestedScroll(pullDownToClose),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.key }, contentType = { 0 }) { app ->
                DrawerTile(launcher, app)
            }
        }
    }
}

@Composable
private fun DrawerTile(launcher: Launcher, app: AppEntry) {
    Column(
        Modifier
            .hitTarget(launcher.registry, HitTarget.DrawerApp(app.key))
            .clickable { launcher.launch(app) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

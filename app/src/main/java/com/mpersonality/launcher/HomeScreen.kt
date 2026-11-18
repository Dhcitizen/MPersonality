package com.mpersonality.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 4f),
)

/**
 * Tela inicial: relógio, páginas de ícones (deslizar para os lados) e dock.
 * - Deslizar para cima: gaveta de apps.
 * - Segurar: menu / arrastar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(launcher: Launcher, pagerState: PagerState) {
    val openDistance = with(LocalDensity.current) { 60.dp.toPx() }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { launcher.currentPage = it }
    }
    LaunchedEffect(launcher.homePresses) {
        if (launcher.homePresses > 0 && pagerState.currentPage != 0) pagerState.animateScrollToPage(0)
    }
    EdgePaging(launcher, pagerState)

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(launcher) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -openDistance) launcher.openDrawer() },
                ) { change, dragAmount ->
                    change.consume()
                    total += dragAmount
                }
            }
            .systemBarsPadding(),
    ) {
        ClockArea(launcher.clock)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            HomePage(launcher, page)
        }
        PageDots(pagerState)
        Dock(launcher)
    }
}

/** Durante um arraste, segurar o ícone na borda da tela muda de página. */
@Composable
private fun EdgePaging(launcher: Launcher, pagerState: PagerState) {
    val dragging = launcher.dragSource != null
    val edge = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        var dwell = 0
        while (true) {
            delay(100)
            val x = launcher.dragPosition.x
            val width = launcher.wallpaper.rootSize.width
            val direction = when {
                x < edge -> -1
                width > 0 && x > width - edge -> 1
                else -> 0
            }
            if (direction == 0) {
                dwell = 0
                continue
            }
            dwell += 100
            if (dwell >= 600) {
                dwell = 0
                val target = (pagerState.currentPage + direction).coerceIn(0, pagerState.pageCount - 1)
                if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
            }
        }
    }
}

@Composable
private fun ClockArea(clock: ClockSettings) {
    val alignment = when (clock.align) {
        ClockAlign.ESQUERDA -> Alignment.CenterStart
        ClockAlign.CENTRO -> Alignment.Center
        ClockAlign.DIREITA -> Alignment.CenterEnd
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = clock.topOffset.dp, bottom = 12.dp),
        contentAlignment = alignment,
    ) {
        ClockView(clock)
    }
}

@Composable
private fun HomePage(launcher: Launcher, page: Int) {
    val drag = launcher.dragSource
    BoxWithConstraints(Modifier.fillMaxSize().hitTarget(launcher.registry, HitTarget.Grid(page))) {
        val cellW = maxWidth / GRID_COLUMNS
        val cellH = maxHeight / GRID_ROWS
        val iconSize = (cellH - 30.dp).coerceIn(28.dp, 54.dp)
        val pageItems = launcher.layout.items.filter { it.page == page }
        for (item in pageItems) {
            key(item.id) {
                when (item) {
                    is AppItem -> {
                        val app = launcher.apps.byKey[item.app]
                        if (app != null) {
                            HomeAppTile(
                                app = app,
                                iconSize = iconSize,
                                faded = drag is DragSource.Home && drag.itemId == item.id,
                                onClick = { launcher.launch(app) },
                                modifier = Modifier
                                    .offset(cellW * item.col, cellH * item.row)
                                    .size(cellW, cellH)
                                    .hitTarget(launcher.registry, HitTarget.HomeApp(item.id)),
                            )
                        }
                    }
                    is CardItem -> {
                        val hasTitle = item.style.showTitle && item.style.title.isNotBlank()
                        AppCard(
                            launcher = launcher,
                            card = item,
                            modifier = Modifier
                                .offset(0.dp, cellH * item.row)
                                .size(maxWidth, cellH)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            iconSize = (cellH - if (hasTitle) 44.dp else 26.dp).coerceIn(24.dp, 48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAppTile(app: AppEntry, iconSize: Dp, faded: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.alpha(if (faded) 0.3f else 1f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(app, iconSize)
        Spacer(Modifier.height(4.dp))
        Text(
            app.label,
            style = LabelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageDots(pagerState: PagerState) {
    val count = pagerState.pageCount
    val current = pagerState.currentPage
    Row(
        Modifier.fillMaxWidth().height(14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count > 1) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == current) 8.dp else 6.dp)
                        .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.45f), CircleShape),
                )
            }
        }
    }
}

/** Dock fixo embaixo, com até 5 apps. */
@Composable
private fun Dock(launcher: Launcher) {
    val drag = launcher.dragSource
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(80.dp)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
            .hitTarget(launcher.registry, HitTarget.DockArea),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (slot in 0 until DOCK_SLOTS) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val app = launcher.layout.dock.getOrNull(slot)?.let { launcher.apps.byKey[it] }
                if (app != null) {
                    AppIcon(
                        app,
                        52.dp,
                        Modifier
                            .hitTarget(launcher.registry, HitTarget.DockApp(slot))
                            .alpha(if (drag is DragSource.Dock && drag.slot == slot) 0.3f else 1f)
                            .clickable { launcher.launch(app) },
                    )
                }
            }
        }
    }
}

// ---------- Camadas por cima de tudo: arrastar e menu ----------

/** Ícone que segue o dedo, área "Remover" e o destaque de onde o item vai cair. */
@Composable
fun DragOverlay(launcher: Launcher) {
    val source = launcher.dragSource ?: return
    val density = LocalDensity.current
    val target by remember(source) {
        derivedStateOf { launcher.computeTarget(source, launcher.dragPosition) }
    }

    Box(Modifier.fillMaxSize()) {
        if (source !is DragSource.Drawer) {
            val hot = target is DropTarget.Remove
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp)
                    .height(56.dp)
                    .hitTarget(launcher.registry, HitTarget.RemoveZone)
                    .background(if (hot) Color(0xE6D32F2F) else Color(0x99000000), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕  Remover", fontWeight = FontWeight.Medium)
            }
        }

        val t = target
        if (t != null && t !is DropTarget.Remove) {
            val r = t.rect
            Box(
                Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp)),
            )
        }

        val app = source.app?.let { launcher.apps.byKey[it] }
        val ghostW = if (app != null) 60.dp else 220.dp
        val ghostH = if (app != null) 60.dp else 64.dp
        val halfW = with(density) { ghostW.toPx() / 2f }
        val halfH = with(density) { ghostH.toPx() / 2f }
        Box(
            Modifier
                .offset {
                    val p = launcher.dragPosition
                    IntOffset((p.x - halfW).roundToInt(), (p.y - halfH).roundToInt())
                }
                .size(ghostW, ghostH),
            contentAlignment = Alignment.Center,
        ) {
            if (app != null) {
                AppIcon(app, 60.dp)
            } else {
                val card = (source as? DragSource.Card)?.let { launcher.layout.card(it.cardId) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000), RoundedCornerShape(18.dp))
                        .border(1.5.dp, Color.White, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(card?.style?.title?.takeIf { it.isNotBlank() } ?: "Card", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Menu que aparece ao segurar um item. Toque fora para fechar. */
@Composable
fun MenuOverlay(launcher: Launcher) {
    val menu = launcher.menu ?: return
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(menu) { detectTapGestures { launcher.menu = null } },
    ) {
        PositionedAt(menu.position) {
            Column(
                Modifier
                    .width(230.dp)
                    .background(Color(0xF2202028), RoundedCornerShape(16.dp))
                    .padding(vertical = 6.dp),
            ) {
                menu.title?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
                menu.entries.forEach { entry ->
                    Text(
                        entry.label,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                launcher.menu = null
                                entry.action()
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** Coloca o conteúdo perto do ponto tocado, sem sair da tela. */
@Composable
private fun PositionedAt(position: Offset, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val margin = 16.dp.roundToPx()
        val gap = 28.dp.roundToPx()
        var y = position.y.roundToInt() - placeable.height - gap
        if (y < margin) y = position.y.roundToInt() + gap
        val x = (position.x.roundToInt() - placeable.width / 2)
            .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
        y = y.coerceIn(margin, (constraints.maxHeight - placeable.height - margin).coerceAtLeast(margin))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
    }
}

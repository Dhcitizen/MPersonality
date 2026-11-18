package com.mpersonality.launcher

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class Screen { HOME, DRAWER, SETTINGS, CARD_EDITOR }

class MenuEntry(val label: String, val action: () -> Unit)
class MenuState(val position: Offset, val title: String?, val entries: List<MenuEntry>)

/** De onde veio o item que está sendo arrastado. */
sealed interface DragSource {
    val app: String?

    data class Drawer(override val app: String) : DragSource
    data class Home(val itemId: Long, override val app: String) : DragSource
    data class Dock(val slot: Int, override val app: String) : DragSource
    data class InCard(val cardId: Long, override val app: String) : DragSource
    data class Card(val cardId: Long) : DragSource {
        override val app: String? get() = null
    }
}

/** Onde o item vai cair se o dedo for solto agora. */
sealed interface DropTarget {
    val rect: Rect

    data class Remove(override val rect: Rect) : DropTarget
    data class DockSlot(val slot: Int, override val rect: Rect) : DropTarget
    data class IntoCard(val cardId: Long, val index: Int, override val rect: Rect) : DropTarget
    data class Cell(val page: Int, val row: Int, val col: Int, override val rect: Rect) : DropTarget
    data class CardRow(val page: Int, val row: Int, override val rect: Rect) : DropTarget
}

/** Áreas da tela que podem ser tocadas/arrastadas. Menor prioridade = mais "por cima". */
sealed class HitTarget(val priority: Int) {
    data class DrawerApp(val app: String) : HitTarget(0)
    data class DockApp(val slot: Int) : HitTarget(1)
    data class CardApp(val cardId: Long, val app: String) : HitTarget(2)
    data class HomeApp(val itemId: Long) : HitTarget(3)
    data class Card(val cardId: Long) : HitTarget(4)
    data class Grid(val page: Int) : HitTarget(10)
    data object DockArea : HitTarget(11)
    data object RemoveZone : HitTarget(12)
}

/** Guarda onde cada item está na tela, para saber o que está embaixo do dedo. */
class HitRegistry {
    private val entries = HashMap<HitTarget, LayoutCoordinates>()

    fun put(target: HitTarget, coordinates: LayoutCoordinates) {
        entries[target] = coordinates
    }

    fun remove(target: HitTarget) {
        entries.remove(target)
    }

    fun bounds(target: HitTarget): Rect? {
        val c = entries[target] ?: return null
        if (!c.isAttached) return null
        val b = c.boundsInRoot()
        return if (b.width > 0f && b.height > 0f) b else null
    }

    fun find(point: Offset, filter: (HitTarget) -> Boolean): HitTarget? {
        var best: HitTarget? = null
        for ((target, c) in entries) {
            if (!filter(target) || !c.isAttached) continue
            if (c.boundsInRoot().contains(point) && (best == null || target.priority < best.priority)) best = target
        }
        return best
    }

    /** Em que posição dentro do card um app solto em [x] deve entrar. */
    fun cardIndexAt(cardId: Long, x: Float): Int = entries.count { (target, c) ->
        target is HitTarget.CardApp && target.cardId == cardId && c.isAttached && c.boundsInRoot().center.x < x
    }
}

@Composable
fun Modifier.hitTarget(registry: HitRegistry, target: HitTarget): Modifier {
    DisposableEffect(registry, target) {
        onDispose { registry.remove(target) }
    }
    return this.onGloballyPositioned { registry.put(target, it) }
}

/** Cérebro da tela inicial: telas, arrastar e soltar, menus. */
class Launcher(private val context: Context) {
    val scope = MainScope()
    val clock = ClockSettings(context.applicationContext)
    val layout = HomeLayout(context.applicationContext)
    val apps = AppsModel(context.applicationContext, scope) { layout.allAppKeys() }
    val wallpaper = WallpaperState(context.applicationContext)
    val registry = HitRegistry()

    var screen by mutableStateOf(Screen.HOME)
    var editingCardId by mutableStateOf<Long?>(null)
        private set
    var currentPage by mutableIntStateOf(0)
    var homePresses by mutableIntStateOf(0)
        private set

    var dragSource by mutableStateOf<DragSource?>(null)
        private set
    var dragPosition by mutableStateOf(Offset.Zero)
        private set
    var menu by mutableStateOf<MenuState?>(null)
    private var pressed: DragSource? = null
    var haptic: () -> Unit = {}

    /** Durante um arraste existe uma página extra vazia no fim, para criar páginas novas. */
    val pagerPageCount: Int
        get() = layout.pageCount + if (dragSource != null) 1 else 0

    fun start() {
        apps.onPackageRemoved = { pkg -> layout.removePackage(pkg) }
        apps.start()
        scope.launch { wallpaper.loadSaved() }
    }

    fun dispose() {
        apps.stop()
        scope.cancel()
    }

    // ---------- Navegação ----------

    fun goHome() {
        screen = Screen.HOME
        editingCardId = null
    }

    fun openDrawer() {
        if (dragSource == null && menu == null) screen = Screen.DRAWER
    }

    fun openSettings() {
        menu = null
        screen = Screen.SETTINGS
    }

    fun editCard(id: Long) {
        menu = null
        editingCardId = id
        screen = Screen.CARD_EDITOR
    }

    fun onBack() {
        when {
            menu != null -> menu = null
            screen == Screen.HOME -> homePresses++
            else -> goHome()
        }
    }

    fun onHomePressed() {
        menu = null
        cancelDrag()
        if (screen == Screen.HOME) homePresses++ else goHome()
    }

    fun launch(app: AppEntry) {
        apps.launch(app)
        if (screen == Screen.DRAWER) goHome()
    }

    fun importWallpaper(uri: Uri) {
        scope.launch {
            toast("Aplicando papel de parede...")
            val ok = wallpaper.import(uri)
            toast(if (ok) "Papel de parede aplicado" else "Não foi possível usar essa imagem")
        }
    }

    // ---------- Toque longo, arrastar e soltar ----------

    /** Chamado quando o dedo fica parado tempo suficiente. Retorna true se algo foi "pego". */
    fun onLongPress(position: Offset): Boolean {
        if (menu != null || dragSource != null) return false
        pressed = null
        when (screen) {
            Screen.DRAWER -> {
                val t = registry.find(position) { it is HitTarget.DrawerApp } as? HitTarget.DrawerApp ?: return false
                val app = apps.byKey[t.app] ?: return false
                pressed = DragSource.Drawer(app.key)
                menu = MenuState(
                    position, app.label,
                    listOf(
                        MenuEntry("Adicionar à tela inicial") { addToHome(app.key) },
                        MenuEntry("Informações do app") { apps.openDetails(app) },
                    ),
                )
            }
            Screen.HOME -> {
                val t = registry.find(position) { it.priority in 1..4 }
                when (t) {
                    is HitTarget.DockApp -> {
                        val key = layout.dock.getOrNull(t.slot) ?: return false
                        val app = apps.byKey[key] ?: return false
                        pressed = DragSource.Dock(t.slot, key)
                        menu = appMenu(position, app, "Remover do dock") { layout.dock[t.slot] = null; layout.save() }
                    }
                    is HitTarget.CardApp -> {
                        val app = apps.byKey[t.app] ?: return false
                        pressed = DragSource.InCard(t.cardId, t.app)
                        menu = appMenu(position, app, "Remover do card") {
                            layout.updateCard(t.cardId) { it.copy(apps = it.apps - t.app) }
                            layout.save()
                        }
                    }
                    is HitTarget.HomeApp -> {
                        val item = layout.item(t.itemId) as? AppItem ?: return false
                        val app = apps.byKey[item.app] ?: return false
                        pressed = DragSource.Home(item.id, item.app)
                        menu = appMenu(position, app, "Remover da tela inicial") {
                            layout.remove(item.id)
                            layout.normalizePages()
                            layout.save()
                        }
                    }
                    is HitTarget.Card -> {
                        val card = layout.card(t.cardId) ?: return false
                        pressed = DragSource.Card(card.id)
                        menu = MenuState(
                            position,
                            card.style.title.takeIf { it.isNotBlank() } ?: "Card",
                            listOf(
                                MenuEntry("Editar card") { editCard(card.id) },
                                MenuEntry("Remover card") {
                                    layout.remove(card.id)
                                    layout.normalizePages()
                                    layout.save()
                                },
                            ),
                        )
                    }
                    else -> {
                        val cell = gridCellAt(position)
                        menu = MenuState(
                            position, null,
                            listOf(
                                MenuEntry(if (cell != null) "Criar card aqui" else "Criar card") {
                                    createCard(cell?.first ?: currentPage, cell?.second)
                                },
                                MenuEntry("Configurações") { openSettings() },
                            ),
                        )
                    }
                }
            }
            else -> return false
        }
        return true
    }

    private fun appMenu(position: Offset, app: AppEntry, removeLabel: String, remove: () -> Unit) = MenuState(
        position, app.label,
        listOf(
            MenuEntry(removeLabel, remove),
            MenuEntry("Informações do app") { apps.openDetails(app) },
        ),
    )

    /** O dedo se moveu depois do toque longo: começa a arrastar. */
    fun startDrag(position: Offset): Boolean {
        val source = pressed ?: return false
        menu = null
        dragPosition = position
        dragSource = source
        if (screen == Screen.DRAWER) screen = Screen.HOME
        return true
    }

    fun dragTo(position: Offset) {
        dragPosition = position
    }

    fun endGesture(dragged: Boolean) {
        val source = dragSource
        if (dragged && source != null) {
            computeTarget(source, dragPosition)?.let { drop(source, it) }
        }
        dragSource = null
        pressed = null
    }

    fun cancelDrag() {
        dragSource = null
        pressed = null
    }

    private fun gridCellAt(position: Offset): Pair<Int, Int>? {
        val grid = registry.bounds(HitTarget.Grid(currentPage)) ?: return null
        if (!grid.contains(position)) return null
        val row = ((position.y - grid.top) / (grid.height / GRID_ROWS)).toInt().coerceIn(0, GRID_ROWS - 1)
        return currentPage to row
    }

    fun computeTarget(source: DragSource, position: Offset): DropTarget? {
        if (source !is DragSource.Drawer) {
            registry.bounds(HitTarget.RemoveZone)?.let { if (it.contains(position)) return DropTarget.Remove(it) }
        }
        val movingCard = source is DragSource.Card
        if (!movingCard) {
            registry.bounds(HitTarget.DockArea)?.let { b ->
                if (b.contains(position)) {
                    val w = b.width / DOCK_SLOTS
                    val slot = ((position.x - b.left) / w).toInt().coerceIn(0, DOCK_SLOTS - 1)
                    return DropTarget.DockSlot(slot, Rect(b.left + slot * w, b.top, b.left + (slot + 1) * w, b.bottom))
                }
            }
            val cardHit = registry.find(position) { it is HitTarget.Card } as? HitTarget.Card
            if (cardHit != null) {
                val b = registry.bounds(cardHit)
                if (b != null) return DropTarget.IntoCard(cardHit.cardId, registry.cardIndexAt(cardHit.cardId, position.x), b)
            }
        }
        val page = currentPage
        val grid = registry.bounds(HitTarget.Grid(page)) ?: return null
        if (!grid.contains(position)) return null
        val cw = grid.width / GRID_COLUMNS
        val ch = grid.height / GRID_ROWS
        val col = ((position.x - grid.left) / cw).toInt().coerceIn(0, GRID_COLUMNS - 1)
        val row = ((position.y - grid.top) / ch).toInt().coerceIn(0, GRID_ROWS - 1)
        val top = grid.top + row * ch
        return if (movingCard) {
            DropTarget.CardRow(page, row, Rect(grid.left, top, grid.right, top + ch))
        } else {
            DropTarget.Cell(page, row, col, Rect(grid.left + col * cw, top, grid.left + (col + 1) * cw, top + ch))
        }
    }

    private fun drop(source: DragSource, target: DropTarget) {
        when (target) {
            is DropTarget.Remove -> detach(source)
            is DropTarget.DockSlot -> dropOnDock(source, target.slot)
            is DropTarget.IntoCard -> dropIntoCard(source, target.cardId, target.index)
            is DropTarget.Cell -> dropOnCell(source, target.page, target.row, target.col)
            is DropTarget.CardRow -> if (source is DragSource.Card) moveCard(source.cardId, target.page, target.row)
        }
        layout.normalizePages()
        layout.save()
    }

    /** Tira o item do lugar de onde ele veio. */
    private fun detach(source: DragSource) {
        when (source) {
            is DragSource.Drawer -> Unit
            is DragSource.Home -> layout.remove(source.itemId)
            is DragSource.Dock -> layout.dock[source.slot] = null
            is DragSource.InCard -> layout.updateCard(source.cardId) { it.copy(apps = it.apps - source.app) }
            is DragSource.Card -> layout.remove(source.cardId)
        }
    }

    private fun dropOnCell(source: DragSource, page: Int, row: Int, col: Int) {
        val app = source.app ?: return
        val card = layout.cardAt(page, row)
        if (card != null) {
            dropIntoCard(source, card.id, card.apps.size)
            return
        }
        val occupant = layout.appAt(page, row, col)
        when {
            occupant != null && source is DragSource.Home && occupant.id == source.itemId -> Unit
            occupant != null && source is DragSource.Home -> {
                // Troca os dois de lugar.
                val moving = layout.item(source.itemId) as? AppItem ?: return
                layout.replace(occupant.copy(page = moving.page, row = moving.row, col = moving.col))
                layout.replace(moving.copy(page = page, row = row, col = col))
            }
            occupant != null && source is DragSource.Dock -> {
                // Troca com o dock.
                layout.dock[source.slot] = occupant.app
                layout.replace(occupant.copy(app = app))
            }
            occupant != null -> {
                val free = layout.nearestFreeCell(page, row, col)
                if (free == null) {
                    toast("Não há espaço livre nesta página")
                    return
                }
                detach(source)
                layout.addApp(app, page, free.first, free.second)
            }
            source is DragSource.Home -> {
                val moving = layout.item(source.itemId) as? AppItem ?: return
                layout.replace(moving.copy(page = page, row = row, col = col))
            }
            else -> {
                detach(source)
                layout.addApp(app, page, row, col)
            }
        }
    }

    private fun dropOnDock(source: DragSource, slot: Int) {
        val app = source.app ?: return
        val occupant = layout.dock[slot]
        when {
            source is DragSource.Dock -> {
                layout.dock[source.slot] = occupant
                layout.dock[slot] = app
            }
            occupant == null -> {
                detach(source)
                layout.dock[slot] = app
            }
            source is DragSource.Home -> {
                // Troca: o app do dock vai para o lugar de onde o outro saiu.
                val moving = layout.item(source.itemId) as? AppItem ?: return
                val displaced = occupant ?: return
                layout.dock[slot] = app
                layout.replace(moving.copy(app = displaced))
            }
            else -> {
                val free = layout.nearestFreeDockSlot(slot)
                if (free == null) {
                    toast("O dock está cheio (máximo de $DOCK_SLOTS apps)")
                    return
                }
                detach(source)
                layout.dock[free] = app
            }
        }
    }

    private fun dropIntoCard(source: DragSource, cardId: Long, index: Int) {
        val app = source.app ?: return
        val card = layout.card(cardId) ?: return
        if (source is DragSource.InCard && source.cardId == cardId) {
            // Reorganizando dentro do mesmo card.
            val list = card.apps.toMutableList()
            val from = list.indexOf(app)
            if (from < 0) return
            list.removeAt(from)
            val to = (if (from < index) index - 1 else index).coerceIn(0, list.size)
            list.add(to, app)
            layout.replace(card.copy(apps = list))
            return
        }
        if (app in card.apps) {
            toast("Esse app já está neste card")
            return
        }
        detach(source)
        layout.updateCard(cardId) {
            val list = it.apps.toMutableList()
            list.add(index.coerceIn(0, list.size), app)
            it.copy(apps = list)
        }
    }

    private fun moveCard(cardId: Long, page: Int, row: Int) {
        val card = layout.card(cardId) ?: return
        if (card.page == page && card.row == row) return
        // O que estava na linha de destino vai para a linha antiga do card.
        val others = layout.items.filter { it.page == page && it.row == row && it.id != cardId }
        others.forEach { layout.replace(it.moveTo(card.page, card.row)) }
        layout.replace(card.copy(page = page, row = row))
    }

    private fun addToHome(appKey: String) {
        for (page in 0..layout.pageCount) {
            val cell = layout.firstFreeCell(page) ?: continue
            layout.addApp(appKey, page, cell.first, cell.second)
            layout.save()
            toast("Adicionado à tela inicial (página ${page + 1})")
            return
        }
    }

    private fun createCard(page: Int, row: Int?) {
        val spot: Pair<Int, Int>? = if (row != null && layout.isRowFree(page, row)) {
            page to row
        } else {
            (listOf(page) + (0..layout.pageCount)).firstNotNullOfOrNull { p ->
                (0 until GRID_ROWS).firstOrNull { layout.isRowFree(p, it) }?.let { p to it }
            }
        }
        if (spot == null) {
            toast("Não há uma linha livre para o card")
            return
        }
        val id = layout.addCard(spot.first, spot.second)
        layout.normalizePages()
        layout.save()
        editCard(id)
    }

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Detecta o toque longo em qualquer lugar da tela. Observa os toques "por cima"
 * de tudo (PointerEventPass.Initial) sem atrapalhar rolagem e cliques; só depois
 * do toque longo ele assume o gesto (para arrastar).
 */
fun Modifier.launcherGestures(launcher: Launcher): Modifier = pointerInput(launcher) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val pointerId = down.id
        var position = down.position
        val slop = viewConfiguration.touchSlop

        val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var stop = false
            while (!stop) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change != null) position = change.position
                stop = change == null || !change.pressed || event.changes.size > 1 ||
                    (change.position - down.position).getDistance() > slop
            }
            true
        }
        if (endedEarly != null) return@awaitEachGesture
        if (!launcher.onLongPress(position)) return@awaitEachGesture
        launcher.haptic()

        val dragSlop = 12.dp.toPx()
        var dragging = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                event.changes.forEach { it.consume() }
                if (!change.pressed) break
                if (!dragging && (change.position - position).getDistance() > dragSlop) {
                    dragging = launcher.startDrag(change.position)
                }
                if (dragging) launcher.dragTo(change.position)
            }
            launcher.endGesture(dragging)
        } catch (e: CancellationException) {
            launcher.cancelDrag()
            throw e
        }
    }
}

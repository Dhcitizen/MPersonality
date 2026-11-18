package com.mpersonality.launcher

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

const val GRID_COLUMNS = 4
const val GRID_ROWS = 5
const val DOCK_SLOTS = 5

enum class CardBackground(val label: String) {
    TRANSPARENT("Transparente"),
    BLUR("Desfoque"),
    SOLID("Cor sólida"),
}

data class CardStyle(
    val title: String = "",
    val showTitle: Boolean = false,
    val showBorder: Boolean = true,
    val borderWidth: Float = 1.5f,
    val borderColor: Int = 0xFFFFFFFF.toInt(),
    val cornerRadius: Float = 22f,
    val background: CardBackground = CardBackground.BLUR,
    val backgroundColor: Int = 0xFF000000.toInt(),
    val backgroundAlpha: Float = 0.30f,
    val spacing: Float = 12f,
)

/** Algo colocado na grade da tela inicial. */
sealed interface HomeItem {
    val id: Long
    val page: Int
    val row: Int
}

/** Um app ocupando uma célula. */
data class AppItem(
    override val id: Long,
    override val page: Int,
    override val row: Int,
    val col: Int,
    val app: String,
) : HomeItem

/** Um card ocupa uma linha inteira da grade. */
data class CardItem(
    override val id: Long,
    override val page: Int,
    override val row: Int,
    val apps: List<String>,
    val style: CardStyle,
) : HomeItem

fun HomeItem.moveTo(page: Int, row: Int): HomeItem = when (this) {
    is AppItem -> copy(page = page, row = row)
    is CardItem -> copy(page = page, row = row)
}

/**
 * O que está na tela inicial: apps, cards e dock. Salvo no próprio celular
 * (SharedPreferences, em JSON).
 */
class HomeLayout(context: Context) {

    private val prefs = context.getSharedPreferences("mpersonality_home", Context.MODE_PRIVATE)

    val items = mutableStateListOf<HomeItem>()
    val dock = mutableStateListOf<String?>()
    private var nextId = 1L

    init {
        load()
    }

    val pageCount: Int
        get() = (items.maxOfOrNull { it.page } ?: 0) + 1

    fun newId(): Long = nextId++

    fun item(id: Long): HomeItem? = items.firstOrNull { it.id == id }

    fun card(id: Long): CardItem? = item(id) as? CardItem

    fun appAt(page: Int, row: Int, col: Int): AppItem? =
        items.firstOrNull { it is AppItem && it.page == page && it.row == row && it.col == col } as? AppItem

    fun cardAt(page: Int, row: Int): CardItem? =
        items.firstOrNull { it is CardItem && it.page == page && it.row == row } as? CardItem

    fun isCellFree(page: Int, row: Int, col: Int): Boolean = cardAt(page, row) == null && appAt(page, row, col) == null

    fun isRowFree(page: Int, row: Int): Boolean = items.none { it.page == page && it.row == row }

    /** Primeira célula livre da página, da esquerda para a direita, de cima para baixo. */
    fun firstFreeCell(page: Int): Pair<Int, Int>? {
        for (r in 0 until GRID_ROWS) for (c in 0 until GRID_COLUMNS) {
            if (isCellFree(page, r, c)) return r to c
        }
        return null
    }

    /** Célula livre mais próxima de (row, col). */
    fun nearestFreeCell(page: Int, row: Int, col: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestDistance = Int.MAX_VALUE
        for (r in 0 until GRID_ROWS) for (c in 0 until GRID_COLUMNS) {
            if (!isCellFree(page, r, c)) continue
            val d = (r - row) * (r - row) + (c - col) * (c - col)
            if (d < bestDistance) {
                bestDistance = d
                best = r to c
            }
        }
        return best
    }

    fun nearestFreeDockSlot(slot: Int): Int? =
        (0 until DOCK_SLOTS).filter { dock[it] == null }.minByOrNull { kotlin.math.abs(it - slot) }

    fun replace(updated: HomeItem) {
        val i = items.indexOfFirst { it.id == updated.id }
        if (i >= 0) items[i] = updated
    }

    fun addApp(app: String, page: Int, row: Int, col: Int) {
        items += AppItem(newId(), page, row, col, app)
    }

    fun addCard(page: Int, row: Int): Long {
        val id = newId()
        items += CardItem(id, page, row, emptyList(), CardStyle())
        return id
    }

    fun remove(id: Long) {
        items.removeAll { it.id == id }
    }

    fun updateCard(id: Long, transform: (CardItem) -> CardItem) {
        card(id)?.let { replace(transform(it)) }
    }

    /** Tira da tela inicial um app que foi desinstalado. */
    fun removePackage(packageName: String) {
        val prefix = "$packageName/"
        items.removeAll { it is AppItem && it.app.startsWith(prefix) }
        for (i in items.indices) {
            val item = items[i]
            if (item is CardItem && item.apps.any { it.startsWith(prefix) }) {
                items[i] = item.copy(apps = item.apps.filterNot { it.startsWith(prefix) })
            }
        }
        for (i in 0 until DOCK_SLOTS) if (dock[i]?.startsWith(prefix) == true) dock[i] = null
        normalizePages()
        save()
    }

    /** Remove páginas vazias do meio, renumerando as seguintes. */
    fun normalizePages() {
        val used = items.map { it.page }.distinct().sorted()
        if (used == used.indices.toList()) return
        val newIndex = used.withIndex().associate { (index, page) -> page to index }
        for (i in items.indices) {
            val item = items[i]
            val page = newIndex.getValue(item.page)
            if (page != item.page) items[i] = item.moveTo(page, item.row)
        }
    }

    fun allAppKeys(): Set<String> {
        val keys = HashSet<String>()
        for (item in items) when (item) {
            is AppItem -> keys += item.app
            is CardItem -> keys += item.apps
        }
        dock.forEach { if (it != null) keys += it }
        return keys
    }

    // ---------- Salvar / carregar ----------

    fun save() {
        val array = JSONArray()
        for (item in items) {
            val o = JSONObject()
                .put("id", item.id)
                .put("page", item.page)
                .put("row", item.row)
            when (item) {
                is AppItem -> o.put("type", "app").put("col", item.col).put("app", item.app)
                is CardItem -> o.put("type", "card").put("apps", JSONArray(item.apps)).put("style", styleToJson(item.style))
            }
            array.put(o)
        }
        val dockArray = JSONArray()
        dock.forEach { dockArray.put(it ?: "") }
        val root = JSONObject().put("items", array).put("dock", dockArray)
        prefs.edit().putString(KEY_LAYOUT, root.toString()).apply()
    }

    private fun load() {
        val loadedItems = ArrayList<HomeItem>()
        val loadedDock = MutableList<String?>(DOCK_SLOTS) { null }
        runCatching {
            val json = prefs.getString(KEY_LAYOUT, null) ?: return@runCatching
            val root = JSONObject(json)
            val array = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val id = o.getLong("id")
                val page = o.optInt("page", 0).coerceAtLeast(0)
                val row = o.optInt("row", 0).coerceIn(0, GRID_ROWS - 1)
                when (o.optString("type")) {
                    "app" -> loadedItems += AppItem(id, page, row, o.optInt("col", 0).coerceIn(0, GRID_COLUMNS - 1), o.getString("app"))
                    "card" -> {
                        val appsArray = o.optJSONArray("apps") ?: JSONArray()
                        val apps = (0 until appsArray.length()).map { appsArray.getString(it) }.distinct()
                        loadedItems += CardItem(id, page, row, apps, styleFromJson(o.optJSONObject("style")))
                    }
                }
            }
            val dockArray = root.optJSONArray("dock") ?: JSONArray()
            for (i in 0 until minOf(DOCK_SLOTS, dockArray.length())) {
                loadedDock[i] = dockArray.optString(i).takeIf { it.isNotBlank() }
            }
        }
        items.clear()
        items.addAll(loadedItems)
        dock.clear()
        dock.addAll(loadedDock)
        nextId = (loadedItems.maxOfOrNull { it.id } ?: 0L) + 1
        normalizePages()
    }

    private fun styleToJson(s: CardStyle): JSONObject = JSONObject()
        .put("title", s.title)
        .put("showTitle", s.showTitle)
        .put("showBorder", s.showBorder)
        .put("borderWidth", s.borderWidth.toDouble())
        .put("borderColor", s.borderColor)
        .put("cornerRadius", s.cornerRadius.toDouble())
        .put("background", s.background.name)
        .put("backgroundColor", s.backgroundColor)
        .put("backgroundAlpha", s.backgroundAlpha.toDouble())
        .put("spacing", s.spacing.toDouble())

    private fun styleFromJson(o: JSONObject?): CardStyle {
        val d = CardStyle()
        if (o == null) return d
        return CardStyle(
            title = o.optString("title", d.title),
            showTitle = o.optBoolean("showTitle", d.showTitle),
            showBorder = o.optBoolean("showBorder", d.showBorder),
            borderWidth = o.optDouble("borderWidth", d.borderWidth.toDouble()).toFloat(),
            borderColor = o.optInt("borderColor", d.borderColor),
            cornerRadius = o.optDouble("cornerRadius", d.cornerRadius.toDouble()).toFloat(),
            background = CardBackground.entries.firstOrNull { it.name == o.optString("background") } ?: d.background,
            backgroundColor = o.optInt("backgroundColor", d.backgroundColor),
            backgroundAlpha = o.optDouble("backgroundAlpha", d.backgroundAlpha.toDouble()).toFloat(),
            spacing = o.optDouble("spacing", d.spacing.toDouble()).toFloat(),
        )
    }

    private companion object {
        const val KEY_LAYOUT = "layout_v1"
    }
}

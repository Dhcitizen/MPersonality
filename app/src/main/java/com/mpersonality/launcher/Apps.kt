package com.mpersonality.launcher

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.roundToInt

/**
 * Um app instalado. [key] é um identificador que não muda entre reinicializações
 * (componente + perfil do usuário) e é o que fica salvo no layout da tela inicial.
 */
class AppEntry(val key: String, val label: String, val info: LauncherActivityInfo)

/**
 * Lista de apps instalados. Só é recarregada quando o Android avisa que algum
 * app foi instalado, removido ou atualizado (e não a cada abertura da gaveta).
 */
class AppsModel(
    private val context: Context,
    private val scope: CoroutineScope,
    private val priorityKeys: () -> Collection<String>,
) {
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var byKey by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set

    var onPackageRemoved: (String) -> Unit = {}

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private var loadJob: Job? = null
    private var preloadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            IconCache.invalidatePackage(packageName)
            onPackageRemoved(packageName)
            reload()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            IconCache.invalidatePackage(packageName)
            reload()
        }

        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload()

        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload(delayMs = 0)
    }

    fun stop() {
        runCatching { launcherApps.unregisterCallback(callback) }
    }

    fun reload(delayMs: Long = 400) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (delayMs > 0) delay(delayMs) // junta vários avisos seguidos num só recarregamento
            val list = withContext(Dispatchers.Default) { query() }
            apps = list
            byKey = list.associateBy { it.key }
            preloadIcons(list)
        }
    }

    /** Desenha os ícones em segundo plano: primeiro os da tela inicial, depois o resto. */
    private fun preloadIcons(list: List<AppEntry>) {
        val first = priorityKeys().toHashSet()
        val ordered = list.filter { it.key in first } + list.filter { it.key !in first }
        preloadJob?.cancel()
        preloadJob = scope.launch(Dispatchers.Default) {
            for (app in ordered) {
                ensureActive()
                IconCache.load(context, app)
            }
        }
    }

    private fun query(): List<AppEntry> {
        val collator = Collator.getInstance()
        val own = context.packageName
        val result = ArrayList<AppEntry>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val list = runCatching { launcherApps.getActivityList(null, user) }.getOrNull() ?: continue
            for (info in list) {
                if (info.componentName.packageName == own) continue
                val key = info.componentName.flattenToString() + "#" + serial
                result += AppEntry(key, info.label.toString(), info)
            }
        }
        result.sortWith { a, b -> collator.compare(a.label, b.label) }
        return result
    }

    fun launch(app: AppEntry) {
        try {
            launcherApps.startMainActivity(app.info.componentName, app.info.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Abre a tela "Informações do app" do Android (desinstalar, permissões etc.). */
    fun openDetails(app: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(app.info.componentName, app.info.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir as informações", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Ícones já desenhados, guardados na memória para não redesenhar ao rolar. */
object IconCache {
    private const val ICON_DP = 56f

    private val cache = object : LruCache<String, ImageBitmap>(
        minOf(32 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 6).toInt()),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount
    }

    fun get(key: String): ImageBitmap? = cache.get(key)

    /** Desenha o ícone (se ainda não estiver na memória). Chamar fora da thread principal. */
    fun load(context: Context, app: AppEntry): ImageBitmap {
        cache.get(app.key)?.let { return it }
        val metrics = context.resources.displayMetrics
        val sizePx = (ICON_DP * metrics.density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        runCatching { app.info.getBadgedIcon(metrics.densityDpi) }.getOrNull()?.let { drawable ->
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(bitmap))
        }
        val image = bitmap.asImageBitmap()
        image.prepareToDraw()
        cache.put(app.key, image)
        return image
    }

    fun invalidatePackage(packageName: String) {
        val prefix = "$packageName/"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState(initialValue = IconCache.get(app.key), app) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { IconCache.load(context, app) }
        }
    }
    val current = image
    if (current != null) {
        Image(current, contentDescription = app.label, modifier = modifier.size(size), filterQuality = FilterQuality.Low)
    } else {
        Spacer(modifier.size(size))
    }
}

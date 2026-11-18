package com.mpersonality.launcher

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.UserManager
import android.util.LruCache
import android.widget.Toast
import java.text.Collator

/** Um app que aparece na gaveta. */
class AppEntry(val label: String, val info: LauncherActivityInfo) {
    val key: String = info.componentName.flattenToString() + "#" + info.user.hashCode()
}

object AppRepository {

    /** Lê a lista de apps instalados, em ordem alfabética. Chamar fora da thread principal. */
    fun load(context: Context): List<AppEntry> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val collator = Collator.getInstance()
        return userManager.userProfiles
            .flatMap { user -> runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()) }
            .filter { it.componentName.packageName != context.packageName }
            .map { AppEntry(it.label.toString(), it) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun launch(context: Context, app: AppEntry) {
        try {
            launcherApps(context).startMainActivity(app.info.componentName, app.info.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Abre a tela "Informações do app" do Android (para desinstalar, permissões etc.). */
    fun openDetails(context: Context, app: AppEntry) {
        try {
            launcherApps(context).startAppDetailsActivity(app.info.componentName, app.info.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir as informações", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launcherApps(context: Context) =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
}

/** Guarda os ícones já desenhados na memória (até ~16 MB) para a gaveta abrir rápido. */
object IconCache {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    /** Desenha o ícone do app. Chamar fora da thread principal. */
    fun load(app: AppEntry, sizePx: Int, densityDpi: Int): Bitmap {
        cache.get(app.key)?.let { return it }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        runCatching { app.info.getBadgedIcon(densityDpi) }.getOrNull()?.let { drawable ->
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(bitmap))
        }
        cache.put(app.key, bitmap)
        return bitmap
    }
}

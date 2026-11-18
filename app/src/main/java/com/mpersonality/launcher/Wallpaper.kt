package com.mpersonality.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Papel de parede escolhido dentro do MPersonality.
 * A imagem é reduzida UMA vez para a resolução da tela e salva assim, então
 * o app nunca carrega uma foto gigante na memória. Uma cópia bem pequena e
 * desfocada também é gerada uma vez, para o fundo "desfoque" dos cards.
 */
class WallpaperState(private val context: Context) {

    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    var blurred by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Tamanho da área do app na tela (usado para alinhar o desfoque dos cards). */
    var rootSize by mutableStateOf(IntSize.Zero)

    private val file get() = File(context.filesDir, "wallpaper.jpg")
    private val blurFile get() = File(context.filesDir, "wallpaper_blur.png")

    suspend fun loadSaved() {
        val (img, blur) = withContext(Dispatchers.IO) {
            val i = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
            val b = if (blurFile.exists()) BitmapFactory.decodeFile(blurFile.path) else null
            i to b
        }
        set(img, blur)
    }

    suspend fun import(uri: Uri): Boolean {
        val result = withContext(Dispatchers.IO) { runCatching { process(uri) }.getOrNull() }
        if (result != null) set(result.first, result.second)
        return result != null
    }

    fun clear() {
        file.delete()
        blurFile.delete()
        image = null
        blurred = null
    }

    private fun set(img: Bitmap?, blur: Bitmap?) {
        image = img?.asImageBitmap()?.also { it.prepareToDraw() }
        blurred = blur?.asImageBitmap()?.also { it.prepareToDraw() }
    }

    private fun process(uri: Uri): Pair<Bitmap, Bitmap>? {
        val resolver = context.contentResolver
        val screen = screenSize()
        val targetW = min(screen.width, screen.height)
        val targetH = max(screen.width, screen.height)

        // 1. Só lê as dimensões da foto (não carrega a imagem).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val rotation = readRotation(uri)
        val sideways = rotation == 90 || rotation == 270
        val srcW = if (sideways) bounds.outHeight else bounds.outWidth
        val srcH = if (sideways) bounds.outWidth else bounds.outHeight

        // 2. Carrega já reduzida (inSampleSize), perto do tamanho da tela.
        var sample = 1
        while (srcW / (sample * 2) >= targetW && srcH / (sample * 2) >= targetH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        // 3. Recorta o centro no formato da tela e ajusta para a resolução exata.
        val scale = max(targetW.toFloat() / bitmap.width, targetH.toFloat() / bitmap.height)
        val cropW = min(bitmap.width, (targetW / scale).roundToInt()).coerceAtLeast(1)
        val cropH = min(bitmap.height, (targetH / scale).roundToInt()).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - cropW) / 2, (bitmap.height - cropH) / 2, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)

        val blur = makeBlur(scaled)

        writeAtomically(file) { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        writeAtomically(blurFile) { blur.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return scaled to blur
    }

    private fun writeAtomically(target: File, write: (FileOutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use(write)
        tmp.renameTo(target)
    }

    private fun readRotation(uri: Uri): Int {
        if (Build.VERSION.SDK_INT < 24) return 0
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    private fun screenSize(): IntSize {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            IntSize(b.width(), b.height())
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            IntSize(m.widthPixels, m.heightPixels)
        }
    }
}

/** Desfoque barato: reduz muito a imagem e faz uma média simples dos pixels. Feito uma vez só. */
private fun makeBlur(src: Bitmap): Bitmap {
    val w = max(1, src.width / 16)
    val h = max(1, src.height / 16)
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    repeat(3) {
        boxPass(pixels, tmp, w, h, horizontal = true)
        boxPass(tmp, pixels, w, h, horizontal = false)
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, horizontal: Boolean, radius: Int = 2) {
    val n = 2 * radius + 1
    for (y in 0 until h) {
        for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val c = if (horizontal) {
                    src[y * w + (x + k).coerceIn(0, w - 1)]
                } else {
                    src[(y + k).coerceIn(0, h - 1) * w + x]
                }
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            dst[y * w + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
    }
}

/** Onde a imagem fica na tela (preenchendo tudo, cortando as sobras, como "Crop"). */
private fun wallpaperRect(container: IntSize, imageWidth: Int, imageHeight: Int): Pair<IntOffset, IntSize> {
    val scale = max(container.width.toFloat() / imageWidth, container.height.toFloat() / imageHeight)
    val w = (imageWidth * scale).roundToInt()
    val h = (imageHeight * scale).roundToInt()
    return IntOffset((container.width - w) / 2, (container.height - h) / 2) to IntSize(w, h)
}

/**
 * Desenha atrás do componente o pedaço do papel de parede que está exatamente
 * atrás dele na tela (nítido ou desfocado). É assim que o card "vê através".
 */
@Composable
fun Modifier.wallpaperBehind(wallpaper: WallpaperState, blurred: Boolean): Modifier {
    val position = remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { position.value = it.positionInRoot() }
        .drawBehind {
            val img = (if (blurred) wallpaper.blurred else wallpaper.image) ?: return@drawBehind
            val root = wallpaper.rootSize
            if (root.width == 0 || root.height == 0) return@drawBehind
            val (offset, size) = wallpaperRect(root, img.width, img.height)
            val p = position.value
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(offset.x - p.x.roundToInt(), offset.y - p.y.roundToInt()),
                dstSize = size,
                filterQuality = FilterQuality.Low,
            )
        }
}

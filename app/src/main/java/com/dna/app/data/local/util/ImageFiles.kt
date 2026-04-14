package com.dna.app.data.local.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Three-tier image pipeline: original stays as-is, display ~1024px, thumb ~256px. */
object ImageTiers {
    const val DISPLAY_MAX_PX = 1024
    const val THUMB_MAX_PX = 256
    const val DISPLAY_QUALITY = 85
    const val THUMB_QUALITY = 80
}

data class PreparedDress(
    val id: String,
    val original: File,
    val display: File,
    val thumb: File,
)

@Singleton
class ImageFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Copies the content Uri (PhotoPicker) into app cache, then renders display
     * and thumbnail JPEGs from it. Returns file handles keyed by a stable id
     * derived from the content hash (so the same photo picked twice dedupes).
     */
    suspend fun prepare(uri: Uri): PreparedDress {
        val stagingDir = File(context.cacheDir, "uploads").apply { mkdirs() }

        // 1. Copy to a staging file first so we can hash + decode independently.
        val staging = File.createTempFile("stage", ".bin", stagingDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(staging).use { output -> input.copyTo(output) }
        } ?: error("Unable to read content URI: $uri")

        val id = sha1(staging).take(24)
        val dir = File(stagingDir, id).apply { mkdirs() }

        val original = File(dir, "original.bin").also { staging.copyTo(it, overwrite = true) }
        staging.delete()

        val bitmap = BitmapFactory.decodeFile(original.absolutePath)
            ?: error("Unable to decode image at ${original.absolutePath}")

        val display = File(dir, "display.jpg")
        val thumb = File(dir, "thumb.jpg")
        writeDownsampled(bitmap, display, ImageTiers.DISPLAY_MAX_PX, ImageTiers.DISPLAY_QUALITY)
        writeDownsampled(bitmap, thumb, ImageTiers.THUMB_MAX_PX, ImageTiers.THUMB_QUALITY)
        bitmap.recycle()

        return PreparedDress(id = id, original = original, display = display, thumb = thumb)
    }

    private fun writeDownsampled(src: Bitmap, dest: File, maxPx: Int, quality: Int) {
        val w = src.width
        val h = src.height
        val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
        } else {
            src
        }
        FileOutputStream(dest).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        if (scaled !== src) scaled.recycle()
    }

    private fun sha1(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

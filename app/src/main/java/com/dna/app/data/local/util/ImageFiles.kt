package com.dna.app.data.local.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.dna.app.domain.taxonomy.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Three-tier media pipeline: original bytes are preserved untouched; display + thumb are derived. */
object ImageTiers {
    const val DISPLAY_MAX_PX = 2048
    const val THUMB_MAX_PX = 256
    const val DISPLAY_QUALITY = 95
    const val THUMB_QUALITY = 80
}

/**
 * Result of staging a picked Uri for upload.
 *
 * For images, [original] is a bit-exact copy of the source bytes (no decode → re-encode).
 * For videos, [original] is the raw video file and [display]/[thumb] are poster frames.
 */
data class PreparedDress(
    val id: String,
    val mediaType: MediaType,
    val mimeType: String,
    val original: File,
    val originalContentType: String,
    val display: File,
    val thumb: File,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
)

@Singleton
class ImageFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val probe: MediaProbe,
) {

    /**
     * Streams the picked Uri into app cache (bit-exact), then renders the
     * display + thumb derivatives. The id is a SHA-1 of the source bytes so
     * re-picking the same media dedupes.
     */
    suspend fun prepare(uri: Uri): PreparedDress {
        val info = probe.probe(uri)
        val stagingDir = File(context.cacheDir, "uploads").apply { mkdirs() }

        val staging = File.createTempFile("stage", ".${info.extension}", stagingDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(staging).use { output -> input.copyTo(output) }
        } ?: error("Unable to read content URI: $uri")

        val id = sha1(staging).take(24)
        val dir = File(stagingDir, id).apply { mkdirs() }

        val original = File(dir, "original.${info.extension}").also {
            staging.copyTo(it, overwrite = true)
        }
        staging.delete()

        return when (info.mediaType) {
            MediaType.IMAGE -> prepareImage(id, info, original, dir)
            MediaType.VIDEO -> prepareVideo(id, info, original, dir, uri)
        }
    }

    private fun prepareImage(
        id: String,
        info: MediaProbeResult,
        original: File,
        dir: File,
    ): PreparedDress {
        // Decode the original honouring DISPLAY_P3 so re-encoded derivatives keep wide-gamut colour.
        val opts = BitmapFactory.Options().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
            }
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(original.absolutePath, opts)
            ?: error("Unable to decode image at ${original.absolutePath}")

        // Apply EXIF orientation so the re-encoded derivatives are upright (and we can stamp
        // their orientation tag back to NORMAL). The original on disk keeps its EXIF intact.
        val rotated = applyExifOrientation(original, decoded)

        val display = File(dir, "display.jpg")
        val thumb = File(dir, "thumb.jpg")
        writeDownsampled(rotated, display, ImageTiers.DISPLAY_MAX_PX, ImageTiers.DISPLAY_QUALITY)
        writeDownsampled(rotated, thumb, ImageTiers.THUMB_MAX_PX, ImageTiers.THUMB_QUALITY)

        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()

        return PreparedDress(
            id = id,
            mediaType = MediaType.IMAGE,
            mimeType = info.mimeType,
            original = original,
            originalContentType = info.mimeType,
            display = display,
            thumb = thumb,
            width = info.width,
            height = info.height,
            durationMs = null,
        )
    }

    private fun prepareVideo(
        id: String,
        info: MediaProbeResult,
        original: File,
        dir: File,
        sourceUri: Uri,
    ): PreparedDress {
        val (poster, w, h, durationMs) = extractPosterFrame(original, sourceUri)
        val display = File(dir, "display.jpg")
        val thumb = File(dir, "thumb.jpg")

        if (poster != null) {
            writeDownsampled(poster, display, ImageTiers.DISPLAY_MAX_PX, ImageTiers.DISPLAY_QUALITY)
            writeDownsampled(poster, thumb, ImageTiers.THUMB_MAX_PX, ImageTiers.THUMB_QUALITY)
            poster.recycle()
        } else {
            // Fallback: tiny black placeholder so upstream code always has a poster URL.
            val placeholder = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            writeDownsampled(placeholder, display, ImageTiers.DISPLAY_MAX_PX, ImageTiers.DISPLAY_QUALITY)
            writeDownsampled(placeholder, thumb, ImageTiers.THUMB_MAX_PX, ImageTiers.THUMB_QUALITY)
            placeholder.recycle()
        }

        return PreparedDress(
            id = id,
            mediaType = MediaType.VIDEO,
            mimeType = info.mimeType,
            original = original,
            originalContentType = info.mimeType,
            display = display,
            thumb = thumb,
            width = w ?: info.width,
            height = h ?: info.height,
            durationMs = durationMs ?: info.durationMs,
        )
    }

    private data class PosterResult(
        val bitmap: Bitmap?,
        val width: Int?,
        val height: Int?,
        val durationMs: Long?,
    )

    private fun extractPosterFrame(file: File, sourceUri: Uri): PosterResult {
        val retriever = MediaMetadataRetriever()
        return try {
            // Prefer the file we just staged (always readable); fall back to the source uri.
            runCatching { retriever.setDataSource(file.absolutePath) }
                .recoverCatching { retriever.setDataSource(context, sourceUri) }
                .getOrThrow()
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            PosterResult(frame, w, h, d)
        } catch (_: Throwable) {
            PosterResult(null, null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun applyExifOrientation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.preScale(-1f, 1f); matrix.postRotate(270f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.preScale(-1f, 1f); matrix.postRotate(90f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
        // Stamp the derivative as already-upright so downstream viewers don't double-rotate.
        runCatching {
            ExifInterface(dest.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                saveAttributes()
            }
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

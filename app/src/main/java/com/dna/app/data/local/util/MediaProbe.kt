package com.dna.app.data.local.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import com.dna.app.domain.taxonomy.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class MediaProbeResult(
    val mediaType: MediaType,
    val mimeType: String,
    val extension: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
)

@Singleton
class MediaProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun probe(uri: Uri): MediaProbeResult {
        val mime = context.contentResolver.getType(uri)
            ?: guessMimeFromUri(uri)
            ?: "application/octet-stream"
        val mediaType = if (mime.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: when (mediaType) {
                MediaType.IMAGE -> "jpg"
                MediaType.VIDEO -> "mp4"
            }

        return when (mediaType) {
            MediaType.IMAGE -> MediaProbeResult(mediaType, mime, ext, null, null, null)
            MediaType.VIDEO -> probeVideo(uri, mime, ext)
        }
    }

    private fun probeVideo(uri: Uri, mime: String, ext: String): MediaProbeResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            MediaProbeResult(MediaType.VIDEO, mime, ext, w, h, d)
        } catch (_: Throwable) {
            MediaProbeResult(MediaType.VIDEO, mime, ext, null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun guessMimeFromUri(uri: Uri): String? {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
            ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
    }
}

package io.github.roggzuay.ondalune.auto

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import coil.imageLoader
import coil.request.ImageRequest
import io.github.roggzuay.ondalune.ui.theme.artworkCacheKey
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Covers for Android Auto. The car loads pictures itself, and a cover's real address carries the
 * server login, so the car is given an address that says nothing instead, and the picture is
 * fetched here, inside the app (from the same cache the screens use).
 */
class OndaluneArtProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException()
        val url = urls[uri.lastPathSegment] ?: throw FileNotFoundException("No cover at $uri")
        val key = artworkCacheKey(url)
        val request = ImageRequest.Builder(context).data(url).memoryCacheKey(key).diskCacheKey(key)
            .size(ART_PX).allowHardware(false).build()
        val cover = (runBlocking { context.imageLoader.execute(request) }.drawable as? BitmapDrawable)?.bitmap
            ?: throw FileNotFoundException("No cover at $uri")
        return openPipeHelper(uri, MIME, null, cover) { output, _, _, _, bitmap ->
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 90, FileOutputStream(output.fileDescriptor))
        }
    }

    override fun getType(uri: Uri) = MIME
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val MIME = "image/jpeg"
        private const val ART_PX = 400

        /** The covers the car has been told about, by the name it knows them under. */
        private val urls = ConcurrentHashMap<String, String>()

        /** The address the car loads [coverArtUrl] from, or null when there is no cover. */
        fun uriFor(context: Context, coverArtUrl: String): Uri? {
            if (coverArtUrl.isBlank()) return null
            val name = MessageDigest.getInstance("SHA-1").digest(artworkCacheKey(coverArtUrl).toByteArray())
                .joinToString("") { "%02x".format(it) }
            urls[name] = coverArtUrl
            return Uri.Builder().scheme("content").authority(context.packageName + ".art").appendPath(name).build()
        }
    }
}

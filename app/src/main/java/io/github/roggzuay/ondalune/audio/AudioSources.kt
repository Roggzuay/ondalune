package io.github.roggzuay.ondalune.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaPlayer
import android.net.Uri

/**
 * Where a song plays from: a file path, a stream URL, or a `content://` document from a music folder
 * picked in Settings (read through the folder's persisted permission, without storage access).
 */
fun isDocumentPath(path: String) = path.startsWith("content://")

fun MediaExtractor.setDataSourceOf(context: Context, path: String) =
    if (isDocumentPath(path)) setDataSource(context, Uri.parse(path), null) else setDataSource(path)

fun MediaPlayer.setDataSourceOf(context: Context, path: String) =
    if (isDocumentPath(path)) setDataSource(context, Uri.parse(path)) else setDataSource(path)

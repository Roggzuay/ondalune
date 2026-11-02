package io.github.roggzuay.ondalune.data.local

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.roggzuay.ondalune.data.network.updateApkUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where an in-app update stands, from tapping Update to Android's install screen. */
sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    data class Downloading(val progress: Float) : UpdateInstallState
    /** Downloaded, but Android hasn't let Ondalune install apps yet. */
    data class NeedsPermission(val apk: File) : UpdateInstallState
    /** Handed to Android's installer, which asks the user to confirm. */
    data class Ready(val apk: File) : UpdateInstallState
    data class Failed(val message: String) : UpdateInstallState
}

/**
 * Downloads a release APK from GitHub and hands it to Android's own installer. Nothing installs
 * silently: Android asks the user to allow installs from Ondalune once, then to confirm each
 * update, and refuses any APK not signed with the same key as the installed app.
 */
object AppUpdateInstaller {
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun updatesDir(context: Context) = File(context.cacheDir, "updates")

    /** Reuses a finished download of [version] (say, from before granting the install permission). */
    suspend fun download(context: Context, version: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = updatesDir(context).apply { mkdirs() }
            val apk = File(dir, "Ondalune-$version.apk")
            if (apk.exists()) return@withContext apk
            dir.listFiles()?.forEach { it.delete() } // older updates and unfinished downloads
            val part = File(dir, "${apk.name}.part")
            val request = Request.Builder()
                .url(updateApkUrl(version))
                .header("User-Agent", "Ondalune (update)")
                .build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty response")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
            if (!part.renameTo(apk)) throw IOException("Couldn't save the update")
            apk
        }

    /**
     * The APK is this app, signed with the same key. Android enforces the key on install anyway;
     * checking first gives a clear message instead of the installer's generic failure.
     */
    fun isTrustedUpdate(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, signerFlag) ?: return false
        if (archive.packageName != context.packageName) return false
        val incoming = signers(archive)
        // Some older Androids can't read an archive's certificates; the installer still checks.
        return incoming.isEmpty() || incoming == signers(pm.getPackageInfo(context.packageName, signerFlag))
    }

    /** Android 8+ asks once whether Ondalune may install apps; older versions use a global switch. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(context: Context, apk: File): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                FileProvider.getUriForFile(context, "${context.packageName}.updates", apk),
                "application/vnd.android.package-archive"
            )
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun clearDownloads(context: Context) {
        updatesDir(context).deleteRecursively()
    }

    @Suppress("DEPRECATION")
    private val signerFlag =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
        } else {
            info.signatures.orEmpty().map { it.toCharsString() }.toSet()
        }
}

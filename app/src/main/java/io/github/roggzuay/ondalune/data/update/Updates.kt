package io.github.roggzuay.ondalune.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.roggzuay.ondalune.data.network.AppUpdate
import io.github.roggzuay.ondalune.data.network.isNewerVersion
import io.github.roggzuay.ondalune.data.network.releaseNoteItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** How the installed copy of Ondalune got here, which decides how it should be updated. */
enum class InstallSource {
    /** A release APK from GitHub (or a tool that installs them): it can update itself in the app. */
    GITHUB,
    /** F-Droid (or a client of its repositories): F-Droid delivers updates, signed its way. */
    FDROID,
    /** Anything else (a development build, another store): only instructions, never an APK. */
    OTHER
}

private const val REPO = "Roggzuay/ondalune"
private const val RELEASES_PAGE_PREFIX = "https://github.com/$REPO/releases/"

/**
 * SHA-256 of the certificate GitHub releases are signed with (public; it is in every release APK).
 * Only a copy signed with it can install a release APK over itself.
 */
const val RELEASE_CERT_SHA256 = "2b4608125f8267852d26c80bc65690a1fe4255de5beca9f4b7afa5f75e357430"

private val fdroidClients = setOf(
    "org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid", "eu.bubu1.fdroidclassic"
)

/** Works out [InstallSource] from the installer Android recorded and the app's signing certificate. */
fun installSourceOf(context: Context): InstallSource {
    val pm = context.packageManager
    val pkg = context.packageName
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
    }.getOrNull()
    if (installer in fdroidClients) return InstallSource.FDROID
    val certs = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.toList()
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures?.toList()
        }
    }.getOrNull().orEmpty()
    val sha = certs.map { cert ->
        MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    return if (RELEASE_CERT_SHA256 in sha) InstallSource.GITHUB else InstallSource.OTHER
}

/** A release from GitHub's JSON, or null unless it is a published, well-formed Ondalune release. */
fun parseRelease(json: JSONObject): AppUpdate? {
    if (json.optBoolean("draft")) return null
    val tag = json.optString("tag_name")
    if (!Regex("""v?\d+(\.\d+){1,3}(-[0-9A-Za-z.]+)?""").matches(tag)) return null
    val page = json.optString("html_url")
    if (!page.startsWith(RELEASES_PAGE_PREFIX)) return null
    return AppUpdate(
        version = tag.removePrefix("v"),
        notes = releaseNoteItems(json.optString("body").take(20_000)),
        publishedOn = json.optString("published_at").take(10),
        pageUrl = page,
        prerelease = json.optBoolean("prerelease")
    )
}

/** The newest release in a `/releases` list; pre-releases only when [includePrereleases]. */
fun newestRelease(list: JSONArray, includePrereleases: Boolean): AppUpdate? =
    (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let(::parseRelease) }
        .filter { includePrereleases || !it.prerelease }
        .fold(null as AppUpdate?) { best, r -> if (best == null || isNewerVersion(r.version, best.version)) r else best }

/**
 * Release information from GitHub, cached in preferences so Home, the notification and the worker
 * agree without asking again. A check within [MAX_AGE_MS] of the last one uses the cache unless
 * forced; GitHub's ETag makes a repeated check cheap; a refusal (rate limit) keeps the cache.
 */
class UpdateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ondalune_prefs", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    enum class Result { FRESH, CACHED, FAILED }

    fun cached(): AppUpdate? {
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        return AppUpdate(
            version = version,
            notes = prefs.getString(KEY_NOTES, "").orEmpty().lines().filter { it.isNotBlank() },
            publishedOn = prefs.getString(KEY_DATE, "").orEmpty(),
            pageUrl = prefs.getString(KEY_PAGE, null) ?: "${RELEASES_PAGE_PREFIX}tag/v$version",
            prerelease = prefs.getBoolean(KEY_PRE, false)
        )
    }

    suspend fun refresh(force: Boolean, includePrereleases: Boolean): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sameChannel = prefs.getBoolean(KEY_CHANNEL_PRE, false) == includePrereleases
        if (!force && sameChannel && now - prefs.getLong(KEY_CHECKED_AT, 0L) < MAX_AGE_MS) return@withContext Result.CACHED
        if (now < prefs.getLong(KEY_RETRY_AFTER, 0L)) return@withContext Result.FAILED
        val url = if (includePrereleases) "https://api.github.com/repos/$REPO/releases?per_page=15"
        else "https://api.github.com/repos/$REPO/releases/latest"
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Ondalune (update check)")
            .apply { if (sameChannel) prefs.getString(KEY_ETAG, null)?.let { header("If-None-Match", it) } }
            .build()
        runCatching {
            http.newCall(request).execute().use { resp ->
                when {
                    resp.code == 304 -> {
                        prefs.edit().putLong(KEY_CHECKED_AT, now).apply()
                        Result.CACHED
                    }
                    resp.code == 403 || resp.code == 429 -> {
                        val reset = resp.header("x-ratelimit-reset")?.toLongOrNull()?.times(1000)
                        prefs.edit().putLong(KEY_RETRY_AFTER, reset ?: (now + 60 * 60 * 1000L)).apply()
                        Result.FAILED
                    }
                    !resp.isSuccessful -> Result.FAILED
                    else -> {
                        val body = resp.body?.string().orEmpty()
                        val release = if (includePrereleases) newestRelease(JSONArray(body), true) else parseRelease(JSONObject(body))
                        release ?: return@use Result.FAILED
                        prefs.edit()
                            .putLong(KEY_CHECKED_AT, now)
                            .putBoolean(KEY_CHANNEL_PRE, includePrereleases)
                            .putString(KEY_ETAG, resp.header("ETag"))
                            .putString(KEY_VERSION, release.version)
                            .putString(KEY_NOTES, release.notes.joinToString("\n"))
                            .putString(KEY_DATE, release.publishedOn)
                            .putString(KEY_PAGE, release.pageUrl)
                            .putBoolean(KEY_PRE, release.prerelease)
                            .apply()
                        Result.FRESH
                    }
                }
            }
        }.getOrDefault(Result.FAILED)
    }

    /** The release worth telling about: newer than [installedVersion], else null. */
    fun availableFor(installedVersion: String): AppUpdate? = cached()?.takeIf { isNewerVersion(it.version, installedVersion) }

    /** Notifications go out once per version, whatever happens to them. */
    fun shouldNotify(version: String) = prefs.getString(KEY_NOTIFIED, null) != version

    fun markNotified(version: String) = prefs.edit().putString(KEY_NOTIFIED, version).apply()

    companion object {
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val KEY_CHECKED_AT = "update_checked_at"
        private const val KEY_VERSION = "update_latest_version"
        private const val KEY_NOTES = "update_latest_notes"
        private const val KEY_DATE = "update_latest_date"
        private const val KEY_PAGE = "update_latest_page"
        private const val KEY_PRE = "update_latest_prerelease"
        private const val KEY_CHANNEL_PRE = "update_channel_prereleases"
        private const val KEY_ETAG = "update_etag"
        private const val KEY_RETRY_AFTER = "update_retry_after"
        private const val KEY_NOTIFIED = "update_notified_version"
    }
}

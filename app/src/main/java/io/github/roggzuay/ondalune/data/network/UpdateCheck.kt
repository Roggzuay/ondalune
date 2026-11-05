package io.github.roggzuay.ondalune.data.network

/** Where people download it: the release page with the APK and its checksum. */
const val LATEST_RELEASE_PAGE = "https://github.com/Roggzuay/ondalune/releases/latest"

/** The release's APK, named as the release process uploads it ("Ondalune-1.1.0.apk"). */
fun updateApkUrl(version: String) =
    "https://github.com/Roggzuay/ondalune/releases/download/v$version/Ondalune-$version.apk"

/** A release from GitHub, checked before use, with its notes as plain lines. */
data class AppUpdate(
    val version: String,
    val notes: List<String>,
    /** ISO date ("2026-11-05"), blank when GitHub didn't say. */
    val publishedOn: String = "",
    val pageUrl: String = LATEST_RELEASE_PAGE,
    val prerelease: Boolean = false
)

/** Result of Settings → Updates → Check now; an available update shows as the update itself. */
enum class UpdateCheckStatus { IDLE, CHECKING, UP_TO_DATE, FAILED }

/**
 * True when [remoteTag] ("v1.2.0") is newer than [currentVersion] ("1.1.0.2-preview"). Missing
 * parts count as 0; a version that isn't all numbers never counts as an update.
 */
fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
    val remote = versionNumbers(remoteTag.removePrefix("v"))
    val current = versionNumbers(currentVersion)
    if (remote.isEmpty() || current.isEmpty()) return false
    for (i in 0 until maxOf(remote.size, current.size)) {
        val r = remote.getOrElse(i) { 0 }
        val c = current.getOrElse(i) { 0 }
        if (r != c) return r > c
    }
    return false
}

private fun versionNumbers(version: String): List<Int> {
    val parts = version.substringBefore('-').split('.')
    return parts.map { it.toIntOrNull() ?: return emptyList() }
}

/**
 * A release body as plain lines for the update dialog: bullets and sentences without Markdown,
 * dropping bold section headers ("**New**") and the checksum lines.
 */
fun releaseNoteItems(body: String): List<String> = body.lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !boldHeader.matches(it) && !it.startsWith("Verify the download") && !it.startsWith("SHA-256 of") }
    .map { it.removePrefix("- ").removePrefix("* ").replace("**", "").trim() }

private val boldHeader = Regex("""\*\*[^*]+\*\*""")

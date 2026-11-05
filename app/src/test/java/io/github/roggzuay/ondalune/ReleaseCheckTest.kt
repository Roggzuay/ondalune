package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.update.newestRelease
import io.github.roggzuay.ondalune.data.update.parseRelease
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReleaseCheckTest {
    private fun release(tag: String, pre: Boolean = false, draft: Boolean = false, page: String = "https://github.com/Roggzuay/ondalune/releases/tag/$tag") =
        JSONObject()
            .put("tag_name", tag).put("prerelease", pre).put("draft", draft).put("html_url", page)
            .put("published_at", "2026-11-05T12:17:24Z").put("body", "**New**\n- Music folders\n- Jellyfin")

    @Test
    fun `a published release is read with its date, page and notes`() {
        val r = parseRelease(release("v1.6.0"))!!
        assertEquals("1.6.0", r.version)
        assertEquals("2026-11-05", r.publishedOn)
        assertEquals(listOf("Music folders", "Jellyfin"), r.notes)
        assertTrue(r.pageUrl.startsWith("https://github.com/Roggzuay/ondalune/releases/"))
    }

    @Test
    fun `drafts, odd tags and foreign links are refused`() {
        assertNull(parseRelease(release("v1.6.0", draft = true)))
        assertNull(parseRelease(release("latest-build")))
        assertNull(parseRelease(release("v1.6.0", page = "https://example.com/ondalune.apk")))
        assertNull(parseRelease(JSONObject("{}")))
    }

    @Test
    fun `pre-releases are offered only when asked for`() {
        val list = JSONArray().put(release("v1.7.0-beta.1", pre = true)).put(release("v1.6.0")).put(release("v1.5.0"))
        assertEquals("1.6.0", newestRelease(list, includePrereleases = false)!!.version)
        assertEquals("1.7.0-beta.1", newestRelease(list, includePrereleases = true)!!.version)
    }

    @Test
    fun `the newest stable release wins whatever the order`() {
        val list = JSONArray().put(release("v1.5.0")).put(release("v1.10.0")).put(release("v1.9.2"))
        assertEquals("1.10.0", newestRelease(list, false)!!.version)
    }
}

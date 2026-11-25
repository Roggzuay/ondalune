package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.ui.components.safeName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file a shared card is written to. A song can be called anything at all, and the name still
 * has to be one a file system will take.
 */
class ShareCardTest {

    @Test
    fun `an ordinary name comes through readable`() {
        assertEquals("Led-Zeppelin---Tangerine", safeName("Led Zeppelin - Tangerine"))
    }

    @Test
    fun `slashes and other trouble are replaced, not dropped silently`() {
        val name = safeName("AC/DC - Back in Black: Side A")
        assertTrue(name, !name.contains('/'))
        assertTrue(name, !name.contains(':'))
        assertTrue(name, name.contains("AC_DC"))
    }

    @Test
    fun `a name of nothing but trouble still gives a usable file`() {
        assertEquals("ondalune", safeName("///"))
        assertEquals("ondalune", safeName(""))
        assertEquals("ondalune", safeName("   "))
    }

    @Test
    fun `a very long name is cut to something a file system will take`() {
        assertTrue(safeName("x".repeat(400)).length <= 48)
    }

    @Test
    fun `a name in another script is not emptied out`() {
        // Letters are letters, whatever the alphabet.
        assertEquals("Пикник", safeName("Пикник"))
        assertEquals("東京", safeName("東京"))
    }
}

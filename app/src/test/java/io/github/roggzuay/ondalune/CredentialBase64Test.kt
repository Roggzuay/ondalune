package io.github.roggzuay.ondalune

import android.util.Base64
import io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Stored credentials were encoded with java.util.Base64 (no padding); they are now encoded with
 * android.util.Base64, which also works on Android 7. Both must agree byte for byte, both ways.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
/** Moved from ExampleUnitTest: it needs android.util.Base64, which plain JVM tests only stub. */
class CredentialBase64Test {
    private val flags = Base64.NO_WRAP or Base64.NO_PADDING

    @Test
    fun `android encoding matches the old java encoding and reads it back`() {
        val random = Random(42)
        repeat(200) {
            val bytes = random.nextBytes(random.nextInt(1, 80))
            val old = java.util.Base64.getEncoder().withoutPadding().encodeToString(bytes)
            assertEquals(old, Base64.encodeToString(bytes, flags))
            assertArrayEquals(bytes, Base64.decode(old, flags))
        }
    }

    @Test
    fun `credential security encrypts and decrypts tokens and redacts sensitive urls`() {
        val secret = "SuperSecretSubsonicToken!2026"
        val encrypted = OndaluneCredentialSecurity.encryptCredential(secret)
        assertTrue(OndaluneCredentialSecurity.isEncrypted(encrypted))
        assertNotEquals(secret, encrypted)
        assertEquals(secret, OndaluneCredentialSecurity.decryptCredential(encrypted))

        val rawUrl = "https://music.example.com/rest/stream.view?u=alice&t=9f8e7d6c5b&s=123456&p=enc:70617373&id=tr_1"
        val redacted = OndaluneCredentialSecurity.redactSensitiveUrl(rawUrl)
        assertFalse(redacted.contains("9f8e7d6c5b"))
        assertFalse(redacted.contains("70617373"))
        assertTrue(redacted.contains("t=[REDACTED]"))
    }
}

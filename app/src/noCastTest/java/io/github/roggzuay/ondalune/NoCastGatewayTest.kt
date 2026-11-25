package io.github.roggzuay.ondalune

import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.cast.createCastGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The build without the Cast SDK. It has to answer a plain no and do nothing at all, so the rest
 * of the app can call it without asking which build it is in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoCastGatewayTest {

    @Test
    fun `casting is simply not supported here`() {
        val gateway = createCastGateway(ApplicationProvider.getApplicationContext())
        assertFalse(gateway.supported)
        assertFalse(gateway.state.value.connected)
        assertEquals(0L, gateway.positionMs())
        assertEquals(0L, gateway.durationMs())
    }

    @Test
    fun `every call is safe to make and does nothing`() {
        val gateway = createCastGateway(ApplicationProvider.getApplicationContext())
        gateway.play()
        gateway.pause()
        gateway.seekTo(1_000L)
        gateway.stop()
        gateway.onTrackEnded { }
        assertFalse(gateway.state.value.connected)
    }
}

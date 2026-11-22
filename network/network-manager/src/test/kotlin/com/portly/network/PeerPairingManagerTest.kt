package com.portly.network

import com.portly.core.model.ProtocolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerPairingManagerTest {

    @Test
    fun `pairing offer encodes decodes and enforces single use`() {
        var fakeNow = 1_000_000L
        val manager = PeerPairingManager(nowProvider = { fakeNow })

        val offer = manager.createPairingOffer(
            protocol = ProtocolType.FTPS,
            host = "192.168.1.42",
            port = 2122,
            deviceName = "Pixel 9",
            sha256CertFingerprint = "AA:BB:CC:DD",
            advertiseNsd = false,
        )

        assertTrue(offer.pairingUri.startsWith("portly://pair?payload="))
        assertNotNull(manager.getActiveOffer())

        val parsed = PeerPairingManager.parseAndValidateIncoming(offer.pairingUri, nowMillis = fakeNow + 5_000L)
            .getOrThrow()
        assertEquals(ProtocolType.FTPS, parsed.protocol)
        assertEquals("192.168.1.42", parsed.host)
        assertEquals(2122, parsed.port)
        assertEquals("Pixel 9", parsed.deviceName)
        assertEquals("AA:BB:CC:DD", parsed.sha256CertFingerprint)

        // Wrong token fails without consuming the offer.
        assertTrue(manager.consumeIssuedToken("wrong-token").isFailure)
        // First valid consumption succeeds.
        assertTrue(manager.consumeIssuedToken(parsed.token).isSuccess)
        // Second consumption of same one-time token fails cleanly.
        assertTrue(manager.consumeIssuedToken(parsed.token).isFailure)
        assertNull(manager.getActiveOffer())
    }

    @Test
    fun `pairing offer and incoming URI reject expired 60-second window`() {
        var fakeNow = 2_000_000L
        val manager = PeerPairingManager(nowProvider = { fakeNow })

        val offer = manager.createPairingOffer(
            protocol = ProtocolType.FTP,
            host = "192.168.1.10",
            port = 2121,
            advertiseNsd = false,
        )

        fakeNow += 61_000L
        assertNull(manager.getActiveOffer())
        assertTrue(PeerPairingManager.parseAndValidateIncoming(offer.pairingUri, nowMillis = fakeNow).isFailure)
        assertTrue(manager.consumeIssuedToken(offer.payload.token).isFailure)
    }
}

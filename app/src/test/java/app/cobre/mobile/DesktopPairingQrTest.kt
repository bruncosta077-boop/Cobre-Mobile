package app.cobre.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DesktopPairingQrTest {
    private val host = "123e4567-e89b-12d3-a456-426614174000"
    private val now = 1_000_000L

    private fun qr(
        origin: String = "http%3A%2F%2F192.168.1.20%3A8787",
        expires: Long = now + 60_000,
        version: String = "1",
        hostId: String = host,
        scheme: String = "cobre",
    ) = "$scheme://pair?v=$version&host=$hostId&origin=$origin&expires=$expires"

    private fun assertRejected(raw: String) {
        assertThrows(CobreIdException::class.java) { parseDesktopPairingQr(raw, now) }
    }

    @Test fun acceptsValidLocalNetworkQr() {
        val parsed = parseDesktopPairingQr(qr(), now)
        assertEquals(host, parsed.hostId)
        assertEquals("http://192.168.1.20:8787", parsed.origin)
        assertEquals(now + 60_000, parsed.expiresAt)
    }

    @Test fun acceptsLocalhostAndPrivate172Range() {
        assertEquals("http://localhost:8787", parseDesktopPairingQr(qr(origin = "http%3A%2F%2Flocalhost%3A8787"), now).origin)
        assertEquals("http://172.16.0.5:8787", parseDesktopPairingQr(qr(origin = "http%3A%2F%2F172.16.0.5%3A8787"), now).origin)
    }

    @Test fun rejectsPublicAddress() = assertRejected(qr(origin = "http%3A%2F%2F8.8.8.8%3A8787"))

    @Test fun rejectsOutsidePrivate172Range() = assertRejected(qr(origin = "http%3A%2F%2F172.32.0.5%3A8787"))

    @Test fun rejectsWrongPort() = assertRejected(qr(origin = "http%3A%2F%2F192.168.1.20%3A8080"))

    @Test fun rejectsHttps() = assertRejected(qr(origin = "https%3A%2F%2F192.168.1.20%3A8787"))

    @Test fun rejectsExpiredQr() = assertRejected(qr(expires = now - 1))

    @Test fun rejectsValidityLongerThanSixMinutes() = assertRejected(qr(expires = now + 7 * 60_000))

    @Test fun rejectsUnknownVersion() = assertRejected(qr(version = "2"))

    @Test fun rejectsMalformedHostId() = assertRejected(qr(hostId = "not-a-uuid"))

    @Test fun rejectsOtherScheme() = assertRejected(qr(scheme = "https"))
}

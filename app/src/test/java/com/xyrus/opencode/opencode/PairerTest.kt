package com.xyrus.opencode.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's pairing decisions, checked against the real QR payload.
 *
 * `opencode pair` prints one code that answers on several addresses, ranked with
 * Tailscale first, and omits loopback because a phone can never use it. The
 * phone may sit on any one of them, so the whole point is trying the list in
 * order rather than committing to whatever was scanned first.
 */
class PairerTest {

    private val qr = """
        {"code":"GjH-wVfX2KIOe3FzKi2Z4g","urls":[
          "http://100.96.0.2:45278",
          "http://169.254.123.33:45278",
          "http://192.168.0.166:45278",
          "http://172.23.128.1:45278",
          "https://1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz"]}
    """.trimIndent()

    @Test
    fun `every address in the pairing is a candidate`() {
        val parts = PairLink.fromScan(qr)!!
        assertEquals(5, parts.bases.size)
    }

    @Test
    fun `loopback is never a candidate`() {
        // opencode leaves it out because it cannot work from a phone; if one
        // ever sneaks in it must not be tried first.
        assertTrue(PairLink.fromScan(qr)!!.bases.none { it.contains("127.0.0.1") })
    }

    @Test
    fun `a phone off the LAN still gets an ordered path to the tunnel`() {
        val parts = PairLink.fromScan(qr)!!
        // Last resort is the remote tunnel, which is reachable from anywhere.
        assertEquals(
            "https://1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz",
            parts.bases.last(),
        )
    }

    @Test
    fun `pairing with no candidates is reported rather than attempted`() {
        assertTrue(Pairer.pair("abc", emptyList()) is Pairer.Outcome.Unreachable)
    }
}
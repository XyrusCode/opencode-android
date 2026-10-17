package com.xyrus.opencode.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairLinkTest {

    // The payload decoded from the QR that `opencode pair` printed, verbatim.
    private val qrJson = """
        {"code":"GjH-wVfX2KIOe3FzKi2Z4g","urls":[
          "http://100.96.0.2:45278",
          "http://169.254.123.33:45278",
          "http://192.168.0.166:45278",
          "http://172.23.128.1:45278",
          "https://1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz"]}
    """.trimIndent()

    @Test
    fun `a scanned qr yields the code and every address`() {
        val parts = PairLink.fromScan(qrJson)!!
        assertEquals("GjH-wVfX2KIOe3FzKi2Z4g", parts.code)
        assertEquals(5, parts.bases.size)
        assertTrueContains(parts.bases, "http://100.96.0.2:45278")
        assertTrueContains(parts.bases, "https://1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz")
    }

    @Test
    fun `tailscale is tried before the lan`() {
        val parts = PairLink.fromScan(qrJson)!!
        // A phone on Tailscale must reach the machine even while sitting on a
        // wifi network it has no route to.
        assertEquals("http://100.96.0.2:45278", parts.bases.first())
    }

    @Test
    fun `the lan is tried before link-local and the tunnel`() {
        val parts = PairLink.fromScan(qrJson)!!
        val lan = parts.bases.indexOf("http://192.168.0.166:45278")
        val linkLocal = parts.bases.indexOf("http://169.254.123.33:45278")
        val tunnel = parts.bases.indexOf("https://1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz")
        assertTrue(lan in 0 until linkLocal)
        assertTrue(linkLocal in 0 until tunnel)
    }

    @Test
    fun `ordering does not depend on the order opencode happened to print`() {
        val shuffled = """
            {"code":"abc","urls":[
              "https://tunnel.example.com",
              "http://192.168.0.166:45278",
              "http://100.96.0.2:45278"]}
        """.trimIndent()
        assertEquals(
            listOf(
                "http://100.96.0.2:45278",
                "http://192.168.0.166:45278",
                "https://tunnel.example.com",
            ),
            PairLink.fromScan(shuffled)!!.bases,
        )
    }

    @Test
    fun `duplicate addresses are collapsed`() {
        val dup = """{"code":"abc","urls":["http://100.96.0.2:45278","http://100.96.0.2:45278"]}"""
        assertEquals(1, PairLink.fromScan(dup)!!.bases.size)
    }

    @Test
    fun `a scanned plain url still works`() {
        val parts = PairLink.fromScan(
            "https://abc.opentunnel.xyz/auth/connect/uN0DZYwJDl5fFy0DuUDfuQ"
        )!!
        assertEquals("uN0DZYwJDl5fFy0DuUDfuQ", parts.code)
        assertEquals("https://abc.opentunnel.xyz", parts.primary)
    }

    @Test
    fun `a scanned terminal one-liner still works`() {
        val parts = PairLink.fromScan("http://192.168.0.166:45278/auth/connect/JBkxZ-bw3RQ")!!
        assertEquals("JBkxZ-bw3RQ", parts.code)
        assertEquals("http://192.168.0.166:45278", parts.primary)
    }

    @Test
    fun `junk scans are rejected rather than half-accepted`() {
        listOf("", "   ", "hello", "{not json").forEach {
            assertNull("expected reject for '$it'", PairLink.fromScan(it))
        }
    }

    // --- manual entry -----------------------------------------------------

    @Test
    fun `defaults to https for a remote host`() {
        // The tunnel is HTTPS-only. Prefixing http:// is what made pairing fail.
        assertEquals(
            "https://1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz",
            PairLink.normalizeBase("1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz"),
        )
    }

    @Test
    fun `keeps http for loopback and private LAN addresses`() {
        listOf(
            "localhost:45278",
            "127.0.0.1:45278",
            "192.168.1.5:4096",
            "10.0.0.7:4096",
            "172.16.4.2:4096",
            "devbox:4096",
            "macbook.local:4096",
        ).forEach {
            assertEquals("http://$it", PairLink.normalizeBase(it))
        }
    }

    @Test
    fun `respects an explicit scheme and trims trailing slashes`() {
        assertEquals("https://x.dev", PairLink.normalizeBase("https://x.dev/"))
        assertEquals("http://x.dev", PairLink.normalizeBase("http://x.dev"))
        assertEquals("https://x.dev", PairLink.normalizeBase("https://x.dev///"))
    }

    @Test
    fun `a full link pasted into the address field works`() {
        val parts = PairLink.resolve(
            addressInput = "https://abc.opentunnel.xyz/auth/connect/CODE123",
            codeInput = "",
        )!!
        assertEquals("CODE123", parts.code)
        assertEquals("https://abc.opentunnel.xyz", parts.primary)
    }

    @Test
    fun `a full link pasted into the code field works`() {
        val parts = PairLink.resolve(
            addressInput = "abc.opentunnel.xyz",
            codeInput = "https://abc.opentunnel.xyz/auth/connect/CODE123",
        )!!
        assertEquals("CODE123", parts.code)
        assertEquals("https://abc.opentunnel.xyz", parts.primary)
    }

    @Test
    fun `address plus bare code is the normal case`() {
        val parts = PairLink.resolve("192.168.1.5:4096", "CODE123")!!
        assertEquals("CODE123", parts.code)
        assertEquals("http://192.168.1.5:4096", parts.primary)
    }

    @Test
    fun `nothing usable returns null rather than a broken request`() {
        assertNull(PairLink.resolve("", ""))
        assertNull(PairLink.resolve("192.168.1.5:4096", "   "))
        assertNull(PairLink.resolve("", "CODE123"))
    }

    private fun assertTrueContains(haystack: List<String>, needle: String) {
        assertTrue("expected $needle in $haystack", haystack.contains(needle))
    }
}
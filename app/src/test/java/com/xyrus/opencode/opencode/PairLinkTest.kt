package com.xyrus.opencode.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairLinkTest {

    // A real link captured from `opencode pair --remote`.
    private val link = "https://1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz/auth/connect/uN0DZYwJDl5fFy0DuUDfuQ"

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
    fun `respects an explicit scheme`() {
        assertEquals("https://x.dev", PairLink.normalizeBase("https://x.dev"))
        assertEquals("http://x.dev", PairLink.normalizeBase("http://x.dev"))
    }

    @Test
    fun `does not double up a scheme or a trailing slash`() {
        assertEquals("https://x.dev", PairLink.normalizeBase("https://x.dev/"))
        assertEquals("https://x.dev", PairLink.normalizeBase("https://x.dev///"))
    }

    @Test
    fun `extracts the code from a full link`() {
        assertEquals("uN0DZYwJDl5fFy0DuUDfuQ", PairLink.extractCode(link))
    }

    @Test
    fun `a plain code is not mistaken for a link`() {
        assertNull(PairLink.extractCode("uN0DZYwJDl5fFy0DuUDfuQ"))
    }

    @Test
    fun `a full link pasted into the address field works`() {
        val parts = PairLink.resolve(addressInput = link, codeInput = "")!!
        assertEquals("https://1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz", parts.base)
        assertEquals("uN0DZYwJDl5fFy0DuUDfuQ", parts.code)
    }

    @Test
    fun `a full link pasted into the code field works`() {
        val parts = PairLink.resolve(
            addressInput = "1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz",
            codeInput = link,
        )!!
        assertEquals("https://1f6f026ddc1b1e9e.yohuce2e45dm.opentunnel.xyz", parts.base)
        assertEquals("uN0DZYwJDl5fFy0DuUDfuQ", parts.code)
    }

    @Test
    fun `address plus bare code is the normal case`() {
        val parts = PairLink.resolve("192.168.1.5:4096", "uN0DZYwJDl5fFy0DuUDfuQ")!!
        assertEquals("http://192.168.1.5:4096", parts.base)
        assertEquals("uN0DZYwJDl5fFy0DuUDfuQ", parts.code)
    }

    @Test
    fun `nothing usable returns null rather than a broken request`() {
        assertNull(PairLink.resolve("", ""))
        assertNull(PairLink.resolve("192.168.1.5:4096", "   "))
        assertNull(PairLink.resolve("", "uN0DZYwJDl5fFy0DuUDfuQ"))
    }
}
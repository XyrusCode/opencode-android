package com.xyrus.opencode.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairCredentialTest {

    // A real paired token observed from a live server, with its expiry rewritten
    // to a fixed point so the arithmetic is checkable.
    private val paired = "1794822606.81tHBuYrmq-eHIZC7KnWHB1W8srPTBf0GQt3VJkQlcY"
    private val now = 1_792_230_606_000L // the token's expiry minus exactly 30 days

    @Test
    fun `reads the expiry embedded in a paired token`() {
        assertEquals(1794822606_000L, PairCredential.expiresAtMillis(paired))
    }

    @Test
    fun `the live server pairs for thirty days`() {
        assertEquals(30L, PairCredential.daysRemaining(paired, now))
    }

    @Test
    fun `a paired token is live until its expiry passes`() {
        assertFalse(PairCredential.isExpired(paired, now))
        assertFalse(PairCredential.isExpired(paired, 1794822606_000L - 1))
    }

    @Test
    fun `an expired paired token is reported expired`() {
        assertTrue(PairCredential.isExpired(paired, 1794822606_000L))
        assertTrue(PairCredential.isExpired(paired, 1794822606_000L + 1))
    }

    @Test
    fun `a plain password never looks expired`() {
        // Server passwords carry no expiry, so a saved password server must keep
        // working rather than being treated as stale.
        assertNull(PairCredential.expiresAtMillis("hunter2"))
        assertFalse(PairCredential.isExpired("hunter2", now))
        assertNull(PairCredential.daysRemaining("hunter2", now))
    }

    @Test
    fun `junk does not become an expiry`() {
        listOf("", "   ", "abc", "12", "999999999999999999999").forEach {
            assertNull("expected no expiry for '$it'", PairCredential.expiresAtMillis(it))
        }
    }
}
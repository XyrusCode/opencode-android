package com.xyrus.opencode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The cached-update-APK policy: which file to keep offering for install, and which to delete.
 *
 * Pure, so none of this needs a device, a PackageManager, or a real APK. What it
 * encodes is the answer to "did that install actually happen?", which nothing can
 * observe directly — so it is inferred from version codes instead.
 */
class UpdateCachePolicyTest {

    private fun apk(version: String, versionCode: Int) =
        CachedApk(File("/tmp/${apkFileName(version)}"), versionCode)

    // --- File naming ----------------------------------------------------------------------

    @Test
    fun `apk file name is version stamped`() {
        assertEquals("opencode-0.2.2.apk", apkFileName("0.2.2"))
        assertEquals("opencode-0.3.10.apk", apkFileName("0.3.10"))
    }

    // --- The reported bug: a finished download is offered again ---------------------------

    @Test
    fun `keeps a downloaded update that is newer than what is installed`() {
        val candidate = apk("0.2.2", 2)
        val (keep, drop) = partitionCachedApks(listOf(candidate), installedVersionCode = 1)
        assertEquals(candidate, keep)
        assertTrue(drop.isEmpty())
    }

    // --- Cleanup after a successful install -----------------------------------------------

    @Test
    fun `deletes a cached apk that has already been installed`() {
        // The user installed 0.2.2, so the cached copy is dead weight.
        val (keep, drop) = partitionCachedApks(listOf(apk("0.2.2", 2)), installedVersionCode = 2)
        assertNull(keep)
        assertEquals(1, drop.size)
    }

    @Test
    fun `deletes a cached apk older than what is installed`() {
        val (keep, drop) = partitionCachedApks(listOf(apk("0.2.1", 1)), installedVersionCode = 2)
        assertNull(keep)
        assertEquals(1, drop.size)
    }

    // --- A newer release overtaking an older cached apk -----------------------------------

    @Test
    fun `keeps the newest cached apk and drops the ones it supersedes`() {
        val old = apk("0.2.1", 1)
        val mid = apk("0.2.2", 2)
        val newest = apk("0.3.0", 3)
        val (keep, drop) =
            partitionCachedApks(listOf(old, mid, newest), installedVersionCode = 0)
        assertEquals(newest, keep)
        assertEquals(2, drop.size)
        assertTrue(drop.contains(old) && drop.contains(mid))
    }

    @Test
    fun `drops an already-installed apk while keeping a newer one`() {
        val installed = apk("0.2.1", 1)
        val pending = apk("0.2.2", 2)
        val (keep, drop) = partitionCachedApks(listOf(installed, pending), installedVersionCode = 0)
        assertEquals(pending, keep)
        assertEquals(listOf(installed), drop)
    }

    // --- Unusable files -------------------------------------------------------------------

    @Test
    fun `drops an apk whose manifest could not be parsed`() {
        val broken = apk("0.2.2", 0)
        val (keep, drop) = partitionCachedApks(listOf(broken), installedVersionCode = 1)
        assertNull(keep)
        assertEquals(1, drop.size)
    }

    @Test
    fun `keeps a good apk and drops a broken one alongside it`() {
        val broken = apk("garbage", 0)
        val good = apk("0.2.2", 2)
        val (keep, drop) = partitionCachedApks(listOf(broken, good), installedVersionCode = 1)
        assertEquals(good, keep)
        assertEquals(listOf(broken), drop)
    }

    // --- Empty cases ----------------------------------------------------------------------

    @Test
    fun `nothing cached means nothing to keep or drop`() {
        val (keep, drop) = partitionCachedApks(emptyList(), installedVersionCode = 1)
        assertNull(keep)
        assertTrue(drop.isEmpty())
    }

    @Test
    fun `every candidate is accounted for exactly once`() {
        val all = listOf(apk("0.2.0", 0), apk("0.2.1", 1), apk("0.2.2", 2), apk("junk", 0))
        val (keep, drop) = partitionCachedApks(all, installedVersionCode = 0)
        val accounted = listOfNotNull(keep) + drop
        // Nothing lost, nothing double-counted, nothing invented.
        assertEquals(all.size, accounted.size)
        assertEquals(all.size, accounted.distinct().size)
        assertTrue(accounted.containsAll(all))
    }
}

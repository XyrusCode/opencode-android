package com.xyrus.opencode.opencode

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The catalog cache exists to keep an 892 KB model list off the connect path.
 * These cover the ways it can go wrong, because every failure here degrades to
 * "the model picker is mysteriously empty" with nothing in the logs.
 */
class CatalogCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun catalogs(models: Int = 2, agents: Int = 1): CatalogCache.Catalogs {
        val providers = JSONArray()
        for (i in 0 until models) {
            providers.put(JSONObject().put("id", "provider$i").put("models", JSONArray()))
        }
        val agentList = JSONArray()
        for (i in 0 until agents) {
            agentList.put(JSONObject().put("name", "agent$i").put("description", ""))
        }
        return CatalogCache.Catalogs(providers, agentList)
    }

    @Test
    fun `round trips providers and agents`() {
        val file = File(tmp.newFolder(), "cache.json")
        CatalogCache.write(file, catalogs(models = 3, agents = 2))

        val read = CatalogCache.read(file)
        assertNotNull(read)
        assertEquals(3, read!!.providers.length())
        assertEquals(2, read.agents.length())
        assertEquals("provider0", read.providers.getJSONObject(0).getString("id"))
    }

    @Test
    fun `creates the parent directory`() {
        val file = File(tmp.newFolder(), "nested/deeper/cache.json")
        CatalogCache.write(file, catalogs())
        assertNotNull(CatalogCache.read(file))
    }

    @Test
    fun `missing file reads as null rather than throwing`() {
        assertNull(CatalogCache.read(File(tmp.newFolder(), "absent.json")))
    }

    @Test
    fun `null file is a no-op on both sides`() {
        CatalogCache.write(null, catalogs())
        assertNull(CatalogCache.read(null))
    }

    @Test
    fun `empty file reads as null`() {
        val file = File(tmp.newFolder(), "empty.json")
        file.writeText("")
        assertNull(CatalogCache.read(file))
    }

    @Test
    fun `truncated file does not throw`() {
        // This is the case the temp-file-and-rename exists to prevent, but a
        // cache written by an older build could still be half a file. It must
        // degrade to "no cache", never to a crash on launch.
        val file = File(tmp.newFolder(), "torn.json")
        file.writeText("""{"providers":[{"id":"prov""")
        assertNull(CatalogCache.read(file))
    }

    @Test
    fun `file without providers is rejected`() {
        val file = File(tmp.newFolder(), "wrong.json")
        file.writeText("""{"agents":[{"name":"build"}]}""")
        assertNull(CatalogCache.read(file))
    }

    @Test
    fun `agents are optional`() {
        val file = File(tmp.newFolder(), "provideronly.json")
        file.writeText("""{"providers":[{"id":"anthropic"}]}""")
        val read = CatalogCache.read(file)
        assertNotNull(read)
        assertEquals(0, read!!.agents.length())
    }

    @Test
    fun `a rewrite replaces the previous contents`() {
        val file = File(tmp.newFolder(), "cache.json")
        CatalogCache.write(file, catalogs(models = 5))
        CatalogCache.write(file, catalogs(models = 1))
        assertEquals(1, CatalogCache.read(file)!!.providers.length())
    }

    @Test
    fun `no temp file is left behind`() {
        val file = File(tmp.newFolder(), "cache.json")
        CatalogCache.write(file, catalogs())
        val leftovers = file.parentFile!!.listFiles()!!.filter { it.name.endsWith(".tmp") }
        assertEquals(emptyList<String>(), leftovers.map { it.name })
    }
}
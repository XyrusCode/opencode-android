package com.xyrus.opencode.ui

import com.xyrus.opencode.bridge.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectGroupingTest {

    private fun session(id: String, dir: String?, title: String = "t", updated: Long = 0L) =
        Session(id = id, title = title, directory = dir, model = null, updated = updated)

    @Test
    fun `groups sessions under their real directory basename`() {
        val groups = groupSessionsByProject(
            listOf(
                session("a", "C:\\work\\Appetite-Contact"),
                session("b", "C:\\work\\Appetite-Contact"),
                session("c", "C:\\work\\Video-Plucker"),
            )
        )

        assertEquals(2, groups.size)
        assertEquals(listOf("Appetite-Contact", "Video-Plucker"), groups.map { it.first.name })
        assertEquals(2, groups.first().second.size)
        assertEquals("C:\\work\\Appetite-Contact", groups.first().first.directory)
    }

    @Test
    fun `a busy project cannot push other projects out of view`() {
        // 20 sessions in one project used to dominate a capped flat list and hide
        // the rest of the machine; every project must survive grouping.
        val busy = (1..20).map { session("busy$it", "C:\\work\\Busy", updated = it.toLong()) }
        val quiet = (1..3).map { session("quiet$it", "C:\\work\\Quiet", updated = it.toLong()) }

        val groups = groupSessionsByProject(busy + quiet)

        assertEquals(2, groups.size)
        assertEquals(listOf("Busy", "Quiet"), groups.map { it.first.name })
    }

    @Test
    fun `each project previews only its most recent sessions`() {
        val many = (1..12).map { session("s$it", "C:\\work\\Repo", updated = it.toLong()) }

        val group = groupSessionsByProject(many).single()

        assertEquals(5, group.second.size)
        // Newest first: ids 12 down to 8.
        assertEquals(listOf("s12", "s11", "s10", "s9", "s8"), group.second.map { it.id })
    }

    @Test
    fun `search still finds a session older than the preview cap`() {
        val many = (1..12).map { session("s$it", "C:\\work\\Repo", title = "task $it", updated = it.toLong()) }

        val group = groupSessionsByProject(many, search = "task 2").single()

        assertEquals(listOf("task 2"), group.second.map { it.title })
    }

    @Test
    fun `search matches the project name`() {
        val groups = groupSessionsByProject(
            listOf(
                session("a", "C:\\work\\Appetite-Contact", title = "x"),
                session("b", "C:\\work\\Video-Plucker", title = "y"),
            ),
            search = "appetite",
        )

        assertEquals(listOf("Appetite-Contact"), groups.map { it.first.name })
    }

    @Test
    fun `a renamed session replaces its entry in the list`() {
        // Renames arrive broadcast, so the drawer must swap the row in place
        // rather than dropping it or showing a duplicate.
        val sessions = listOf(
            session("a", "C:\\work\\Repo", title = "before"),
            session("b", "C:\\work\\Repo", title = "other"),
        )
        val renamed = sessions[0].copy(title = "after")

        val merged = sessions.map { if (it.id == renamed.id) renamed else it }

        assertEquals(listOf("after", "other"), merged.map { it.title })
        assertEquals(2, groupSessionsByProject(merged).single().second.size)
    }

    @Test
    fun `a deleted session disappears from the list`() {
        val sessions = listOf(
            session("a", "C:\\work\\Repo", title = "keep"),
            session("b", "C:\\work\\Repo", title = "doomed"),
        )

        val remaining = groupSessionsByProject(sessions.filterNot { it.id == "b" })

        assertEquals(listOf("keep"), remaining.single().second.map { it.title })
    }

    @Test
    fun `temp scratch sessions never become projects`() {
        val groups = groupSessionsByProject(
            listOf(
                session("t1", "C:\\Users\\Xyrus\\AppData\\Local\\Temp\\tmp12ab"),
                session("t2", "C:\\Users\\Xyrus\\AppData\\Local\\temp\\tmp99"),
                session("real", "C:\\work\\Repo"),
            )
        )

        assertEquals(listOf("Repo"), groups.map { it.first.name })
    }
}
package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportEvent
import com.familyhub.data.export.ExportShoppingItem
import com.familyhub.data.export.ExportShoppingList
import com.familyhub.data.export.ExportTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCrossDeviceTest {

    private val merger = SyncMerger()

    // ═══════════════════════ TASKS ═══════════════════════

    @Test
    fun `admin creates task — child first sync gets it`() {
        val remote = listOf(task("t1", "TODO", "carol"))
        val merged = merger.mergeSnapshots(
            local = justTasks(),
            remote = withTasks(remote),
            currentUserId = "carol",
            isMemberWriteAllowed = false
        )
        assertEquals(1, merged.tasks.size)
        assertEquals("TODO", merged.tasks[0].status)
    }

    @Test
    fun `child completes — pending keeps WAITING`() {
        val local = listOf(task("t1", "WAITING_FOR_APPROVAL", "carol"))
        val remote = listOf(task("t1", "TODO", "carol"))
        val merged = merger.mergeSnapshots(
            local = withTasks(local),
            remote = withTasks(remote),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = true
        )
        assertEquals("WAITING_FOR_APPROVAL", merged.tasks[0].status)
    }

    @Test
    fun `admin rejected — child no pending picks remote TODO`() {
        val local = listOf(task("t1", "WAITING_FOR_APPROVAL", "carol"))
        val remote = listOf(task("t1", "TODO", "carol"))
        val merged = merger.mergeSnapshots(
            local = withTasks(local),
            remote = withTasks(remote),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertEquals("TODO", merged.tasks[0].status)
    }

    @Test
    fun `admin approved — child picks remote DONE`() {
        val local = listOf(task("t1", "WAITING_FOR_APPROVAL", "carol"))
        val remote = listOf(task("t1", "DONE", "carol"))
        val merged = merger.mergeSnapshots(
            local = withTasks(local),
            remote = withTasks(remote),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertEquals("DONE", merged.tasks[0].status)
    }

    @Test
    fun `admin deleted task — child no pending drops it`() {
        val local = listOf(task("t1", "TODO", "carol"))
        val merged = merger.mergeSnapshots(
            local = withTasks(local),
            remote = withTasks(emptyList()),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertTrue(merged.tasks.isEmpty())
    }

    @Test
    fun `child created task offline — pending keeps it`() {
        val local = listOf(task("new-t", "TODO", "carol", createdBy = "carol"))
        val merged = merger.mergeSnapshots(
            local = withTasks(local),
            remote = withTasks(emptyList()),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = true
        )
        assertEquals(1, merged.tasks.size)
    }

    @Test
    fun `admin edits title — child gets new title`() {
        val local = listOf(task("t1", "TODO", "carol", title = "Old"))
        val remote = listOf(task("t1", "TODO", "carol", title = "New"))
        val merged = merger.mergeSnapshots(
            local = withTasks(local),
            remote = withTasks(remote),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertEquals("New", merged.tasks[0].title)
    }

    // ═══════════════════════ SHOPPING ═══════════════════════

    @Test
    fun `admin creates item — child gets it`() {
        val remote = listOf(item("i1", "Milk"))
        val merged = merger.mergeSnapshots(
            local = justShopping(),
            remote = withShopping(remote),
            currentUserId = "carol",
            isMemberWriteAllowed = false
        )
        assertEquals(1, merged.shoppingItems.size)
    }

    @Test
    fun `admin deletes item — child no pending drops it`() {
        val local = listOf(item("i1", "Milk"))
        val merged = merger.mergeSnapshots(
            local = withShopping(local),
            remote = withShopping(emptyList()),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertTrue(merged.shoppingItems.isEmpty())
    }

    @Test
    fun `child creates item — pending keeps it`() {
        val local = listOf(item("i1", "Cookies", createdBy = "carol"))
        val merged = merger.mergeSnapshots(
            local = withShopping(local),
            remote = withShopping(emptyList()),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = true
        )
        assertEquals(1, merged.shoppingItems.size)
    }

    @Test
    fun `child toggles bought — pending keeps local value`() {
        val local = listOf(item("i1", "Milk", bought = true))
        val remote = listOf(item("i1", "Milk", bought = false))
        val merged = merger.mergeSnapshots(
            local = withShopping(local),
            remote = withShopping(remote),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = true
        )
        assertTrue(merged.shoppingItems[0].bought)
    }

    // ═══════════════════════ EVENTS ═══════════════════════

    @Test
    fun `admin creates event — child gets it`() {
        val remote = listOf(event("e1", "Party"))
        val merged = merger.mergeSnapshots(
            local = justEvents(),
            remote = withEvents(remote),
            currentUserId = "carol",
            isMemberWriteAllowed = false
        )
        assertEquals(1, merged.events.size)
    }

    @Test
    fun `admin deletes event — child no pending drops it`() {
        val local = listOf(event("e1", "Party"))
        val merged = merger.mergeSnapshots(
            local = withEvents(local),
            remote = withEvents(emptyList()),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertTrue(merged.events.isEmpty())
    }

    // ═══════════════════════ SHOPPING LISTS ═══════════════════════

    @Test
    fun `admin creates list — child gets it`() {
        val remote = listOf(listDoc("l1", "Groceries"))
        val merged = merger.mergeSnapshots(
            local = ExportData(shoppingLists = emptyList()),
            remote = ExportData(shoppingLists = remote),
            currentUserId = "carol",
            isMemberWriteAllowed = false
        )
        assertEquals(1, merged.shoppingLists.size)
    }

    @Test
    fun `admin deletes list — child no pending drops it`() {
        val local = listOf(listDoc("l1", "Groceries"))
        val merged = merger.mergeSnapshots(
            local = ExportData(shoppingLists = local),
            remote = ExportData(shoppingLists = emptyList()),
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertTrue(merged.shoppingLists.isEmpty())
    }

    // ═══════════════════════ EDGE CASES ═══════════════════════

    @Test
    fun `first sync — no remote, local kept`() {
        val local = withTasks(listOf(task("t1", "TODO", "carol")))
        val merged = merger.mergeSnapshots(
            local = local,
            remote = null,
            currentUserId = "alice",
            isMemberWriteAllowed = true
        )
        assertEquals(1, merged.tasks.size)
    }

    @Test
    fun `multi-collection deletion propagates independently`() {
        val local = ExportData(
            tasks = listOf(task("t1", "TODO", "carol")),
            shoppingItems = listOf(item("i1", "Milk"))
        )
        val remote = ExportData(
            tasks = emptyList(),
            shoppingItems = emptyList()
        )
        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )
        assertTrue(merged.tasks.isEmpty())
        assertTrue(merged.shoppingItems.isEmpty())
    }

    @Test
    fun `tasks not affected by pending shopping items`() {
        // Regression: global hasPendingChanges caused task merge to wrongly
        // keep local WAITING when shopping_items had pending changes.
        // This test proves per-collection isolation works.
        val local = ExportData(
            tasks = listOf(task("t1", "WAITING_FOR_APPROVAL", "carol")),
            shoppingItems = listOf(item("i1", "Milk", createdBy = "carol"))
        )
        val remote = ExportData(
            tasks = listOf(task("t1", "TODO", "carol")),  // admin rejected
            shoppingItems = emptyList()  // admin deleted
        )
        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            // Real SyncRepository call: the legacy aggregate flag is true because
            // another collection is dirty. The explicit map must isolate tasks.
            hasPendingChanges = true,
            // shoppingItems has pending (from child's edit), but tasks does NOT
            pendingByCollection = mapOf("shoppingItems" to true)
        )
        // Task merge must pick remote TODO (admin's rejection)
        assertEquals("Task must use remote TODO despite shoppingItems pending=true", "TODO", merged.tasks[0].status)
        // Shopping merge: pending=true keeps child's item even though not in remote
        assertEquals("Shopping item with pending must be kept", 1, merged.shoppingItems.size)
    }

    @Test
    fun `two reject cycles plus shopping converge on child TODO`() {
        var cloud = withTasks(listOf(task("t1", "TODO", "carol", completionCycle = 0L)))
        var child = cloud
        var admin = cloud

        // First completion and upload by the child.
        child = child.copy(
            tasks = listOf(task("t1", "WAITING_FOR_APPROVAL", "carol", completionCycle = 0L)),
        )
        cloud = merger.mergeSnapshots(
            local = child,
            remote = cloud,
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = true,
            pendingByCollection = mapOf("tasks" to true),
        )
        child = cloud

        // Admin downloads the request, rejects it, and uploads cycle 1.
        admin = merger.mergeSnapshots(
            local = admin,
            remote = cloud,
            currentUserId = "alice",
            isMemberWriteAllowed = true,
            pendingByCollection = emptyMap(),
        )
        admin = admin.copy(
            tasks = listOf(task("t1", "TODO", "carol", completionCycle = 1L)),
        )
        cloud = merger.mergeSnapshots(
            local = admin,
            remote = cloud,
            currentUserId = "alice",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            pendingByCollection = mapOf("tasks" to true),
        )
        admin = cloud

        // Child downloads the first rejection and completes again in cycle 1.
        child = merger.mergeSnapshots(
            local = child,
            remote = cloud,
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            pendingByCollection = emptyMap(),
        )
        assertEquals("TODO", child.tasks.single().status)
        assertEquals(1L, child.tasks.single().completionCycle)
        child = child.copy(
            tasks = listOf(task("t1", "WAITING_FOR_APPROVAL", "carol", completionCycle = 1L)),
        )
        cloud = merger.mergeSnapshots(
            local = child,
            remote = cloud,
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = true,
            pendingByCollection = mapOf("tasks" to true),
        )
        child = cloud

        // Admin receives the second request, rejects it (cycle 2), and adds a purchase.
        admin = merger.mergeSnapshots(
            local = admin,
            remote = cloud,
            currentUserId = "alice",
            isMemberWriteAllowed = true,
            pendingByCollection = emptyMap(),
        )
        admin = admin.copy(
            tasks = listOf(task("t1", "TODO", "carol", completionCycle = 2L)),
            shoppingItems = listOf(item("i1", "Milk")),
        )
        cloud = merger.mergeSnapshots(
            local = admin,
            remote = cloud,
            currentUserId = "alice",
            isMemberWriteAllowed = true,
            hasPendingChanges = true,
            pendingByCollection = mapOf("tasks" to true, "shoppingItems" to true),
        )

        // Simulate the observed edge: a stale aggregate flag remains while only
        // shopping is marked explicitly. The newer task cycle must still win.
        child = merger.mergeSnapshots(
            local = child,
            remote = cloud,
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = true,
            pendingByCollection = mapOf("shoppingItems" to true),
        )
        assertEquals("TODO", child.tasks.single().status)
        assertEquals(2L, child.tasks.single().completionCycle)
        assertEquals("Milk", child.shoppingItems.single().title)

        val repeated = merger.mergeSnapshots(
            local = child,
            remote = cloud,
            currentUserId = "carol",
            isMemberWriteAllowed = false,
            hasPendingChanges = false,
            pendingByCollection = emptyMap(),
        )
        assertEquals(child, repeated)
    }

    private fun justTasks() = ExportData(tasks = emptyList())
    private fun justShopping() = ExportData(shoppingItems = emptyList())
    private fun justEvents() = ExportData(events = emptyList())
    private fun withTasks(t: List<ExportTask>) = ExportData(tasks = t)
    private fun withShopping(s: List<ExportShoppingItem>) = ExportData(shoppingItems = s)
    private fun withEvents(e: List<ExportEvent>) = ExportData(events = e)

    private fun task(
        id: String, status: String, assignedTo: String,
        createdBy: String = "alice", title: String = "Task $id",
        completionCycle: Long = 0L,
    ) = ExportTask(
        id = id, title = title, status = status,
        createdByUserId = createdBy, assignedToUserId = assignedTo,
        completionCycle = completionCycle, dueDate = null, dueDateHasTime = false,
        reminderMode = "NONE", reminderMinutesOfDay = null,
        repeatRule = "NONE", requiresApproval = (status == "WAITING_FOR_APPROVAL"),
        priority = "MEDIUM", categoryId = "cat1", description = "",
        orderIndex = 0, attachments = emptyList(), comments = emptyList(),
    )

    private fun item(
        id: String, title: String,
        listId: String = "list1", createdBy: String = "alice", bought: Boolean = false,
    ) = ExportShoppingItem(
        id = id, title = title, listId = listId,
        createdByUserId = createdBy, bought = bought,
        createdAt = 1L, note = null,
    )

    private fun event(
        id: String, title: String,
        createdBy: String = "alice",
    ) = ExportEvent(
        id = id, title = title,
        startDateTime = 1L, createdByUserId = createdBy,
        durationMinutes = 60, recurrence = "", reminderMinutesBefore = null,
    )

    private fun listDoc(id: String, title: String) = ExportShoppingList(
        id = id, title = title, createdAt = 1L,
    )
}

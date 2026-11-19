package com.familyhub.domain.utils

import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskTransitionForbiddenException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class RepeatSuccessorIdsTest {

    @Test
    fun deterministicId_usesUtf8AndIsStable() {
        val id1 = RepeatSuccessorIds.deterministicId("task-a")
        val id2 = RepeatSuccessorIds.deterministicId("task-a")
        val expected = UUID.nameUUIDFromBytes(
            "repeat-successor:task-a".toByteArray(StandardCharsets.UTF_8)
        ).toString()
        assertEquals(expected, id1)
        assertEquals(id1, id2)
    }

    @Test
    fun deterministicId_differsPerSourceTask() {
        val a = RepeatSuccessorIds.deterministicId("task-a")
        val b = RepeatSuccessorIds.deterministicId("task-b")
        assert(a != b)
    }
}

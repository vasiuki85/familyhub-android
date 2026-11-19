package com.familyhub.domain.utils

import java.nio.charset.StandardCharsets
import java.util.UUID

object CompletionCycleIds {
    private const val VERSION = 1
    private const val PREFIX = "completion-cycle:"

    fun deterministicId(taskId: String, completionCycle: Long): String {
        CompletionCycleValidation.requireNonNegativeCompletionCycle(completionCycle)
        val input = buildString {
            append(VERSION)
            append(':')
            append(taskId.length)
            append(':')
            append(taskId)
            append(':')
            append(completionCycle)
        }
        return UUID.nameUUIDFromBytes("$PREFIX$input".toByteArray(StandardCharsets.UTF_8)).toString()
    }
}

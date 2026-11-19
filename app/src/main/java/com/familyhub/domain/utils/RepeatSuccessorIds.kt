package com.familyhub.domain.utils

import java.nio.charset.StandardCharsets
import java.util.UUID

object RepeatSuccessorIds {
    private const val PREFIX = "repeat-successor:"

    fun deterministicId(sourceTaskId: String): String =
        UUID.nameUUIDFromBytes("$PREFIX$sourceTaskId".toByteArray(StandardCharsets.UTF_8)).toString()
}

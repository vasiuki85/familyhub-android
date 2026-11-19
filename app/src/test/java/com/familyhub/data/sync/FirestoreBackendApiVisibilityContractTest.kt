package com.familyhub.data.sync

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import com.google.firebase.firestore.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreBackendApiVisibilityContractTest {

    @Test
    fun goalChildDownloads_usePerGoalIdQueriesNotUnrestrictedCollectionGet() {
        val source = readFirestoreBackendApiSource()

        val stagesBlock = extractFunctionBlock(source, "downloadVisibleFamilyGoalStages")
        val stepsBlock = extractFunctionBlock(source, "downloadVisibleFamilyGoalSteps")

        assertTrue(stagesBlock.contains("whereEqualTo(\"goalId\", goalId)"))
        assertTrue(stepsBlock.contains("whereEqualTo(\"goalId\", goalId)"))
        assertFalse(stagesBlock.contains("collection(FAMILY_GOAL_STAGES_COLLECTION)\n        .get("))
        assertFalse(stepsBlock.contains("collection(FAMILY_GOAL_STEPS_COLLECTION)\n        .get("))
    }

    @Test
    fun downloadSnapshot_usesServerSourceOnly() {
        assertEquals(Source.SERVER, FirestoreBackendApi.syncDownloadFirestoreSource())
    }

    @Test
    fun downloadSnapshot_doesNotFallbackToCache() {
        val source = readFirestoreBackendApiSource()
        val downloadBlock = extractFunctionBlock(source, "downloadSnapshot")

        assertTrue(downloadBlock.contains("syncDownloadFirestoreSource()"))
        assertFalse(downloadBlock.contains("Source.CACHE"))
    }

    private fun readFirestoreBackendApiSource(): String {
        val path = Paths.get("src/main/java/com/familyhub/data/sync/FirestoreBackendApi.kt")
        val resolved = if (Files.exists(path)) {
            path
        } else {
            Path.of("app/src/main/java/com/familyhub/data/sync/FirestoreBackendApi.kt")
        }
        return String(Files.readAllBytes(resolved), StandardCharsets.UTF_8)
    }

    private fun extractFunctionBlock(source: String, functionName: String): String {
        val start = source.indexOf("fun $functionName")
        require(start >= 0) { "Function $functionName not found" }
        val openBrace = source.indexOf('{', start)
        var depth = 0
        for (index in openBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(start, index + 1)
                    }
                }
            }
        }
        error("Unterminated function block for $functionName")
    }
}

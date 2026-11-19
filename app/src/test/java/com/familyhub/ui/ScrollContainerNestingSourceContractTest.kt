package com.familyhub.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollContainerNestingSourceContractTest {

    private val lazyScrollablePattern = Regex("""\b(LazyColumn|LazyVerticalGrid)\s*\(""")
    private val verticalScrollPattern = Regex("""\.verticalScroll\s*\(""")

    @Test
    fun uiPackage_hasNoLazyScrollableNestedInsideVerticalScroll() {
        val uiRoot = File("src/main/java/com/familyhub/ui")
        val violations = uiRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                findViolations(file.readText(Charsets.UTF_8))
                    .map { violation ->
                        "${file.relativeTo(uiRoot).path.replace('\\', '/')}: $violation"
                    }
            }
            .toList()

        assertTrue(
            "Lazy scrollable nested inside verticalScroll: " +
                violations.joinToString(),
            violations.isEmpty(),
        )
    }

    private fun findViolations(source: String): List<String> {
        return extractComposableBodies(source).flatMap(::findNestingInComposableBody)
    }

    private fun extractComposableBodies(source: String): List<String> {
        val bodies = mutableListOf<String>()
        val composableFunPattern = Regex("""@Composable\s*\n\s*fun\s+\w+""")
        var searchStart = 0
        while (true) {
            val match = composableFunPattern.find(source, searchStart) ?: break
            val braceStart = source.indexOf('{', match.range.last)
            if (braceStart == -1) break
            val bodyEnd = findMatchingBrace(source, braceStart)
            if (bodyEnd == -1) break
            bodies += source.substring(braceStart, bodyEnd + 1)
            searchStart = bodyEnd + 1
        }
        return bodies
    }

    private fun findNestingInComposableBody(body: String): List<String> {
        if (!verticalScrollPattern.containsMatchIn(body)) return emptyList()

        val violations = mutableListOf<String>()
        var index = 0
        while (index < body.length) {
            val scrollMatch = verticalScrollPattern.find(body, index) ?: break
            val contentBlockStart = findVerticalScrollContentStart(body, scrollMatch.range.first)
            if (contentBlockStart == -1) {
                index = scrollMatch.range.last + 1
                continue
            }
            val contentBlockEnd = findMatchingBrace(body, contentBlockStart)
            if (contentBlockEnd == -1) {
                index = scrollMatch.range.last + 1
                continue
            }
            val scrollableContent = body.substring(contentBlockStart, contentBlockEnd + 1)
            lazyScrollablePattern.findAll(scrollableContent).forEach { lazyMatch ->
                violations += "lazy ${lazyMatch.groupValues[1]} inside verticalScroll block"
            }
            index = contentBlockEnd + 1
        }
        return violations
    }

    private fun findVerticalScrollContentStart(body: String, verticalScrollIndex: Int): Int {
        val containerPattern = Regex("""\b(Column|Box|Row)\s*\(""")
        val containerMatch = containerPattern
            .findAll(body, 0)
            .lastOrNull { it.range.last < verticalScrollIndex }
            ?: return -1

        val openParen = containerMatch.range.last
        var depth = 0
        for (i in openParen until body.length) {
            when (body[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        var next = i + 1
                        while (next < body.length && body[next].isWhitespace()) next++
                        if (next < body.length && body[next] == '{') return next
                    }
                }
            }
        }
        return -1
    }

    private fun findMatchingBrace(source: String, openIndex: Int): Int {
        var depth = 0
        var inString = false
        var inChar = false
        var escaped = false
        for (i in openIndex until source.length) {
            val char = source[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }
            if (inChar) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '\'') {
                    inChar = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '\'' -> inChar = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}

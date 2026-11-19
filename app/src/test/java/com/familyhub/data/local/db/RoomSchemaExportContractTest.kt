package com.familyhub.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM source/file contract: Room schema export must stay enabled and aligned
 * with FamilyHubDatabase.version.
 */
class RoomSchemaExportContractTest {

    private val repoRoot: File = File(".").canonicalFile.let { dir ->
        generateSequence(dir) { it.parentFile }
            .first { File(it, "app/src/main/java/com/familyhub/data/local/db/FamilyHubDatabase.kt").exists() }
    }

    private val databaseSource = File(
        repoRoot,
        "app/src/main/java/com/familyhub/data/local/db/FamilyHubDatabase.kt"
    )
    private val appGradle = File(repoRoot, "app/build.gradle.kts")
    private val schemaDir = File(
        repoRoot,
        "app/schemas/com.familyhub.data.local.db.FamilyHubDatabase"
    )

    @Test
    fun exportSchemaMustRemainTrue() {
        val text = databaseSource.readText(Charsets.UTF_8)
        assertTrue(
            "FamilyHubDatabase must keep exportSchema = true",
            Regex("""exportSchema\s*=\s*true""").containsMatchIn(text)
        )
        assertFalse(
            "FamilyHubDatabase must not set exportSchema = false",
            Regex("""exportSchema\s*=\s*false""").containsMatchIn(text)
        )
    }

    @Test
    fun roomSchemaLocationMustBeConfigured() {
        val text = appGradle.readText(Charsets.UTF_8)
        assertTrue(
            "app/build.gradle.kts must configure room.schemaLocation",
            text.contains("room.schemaLocation")
        )
        assertTrue(
            "room.schemaLocation must point at project schemas dir",
            text.contains("projectDir/schemas")
        )
    }

    @Test
    fun currentDatabaseVersionSchemaJsonMustExist() {
        val version = readDatabaseVersion()
        val schemaFile = File(schemaDir, "$version.json")
        assertTrue(
            "Missing schema JSON for current database version $version at ${schemaFile.path}",
            schemaFile.isFile
        )
        val json = schemaFile.readText(Charsets.UTF_8)
        assertTrue(json.contains("\"version\": $version") || json.contains("\"version\":$version"))
    }

    @Test
    fun schemaDirectoryHasNoDuplicateVersions() {
        assertTrue("Schema directory missing: ${schemaDir.path}", schemaDir.isDirectory)
        val files = schemaDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }!!.toList()
        val versions = files.map { it.name.removeSuffix(".json") }
        assertEquals(
            "Duplicate schema version files detected: $versions",
            versions.size,
            versions.toSet().size
        )
    }

    @Test
    fun maxSchemaVersionEqualsDatabaseVersion() {
        val dbVersion = readDatabaseVersion()
        val schemaVersions = schemaDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }!!
            .map { it.name.removeSuffix(".json").toInt() }
        assertTrue("No schema JSON files found in ${schemaDir.path}", schemaVersions.isNotEmpty())
        assertEquals(
            "Max schema JSON version must equal FamilyHubDatabase.version",
            dbVersion,
            schemaVersions.maxOrNull()
        )
    }

    private fun readDatabaseVersion(): Int {
        val text = databaseSource.readText(Charsets.UTF_8)
        val match = Regex("""version\s*=\s*(\d+)""").find(text)
            ?: error("Could not parse FamilyHubDatabase.version")
        return match.groupValues[1].toInt()
    }
}

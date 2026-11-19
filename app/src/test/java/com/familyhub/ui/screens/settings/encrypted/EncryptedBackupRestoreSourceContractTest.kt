package com.familyhub.ui.screens.settings.encrypted

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source and encoding contracts for encrypted backup/restore B1–B3 scope.
 */
class EncryptedBackupRestoreSourceContractTest {

    private val repoRoot: File = File(".").canonicalFile.let { dir ->
        generateSequence(dir) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
    }

    private val scopeDirectories = listOf(
        "app/src/main/java/com/familyhub/data/backup",
        "app/src/main/java/com/familyhub/data/restore",
        "app/src/main/java/com/familyhub/ui/screens/settings/encrypted",
        "app/src/test/java/com/familyhub/data/backup",
        "app/src/test/java/com/familyhub/data/restore",
        "app/src/test/java/com/familyhub/ui/screens/settings/encrypted",
        "app/src/androidTest/java/com/familyhub/data/backup",
        "app/src/androidTest/java/com/familyhub/data/restore",
        "app/src/androidTest/java/com/familyhub/ui/screens/settings/encrypted"
    )

    private val explicitScopeFiles = listOf(
        "app/src/main/java/com/familyhub/data/repository/AuthenticatedMemberBootstrapPolicy.kt",
        "app/src/main/java/com/familyhub/data/repository/AuthenticatedMemberBootstrapCoordinator.kt",
        "app/src/main/java/com/familyhub/domain/usecase/SignOutUseCase.kt",
        "app/src/main/java/com/familyhub/di/BackupModule.kt",
        "app/src/test/java/com/familyhub/data/repository/AuthenticatedMemberBootstrapPolicyTest.kt",
        "app/src/test/java/com/familyhub/data/repository/AuthenticatedMemberBootstrapCoordinatorTest.kt",
        "app/src/test/java/com/familyhub/domain/usecase/SignOutUseCaseTest.kt",
        ".github/workflows/ci.yml",
        "docs/encrypted-backup-fhb.md",
        "docs/encrypted-restore-fhb.md",
        "docs/encrypted-backup-restore-ui.md"
    )

    private val textExtensions = setOf(".kt", ".md", ".yml", ".yaml")

    @Test
    fun encryptedComposePackage_doesNotCallFirebaseAuthGetInstance() {
        val composeDir = Path.of(repoRoot.path, "app/src/main/java/com/familyhub/ui/screens/settings/encrypted")
        val offenders = Files.walk(composeDir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { path -> path to String(Files.readAllBytes(path), StandardCharsets.UTF_8) }
            .filter { (_, text) -> text.contains("FirebaseAuth.getInstance()") }
            .map { it.first.toString().removePrefix(repoRoot.path + File.separator) }
            .toList()

        assertTrue(
            "encrypted Compose must not call FirebaseAuth.getInstance(): $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun b1ToB3ScopeFiles_useUtf8LfWithoutCarriageReturn() {
        val scopePaths = buildList {
            scopeDirectories.forEach { relativeDir ->
                val dir = Path.of(repoRoot.path, relativeDir)
                if (!Files.isDirectory(dir)) return@forEach
                Files.walk(dir)
                    .filter { Files.isRegularFile(it) }
                    .filter { path -> textExtensions.any { path.toString().endsWith(it) } }
                    .forEach { add(it) }
            }
            explicitScopeFiles.forEach { relative ->
                add(Path.of(repoRoot.path, relative))
            }
        }
            .distinct()
            .sorted()

        assertTrue("B1–B3 scope must include files to scan", scopePaths.isNotEmpty())

        scopePaths.forEach { path ->
            val relative = path.toString().removePrefix(repoRoot.path + File.separator)
            assertTrue("$relative must exist", Files.isRegularFile(path))
            val bytes = Files.readAllBytes(path)
            assertFalse(
                "$relative must not start with UTF-8 BOM",
                bytes.size >= 3 &&
                    bytes[0] == 0xEF.toByte() &&
                    bytes[1] == 0xBB.toByte() &&
                    bytes[2] == 0xBF.toByte()
            )
            assertFalse(
                "$relative must not contain carriage return (CRLF)",
                bytes.contains(0x0D)
            )
        }
    }

    @Test
    fun bootstrapCoordinator_readsFirebaseUserInsideExclusiveLock() {
        val source = String(
            Files.readAllBytes(
                Path.of(
                    repoRoot.path,
                    "app/src/main/java/com/familyhub/data/repository/AuthenticatedMemberBootstrapCoordinator.kt"
                )
            ),
            StandardCharsets.UTF_8
        )
        val body = source.substringAfter("fun ensureAuthenticatedMemberExists")
        assertFalse(
            body.substringBefore("syncExclusiveLock.withLock").contains("firebaseAuth.currentUser")
        )
        assertTrue(
            body.substringAfter("syncExclusiveLock.withLock").contains("firebaseAuth.currentUser")
        )
    }
}

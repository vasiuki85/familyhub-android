package com.familyhub.ci

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source contract for [.github/workflows/ci.yml].
 * Does not replace a real GitHub Actions run.
 */
class CiWorkflowContractTest {

    private val repoRoot: File = File(".").canonicalFile.let { dir ->
        generateSequence(dir) { it.parentFile }
            .first { File(it, ".github/workflows/ci.yml").exists() }
    }

    private val workflowText: String by lazy {
        File(repoRoot, ".github/workflows/ci.yml").readText(StandardCharsets.UTF_8)
    }

    @Test
    fun ciWorkflowFileExists() {
        assertTrue(
            "Missing CI workflow at .github/workflows/ci.yml",
            File(repoRoot, ".github/workflows/ci.yml").isFile
        )
    }

    @Test
    fun ciWorkflowDefinesExpectedJobs() {
        val text = workflowText
        assertTrue(text.contains("android-unit:"))
        assertTrue(text.contains("firebase-config:"))
        assertTrue(text.contains("android-instrumented:"))
        assertTrue(text.contains("firestore-rules:"))

        val normalized = text.replace("\r\n", "\n")
        val jobsBlock = normalized.substringAfter("jobs:\n")
        val jobNames = jobsBlock.lineSequence()
            .map { it.trimEnd() }
            .filter { it.matches(Regex("""^  [a-z0-9-]+:\s*$""")) }
            .map { it.trim().removeSuffix(":") }
            .toSet()
        assertEquals(
            setOf("android-unit", "firebase-config", "android-instrumented", "firestore-rules"),
            jobNames
        )
    }

    @Test
    fun ciWorkflowGatesInstrumentedJobOnFirebaseConfigSecret() {
        val text = workflowText
        // Job-level `if` cannot read secrets directly; a detector job exposes
        // secret availability as an output, and the emulator job gates on it.
        assertTrue(text.contains("needs: firebase-config"))
        assertTrue(text.contains("if: needs.firebase-config.outputs.available == 'true'"))
        assertTrue(text.contains("secrets.GOOGLE_SERVICES_JSON"))
    }

    @Test
    fun ciWorkflowRunsRequiredGradleAndFirestoreCommands() {
        val text = workflowText
        assertTrue(text.contains(":app:testDebugUnitTest"))
        assertTrue(text.contains(":app:assembleDebug"))
        assertTrue(text.contains(":app:compileDebugAndroidTestKotlin"))
        assertTrue(text.contains("connectedDebugAndroidTest"))
        assertTrue(text.contains("npm ci --prefix firestore-rules-tests"))
        assertTrue(text.contains("npm test --prefix firestore-rules-tests"))
    }

    @Test
    fun ciWorkflowRunsVerifierSelfTestsInAndroidUnit() {
        val unitBlock = workflowText.substringAfter("android-unit:")
            .substringBefore("android-instrumented:")
        assertTrue(unitBlock.contains("verify-android-test-results.sh --self-test"))
        assertTrue(unitBlock.contains("verify-firestore-test-results.sh --self-test"))
        assertTrue(unitBlock.contains("verify-native-page-size.sh --self-test"))
    }

    @Test
    fun ciWorkflowGuardsNativePageSizeAfterAssembleDebug() {
        val unitBlock = workflowText.substringAfter("android-unit:")
            .substringBefore("android-instrumented:")
        val assembleIndex = unitBlock.indexOf(":app:assembleDebug")
        val pageSizeIndex = unitBlock.indexOf("verify-native-page-size.sh app/build/outputs/apk/debug/app-debug.apk")
        assertTrue("assembleDebug missing", assembleIndex >= 0)
        assertTrue("native page-size guard missing", pageSizeIndex >= 0)
        assertTrue(assembleIndex < pageSizeIndex)
    }

    @Test
    fun ciWorkflowEnablesKvmBeforeEmulatorRunner() {
        val instrumentedBlock = workflowText.substringAfter("android-instrumented:")
            .substringBefore("firestore-rules:")
        val kvmIndex = instrumentedBlock.indexOf("name: Enable KVM")
        val emulatorIndex = instrumentedBlock.indexOf("android-emulator-runner@")
        assertTrue("Enable KVM step missing", kvmIndex >= 0)
        assertTrue("android-emulator-runner step missing", emulatorIndex >= 0)
        assertTrue(kvmIndex < emulatorIndex)
        assertTrue(instrumentedBlock.contains("99-kvm4all.rules"))
        assertTrue(instrumentedBlock.contains("udevadm control --reload-rules"))
        assertTrue(instrumentedBlock.contains("udevadm trigger --name-match=kvm"))
    }

    @Test
    fun ciWorkflowDoesNotUseSetupJavaGradleCache() {
        assertFalse(workflowText.contains("cache: gradle"))
    }

    @Test
    fun ciWorkflowGuardsRoomSchemaDriftIncludingUntracked() {
        val text = workflowText
        assertTrue(text.contains("guard-room-schema-drift.sh"))
        assertFalse(text.contains("git diff --exit-code -- app/schemas"))

        val guardScript = File(repoRoot, ".github/scripts/guard-room-schema-drift.sh")
            .readText(StandardCharsets.UTF_8)
        assertTrue(guardScript.contains("git status --porcelain=v1 --untracked-files=all -- app/schemas"))
        assertTrue(guardScript.contains("git ls-files --error-unmatch"))
        assertTrue(guardScript.contains("FamilyHubDatabase.kt"))
    }

    @Test
    fun ciWorkflowUsesGradleSetupWrapperValidationOnly() {
        val text = workflowText
        assertTrue(text.contains("validate-wrappers: true"))
        assertFalse(text.contains("wrapper-validation-action"))
    }

    @Test
    fun ciWorkflowUsesReadOnlyPermissionsOnly() {
        val text = workflowText
        assertTrue(text.contains("permissions:"))
        assertTrue(text.contains("contents: read"))
        assertFalse(text.contains("actions: write"))
        assertFalse(text.contains("permissions:\n      contents: write"))
    }

    @Test
    fun ciWorkflowCapturesLogcatBeforeVerifierExit() {
        val text = workflowText
        assertTrue(text.contains("gradle_exit=0"))
        assertTrue(text.contains("adb logcat -d > logcat.txt"))
        assertTrue(text.contains("verify_exit=0"))
        assertTrue(text.contains("verify-android-test-results.sh"))
    }

    @Test
    fun ciWorkflowUsesDemoFamilyhubOnlyForFirestore() {
        val text = workflowText
        assertTrue(text.contains("demo-familyhub"))
        assertFalse(text.contains("FIREBASE_TOKEN"))
        assertFalse(text.contains("GOOGLE_APPLICATION_CREDENTIALS"))
    }

    @Test
    fun ciWorkflowForbidsDeployPullRequestTargetAndContinueOnError() {
        val text = workflowText
        assertFalse(text.contains("pull_request_target"))
        assertFalse(text.contains("continue-on-error:"))
        assertFalse(Regex("""\bdeploy\b""", RegexOption.IGNORE_CASE).containsMatchIn(text))
        assertFalse(text.contains("firebase deploy"))
    }

    @Test
    fun ciWorkflowPinsThirdPartyActionsToFullCommitSha() {
        val usesLines = workflowText.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("uses:") }
            .map { it.removePrefix("uses:").trim().substringBefore('#').trim() }
            .filter { it.contains('/') }
            .toList()

        assertTrue("Expected pinned third-party actions", usesLines.isNotEmpty())
        usesLines.forEach { ref ->
            val sha = ref.substringAfter('@')
            assertTrue(
                "Action reference must be pinned to full commit SHA: $ref",
                sha.matches(Regex("""[0-9a-f]{40}"""))
            )
        }
    }

    @Test
    fun verifyScriptsExistIncludingPythonVerifiers() {
        assertTrue(
            Files.exists(Paths.get(repoRoot.path, ".github/scripts/verify-android-test-results.sh"))
        )
        assertTrue(
            Files.exists(Paths.get(repoRoot.path, ".github/scripts/verify-firestore-test-results.sh"))
        )
        assertTrue(
            Files.exists(Paths.get(repoRoot.path, ".github/scripts/verify_android_test_results.py"))
        )
        assertTrue(
            Files.exists(Paths.get(repoRoot.path, ".github/scripts/verify_firestore_test_results.py"))
        )
        assertTrue(
            Files.exists(Paths.get(repoRoot.path, ".github/scripts/guard-room-schema-drift.sh"))
        )
        assertTrue(
            Files.exists(Paths.get(repoRoot.path, ".github/scripts/verify-native-page-size.sh"))
        )
        assertTrue(
            Files.exists(Paths.get(repoRoot.path, ".github/scripts/verify_native_page_size.py"))
        )
    }

    @Test
    fun androidVerifierRequiredClassesIncludesMenuMutationCoordinator() {
        val script = File(repoRoot, ".github/scripts/verify_android_test_results.py")
            .readText(StandardCharsets.UTF_8)
        val requiredBlock = script.substringAfter("REQUIRED_CLASSES = (")
            .substringBefore(")")
        assertTrue(
            "MenuMutationCoordinatorInstrumentedTest must be in REQUIRED_CLASSES",
            requiredBlock.contains("\"MenuMutationCoordinatorInstrumentedTest\""),
        )
    }

    @Test
    fun ciDocsDescribeUntrackedSchemaGuardAndVerifierSemantics() {
        val doc = Path.of(repoRoot.path, "docs/ci.md")
        assertTrue("Missing docs/ci.md", Files.exists(doc))
        val text = String(Files.readAllBytes(doc), StandardCharsets.UTF_8)
        assertTrue(text.contains("android-unit"))
        assertTrue(text.contains("android-instrumented"))
        assertTrue(text.contains("firestore-rules"))
        assertTrue(text.contains("untracked-files=all"))
        assertTrue(text.contains("git ls-files --error-unmatch"))
        assertFalse(text.contains("actions: write"))
        assertTrue(text.contains("validate-wrappers: true"))
        assertTrue(text.contains("ElementTree"))
        assertTrue(text.contains("tests == pass == 76"))
        assertTrue(text.contains("Node test summary block"))
        assertTrue(text.contains("verify-android-test-results.sh --self-test"))
        assertTrue(text.contains("verify-native-page-size.sh"))
        assertTrue(text.contains("zipalign -c -P 16"))
        assertTrue(text.contains("p_align >= 0x4000"))
        assertTrue(text.contains("AccountingMutationCoordinatorInstrumentedTest"))
        assertTrue(text.contains("MenuMutationCoordinatorInstrumentedTest"))
        assertTrue(text.contains("Argon2KtEncryptedBackupInstrumentedTest"))
        assertTrue(text.contains("EncryptedRestoreRoomInstrumentedTest"))
        assertTrue(text.contains("EncryptedBackupRestoreSectionTest"))
        assertTrue(text.contains("enables KVM"))
        assertTrue(text.contains("does not use `cache: gradle`"))
    }
}

package com.familyhub.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleAccessibilitySourceContractTest {

    /**
     * Files that may contain bare Checkbox( / Switch( outside FhToggleRow.
     * Each entry must stay short and justified — not a rubber-stamp allow-list.
     */
    private val allowedBareToggleFiles = setOf(
        // Shared toggle row components define the canonical Checkbox/Switch usage.
        "components/FhToggleRow.kt",
        // Toggleable row: entire row is Role.Checkbox with onCheckedChange=null on control.
        "screens/home/HomeTaskItemCard.kt",
        // Toggleable row: PriorityTaskRow uses the same merged-row pattern.
        "screens/home/HomeScreen.kt",
        // Toggleable rows for destructive restore confirmations.
        "screens/settings/encrypted/EncryptedBackupRestoreSection.kt",
        // Branch 2: row contains edit/delete/history IconButtons; checkbox labeled per task.
        "components/TaskItemCard.kt",
        // Branch 2: row has separate more-actions menu; checkbox labeled per shopping item.
        "components/ShoppingItemRow.kt",
        // Branch 2: row click toggles selection; checkbox labeled per ingredient.
        "screens/recipes/RecipeBookScreen.kt",
        // Biometric row: toggleable Row + Switch(onCheckedChange=null) beside a two-line label.
        "screens/settings/SettingsScreen.kt",
        // Rotation enable: toggleable Row + Switch(onCheckedChange=null) before label text.
        "screens/tasks/TaskEditorDialog.kt",
    )

    private val bareTogglePattern = Regex("""\b(Checkbox|Switch)\s*\(""")

    @Test
    fun uiPackage_hasNoUnapprovedBareCheckboxOrSwitchCalls() {
        val uiRoot = File("src/main/java/com/familyhub/ui")
        val violations = uiRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kotlin" }
            .mapNotNull { file ->
                val relativePath = file.relativeTo(uiRoot).path.replace('\\', '/')
                if (allowedBareToggleFiles.contains(relativePath)) {
                    null
                } else {
                    val matches = bareTogglePattern.findAll(file.readText(Charsets.UTF_8)).toList()
                    if (matches.isEmpty()) null else relativePath to matches.size
                }
            }
            .toList()

        assertTrue(
            "Unapproved bare Checkbox( / Switch( calls found: " +
                violations.joinToString { (path, count) -> "$path ($count)" },
            violations.isEmpty(),
        )
    }
}

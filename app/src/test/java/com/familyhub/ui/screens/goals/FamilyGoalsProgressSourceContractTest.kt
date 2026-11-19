package com.familyhub.ui.screens.goals

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyGoalsProgressSourceContractTest {

    @Test
    fun goalWithNoSteps_doesNotRenderMisleadingFullProgressTrack() {
        val source = File(
            "src/main/java/com/familyhub/ui/screens/goals/GoalListCard.kt"
        ).readText(Charsets.UTF_8)
        val card = source.substring(
            source.indexOf("fun GoalListCard("),
            source.indexOf("internal fun GoalOverflowMenu("),
        )

        assertTrue(card.contains("if (item.totalSteps > 0)"))
        assertTrue(card.contains("FhProgressBar("))
    }
}

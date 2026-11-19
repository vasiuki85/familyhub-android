package com.familyhub.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeListClearanceSourceContractTest {

    @Test
    fun homeScreen_reservesBottomClearanceForComingUpSection() {
        val source = File("src/main/java/com/familyhub/ui/screens/home/HomeScreen.kt")
            .readText(Charsets.UTF_8)
        assertTrue(
            "HomeScreen should reserve at least 32dp or shared list bottom padding",
            source.contains("FhTheme.spacing.xxl") && source.contains("FhLayout.listBottomPadding"),
        )
    }
}

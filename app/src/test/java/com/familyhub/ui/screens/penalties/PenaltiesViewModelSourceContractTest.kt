package com.familyhub.ui.screens.penalties

import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertFalse
import org.junit.Test

class PenaltiesViewModelSourceContractTest {
    @Test
    fun penaltiesViewModel_doesNotExposeThrowableMessages() {
        val source = File(
            "src/main/java/com/familyhub/ui/screens/penalties/PenaltiesViewModel.kt",
        ).readText(StandardCharsets.UTF_8)

        assertFalse(source.contains("localizedMessage"))
        assertFalse(source.contains("error.message"))
    }
}

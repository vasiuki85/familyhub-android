package com.familyhub.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityScaffoldInsetsInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scaffoldInnerPadding_bottom_isGreaterWhenBottomBarShown() {
        val showBottomBar = mutableStateOf(false)
        var measuredInset = 0.dp
        var baselineInset = 0.dp

        composeRule.setContent {
            Scaffold(
                bottomBar = {
                    if (showBottomBar.value) {
                        NavigationBar {
                            Text("Home")
                        }
                    }
                },
            ) { innerPadding ->
                measuredInset = innerPadding.calculateBottomPadding()
                if (!showBottomBar.value) {
                    baselineInset = measuredInset
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag(CONTENT_SLOT_TAG),
                )
            }
        }

        composeRule.runOnIdle {
            showBottomBar.value = true
        }

        composeRule.runOnIdle {
            assertTrue(
                "Bottom bar should increase scaffold innerPadding.bottom",
                measuredInset > baselineInset,
            )
            assertTrue(
                "innerPadding.bottom with bottom bar should cover at least the bar content height",
                measuredInset >= 48.dp,
            )
        }
    }

    private companion object {
        const val CONTENT_SLOT_TAG = "scaffold-content-slot"
    }
}

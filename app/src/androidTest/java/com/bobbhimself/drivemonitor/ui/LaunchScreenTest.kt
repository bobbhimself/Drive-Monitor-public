package com.bobbhimself.drivemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class LaunchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launchScreenDisplaysForThreeSecondsBeforeMainScreen() {
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            DriveMonitorLaunchGate {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main-screen")
                )
            }
        }

        composeRule.onNodeWithTag("launch-screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("main-screen").assertCountEquals(0)

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("launch-screen").assertCountEquals(0)
        composeRule.onNodeWithTag("main-screen").assertIsDisplayed()
    }
}

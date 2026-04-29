package com.bobbhimself.drivemonitor.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bobbhimself.drivemonitor.data.model.AlertDirection
import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.DirectionalAlert
import com.bobbhimself.drivemonitor.data.model.LiveTelemetryState
import com.bobbhimself.drivemonitor.data.model.MonitoringState
import com.bobbhimself.drivemonitor.ui.theme.DriveMonitorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MainScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inactiveScreenShowsZeroedDashboardAndStartAction() {
        setMainScreen(
            state = MonitoringState.INACTIVE,
            liveTelemetryState = LiveTelemetryState(
                longitudinalG = 0.32f,
                lateralG = -0.18f,
                activeAlerts = listOf(DirectionalAlert(AlertDirection.FRONT, AlertSeverity.ALERT))
            )
        )

        composeRule.onNodeWithTag("header-row").assertIsDisplayed()
        composeRule.onNodeWithTag("truck-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("gauge-row").assertIsDisplayed()
        composeRule.onNodeWithTag("control-area").assertIsDisplayed()
        composeRule.onNodeWithText("Drive Monitor").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Monitoring status: Inactive").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Truck alert view: no active directional alerts").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Acceleration and braking G force: 0.00g").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Left and right G force: 0.00g").assertIsDisplayed()
        composeRule.onNodeWithText("Start Trip").assertHasClickAction()
        composeRule.onNodeWithText("View Log").assertHasClickAction()
    }

    @Test
    fun calibratingScreenShowsZeroedDashboardAndEndAction() {
        setMainScreen(
            state = MonitoringState.CALIBRATING,
            liveTelemetryState = LiveTelemetryState(
                longitudinalG = 0.24f,
                lateralG = 0.26f,
                activeAlerts = listOf(DirectionalAlert(AlertDirection.LEFT, AlertSeverity.CAUTION))
            )
        )

        composeRule.onNodeWithContentDescription("Monitoring status: Calibrating").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Truck alert view: no active directional alerts").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Acceleration and braking G force: 0.00g").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Left and right G force: 0.00g").assertIsDisplayed()
        composeRule.onNodeWithText("End Trip").assertHasClickAction()
        composeRule.onNodeWithText("View Log").assertHasClickAction()
    }

    @Test
    fun activeScreenShowsLiveTelemetryAndAlerts() {
        setMainScreen(
            state = MonitoringState.ACTIVE,
            liveTelemetryState = LiveTelemetryState(
                longitudinalG = 0.31f,
                lateralG = -0.35f,
                activeAlerts = listOf(
                    DirectionalAlert(AlertDirection.FRONT, AlertSeverity.ALERT),
                    DirectionalAlert(AlertDirection.LEFT, AlertSeverity.CAUTION)
                )
            )
        )

        composeRule.onNodeWithContentDescription("Monitoring status: Active").assertIsDisplayed()
        composeRule.onNodeWithTag("longitudinal-gauge")
            .assert(hasContentDescription("Acceleration and braking G force: +0.31g"))
        composeRule.onNodeWithTag("lateral-gauge")
            .assert(hasContentDescription("Left and right G force: -0.35g"))
        composeRule.onNodeWithContentDescription("Truck alert view: front alert, left caution").assertIsDisplayed()
        composeRule.onNodeWithText("End Trip").assertHasClickAction()
        composeRule.onNodeWithText("View Log").assertHasClickAction()
    }

    @Test
    fun inactiveControlsInvokeStartAndViewLogCallbacks() {
        val starts = AtomicInteger(0)
        val ends = AtomicInteger(0)
        val views = AtomicInteger(0)

        setMainScreen(
            state = MonitoringState.INACTIVE,
            onStartTrip = { starts.incrementAndGet() },
            onEndTrip = { ends.incrementAndGet() },
            onViewLog = { views.incrementAndGet() }
        )
        composeRule.onNodeWithText("Start Trip").performClick()
        composeRule.onNodeWithText("View Log").performClick()

        assertEquals(1, starts.get())
        assertEquals(0, ends.get())
        assertEquals(1, views.get())
    }

    @Test
    fun activeControlsInvokeEndTripCallback() {
        val starts = AtomicInteger(0)
        val ends = AtomicInteger(0)
        val views = AtomicInteger(0)

        setMainScreen(
            state = MonitoringState.ACTIVE,
            onStartTrip = { starts.incrementAndGet() },
            onEndTrip = { ends.incrementAndGet() },
            onViewLog = { views.incrementAndGet() }
        )
        composeRule.onNodeWithText("End Trip").performClick()

        assertEquals(0, starts.get())
        assertEquals(1, ends.get())
        assertEquals(0, views.get())
    }

    private fun setMainScreen(
        state: MonitoringState,
        liveTelemetryState: LiveTelemetryState = LiveTelemetryState(),
        onStartTrip: () -> Unit = {},
        onEndTrip: () -> Unit = {},
        onViewLog: () -> Unit = {}
    ) {
        composeRule.setContent {
            DriveMonitorTheme(dynamicColor = false) {
                MainScreen(
                    state = state,
                    liveTelemetryState = liveTelemetryState,
                    onStartTrip = onStartTrip,
                    onEndTrip = onEndTrip,
                    onViewLog = onViewLog
                )
            }
        }
    }
}

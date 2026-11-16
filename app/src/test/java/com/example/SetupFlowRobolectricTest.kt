package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SetupFlowRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `welcome screen shows Step 1 of 5 and Test Robotic Hand`() {
        composeRule.setContent {
            WelcomeScreen(username = "joshi", onSkip = {}, onStartSetup = {})
        }
        composeRule.onNodeWithText("Step 1 of 5").assertExists()
        composeRule.onNodeWithText("Step 1 of 6").assertDoesNotExist()
        composeRule.onNodeWithText("Test Robotic Hand").assertExists()
        composeRule.onNodeWithText("Calibrate Robotic Hand").assertDoesNotExist()
    }

    @Test
    fun `permissions screen shows STEP 2 OF 5 and 40 percent complete`() {
        composeRule.setContent {
            PermissionsScreen(onBack = {}, onContinue = {})
        }
        composeRule.onNodeWithText("STEP 2 OF 5").assertIsDisplayed()
        composeRule.onNodeWithText("40% COMPLETE").assertIsDisplayed()
        composeRule.onNodeWithText("STEP 2 OF 4").assertDoesNotExist()
    }

    @Test
    fun `robotic hand test screen is renumbered to STEP 4 OF 5`() {
        composeRule.setContent {
            RoboticHandTestScreen(onBack = {}, onComplete = {})
        }
        composeRule.onNodeWithText("STEP 4 OF 5").assertIsDisplayed()
        composeRule.onNodeWithText("STEP 5 OF 6").assertDoesNotExist()
    }

    @Test
    fun `setup complete screen is renumbered to STEP 5 of 5 without Bimodal Calibration`() {
        composeRule.setContent {
            SetupCompleteScreen(onGoToDashboard = {}, onViewTutorial = {})
        }
        composeRule.onNodeWithText("STEP 5 of 5").assertIsDisplayed()
        composeRule.onNodeWithText("STEP 6 of 6").assertDoesNotExist()
        composeRule.onNodeWithText("Bimodal Calibration").assertDoesNotExist()
        composeRule.onNodeWithText("Robotic Hand Test").assertExists()
    }
}

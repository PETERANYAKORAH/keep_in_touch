package app.kinkeep

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kinkeep.domain.Circle
import app.kinkeep.domain.ContactHealth
import app.kinkeep.domain.DashboardBuilder
import app.kinkeep.domain.HealthResult
import app.kinkeep.domain.ScoringEngine
import app.kinkeep.ui.dashboard.DashboardScreen
import app.kinkeep.ui.home.UiModel
import app.kinkeep.ui.lock.LockScreen
import app.kinkeep.ui.onboarding.OnboardingScreen
import app.kinkeep.ui.people.PeopleScreen
import app.kinkeep.ui.theme.KinKeepTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreensTest {
    @get:Rule val compose = createComposeRule()
    private val zeros = List(DashboardBuilder.WEEKS) { 0 }

    private fun person(id: Long, name: String, circle: Circle, days: Int?, cadence: Int, score: Int = 30) =
        ContactHealth(id, name, circle, cadence, HealthResult(score, ScoringEngine.categoryFor(score), days, 0))

    private fun model(vararg people: ContactHealth): UiModel {
        val list = people.toList()
        return UiModel(list, DashboardBuilder.build(list, zeros))
    }

    @Test fun dashboardExplainsWhatToDoWhenNobodyIsTracked() {
        compose.setContent { KinKeepTheme { DashboardScreen(model(), {}, {}, {}) } }
        compose.onNodeWithText(str(R.string.dash_empty)).assertIsDisplayed()
    }

    @Test fun dashboardTellsYouWhenYouAreAllCaughtUp() {
        compose.setContent { KinKeepTheme { DashboardScreen(model(person(1, "Ada", Circle.FRIENDS, days = 1, cadence = 30, score = 85)), {}, {}, {}) } }
        compose.onNodeWithText(str(R.string.all_caught_up)).assertIsDisplayed()
    }

    @Test fun dashboardListsWhoToReachOutToAndTheButtonsWork() {
        var called = -1L
        var opened = -1L
        compose.setContent {
            KinKeepTheme {
                DashboardScreen(
                    model(person(1, "Mum", Circle.INNER, days = 20, cadence = 7)),
                    onOpen = { opened = it }, onCall = { called = it }, onMessage = {},
                )
            }
        }
        compose.onNodeWithText("Mum").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_call)).performClick()
        assertEquals(1L, called)
        compose.onNodeWithText("Mum").performClick()
        assertEquals(1L, opened)
    }

    @Test fun peopleSearchFiltersTheList() {
        val tracked = listOf(person(1, "Mum", Circle.INNER, 3, 7), person(2, "Ada", Circle.FRIENDS, 3, 30))
        compose.setContent { KinKeepTheme { PeopleScreen(tracked, emptyList(), syncing = false, message = null, onSync = {}, onOpen = {}) } }
        compose.onNodeWithText("Mum").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.search_hint)).performTextInput("ad")
        compose.onNodeWithText("Mum").assertDoesNotExist()
        compose.onNodeWithText("Ada").assertIsDisplayed()
    }

    /** Skipped if this device already granted contacts access to the app. */
    @Test fun importExplainsWhyBeforeAskingForPermission() {
        assumeTrue(testContext().checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
        compose.setContent { KinKeepTheme { PeopleScreen(emptyList(), emptyList(), syncing = false, message = null, onSync = {}, onOpen = {}) } }
        compose.onNodeWithText(str(R.string.import_button)).performClick()
        compose.onNodeWithText(str(R.string.rationale_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.cancel)).performClick()
        compose.onNodeWithText(str(R.string.rationale_title)).assertDoesNotExist()
    }

    @Test fun onboardingWalksThroughThreePagesAndCanBeSkipped() {
        var result: Boolean? = null
        compose.setContent { KinKeepTheme { OnboardingScreen { result = it } } }
        compose.onNodeWithText(str(R.string.onboarding_title_1)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(str(R.string.onboarding_title_2)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.onboarding_back)).performClick()
        compose.onNodeWithText(str(R.string.onboarding_title_1)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(str(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(str(R.string.onboarding_title_3)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.onboarding_skip)).performClick()
        assertEquals(false, result)
    }

    @Test fun lockScreenOffersUnlock() {
        var unlocks = 0
        compose.setContent { KinKeepTheme { LockScreen { unlocks++ } } }
        compose.onNodeWithText(str(R.string.lock_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.lock_button)).performClick()
        assertEquals(1, unlocks)
    }
}

package ir.restaurant.management.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import ir.restaurant.management.MainActivity
import ir.restaurant.management.RestaurantManagementApplication
import ir.restaurant.management.data.security.StartupSessionBoundary
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** P0 regression suite: authentication is the owner of the protected ERP graph lifetime. */
class StartupAuthenticationBoundaryComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: RestaurantManagementApplication
        get() = composeRule.activity.application as RestaurantManagementApplication

    @After
    fun leaveNoSession() {
        runBlocking { app.container.securityRepository.logout() }
    }

    @Test
    fun coldStartWithoutSession_showsSecurityAndDoesNotComposeProtectedModules() {
        runBlocking { app.container.securityRepository.logout() }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("security_root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("security_root").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun cashierWithoutPayrollPermission_logsInWithoutPayrollSubscription_andLogoutTearsDownProtectedGraph() {
        val cashier = prepareCashierAndLogout()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("security_root").fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("security_users_loaded").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithTag("security_root").performScrollToNode(hasTestTag("security_user_${cashier.id}"))
        composeRule.onNodeWithTag("security_switch_${cashier.id}").performClick()
        composeRule.onNodeWithTag("security_login_pin").performTextReplacement(CASHIER_PIN)
        composeRule.onNodeWithTag("security_login_confirm").performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_action_sale"))
        composeRule.onNodeWithTag("home_action_sale").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("home_action_personnel").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("more_hub").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_SECURITY_کاربران"))
        composeRule.onNodeWithTag("module_SECURITY_کاربران").performClick()
        composeRule.onNodeWithTag("security_logout").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("security_root").fetchSemanticsNodes().isNotEmpty() &&
                runBlocking { app.container.securityRepository.currentUser.first() == null }
        }
        assertTrue(composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isEmpty())
        check(runBlocking { app.container.securityRepository.currentUser.first() } == null)
    }

    @Test
    fun ownerWithPayrollPermission_afterLoginCanLoadPayrollWorkspace() {
        val owner = prepareOwnerAndLogout()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("security_root").fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("security_users_loaded").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithTag("security_root").performScrollToNode(hasTestTag("security_user_${owner.id}"))
        composeRule.onNodeWithTag("security_switch_${owner.id}").performClick()
        composeRule.onNodeWithTag("security_login_pin").performTextReplacement(OWNER_PIN)
        composeRule.onNodeWithTag("security_login_confirm").performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("nav_operations_hub").performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("operations_hub").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("operations_hub").performScrollToNode(hasTestTag("module_PERSONNEL_پرسنل"))
        composeRule.onNodeWithTag("module_PERSONNEL_پرسنل").performClick()
        composeRule.onNodeWithText("منابع انسانی و حقوق").assertIsDisplayed()
    }


    @Test
    fun persistedSessionInvalidatedByStartupBoundary_returnsToLoginAndDropsProtectedGraph() {
        val owner = runBlocking { prepareOwnerForFixture() }
        runBlocking {
            val security = app.container.securityRepository
            if (security.currentUser.first()?.id != owner.id) security.switchUser(owner.id, OWNER_PIN)
            check(security.currentUser.first()?.id == owner.id)
            StartupSessionBoundary.invalidatePersistedSession(app.container.databaseForTesting.openHelper.writableDatabase)
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("security_root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("security_root").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("home_action_personnel").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("module_TREASURY").fetchSemanticsNodes().isEmpty())
        check(runBlocking { app.container.securityRepository.currentUser.first() } == null)
    }

    private fun prepareOwnerAndLogout() = runBlocking {
        val security = app.container.securityRepository
        val users = security.users.first()
        val owner = users.firstOrNull { it.role == UserRole.OWNER && it.username in KNOWN_OWNER_USERNAMES } ?: run {
            check(users.none { it.role == UserRole.OWNER }) { "Unexpected persistent owner prevents deterministic UI-test login" }
            security.save(
                null,
                UserDraft(
                    username = OWNER_USERNAME,
                    displayName = "مالک آزمون مرز احراز",
                    pin = OWNER_PIN,
                    role = UserRole.OWNER,
                    recoveryCode = OWNER_RECOVERY,
                ),
            )
            security.users.first().first { it.username == OWNER_USERNAME }
        }
        if (security.currentUser.first()?.id != owner.id) security.switchUser(owner.id, OWNER_PIN)
        security.logout()
        owner
    }

    private fun prepareCashierAndLogout() = runBlocking {
        val security = app.container.securityRepository
        val owner = prepareOwnerForFixture()
        if (security.currentUser.first()?.id != owner.id) security.switchUser(owner.id, OWNER_PIN)
        val cashier = security.users.first().firstOrNull { it.username == CASHIER_USERNAME } ?: run {
            security.save(
                null,
                UserDraft(
                    username = CASHIER_USERNAME,
                    displayName = "صندوقدار آزمون مرز احراز",
                    pin = CASHIER_PIN,
                    role = UserRole.CASHIER,
                ),
            )
            security.users.first().first { it.username == CASHIER_USERNAME }
        }
        security.logout()
        cashier
    }

    private suspend fun prepareOwnerForFixture() = app.container.securityRepository.let { security ->
        val users = security.users.first()
        users.firstOrNull { it.role == UserRole.OWNER && it.username in KNOWN_OWNER_USERNAMES } ?: run {
            check(users.none { it.role == UserRole.OWNER }) { "Unexpected persistent owner prevents deterministic UI-test login" }
            security.save(
                null,
                UserDraft(OWNER_USERNAME, "مالک آزمون مرز احراز", OWNER_PIN, UserRole.OWNER, OWNER_RECOVERY),
            )
            security.users.first().first { it.username == OWNER_USERNAME }
        }
    }

    private companion object {
        const val OWNER_USERNAME = "authowner"
        const val OWNER_PIN = "246810"
        const val OWNER_RECOVERY = "13572468"
        const val CASHIER_USERNAME = "authcashier"
        const val CASHIER_PIN = "135790"
        val KNOWN_OWNER_USERNAMES = setOf(OWNER_USERNAME, "e2eowner", "ux2owner")
    }
}

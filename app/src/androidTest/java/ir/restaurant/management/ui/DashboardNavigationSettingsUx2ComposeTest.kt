package ir.restaurant.management.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import ir.restaurant.management.MainActivity
import ir.restaurant.management.domain.branch.BranchDraft
import ir.restaurant.management.RestaurantManagementApplication
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardNavigationSettingsUx2ComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: RestaurantManagementApplication
        get() = composeRule.activity.application as RestaurantManagementApplication

    private lateinit var owner: AppUserRecord

    @Before
    fun authenticateOwner() {
        owner = runBlocking {
            val security = app.container.securityRepository
            val users = security.users.first()
            val existing = users.firstOrNull { it.username in KNOWN_OWNER_USERNAMES }
            val resolved = existing ?: run {
                check(users.none { it.role == UserRole.OWNER }) {
                    "Unexpected owner account exists; refusing to guess credentials in UX2 instrumentation test"
                }
                security.save(null, UserDraft(OWNER_USERNAME, "مالک UX2", OWNER_PIN, UserRole.OWNER, OWNER_RECOVERY))
                security.users.first().first { it.username == OWNER_USERNAME }
            }
            if (security.currentUser.first()?.id != resolved.id) security.switchUser(resolved.id, OWNER_PIN)
            if (app.container.branchRepository.listActive().isEmpty()) {
                app.container.branchRepository.create(BranchDraft(name = "شعبه UX2", code = "UX2"))
            }
            resolved
        }
        composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun restoreOwner() {
        runBlocking {
            val security = app.container.securityRepository
            if (security.currentUser.first()?.id != owner.id) runCatching { security.switchUser(owner.id, OWNER_PIN) }
        }
    }

    @Test
    fun ownerHome_hasFourPrimarySections_andNoAuditOrModuleDump() {
        selectHomeBranchIfRequired()
        composeRule.onNodeWithTag("home_header").assertIsDisplayed()
        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_kpi_section"))
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("home_kpi_summary").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("home_kpi_sales", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_kpi_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("home_kpi_sales", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_quick_actions"))
        composeRule.onNodeWithTag("home_quick_actions").assertIsDisplayed()
        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_attention_center"))
        composeRule.onNodeWithTag("home_attention_center").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("فعالیت‌های اخیر").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("module_AUDIT_LOG_امنیت و حسابرسی").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("nav_control_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_operations_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_finance_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_more").assertIsDisplayed()
    }

    @Test
    fun homeRequiresExplicitBranchBeforeCanonicalManagementKpis() {
        composeRule.onNodeWithTag("home_no_branch_state").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("home_management_overview").fetchSemanticsNodes().isEmpty())
        val choices = composeRule.onAllNodesWithTag("home_choose_branch").fetchSemanticsNodes()
        if (choices.isNotEmpty()) {
            composeRule.onAllNodesWithTag("home_choose_branch").onFirst().performClick()
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_revenue_7d_chart").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag("home_revenue_7d_chart").assertIsDisplayed()
        }
    }

    @Test
    fun morePage_groupsAuthorizedModules_andTopLevelNavigationReturnsHome() {
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithTag("more_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("module_BRANCHES_شعب").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_AUDIT_LOG_حسابرسی"))
        composeRule.onNodeWithTag("module_AUDIT_LOG_حسابرسی").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_dashboard").performClick()
        composeRule.onNodeWithTag("home_dashboard").assertIsDisplayed()
    }

    @Test
    fun settingsSecurityAudit_isPrimaryReachableAuditPath() {
        composeRule.onNodeWithTag("home_profile").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_sections").performScrollToNode(hasTestTag("settings_section_SECURITY_AUDIT"))
        composeRule.onNodeWithTag("settings_section_SECURITY_AUDIT").performClick()
        composeRule.onNodeWithTag("settings_security_audit").performScrollTo().performClick()
        composeRule.onNodeWithTag("audit_log_screen").assertIsDisplayed()
        composeRule.onNodeWithText("رویدادهای سیستم").assertIsDisplayed()
        composeRule.onNodeWithTag("audit_filter_search").assertIsDisplayed()
    }

    @Test
    fun cashierHome_filtersSensitiveKpisAndActionsBeforeRendering() {
        switchToRoleUser(CASHIER_USERNAME, CASHIER_PIN, UserRole.CASHIER)
        selectHomeBranchIfRequired()
        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_kpi_section"))
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_kpi_sales", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("home_kpi_summary").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("home_kpi_liquidity", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("home_kpi_sales", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_quick_actions"))
        composeRule.onNodeWithTag("home_action_sale").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("home_action_personnel").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun inventoryHome_showsInventoryContext_withoutFinancialKpis() {
        switchToRoleUser(INVENTORY_USERNAME, INVENTORY_PIN, UserRole.INVENTORY)
        selectHomeBranchIfRequired()
        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_kpi_section"))
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_kpi_low_stock", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("home_kpi_summary").assertIsDisplayed()
        composeRule.onNodeWithText("کم‌موجودی").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("home_kpi_liquidity", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_quick_actions"))
        composeRule.onNodeWithTag("home_action_inventory").assertIsDisplayed()
    }

    @Test
    fun operationsHub_isReachableFromBottomNavigation() {
        composeRule.onNodeWithTag("nav_operations_hub").performClick()
        composeRule.onNodeWithTag("operations_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("module_PURCHASES_خرید").assertIsDisplayed()
        composeRule.onNodeWithTag("module_INVENTORY_موجودی").assertIsDisplayed()
    }

    @Test
    fun controlHub_exposesDedicatedManagementWorkflowRoutes() {
        composeRule.onNodeWithTag("nav_control_hub").performClick()
        composeRule.onNodeWithTag("control_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("module_MANAGEMENT_ISSUES_مسائل مدیریتی").assertIsDisplayed()
        composeRule.onNodeWithTag("module_MANAGEMENT_TASKS_وظایف").assertIsDisplayed()
        composeRule.onNodeWithTag("module_CHECKLISTS_چک‌لیست‌ها").assertIsDisplayed()
        composeRule.onNodeWithTag("module_DAILY_BRIEF_گزارش روزانه مدیریت").assertIsDisplayed()
    }

    @Test
    fun financeHub_containsDailySalesAndReceivables() {
        composeRule.onNodeWithTag("nav_finance_hub").performClick()
        composeRule.onNodeWithTag("finance_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("module_SALES_فروش روزانه").assertIsDisplayed()
        composeRule.onNodeWithTag("module_CRM_مطالبات").assertIsDisplayed()
    }

    @Test
    fun controlChildren_renderAndKeepControlSelected() {
        val children = listOf(
            "module_MANAGEMENT_ISSUES_مسائل مدیریتی" to "مسائل مدیریتی",
            "module_MANAGEMENT_TASKS_وظایف" to "وظایف مدیریتی",
            "module_CHECKLISTS_چک‌لیست‌ها" to "چک‌لیست‌ها",
            "module_DAILY_BRIEF_گزارش روزانه مدیریت" to "گزارش روزانه مدیریت",
        )
        children.forEach { (moduleTag, title) ->
            composeRule.onNodeWithTag("nav_control_hub").performClick()
            composeRule.onNodeWithTag("control_hub").performScrollToNode(hasTestTag(moduleTag))
            composeRule.onNodeWithTag(moduleTag).performClick()
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithTag("nav_control_hub").assertIsSelected()
        }
    }

    @Test
    fun finalHomeFinanceReceivablesBranchSelectorAndBranchManagementSurfacesAreReachable() {
        composeRule.onNodeWithTag("home_dashboard").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_finance_hub").performClick()
        composeRule.onNodeWithTag("finance_hub").performScrollToNode(hasTestTag("module_SALES_فروش روزانه"))
        composeRule.onNodeWithTag("module_SALES_فروش روزانه").performClick()
        composeRule.onNodeWithText("ثبت فروش روزانه").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_finance_hub").assertIsSelected()

        composeRule.onNodeWithTag("nav_finance_hub").performClick()
        composeRule.onNodeWithTag("finance_hub").performScrollToNode(hasTestTag("module_CRM_مطالبات"))
        composeRule.onNodeWithTag("module_CRM_مطالبات").performClick()
        composeRule.onNodeWithText("طرف‌حساب‌ها و مطالبات").assertIsDisplayed()
        composeRule.onNodeWithTag("crm_list").performScrollToNode(hasTestTag("receivables_branch_selector"))
        composeRule.onNodeWithTag("receivables_branch_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_finance_hub").assertIsSelected()

        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_BRANCHES_شعب"))
        composeRule.onNodeWithTag("module_BRANCHES_شعب").performClick()
        composeRule.onNodeWithTag("branch_management").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_more").assertIsSelected()
    }

    private fun selectHomeBranchIfRequired() {
        if (composeRule.onAllNodesWithTag("home_no_branch_state").fetchSemanticsNodes().isEmpty()) return
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_choose_branch").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodesWithTag("home_choose_branch").onFirst().performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_kpi_section").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun switchToRoleUser(username: String, pin: String, role: UserRole) {
        val target = runBlocking {
            val security = app.container.securityRepository
            if (security.currentUser.first()?.id != owner.id) security.switchUser(owner.id, OWNER_PIN)
            val existing = security.users.first().firstOrNull { it.username == username }
            val user = existing ?: run {
                security.save(null, UserDraft(username, "کاربر ${role.title}", pin, role))
                security.users.first().first { it.username == username }
            }
            security.switchUser(user.id, pin)
            user
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isNotEmpty() &&
                runBlocking { app.container.securityRepository.currentUser.first()?.id == target.id }
        }
    }

    private companion object {
        const val OWNER_USERNAME = "ux2owner"
        const val OWNER_PIN = "246810"
        const val OWNER_RECOVERY = "86421357"
        const val CASHIER_USERNAME = "ux2cashier"
        const val CASHIER_PIN = "135790"
        const val INVENTORY_USERNAME = "ux2inventory"
        const val INVENTORY_PIN = "112233"
        val KNOWN_OWNER_USERNAMES = setOf(OWNER_USERNAME, "e2eowner", "authowner")
    }
}

package ir.restaurant.management.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.purchase.ProcurementOverview
import ir.restaurant.management.domain.purchase.RequisitionRecord
import ir.restaurant.management.domain.purchase.RequisitionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PostCiUatCorrectionComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun branchSelector_opensWithoutCrash_selectsCanonicalId_andHandlesEmptyState() {
        var selectedId by mutableStateOf<Long?>(null)
        var branchOptions by mutableStateOf(listOf(
            BranchRecord(11L, "test:branch:11", null, "B11", "شعبه فعال", true),
            BranchRecord(12L, "test:branch:12", null, "B12", "شعبه غیرفعال", false),
        ))
        composeRule.setContent {
            MaterialTheme {
                CanonicalBranchSelector(
                    branches = branchOptions,
                    selectedBranchId = selectedId,
                    onBranchSelected = { selectedId = it },
                    tag = "uat_branch_selector",
                )
            }
        }

        composeRule.onNodeWithTag("uat_branch_selector").performClick()
        composeRule.onNodeWithTag("uat_branch_selector_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("uat_branch_selector_branch_11").performClick()
        composeRule.runOnIdle { assertEquals(11L, selectedId) }

        composeRule.onNodeWithTag("uat_branch_selector").performClick()
        composeRule.onNodeWithTag("uat_branch_selector_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("uat_branch_selector_close").performClick()

        composeRule.runOnIdle {
            selectedId = null
            branchOptions = emptyList()
        }
        composeRule.onNodeWithTag("uat_branch_selector").performClick()
        composeRule.onNodeWithTag("uat_branch_selector_empty").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(selectedId) }
    }

    @Test
    fun procurementApproveAndReject_arePermissionAware_andRejectPersistsReasonThroughCallback() {
        var approvedId: Long? = null
        var rejectedId: Long? = null
        var rejectedReason: String? = null
        val request = RequisitionRecord(
            id = 41L,
            requestNo = "PR-00000041",
            department = "آشپزخانه",
            requiredEpochDay = 20_000L,
            status = RequisitionStatus.SUBMITTED,
            requestedBy = "درخواست‌کننده",
            approvedBy = null,
            note = "تأمین مواد اولیه",
            createdAtEpochMillis = 1_900_000_000_000L,
            estimatedTotalRial = 12_000_000L,
            lineCount = 3,
        )
        val manager = AppUserRecord(7L, "manager", "مدیر", UserRole.MANAGER, true, true)
        val state = OperationsUiState(procurement = ProcurementOverview(requisitions = listOf(request)))

        composeRule.setContent {
            MaterialTheme {
                LazyColumn {
                    item {
                        ProcurementControlPanel(
                            state = state,
                            branches = emptyList(),
                            currentUser = manager,
                            onSubmit = { _, done -> done() },
                            onReview = { id, approve, reason, done ->
                                if (approve) approvedId = id else {
                                    rejectedId = id
                                    rejectedReason = reason
                                }
                                done()
                            },
                            onCreateOrder = { _, done -> done() },
                            onCreateSplitOrders = { _, done -> done() },
                            onMarkOrderSent = { _, _ -> },
                            onAcknowledgeOrder = { _, done -> done() },
                            onReceive = { _, done -> done() },
                            onReturn = { _, done -> done() },
                            onSaveReplenishmentPolicy = { _, done -> done() },
                            onSaveSupplierOffer = { _, done -> done() },
                            onSubmitSuggestedRequisition = { _, done -> done() },
                            onMatchInvoice = { _, _, _, done -> done() },
                            onConsumeLaunchAction = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("procurement_workflow_stepper").assertIsDisplayed()
        composeRule.onNodeWithTag("procurement_approve_41").performScrollTo().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(41L, approvedId) }

        composeRule.onNodeWithTag("procurement_reject_41").performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithTag("procurement_rejection_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("procurement_rejection_confirm").performClick()
        composeRule.runOnIdle {
            assertNull(rejectedId)
            assertNull(rejectedReason)
        }
        composeRule.onNodeWithTag("procurement_rejection_reason").performTextInput("بودجه کافی نیست")
        composeRule.onNodeWithTag("procurement_rejection_confirm").performClick()
        composeRule.runOnIdle {
            assertEquals(41L, rejectedId)
            assertEquals("بودجه کافی نیست", rejectedReason)
        }
    }
}

package ir.restaurant.management.data.security

import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import kotlin.test.assertFailsWith
import org.junit.Test

class SensitiveActionGateTest {
    private var now = 10_000L
    private val gate = SensitiveActionGate(clockMillis = { now }, permitLifetimeMillis = 5_000L)

    @Test
    fun permitIsBoundToUserActionAndSingleConsumption() {
        gate.grant(7L, SensitiveAction.CLOSE_ACCOUNTING_PERIOD)

        assertFailsWith<SensitiveAuthenticationRequiredException> {
            gate.requireAndConsume(8L, SensitiveAction.CLOSE_ACCOUNTING_PERIOD)
        }
        assertFailsWith<SensitiveAuthenticationRequiredException> {
            gate.requireAndConsume(7L, SensitiveAction.REOPEN_ACCOUNTING_PERIOD)
        }

        gate.requireAndConsume(7L, SensitiveAction.CLOSE_ACCOUNTING_PERIOD)
        assertFailsWith<SensitiveAuthenticationRequiredException> {
            gate.requireAndConsume(7L, SensitiveAction.CLOSE_ACCOUNTING_PERIOD)
        }
    }

    @Test
    fun permitIsBoundToExactResourceBranchAndFingerprint() {
        val invoiceA = SensitiveActionContext.resource(
            type = "INVOICE", id = "A", branchId = 10L, commandFingerprint = "void-a-v1",
        )
        gate.grant(7L, SensitiveAction.CLOSE_SALES_DAY, invoiceA)

        assertFailsWith<SensitiveAuthenticationRequiredException> {
            gate.requireAndConsume(7L, SensitiveAction.CLOSE_SALES_DAY, invoiceA.copy(resourceId = "B"))
        }
        assertFailsWith<SensitiveAuthenticationRequiredException> {
            gate.requireAndConsume(7L, SensitiveAction.CLOSE_SALES_DAY, invoiceA.copy(branchId = 11L))
        }
        assertFailsWith<SensitiveAuthenticationRequiredException> {
            gate.requireAndConsume(7L, SensitiveAction.CLOSE_SALES_DAY, invoiceA.copy(commandFingerprint = "void-a-v2"))
        }

        gate.requireAndConsume(7L, SensitiveAction.CLOSE_SALES_DAY, invoiceA)
        assertFailsWith<SensitiveAuthenticationRequiredException> {
            gate.requireAndConsume(7L, SensitiveAction.CLOSE_SALES_DAY, invoiceA)
        }
    }

    @Test
    fun expiredAndInvalidatedPermitsAreRejected() {
        gate.grant(3L, SensitiveAction.RESTORE_BACKUP)
        now += 5_001L
        assertFailsWith<SensitiveAuthenticationRequiredException> {
            gate.requireAndConsume(3L, SensitiveAction.RESTORE_BACKUP)
        }

        gate.grant(3L, SensitiveAction.FACTORY_RESET)
        gate.invalidateAll()
        assertFailsWith<SensitiveAuthenticationRequiredException> {
            gate.requireAndConsume(3L, SensitiveAction.FACTORY_RESET)
        }
    }
}

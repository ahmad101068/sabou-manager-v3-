package ir.sabou.inventory

import android.app.Instrumentation
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.sabou.inventory.domain.operations.UserDraft
import ir.sabou.inventory.domain.operations.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime gate for the exact path that previously escaped JVM/build-only CI:
 * a valid owner session activates every eagerly-created ViewModel and enters the dashboard.
 */
@RunWith(AndroidJUnit4::class)
class Alpha160CleanEntryRuntimeSmokeTest {
    @Test
    fun cleanInstallOwnerEntryKeepsMainApplicationAlive() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as SabouApplication
        withContext(Dispatchers.IO) { application.container.initialize() }

        val security = application.container.securityRepository
        assertTrue("Clean-install smoke test requires an empty user store", security.users.first().isEmpty())
        security.save(
            id = null,
            draft = UserDraft(
                username = TEST_USERNAME,
                displayName = "Runtime Owner",
                pin = TEST_PIN,
                role = UserRole.OWNER,
                recoveryCode = TEST_RECOVERY_CODE,
            ),
        )

        assertEmptyHrFlows(application, employeeId = 1L)
        assertDashboardSurvives(instrumentation)
    }
}

/** Opens a genuine schema-43 app database through the production schema-44 container. */
@RunWith(AndroidJUnit4::class)
class Alpha160UpgradeEntryRuntimeSmokeTest {
    @Test
    fun alpha159DatabaseMigratesAndPostEntryFlowsRemainAlive() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as SabouApplication

        // This is the production SQLCipher + Room builder and therefore executes 43 -> 44.
        withContext(Dispatchers.IO) { application.container.initialize() }

        val security = application.container.securityRepository
        val owner = security.users.first().single { it.username == TEST_USERNAME }
        security.switchUser(owner.id, TEST_PIN)

        val employees = withTimeout(FLOW_TIMEOUT_MILLIS) {
            application.container.personnelRepository.employees.first()
        }
        assertTrue("Representative Alpha159 employee was not preserved", employees.isNotEmpty())
        val employeeId = employees.first().id

        val payroll = application.container.hrPayrollService
        val periods = withTimeout(FLOW_TIMEOUT_MILLIS) { payroll.periods.first() }
        val batches = withTimeout(FLOW_TIMEOUT_MILLIS) { payroll.batches.first() }
        val payslips = withTimeout(FLOW_TIMEOUT_MILLIS) { payroll.employeePayslips(employeeId).first() }
        val timeline = withTimeout(FLOW_TIMEOUT_MILLIS) { payroll.employeeTimeline(employeeId).first() }
        assertTrue("Legacy payroll period was not projected", periods.isNotEmpty())
        assertTrue("Legacy payroll batch was not projected", batches.isNotEmpty())
        assertTrue("Legacy employee payslip was not projected", payslips.isNotEmpty())
        assertTrue("Migrated employee timeline was not readable", timeline.isNotEmpty())

        assertDashboardSurvives(instrumentation)
    }
}

private suspend fun assertEmptyHrFlows(application: SabouApplication, employeeId: Long) {
    val payroll = application.container.hrPayrollService
    withTimeout(FLOW_TIMEOUT_MILLIS) {
        assertTrue(payroll.periods.first().isEmpty())
        assertTrue(payroll.batches.first().isEmpty())
        assertTrue(payroll.employeePayslips(employeeId).first().isEmpty())
        assertTrue(payroll.employeeTimeline(employeeId).first().isEmpty())
    }
}

private fun assertDashboardSurvives(instrumentation: Instrumentation) {
    val scenario = ActivityScenario.launch(MainActivity::class.java)
    try {
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertTrue(
            "Dashboard header did not render after authenticated entry",
            waitForVisibleText(instrumentation, "سلام، Runtime Owner", UI_TIMEOUT_MILLIS),
        )
        SystemClock.sleep(STABILITY_WINDOW_MILLIS)
        scenario.onActivity { activity ->
            assertFalse("MainActivity finished after entry", activity.isFinishing)
            assertFalse("MainActivity was destroyed after entry", activity.isDestroyed)
        }
    } finally {
        scenario.close()
    }
}

private fun waitForVisibleText(
    instrumentation: Instrumentation,
    expectedText: String,
    timeoutMillis: Long,
): Boolean {
    val deadline = SystemClock.elapsedRealtime() + timeoutMillis
    while (SystemClock.elapsedRealtime() < deadline) {
        val matches = instrumentation.uiAutomation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText(expectedText)
            .orEmpty()
        if (matches.isNotEmpty()) return true
        SystemClock.sleep(250L)
    }
    return false
}

private const val TEST_USERNAME = "runtime_owner"
private const val TEST_PIN = "123456"
private const val TEST_RECOVERY_CODE = "87654321"
private const val FLOW_TIMEOUT_MILLIS = 10_000L
private const val UI_TIMEOUT_MILLIS = 20_000L
private const val STABILITY_WINDOW_MILLIS = 8_000L

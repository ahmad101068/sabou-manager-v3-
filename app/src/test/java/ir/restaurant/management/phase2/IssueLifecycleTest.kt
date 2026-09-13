package ir.restaurant.management.phase2
import ir.restaurant.management.domain.control.*
import kotlin.test.*
class IssueLifecycleTest {
 @Test fun validTransitionWorks(){ ManagementIssueLifecycle.requireTransition(ManagementIssueStatus.NEW,ManagementIssueStatus.ASSIGNED) }
 @Test fun resolvedCannotSilentlyReopen(){ assertFailsWith<IllegalArgumentException>{ ManagementIssueLifecycle.requireTransition(ManagementIssueStatus.RESOLVED,ManagementIssueStatus.IN_PROGRESS) } }
 @Test fun dedupKeyStable(){ val i=DetectedIssue(1,ManagementIssueType.CASH_VARIANCE,ManagementIssueSeverity.HIGH,"x","y",businessEpochDay=2,sourceType="CASH",sourceId=3,businessPeriodKey="2"); assertEquals("1|CASH_VARIANCE|CASH|3|2",i.deduplicationKey) }
}

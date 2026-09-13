package ir.restaurant.management.application.personnel

import ir.restaurant.management.domain.personnel.*
import ir.restaurant.management.domain.treasury.TreasuryChannel

class PersonnelUseCases(private val repository: PersonnelRepository) {
    val employees get() = repository.employees
    val payrolls get() = repository.payrolls
    val attendance get() = repository.attendance
    val leaves get() = repository.leaves
    val pendingLeaves get() = repository.pendingLeaves
    val pendingCorrections get() = repository.pendingAttendanceCorrections
    val payrollPolicies get() = repository.payrollPolicies
    val shiftTemplates get() = repository.shiftTemplates
    val workSchedules get() = repository.workSchedules
    val pendingOvertimeApprovals get() = repository.pendingOvertimeApprovals
    val openAdvances get() = repository.openAdvances
    suspend fun saveEmployee(id: Long?, draft: EmployeeDraft) = repository.saveEmployee(id, draft)
    suspend fun deactivateEmployee(id: Long) = repository.deactivateEmployee(id)
    suspend fun savePayrollPolicy(draft: PayrollPolicyDraft) = repository.savePayrollPolicy(draft)
    suspend fun transitionEmploymentStatus(id: Long, to: EmploymentStatus, terminationEpochDay: Long? = null) = repository.transitionEmploymentStatus(id, to, terminationEpochDay)
    suspend fun privateProfile(employeeId: Long) = repository.privateProfile(employeeId)
    fun assignments(employeeId: Long) = repository.assignments(employeeId)
    fun contracts(employeeId: Long) = repository.contracts(employeeId)
    fun advances(employeeId: Long) = repository.advances(employeeId)
    fun auditTimeline(employeeId: Long) = repository.auditTimeline(employeeId)
    fun documents(employeeId: Long) = repository.documents(employeeId)
    suspend fun saveDocument(draft: ir.restaurant.management.domain.personnel.HrDocumentDraft) = repository.saveDocument(draft)
    suspend fun archiveDocument(documentId: Long) = repository.archiveDocument(documentId)
    suspend fun saveContract(id: Long?, draft: EmployeeContractDraft) = repository.saveContract(id, draft)
    suspend fun saveShiftTemplate(id: Long?, draft: ShiftTemplateDraft) = repository.saveShiftTemplate(id, draft)
    suspend fun saveWorkSchedule(id: Long?, draft: WorkScheduleDraft) = repository.saveWorkSchedule(id, draft)
    fun plannedShifts(employeeId: Long) = repository.plannedShifts(employeeId)
    suspend fun savePlannedShift(id: Long?, draft: PlannedShiftDraft) = repository.savePlannedShift(id, draft)
    suspend fun approveContract(id: Long) = repository.approveContract(id)
    suspend fun payrollReadiness(employeeId: Long, businessEpochDay: Long) = repository.payrollReadiness(employeeId, businessEpochDay)
    suspend fun effectiveContract(employeeId: Long, day: Long) = repository.effectiveContract(employeeId, day)
    suspend fun postAdvance(draft: EmployeeAdvanceDraft) = repository.postAdvance(draft)
    suspend fun settleAdvance(id: Long, amountRial: Long, method: TreasuryChannel, day: Long) = repository.settleAdvance(id, amountRial, method, day)
}

class AttendanceUseCases(private val repository: PersonnelRepository) {
    val attendance get() = repository.attendance
    val leaves get() = repository.leaves
    val pendingLeaves get() = repository.pendingLeaves
    val pendingCorrections get() = repository.pendingAttendanceCorrections
    suspend fun save(id: Long?, draft: AttendanceDraft) = repository.saveAttendance(id, draft)
    suspend fun punch(draft: AttendancePunchDraft) = repository.recordAttendancePunch(draft)
    fun events(employeeId: Long, limit: Int = 50) = repository.attendanceEvents(employeeId, limit)
    suspend fun dailySummary(employeeId: Long, day: Long) = repository.attendanceSummaryV2(employeeId, day)
    suspend fun approveCorrection(id: Long) = repository.approveAttendanceCorrection(id)
    suspend fun rejectCorrection(id: Long, reason: String) = repository.rejectAttendanceCorrection(id, reason)
    suspend fun reviewOvertime(command: OvertimeReviewCommand) = repository.reviewOvertime(command)
    suspend fun summary(employeeId: Long, from: Long, to: Long) = repository.attendanceSummary(employeeId, from, to)
    suspend fun requestLeave(draft: LeaveDraft, requestedBy: String) = repository.requestLeave(draft, requestedBy)
    suspend fun grantLeave(draft: LeaveGrantDraft) = repository.grantLeave(draft)
    suspend fun leaveBalance(employeeId: Long, leaveType: LeaveType) = repository.leaveBalance(employeeId, leaveType)
    suspend fun reviewLeave(draft: LeaveReviewDraft) = repository.reviewLeave(draft)
    suspend fun cancelLeave(id: Long) = repository.cancelLeave(id)
    suspend fun approveLeave(draft: LeaveDraft) = repository.approveLeave(draft)
    suspend fun payrollAdjustment(employeeId: Long, from: Long, to: Long, policy: AttendancePayrollPolicy) = repository.attendancePayrollAdjustment(employeeId, from, to, policy)
}

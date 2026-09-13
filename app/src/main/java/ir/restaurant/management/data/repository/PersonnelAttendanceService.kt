package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.BusinessCalendar
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AttendanceCorrectionEntity
import ir.restaurant.management.data.db.AttendanceEntity
import ir.restaurant.management.data.db.AttendanceEventEntity
import ir.restaurant.management.data.db.OvertimeApprovalEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.personnel.AttendanceAggregationPolicy
import ir.restaurant.management.domain.personnel.AttendanceCalculator
import ir.restaurant.management.domain.personnel.AttendanceCalculationEngine
import ir.restaurant.management.domain.personnel.AttendanceCorrectionCodec
import ir.restaurant.management.domain.personnel.AttendanceCorrectionSnapshot
import ir.restaurant.management.domain.personnel.AttendanceDraft
import ir.restaurant.management.domain.personnel.AttendanceEvent
import ir.restaurant.management.domain.personnel.AttendanceEventAggregator
import ir.restaurant.management.domain.personnel.AttendanceEventType
import ir.restaurant.management.domain.personnel.AttendancePayrollAdjustment
import ir.restaurant.management.domain.personnel.AttendancePunchDraft
import ir.restaurant.management.domain.personnel.AttendancePunchSequencePolicy
import ir.restaurant.management.domain.personnel.AttendanceCorrectionRecord
import ir.restaurant.management.domain.personnel.AttendancePayrollCalculator
import ir.restaurant.management.domain.personnel.AttendancePayrollPolicy
import ir.restaurant.management.domain.personnel.AttendanceSource
import ir.restaurant.management.domain.personnel.AttendanceSessionCalculator
import ir.restaurant.management.domain.personnel.AttendanceSummary
import ir.restaurant.management.domain.personnel.DailyAttendanceStatus
import ir.restaurant.management.domain.personnel.DailyAttendanceSummaryV2
import ir.restaurant.management.domain.personnel.EmploymentStatus
import ir.restaurant.management.domain.personnel.OvertimeReviewCommand
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.SegregationOfDuties
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Owns attendance command/query rules so PersonnelRepository does not coordinate employee master,
 * attendance event sourcing, corrections, leave, advances, and legacy payroll in one God class.
 *
 * Every write remains transaction-bound to the shared Room database and keeps permission + audit
 * checks in the domain/data boundary rather than relying on UI visibility.
 */
internal class PersonnelAttendanceService(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val auditWriter: LocalAuditEventWriter,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val personnel get() = database.personnelDao()
    private val hr get() = database.hrPayrollDao()
    private val scheduling = PersonnelSchedulingService(database, authorizer, auditWriter, clock)

    suspend fun save(id: Long?, draft: AttendanceDraft): Long {
        val actor = authorizer.require(Permission.ATTENDANCE_EDIT)
        val valid = draft.validated()
        return database.withTransaction {
            val employee = personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            val employeeStatus = EmploymentStatus.fromStoredValue(employee.status)
            require(employeeStatus in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE)) {
                "برای این وضعیت استخدام نمی‌توان حضور ثبت کرد."
            }
            require(employee.hireEpochDay == null || valid.workEpochDay >= employee.hireEpochDay) { "تاریخ حضور قبل از شروع همکاری است." }
            require(employee.terminationEpochDay == null || valid.workEpochDay <= employee.terminationEpochDay) { "تاریخ حضور بعد از خاتمه همکاری است." }
            require(!personnel.attendanceDayLocked(valid.employeeId, valid.workEpochDay)) { "دوره حقوق این روز نهایی شده و حضور و غیاب آن قفل است." }
            if (valid.status != "LEAVE") {
                require(!personnel.hasApprovedLeave(valid.employeeId, valid.workEpochDay)) { "برای این روز مرخصی تأییدشده وجود دارد." }
            }

            val correlation = CorrelationId.forCommand("attendance", GlobalId.parse(valid.commandId)).value
            val baseKey = "attendance:${valid.commandId}:primary"
            hr.attendanceEventByIdempotencyKey(baseKey)?.let { replay ->
                val existing = personnel.attendanceByEmployeeDay(replay.employeeId, replay.businessEpochDay)
                    ?: error("Projection حضور برای فرمان تکراری پیدا نشد.")
                return@withTransaction existing.id
            }

            val calculation = if (valid.status == "PRESENT") calculateShiftAware(valid) else null
            val entity = AttendanceEntity(
                id = id ?: 0,
                employeeId = valid.employeeId,
                workEpochDay = valid.workEpochDay,
                status = valid.status,
                checkInMinute = valid.checkInMinute,
                checkOutMinute = valid.checkOutMinute,
                lateMinutes = calculation?.payableLateMinutes ?: 0,
                rawLateMinutes = calculation?.rawLateMinutes ?: 0,
                payableLateMinutes = calculation?.payableLateMinutes ?: 0,
                earlyLeaveMinutes = calculation?.payableEarlyLeaveMinutes ?: 0,
                rawOvertimeMinutes = calculation?.rawOvertimeMinutes ?: 0,
                approvedOvertimeMinutes = calculation?.approvedOvertimeMinutes ?: 0,
                overtimeMinutes = calculation?.payrollOvertimeMinutes ?: 0,
                notes = valid.notes,
            )

            if (id == null) {
                val attendanceId = personnel.insertAttendance(entity)
                appendInputEvents(valid, actor.id, correlation, baseKey, calculation, employee.branchId, employee.locationId)
                if (calculation != null) syncOvertimeApproval(valid, calculation, actor.id, correlation)
                auditWriter.appendAuthorized(
                    authorizer, "CREATE", "ATTENDANCE", attendanceId, "ثبت حضور و غیاب روزانه",
                    clock(), valid.workEpochDay, valid.notes.ifBlank { "ثبت حضور" }, null,
                    snapshot(entity), correlation,
                )
                attendanceId
            } else {
                val current = personnel.attendanceById(id) ?: error("رکورد حضور پیدا نشد.")
                require(current.employeeId == valid.employeeId) { "پرسنل رکورد حضور قابل تغییر نیست." }
                require(valid.correctionReason.length >= 3) { "دلیل اصلاح حضور و غیاب الزامی است." }
                val before = snapshot(current)
                val after = snapshot(entity)
                val now = clock()
                val correctionId = hr.insertAttendanceCorrection(
                    AttendanceCorrectionEntity(
                        employeeId = valid.employeeId,
                        businessEpochDay = valid.workEpochDay,
                        idempotencyKey = "attendance_correction:${valid.commandId}",
                        beforeSnapshot = before,
                        afterSnapshot = after,
                        reason = valid.correctionReason,
                        status = "SUBMITTED",
                        requestedByActorId = actor.id,
                        approvedByActorId = null,
                        requestedAtEpochMillis = now,
                        approvedAtEpochMillis = null,
                        correlationId = correlation,
                    ),
                )
                hr.insertAttendanceEvent(
                    AttendanceEventEntity(
                        globalId = GlobalId.new().value,
                        idempotencyKey = baseKey,
                        employeeId = valid.employeeId,
                        eventType = AttendanceEventType.MANUAL_ADJUSTMENT.storedValue,
                        businessEpochDay = valid.workEpochDay,
                        timestampEpochMillis = now,
                        minuteOfDay = valid.checkInMinute ?: 0,
                        source = AttendanceSource.MANUAL.storedValue,
                        deviceId = null,
                        locationId = employee.locationId,
                        branchId = employee.branchId,
                        createdByActorId = actor.id,
                        reason = valid.correctionReason,
                        correlationId = correlation,
                    ),
                )
                auditWriter.appendAuthorized(
                    authorizer, "CORRECTION_SUBMIT", "ATTENDANCE", id, "ارسال اصلاح حضور و غیاب برای تأیید",
                    now, valid.workEpochDay, valid.correctionReason, before, after, correlation,
                    "ATTENDANCE_CORRECTION", correctionId,
                )
                id
            }
        }
    }

    suspend fun recordPunch(draft: AttendancePunchDraft): Long {
        val actor = authorizer.require(Permission.ATTENDANCE_EDIT)
        val valid = draft.validated()
        return database.withTransaction {
            val employee = personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            val employeeStatus = EmploymentStatus.fromStoredValue(employee.status)
            require(employeeStatus in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE)) {
                "برای این وضعیت استخدام نمی‌توان پانچ ثبت کرد."
            }
            val idempotencyKey = "attendance_punch:${valid.commandId}"
            hr.attendanceEventByIdempotencyKey(idempotencyKey)?.let { replay ->
                require(replay.employeeId == valid.employeeId && replay.eventType == valid.eventType.storedValue) {
                    "attendance_punch_idempotency_payload_mismatch"
                }
                return@withTransaction replay.id
            }

            val now = clock()
            val local = attendanceLocalPoint(now)
            val latest = hr.latestAttendanceClockEvent(valid.employeeId)?.toAttendanceDomain()
            val decision = AttendancePunchSequencePolicy.decide(
                employeeId = valid.employeeId,
                requestedType = valid.eventType,
                localEpochDay = local.epochDay,
                timestampEpochMillis = now,
                latestClockEvent = latest,
            )
            val businessDay = decision.businessEpochDay
            require(employee.hireEpochDay == null || businessDay >= employee.hireEpochDay) { "تاریخ پانچ قبل از شروع همکاری است." }
            require(employee.terminationEpochDay == null || businessDay <= employee.terminationEpochDay) { "تاریخ پانچ بعد از خاتمه همکاری است." }
            require(!personnel.attendanceDayLocked(valid.employeeId, businessDay)) {
                "دوره حقوق این روز نهایی شده و پانچ جدید مجاز نیست؛ از مسیر اصلاح/Revision استفاده کنید."
            }
            require(!personnel.hasApprovedLeave(valid.employeeId, businessDay)) {
                "برای این روز مرخصی تأییدشده وجود دارد؛ ابتدا تعارض مرخصی را از مسیر کنترل‌شده رفع کنید."
            }

            val correlation = CorrelationId.forCommand("attendance_punch", GlobalId.parse(valid.commandId)).value
            val eventId = hr.insertAttendanceEvent(
                AttendanceEventEntity(
                    globalId = valid.commandId,
                    idempotencyKey = idempotencyKey,
                    employeeId = valid.employeeId,
                    eventType = valid.eventType.storedValue,
                    businessEpochDay = businessDay,
                    timestampEpochMillis = now,
                    minuteOfDay = local.minuteOfDay,
                    source = valid.source.storedValue,
                    deviceId = null,
                    locationId = employee.locationId,
                    branchId = employee.branchId,
                    createdByActorId = actor.id,
                    reason = valid.reason.takeIf { it.isNotBlank() },
                    correlationId = correlation,
                ),
            )
            val dayEvents = hr.attendanceEventsForDay(valid.employeeId, businessDay).map { it.toAttendanceDomain() }
            val planned = scheduling.resolveOrMaterializePlannedShift(valid.employeeId, businessDay)
            val session = AttendanceSessionCalculator.summarize(
                employeeId = valid.employeeId,
                businessEpochDay = businessDay,
                events = dayEvents,
                scheduledBreakMinutes = planned?.breakMinutes ?: 0,
            )
            val allowedOpenSession = valid.eventType == AttendanceEventType.CLOCK_IN &&
                session.anomalies.all { it.type == ir.restaurant.management.domain.personnel.AttendanceAnomalyType.MISSING_CLOCK_OUT }
            require(session.anomalies.isEmpty() || allowedOpenSession) {
                "پانچ باعث ناهنجاری حضور شد: ${session.anomalies.joinToString { it.type.name }}"
            }
            auditWriter.appendAuthorized(
                authorizer,
                if (valid.eventType == AttendanceEventType.CLOCK_IN) "PUNCH_IN" else "PUNCH_OUT",
                "ATTENDANCE_EVENT",
                eventId,
                if (valid.eventType == AttendanceEventType.CLOCK_IN) "ثبت ورود واقعی" else "ثبت خروج واقعی",
                now,
                businessDay,
                valid.reason.ifBlank { valid.eventType.storedValue },
                null,
                "employeeId=${valid.employeeId};type=${valid.eventType.storedValue};timestamp=$now;branchId=${employee.branchId};locationId=${employee.locationId}",
                correlation,
            )
            eventId
        }
    }

    fun events(employeeId: Long, limit: Int): Flow<List<AttendanceEvent>> = flow {
        authorizer.require(Permission.PERSONNEL_VIEW)
        require(employeeId > 0 && limit in 1..200) { "attendance_event_query_invalid" }
        personnel.employeeById(employeeId) ?: error("پرسنل پیدا نشد.")
        emitAll(hr.observeAttendanceEvents(employeeId, limit).map { rows -> rows.map { it.toAttendanceDomain() } })
    }

    fun pendingCorrections(): Flow<List<AttendanceCorrectionRecord>> = flow {
        authorizer.require(Permission.PERSONNEL_VIEW)
        emitAll(hr.observePendingAttendanceCorrections().map { rows -> rows.map { it.toAttendanceCorrectionRecord() } })
    }

    suspend fun dailySummary(employeeId: Long, businessEpochDay: Long): DailyAttendanceSummaryV2 {
        authorizer.require(Permission.PERSONNEL_VIEW)
        val employee = personnel.employeeById(employeeId) ?: error("پرسنل پیدا نشد.")
        hr.latestApprovedAttendanceCorrection(employeeId, businessEpochDay)?.let { correction ->
            return correction.toDailySummary()
        }
        personnel.attendanceByEmployeeDay(employeeId, businessEpochDay)?.let { return it.toDailySummaryV2() }
        val events = hr.attendanceEventsForDay(employeeId, businessEpochDay).sortedBy { it.timestampEpochMillis }
        if (events.isEmpty()) {
            return DailyAttendanceSummaryV2(
                employeeId = employeeId,
                businessEpochDay = businessEpochDay,
                firstInMinute = null,
                lastOutMinute = null,
                workedMinutes = 0,
                breakMinutes = 0,
                lateMinutes = 0,
                earlyLeaveMinutes = 0,
                overtimeMinutes = 0,
                absenceMinutes = 0,
                paidLeaveMinutes = 0,
                unpaidLeaveMinutes = 0,
                status = DailyAttendanceStatus.INCOMPLETE,
                anomalies = emptyList(),
                source = AttendanceSource.SYSTEM,
            )
        }
        val planned = scheduling.resolveOrMaterializePlannedShift(employeeId, businessEpochDay)
        val session = AttendanceSessionCalculator.summarize(
            employeeId = employeeId,
            businessEpochDay = businessEpochDay,
            events = events.map { it.toAttendanceDomain() },
            scheduledBreakMinutes = planned?.breakMinutes ?: 0,
        )
        val firstIn = session.firstIn
        val lastOut = session.lastOut
        if (firstIn == null || lastOut == null || session.anomalies.isNotEmpty()) {
            return DailyAttendanceSummaryV2(
                employeeId = employeeId,
                businessEpochDay = businessEpochDay,
                firstInMinute = firstIn?.minuteOfDay,
                lastOutMinute = lastOut?.minuteOfDay,
                workedMinutes = session.workedMinutes,
                breakMinutes = session.breakMinutes,
                lateMinutes = 0,
                earlyLeaveMinutes = 0,
                overtimeMinutes = 0,
                absenceMinutes = 0,
                paidLeaveMinutes = 0,
                unpaidLeaveMinutes = 0,
                status = if (session.anomalies.isEmpty()) DailyAttendanceStatus.INCOMPLETE else DailyAttendanceStatus.ANOMALY,
                anomalies = session.anomalies,
                source = firstIn?.source ?: events.firstOrNull()?.let { AttendanceSource.fromStoredValue(it.source) } ?: AttendanceSource.SYSTEM,
            )
        }
        val draft = AttendanceDraft(
            employeeId = employeeId,
            workEpochDay = businessEpochDay,
            status = "PRESENT",
            checkInMinute = firstIn.minuteOfDay,
            checkOutMinute = lastOut.minuteOfDay,
            source = firstIn.source,
        ).validated()
        val calc = calculateShiftAware(draft)
        return DailyAttendanceSummaryV2(
            employeeId = employeeId,
            businessEpochDay = businessEpochDay,
            firstInMinute = firstIn.minuteOfDay,
            lastOutMinute = lastOut.minuteOfDay,
            workedMinutes = session.workedMinutes,
            breakMinutes = session.breakMinutes,
            lateMinutes = calc.payableLateMinutes,
            earlyLeaveMinutes = calc.payableEarlyLeaveMinutes,
            overtimeMinutes = calc.payrollOvertimeMinutes,
            absenceMinutes = 0,
            paidLeaveMinutes = 0,
            unpaidLeaveMinutes = 0,
            status = DailyAttendanceStatus.PRESENT,
            anomalies = emptyList(),
            source = firstIn.source,
        )
    }

    suspend fun approveCorrection(correctionId: Long) {
        val actor = authorizer.require(Permission.ATTENDANCE_CORRECTION_APPROVE)
        database.withTransaction {
            val correction = hr.attendanceCorrection(correctionId) ?: error("درخواست اصلاح حضور پیدا نشد.")
            if (correction.status == "APPROVED") return@withTransaction
            require(correction.status == "SUBMITTED") { "درخواست اصلاح در انتظار تأیید نیست." }
            SegregationOfDuties.requireDifferentActors(
                "ATTENDANCE_CORRECTION_APPROVAL", correction.requestedByActorId, actor.id,
            )
            val now = clock()
            require(!personnel.attendanceDayLocked(correction.employeeId, correction.businessEpochDay)) {
                "دوره حقوق این روز نهایی شده و اصلاح باید از مسیر revision حقوق انجام شود."
            }
            check(hr.approveAttendanceCorrection(correction.id, actor.id, now) == 1) { "تأیید اصلاح انجام نشد." }
            val correctionSnapshot = AttendanceCorrectionCodec.decode(correction.afterSnapshot)
            personnel.attendanceByEmployeeDay(correction.employeeId, correction.businessEpochDay)?.let { current ->
                check(
                    personnel.updateAttendance(
                        correctionProjection(current, correctionSnapshot, correction.reason, actor.id, correction.correlationId),
                    ) == 1,
                ) { "به‌روزرسانی Projection حضور پس از تأیید انجام نشد." }
            }
            auditWriter.appendAuthorized(
                authorizer, "CORRECTION_APPROVE", "ATTENDANCE", null, "تأیید اصلاح حضور و غیاب",
                now, correction.businessEpochDay, correction.reason, correction.beforeSnapshot,
                correction.afterSnapshot, correction.correlationId, "ATTENDANCE_CORRECTION", correction.id,
            )
        }
    }

    suspend fun rejectCorrection(correctionId: Long, reason: String) {
        val actor = authorizer.require(Permission.ATTENDANCE_CORRECTION_APPROVE)
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..500) { "دلیل رد اصلاح حضور الزامی است." }
        database.withTransaction {
            val correction = hr.attendanceCorrection(correctionId) ?: error("درخواست اصلاح حضور پیدا نشد.")
            if (correction.status == "REJECTED") return@withTransaction
            require(correction.status == "SUBMITTED") { "درخواست اصلاح در انتظار بررسی نیست." }
            SegregationOfDuties.requireDifferentActors(
                "ATTENDANCE_CORRECTION_REJECTION", correction.requestedByActorId, actor.id,
            )
            val now = clock()
            check(hr.rejectAttendanceCorrection(correction.id, actor.id, now) == 1) { "رد اصلاح حضور انجام نشد." }
            auditWriter.appendAuthorized(
                authorizer, "CORRECTION_REJECT", "ATTENDANCE", null, "رد اصلاح حضور و غیاب",
                now, correction.businessEpochDay, normalizedReason, correction.beforeSnapshot,
                correction.afterSnapshot, correction.correlationId, "ATTENDANCE_CORRECTION", correction.id,
            )
        }
    }

    suspend fun reviewOvertime(command: OvertimeReviewCommand) {
        val actor = authorizer.require(Permission.OVERTIME_APPROVE)
        database.withTransaction {
            val approval = hr.overtimeApprovalById(command.approvalId) ?: error("درخواست اضافه‌کار پیدا نشد.")
            require(approval.status == "PENDING") { "این درخواست اضافه‌کار قبلاً بررسی شده است." }
            val valid = command.validated(approval.rawMinutes)
            val requesterActorId = requireNotNull(approval.requestedByActorId) { "ثبت‌کننده درخواست اضافه‌کار مشخص نیست." }
            SegregationOfDuties.requireDifferentActors("OVERTIME_APPROVAL", requesterActorId, actor.id)
            require(!personnel.attendanceDayLocked(approval.employeeId, approval.businessEpochDay)) {
                "دوره حقوق این روز قفل شده و تغییر اضافه‌کار باید از مسیر Revision انجام شود."
            }
            val status = if (valid.reject) "REJECTED" else "APPROVED"
            val approved = if (valid.reject) 0 else valid.approvedMinutes
            val now = clock()
            check(hr.reviewOvertimeApproval(approval.id, approved, status, valid.reason, actor.id, now) == 1) {
                "ثبت نتیجه اضافه‌کار انجام نشد."
            }
            val attendance = personnel.attendanceByEmployeeDay(approval.employeeId, approval.businessEpochDay)
                ?: error("رکورد حضور مرتبط با اضافه‌کار پیدا نشد.")
            check(
                personnel.updateAttendance(
                    attendance.copy(
                        approvedOvertimeMinutes = approved,
                        overtimeMinutes = approved,
                    ),
                ) == 1,
            ) { "به‌روزرسانی اضافه‌کار تأییدشده در حضور انجام نشد." }
            auditWriter.appendAuthorized(
                authorizer, if (valid.reject) "OVERTIME_REJECT" else "OVERTIME_APPROVE", "OVERTIME_APPROVAL", approval.id,
                "بررسی اضافه‌کار پرسنل", now, approval.businessEpochDay, valid.reason,
                "raw=${approval.rawMinutes}|status=${approval.status}",
                "raw=${approval.rawMinutes}|approved=$approved|status=$status",
                approval.correlationId, "ATTENDANCE", attendance.id,
            )
        }
    }

    suspend fun summary(employeeId: Long, startEpochDay: Long, endEpochDay: Long): AttendanceSummary {
        authorizer.require(Permission.PERSONNEL)
        require(employeeId > 0 && startEpochDay > 0 && endEpochDay >= startEpochDay) { "بازه گزارش معتبر نیست." }
        val rows = personnel.attendanceInRange(employeeId, startEpochDay, endEpochDay)
        return AttendanceSummary(
            employeeId,
            startEpochDay,
            endEpochDay,
            presentDays = rows.count { it.status == "PRESENT" },
            absentDays = rows.count { it.status == "ABSENT" },
            leaveDays = rows.count { it.status == "LEAVE" },
            missionDays = rows.count { it.status == "MISSION" },
            workedMinutes = rows.sumOf { row ->
                attendanceWorkedMinutes(row.checkInMinute, row.checkOutMinute)
            },
            lateMinutes = rows.sumOf { it.lateMinutes },
            overtimeMinutes = rows.sumOf { it.overtimeMinutes },
        )
    }

    suspend fun payrollAdjustment(
        employeeId: Long,
        startEpochDay: Long,
        endEpochDay: Long,
        policy: AttendancePayrollPolicy,
    ): AttendancePayrollAdjustment = AttendancePayrollCalculator.calculate(
        summary(employeeId, startEpochDay, endEpochDay),
        policy,
    )

    private suspend fun calculateShiftAware(draft: AttendanceDraft): AttendanceCalculationEngine.Result {
        val planned = scheduling.resolveOrMaterializePlannedShift(draft.employeeId, draft.workEpochDay)
            ?: error("NO_EFFECTIVE_SHIFT: برای این روز برنامه کاری/شیفت مؤثر تعریف نشده است.")
        val shift = planned.shiftTemplateId?.let { database.managementControlDao().shiftTemplate(it) }
            ?: error("NO_EFFECTIVE_SHIFT: الگوی شیفت برنامه‌ریزی‌شده پیدا نشد.")
        val existingApproval = hr.overtimeApproval(draft.employeeId, draft.workEpochDay)
            ?.takeIf { it.status == "APPROVED" }
        return AttendanceCalculationEngine.calculate(
            businessEpochDay = draft.workEpochDay,
            checkInMinute = requireNotNull(draft.checkInMinute),
            checkOutMinute = requireNotNull(draft.checkOutMinute),
            shift = AttendanceCalculationEngine.ShiftInput(
                plannedStartEpochMillis = planned.plannedStartEpochMillis,
                plannedEndEpochMillis = planned.plannedEndEpochMillis,
                breakMinutes = planned.breakMinutes,
                graceInMinutes = shift.graceInMinutes,
                graceOutMinutes = shift.graceOutMinutes,
                overtimeEligible = shift.overtimeEligible,
                overtimeRequiresApproval = shift.overtimeRequiresApproval,
            ),
            approvedOvertimeMinutes = existingApproval?.approvedMinutes,
        )
    }

    private suspend fun syncOvertimeApproval(
        draft: AttendanceDraft,
        calculation: AttendanceCalculationEngine.Result,
        actorId: Long,
        correlationId: String,
    ) {
        if (calculation.rawOvertimeMinutes <= 0 || calculation.approvedOvertimeMinutes == calculation.rawOvertimeMinutes) return
        val existing = hr.overtimeApproval(draft.employeeId, draft.workEpochDay)
        if (existing == null) {
            hr.insertOvertimeApproval(
                OvertimeApprovalEntity(
                    commandId = draft.commandId,
                    employeeId = draft.employeeId,
                    businessEpochDay = draft.workEpochDay,
                    rawMinutes = calculation.rawOvertimeMinutes,
                    approvedMinutes = 0,
                    rejectedMinutes = 0,
                    status = "PENDING",
                    reason = "در انتظار تأیید اضافه‌کار",
                    requestedByActorId = actorId,
                    reviewedByActorId = null,
                    requestedAtEpochMillis = clock(),
                    reviewedAtEpochMillis = null,
                    correlationId = correlationId,
                ),
            )
        } else if (existing.status != "PENDING" || existing.rawMinutes != calculation.rawOvertimeMinutes) {
            check(hr.reopenOvertimeApproval(existing.id, draft.commandId, calculation.rawOvertimeMinutes, actorId, clock(), correlationId) == 1) {
                "بازگشایی درخواست اضافه‌کار انجام نشد."
            }
        }
    }

    private suspend fun correctionProjection(
        current: AttendanceEntity,
        snapshot: AttendanceCorrectionSnapshot,
        reason: String,
        actorId: Long,
        correlationId: String,
    ): AttendanceEntity {
        val status = snapshot.status.toStoredAttendanceStatus()
        if (status != "PRESENT") return current.copy(
            status = status,
            checkInMinute = snapshot.firstInMinute,
            checkOutMinute = snapshot.lastOutMinute,
            lateMinutes = 0,
            rawLateMinutes = 0,
            payableLateMinutes = 0,
            earlyLeaveMinutes = 0,
            rawOvertimeMinutes = 0,
            approvedOvertimeMinutes = 0,
            overtimeMinutes = 0,
            notes = "اصلاح تأییدشده: $reason",
        )
        val draft = AttendanceDraft(
            employeeId = current.employeeId,
            workEpochDay = current.workEpochDay,
            status = "PRESENT",
            checkInMinute = snapshot.firstInMinute,
            checkOutMinute = snapshot.lastOutMinute,
            notes = current.notes,
            commandId = GlobalId.new().value,
        ).validated()
        val calculation = calculateShiftAware(draft)
        syncOvertimeApproval(draft, calculation, actorId, correlationId)
        return current.copy(
            status = status,
            checkInMinute = draft.checkInMinute,
            checkOutMinute = draft.checkOutMinute,
            lateMinutes = calculation.payableLateMinutes,
            rawLateMinutes = calculation.rawLateMinutes,
            payableLateMinutes = calculation.payableLateMinutes,
            earlyLeaveMinutes = calculation.payableEarlyLeaveMinutes,
            rawOvertimeMinutes = calculation.rawOvertimeMinutes,
            approvedOvertimeMinutes = calculation.approvedOvertimeMinutes,
            overtimeMinutes = calculation.payrollOvertimeMinutes,
            notes = "اصلاح تأییدشده: $reason",
        )
    }

    private suspend fun appendInputEvents(
        draft: AttendanceDraft,
        actorId: Long,
        correlationId: String,
        baseIdempotencyKey: String,
        calculation: AttendanceCalculationEngine.Result?,
        branchId: Long?,
        locationId: Long?,
    ) {
        suspend fun append(type: AttendanceEventType, minuteOfDay: Int, key: String, globalId: String, timestampOverride: Long? = null) {
            hr.insertAttendanceEvent(
                AttendanceEventEntity(
                    globalId = globalId,
                    idempotencyKey = key,
                    employeeId = draft.employeeId,
                    eventType = type.storedValue,
                    businessEpochDay = draft.workEpochDay,
                    timestampEpochMillis = timestampOverride ?: attendanceEventEpochMillis(draft.workEpochDay, minuteOfDay),
                    minuteOfDay = minuteOfDay,
                    source = draft.source.storedValue,
                    deviceId = null,
                    locationId = locationId,
                    branchId = branchId,
                    createdByActorId = actorId,
                    reason = draft.notes.takeIf { it.isNotBlank() },
                    correlationId = correlationId,
                ),
            )
        }
        when (draft.status) {
            "PRESENT" -> {
                append(AttendanceEventType.CLOCK_IN, requireNotNull(draft.checkInMinute), baseIdempotencyKey, draft.commandId, calculation?.actualCheckInEpochMillis)
                append(
                    AttendanceEventType.CLOCK_OUT,
                    requireNotNull(draft.checkOutMinute),
                    "$baseIdempotencyKey:clock_out",
                    GlobalId.new().value,
                    calculation?.actualCheckOutEpochMillis,
                )
            }
            "ABSENT" -> append(AttendanceEventType.ABSENCE_MARK, 0, baseIdempotencyKey, draft.commandId)
            "LEAVE", "MISSION", "HOLIDAY", "OFF_DAY" -> append(
                AttendanceEventType.MANUAL_ADJUSTMENT,
                draft.checkInMinute ?: 0,
                baseIdempotencyKey,
                draft.commandId,
            )
            else -> error("attendance_status_not_exhaustive:${draft.status}")
        }
    }
}

private data class AttendanceLocalPoint(val epochDay: Long, val minuteOfDay: Int)

private fun attendanceLocalPoint(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): AttendanceLocalPoint {
    val local = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    return AttendanceLocalPoint(
        epochDay = local.toLocalDate().toEpochDay(),
        minuteOfDay = local.hour * 60 + local.minute,
    )
}

private fun AttendanceCorrectionEntity.toAttendanceCorrectionRecord() = AttendanceCorrectionRecord(
    id = id,
    employeeId = employeeId,
    businessEpochDay = businessEpochDay,
    reason = reason,
    status = status,
    requestedByActorId = requestedByActorId,
    reviewedByActorId = approvedByActorId,
    requestedAtEpochMillis = requestedAtEpochMillis,
    reviewedAtEpochMillis = approvedAtEpochMillis,
    correlationId = correlationId,
)

private fun AttendanceEventEntity.toAttendanceDomain() = AttendanceEvent(
    id = id,
    employeeId = employeeId,
    eventType = AttendanceEventType.fromStoredValue(eventType),
    businessEpochDay = businessEpochDay,
    timestampEpochMillis = timestampEpochMillis,
    minuteOfDay = minuteOfDay,
    source = AttendanceSource.fromStoredValue(source),
    deviceId = deviceId,
    locationId = locationId,
    createdByActorId = createdByActorId,
    reason = reason,
    correlationId = CorrelationId.parse(correlationId),
    branchId = branchId,
)

private fun AttendanceCorrectionEntity.toDailySummary(): DailyAttendanceSummaryV2 {
    val snapshot = AttendanceCorrectionCodec.decode(afterSnapshot)
    return DailyAttendanceSummaryV2(
        employeeId = employeeId,
        businessEpochDay = businessEpochDay,
        firstInMinute = snapshot.firstInMinute,
        lastOutMinute = snapshot.lastOutMinute,
        workedMinutes = snapshot.workedMinutes,
        breakMinutes = snapshot.breakMinutes,
        lateMinutes = snapshot.lateMinutes,
        earlyLeaveMinutes = snapshot.earlyLeaveMinutes,
        overtimeMinutes = snapshot.overtimeMinutes,
        absenceMinutes = snapshot.absenceMinutes,
        paidLeaveMinutes = 0,
        unpaidLeaveMinutes = 0,
        status = snapshot.status,
        anomalies = emptyList(),
        source = AttendanceSource.MANUAL,
    )
}

private fun AttendanceEntity.toDailySummaryV2(): DailyAttendanceSummaryV2 {
    val worked = attendanceWorkedMinutes(checkInMinute, checkOutMinute)
    val typedStatus = when (status) {
        "PRESENT" -> DailyAttendanceStatus.PRESENT
        "ABSENT" -> DailyAttendanceStatus.ABSENT
        "MISSION" -> DailyAttendanceStatus.MISSION
        "HOLIDAY" -> DailyAttendanceStatus.HOLIDAY
        "LEAVE" -> DailyAttendanceStatus.INCOMPLETE
        else -> DailyAttendanceStatus.INCOMPLETE
    }
    return DailyAttendanceSummaryV2(
        employeeId = employeeId,
        businessEpochDay = workEpochDay,
        firstInMinute = checkInMinute,
        lastOutMinute = checkOutMinute,
        workedMinutes = worked,
        breakMinutes = 0,
        lateMinutes = lateMinutes,
        earlyLeaveMinutes = earlyLeaveMinutes,
        overtimeMinutes = approvedOvertimeMinutes,
        absenceMinutes = 0,
        paidLeaveMinutes = 0,
        unpaidLeaveMinutes = 0,
        status = typedStatus,
        anomalies = emptyList(),
        source = AttendanceSource.LEGACY,
    )
}

private fun snapshot(entity: AttendanceEntity): String = AttendanceCorrectionCodec.encode(
    AttendanceCorrectionSnapshot(
        firstInMinute = entity.checkInMinute,
        lastOutMinute = entity.checkOutMinute,
        workedMinutes = attendanceWorkedMinutes(entity.checkInMinute, entity.checkOutMinute),
        breakMinutes = 0,
        lateMinutes = entity.payableLateMinutes,
        earlyLeaveMinutes = entity.earlyLeaveMinutes,
        overtimeMinutes = entity.approvedOvertimeMinutes,
        absenceMinutes = 0,
        status = when (entity.status) {
            "PRESENT" -> DailyAttendanceStatus.PRESENT
            "ABSENT" -> DailyAttendanceStatus.ABSENT
            "MISSION" -> DailyAttendanceStatus.MISSION
            "HOLIDAY" -> DailyAttendanceStatus.HOLIDAY
            "LEAVE" -> DailyAttendanceStatus.INCOMPLETE
            else -> DailyAttendanceStatus.INCOMPLETE
        },
    ),
)

private fun DailyAttendanceStatus.toStoredAttendanceStatus(): String = when (this) {
    DailyAttendanceStatus.PRESENT -> "PRESENT"
    DailyAttendanceStatus.ABSENT -> "ABSENT"
    DailyAttendanceStatus.PAID_LEAVE, DailyAttendanceStatus.UNPAID_LEAVE -> "LEAVE"
    DailyAttendanceStatus.MISSION -> "MISSION"
    DailyAttendanceStatus.HOLIDAY -> "HOLIDAY"
    DailyAttendanceStatus.INCOMPLETE, DailyAttendanceStatus.ANOMALY -> "PRESENT"
}

private fun attendanceWorkedMinutes(checkInMinute: Int?, checkOutMinute: Int?): Int {
    if (checkInMinute == null || checkOutMinute == null) return 0
    val normalizedOut = if (checkOutMinute <= checkInMinute) checkOutMinute + 1440 else checkOutMinute
    return (normalizedOut - checkInMinute).coerceAtLeast(0)
}

private fun attendanceEventEpochMillis(epochDay: Long, minuteOfDay: Int): Long = BusinessCalendar.epochMillisAtMinute(epochDay, minuteOfDay)

package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Personnel/HR/Attendance/Payroll 2.1: scheduling truth, overtime approval, attendance facts. */
internal val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS shift_templates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                startMinute INTEGER NOT NULL,
                endMinute INTEGER NOT NULL,
                crossesMidnight INTEGER NOT NULL,
                plannedWorkMinutes INTEGER NOT NULL,
                breakMinutes INTEGER NOT NULL,
                graceInMinutes INTEGER NOT NULL,
                graceOutMinutes INTEGER NOT NULL,
                overtimeEligible INTEGER NOT NULL,
                overtimeRequiresApproval INTEGER NOT NULL,
                nightShift INTEGER NOT NULL,
                active INTEGER NOT NULL,
                branchId INTEGER,
                notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                createdBy TEXT NOT NULL,
                updatedBy TEXT NOT NULL
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_shift_templates_code ON shift_templates(code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_templates_category ON shift_templates(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_templates_active ON shift_templates(active)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_templates_branchId ON shift_templates(branchId)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS work_schedules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                name TEXT NOT NULL,
                patternType TEXT NOT NULL,
                cycleLengthDays INTEGER NOT NULL,
                effectiveFromEpochDay INTEGER NOT NULL,
                effectiveToEpochDay INTEGER,
                active INTEGER NOT NULL,
                branchName TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                createdBy TEXT NOT NULL,
                updatedBy TEXT NOT NULL
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_work_schedules_code ON work_schedules(code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_schedules_patternType ON work_schedules(patternType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_schedules_active ON work_schedules(active)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_schedules_effectiveFromEpochDay ON work_schedules(effectiveFromEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_schedules_effectiveToEpochDay ON work_schedules(effectiveToEpochDay)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS work_schedule_days (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scheduleId INTEGER NOT NULL,
                sequenceDay INTEGER NOT NULL,
                dayOfWeek INTEGER,
                shiftTemplateId INTEGER,
                isOffDay INTEGER NOT NULL,
                overrideStartMinute INTEGER,
                overrideEndMinute INTEGER,
                notes TEXT NOT NULL,
                FOREIGN KEY(scheduleId) REFERENCES work_schedules(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(shiftTemplateId) REFERENCES shift_templates(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_work_schedule_days_scheduleId_sequenceDay ON work_schedule_days(scheduleId,sequenceDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_schedule_days_shiftTemplateId ON work_schedule_days(shiftTemplateId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_schedule_days_dayOfWeek ON work_schedule_days(dayOfWeek)")

        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN shiftTemplateId INTEGER")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN scheduleId INTEGER")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN plannedStartEpochMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN plannedEndEpochMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN breakMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN status TEXT NOT NULL DEFAULT 'PUBLISHED'")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN source TEXT NOT NULL DEFAULT 'LEGACY'")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN overrideReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN createdBy TEXT NOT NULL DEFAULT 'MIGRATION'")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN updatedBy TEXT NOT NULL DEFAULT 'MIGRATION'")
        db.execSQL("ALTER TABLE planned_shifts ADD COLUMN auditRef TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """UPDATE planned_shifts
               SET plannedStartEpochMillis=(epochDay*86400000)+(startMinute*60000),
                   plannedEndEpochMillis=((epochDay + CASE WHEN endMinute<=startMinute THEN 1 ELSE 0 END)*86400000)+(endMinute*60000)
               WHERE plannedStartEpochMillis=0 OR plannedEndEpochMillis=0""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_shifts_shiftTemplateId ON planned_shifts(shiftTemplateId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_shifts_scheduleId ON planned_shifts(scheduleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_shifts_status ON planned_shifts(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_shifts_employeeId_epochDay_plannedStartEpochMillis ON planned_shifts(employeeId,epochDay,plannedStartEpochMillis)")

        db.execSQL("ALTER TABLE employment_contract_versions ADD COLUMN workScheduleId INTEGER")
        db.execSQL("ALTER TABLE employment_contract_versions ADD COLUMN defaultShiftTemplateId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employment_contract_versions_workScheduleId ON employment_contract_versions(workScheduleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employment_contract_versions_defaultShiftTemplateId ON employment_contract_versions(defaultShiftTemplateId)")

        db.execSQL("ALTER TABLE attendance ADD COLUMN earlyLeaveMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE attendance ADD COLUMN rawLateMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE attendance ADD COLUMN payableLateMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE attendance ADD COLUMN rawOvertimeMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE attendance ADD COLUMN approvedOvertimeMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE attendance SET rawLateMinutes=lateMinutes, payableLateMinutes=lateMinutes, rawOvertimeMinutes=overtimeMinutes, approvedOvertimeMinutes=overtimeMinutes")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS overtime_approvals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                commandId TEXT NOT NULL,
                employeeId INTEGER NOT NULL,
                businessEpochDay INTEGER NOT NULL,
                rawMinutes INTEGER NOT NULL,
                approvedMinutes INTEGER NOT NULL,
                rejectedMinutes INTEGER NOT NULL,
                status TEXT NOT NULL,
                reason TEXT NOT NULL,
                requestedByActorId INTEGER,
                reviewedByActorId INTEGER,
                requestedAtEpochMillis INTEGER NOT NULL,
                reviewedAtEpochMillis INTEGER,
                correlationId TEXT NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_overtime_approvals_commandId ON overtime_approvals(commandId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_overtime_approvals_employeeId_businessEpochDay ON overtime_approvals(employeeId,businessEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_overtime_approvals_status ON overtime_approvals(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_overtime_approvals_reviewedByActorId ON overtime_approvals(reviewedByActorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_overtime_approvals_correlationId ON overtime_approvals(correlationId)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS hr_documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                employeeId INTEGER NOT NULL,
                documentType TEXT NOT NULL,
                displayName TEXT NOT NULL,
                contentUri TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                issueEpochDay INTEGER,
                expiryEpochDay INTEGER,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                createdByActorId INTEGER NOT NULL,
                correlationId TEXT NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_hr_documents_employeeId ON hr_documents(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_hr_documents_documentType ON hr_documents(documentType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_hr_documents_status ON hr_documents(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_hr_documents_expiryEpochDay ON hr_documents(expiryEpochDay)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_hr_documents_employeeId_contentUri ON hr_documents(employeeId,contentUri)")

        // Cross-table referential rules for legacy tables that cannot receive SQLite FKs via ALTER TABLE.
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS trg_contract_schedule_insert
               BEFORE INSERT ON employment_contract_versions
               WHEN NEW.workScheduleId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM work_schedules WHERE id=NEW.workScheduleId)
               BEGIN SELECT RAISE(ABORT,'invalid_work_schedule'); END""",
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS trg_contract_schedule_update
               BEFORE UPDATE OF workScheduleId ON employment_contract_versions
               WHEN NEW.workScheduleId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM work_schedules WHERE id=NEW.workScheduleId)
               BEGIN SELECT RAISE(ABORT,'invalid_work_schedule'); END""",
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS trg_contract_shift_insert
               BEFORE INSERT ON employment_contract_versions
               WHEN NEW.defaultShiftTemplateId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shift_templates WHERE id=NEW.defaultShiftTemplateId)
               BEGIN SELECT RAISE(ABORT,'invalid_shift_template'); END""",
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS trg_contract_shift_update
               BEFORE UPDATE OF defaultShiftTemplateId ON employment_contract_versions
               WHEN NEW.defaultShiftTemplateId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shift_templates WHERE id=NEW.defaultShiftTemplateId)
               BEGIN SELECT RAISE(ABORT,'invalid_shift_template'); END""",
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS trg_planned_shift_refs_insert
               BEFORE INSERT ON planned_shifts
               WHEN NOT EXISTS(SELECT 1 FROM employees WHERE id=NEW.employeeId)
                 OR (NEW.shiftTemplateId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shift_templates WHERE id=NEW.shiftTemplateId))
                 OR (NEW.scheduleId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM work_schedules WHERE id=NEW.scheduleId))
               BEGIN SELECT RAISE(ABORT,'invalid_planned_shift_reference'); END""",
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS trg_planned_shift_refs_update
               BEFORE UPDATE OF employeeId,shiftTemplateId,scheduleId ON planned_shifts
               WHEN NOT EXISTS(SELECT 1 FROM employees WHERE id=NEW.employeeId)
                 OR (NEW.shiftTemplateId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shift_templates WHERE id=NEW.shiftTemplateId))
                 OR (NEW.scheduleId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM work_schedules WHERE id=NEW.scheduleId))
               BEGIN SELECT RAISE(ABORT,'invalid_planned_shift_reference'); END""",
        )
    }
}

package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HrPayrollMigration43To44Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "hr-payroll-43-44.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun preservesLegacyFactsWithoutFabricatingDetailedPayrollOrClockEvents() {
        openDatabase(43).use { helper ->
            val db = helper.writableDatabase
            createVersion43PersonnelSubset(db)
            seedLegacyFacts(db)
        }

        openDatabase(44).use { helper ->
            val db = helper.writableDatabase

            assertEquals("EMP-000001", stringScalar(db, "SELECT employeeCode FROM employees WHERE id=1"))
            assertEquals("ACTIVE", stringScalar(db, "SELECT status FROM employees WHERE id=1"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM employees"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM employee_private_profiles WHERE employeeId=1"))
            assertEquals("4321", stringScalar(db, "SELECT bankAccountLast4 FROM employee_private_profiles WHERE employeeId=1"))
            assertEquals(1L, scalar(db, "SELECT nationalId IS NULL AND insuranceNumber IS NULL AND bankCard IS NULL FROM employees WHERE id=1"))

            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM employment_assignments WHERE employeeId=1"))
            assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM employment_contract_versions WHERE employeeId=1"))
            assertTrue(scalar(db, "SELECT COUNT(*) FROM hr_payroll_migration_anomalies WHERE code='OVERLAPPING_LEGACY_CONTRACT'") > 0)

            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM attendance WHERE id=1 AND employeeId=1"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM attendance_events"))
            assertEquals("SUBMITTED", stringScalar(db, "SELECT status FROM leaves WHERE id=1"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM employee_advances WHERE id=1 AND settledAmountRial=2000000"))

            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM payroll_periods WHERE periodKey='LEGACY-1404-12'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM payroll_batches WHERE documentNumber='LEG-PAY-1404-12'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM payroll_payslips WHERE legacyPayrollRunId=1"))
            assertEquals(0L, scalar(db, "SELECT componentDetailComplete FROM payroll_payslips WHERE legacyPayrollRunId=1"))
            assertEquals("LEGACY_MIGRATION", stringScalar(db, "SELECT source FROM payroll_payslips WHERE legacyPayrollRunId=1"))
            assertEquals(0L, scalar(db, "SELECT detailComplete FROM payroll_snapshots WHERE payslipId=(SELECT id FROM payroll_payslips WHERE legacyPayrollRunId=1)"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM payroll_components"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM payroll_payments"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM hr_payroll_migration_anomalies WHERE entityType='PAYROLL_RUN' AND entityId=1 AND code='LEGACY_PAYSLIP_INCOMPLETE'"))

            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_payroll_payslips_employeeId_periodId_revisionNo'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='trg_payroll_snapshot_no_update'"))

            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE payroll_snapshots SET netPayRial=1 WHERE payslipId=(SELECT id FROM payroll_payslips WHERE legacyPayrollRunId=1)")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM payroll_payslips WHERE legacyPayrollRunId=1")
            }
            assertThrows(Exception::class.java) {
                db.execSQL(
                    """INSERT INTO employment_contract_versions(
                        employeeId,contractNumber,versionNo,replacesContractId,contractType,effectiveFromEpochDay,
                        effectiveToEpochDay,baseSalaryRial,standardDailyMinutes,standardWeeklyMinutes,
                        overtimePolicyId,payrollPolicyId,jobTitleSnapshot,departmentSnapshot,branchSnapshot,status,
                        notes,createdAtEpochMillis,createdByActorId,approvedAtEpochMillis,approvedByActorId,correlationId,source
                    ) VALUES(1,'CTR-CONFLICT',1,NULL,'FIXED_TERM',20025,20075,50000000,480,2880,NULL,NULL,
                        'آشپز','آشپزخانه','مرکزی','ACTIVE','',1000,NULL,NULL,NULL,'test:contract:conflict','NATIVE')""",
                )
            }
        }
    }

    private fun seedLegacyFacts(db: SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO employees(
                id,name,fatherName,employeeCode,jobTitle,branchName,phone,nationalId,birthEpochDay,
                hireEpochDay,insuranceNumber,bankCard,address,emergencyContact,monthlySalaryRial,
                leaveBalanceMicros,status,createdAtEpochMillis,updatedAtEpochMillis
            ) VALUES(1,'علی رضایی','حسن',NULL,'آشپز','مرکزی','09120000000','0012345678',10000,
                19000,'INS-1','6037997512344321','تهران','09121111111',30000000,5000000,
                'ACTIVE',1000,2000)""",
        )
        db.execSQL("INSERT INTO attendance(id,employeeId,workEpochDay,status,checkInMinute,checkOutMinute,lateMinutes,overtimeMinutes,notes) VALUES(1,1,20010,'PRESENT',480,960,0,0,'legacy daily fact')")
        db.execSQL(
            """INSERT INTO leaves(
                id,employeeId,startEpochDay,endEpochDay,daysMicros,leaveType,status,notes,requestedBy,
                reviewedBy,reviewNotes,reviewedAtEpochMillis,cancelledAtEpochMillis,createdAtEpochMillis,updatedAtEpochMillis
            ) VALUES(1,1,20020,20020,1000000,'ANNUAL','PENDING','legacy leave','manager',NULL,'',NULL,NULL,1000,1000)""",
        )
        db.execSQL("INSERT INTO payroll_policies(id,title,effectiveFromEpochDay,effectiveToEpochDay,overtimeHourlyRateRial,absenceDailyDeductionRial,lateMinuteDeductionRial,createdBy,createdAtEpochMillis) VALUES(1,'Legacy policy',19000,NULL,100000,1000000,1000,'SYSTEM',1000)")
        db.execSQL("INSERT INTO employee_contracts(id,employeeId,contractType,startEpochDay,endEpochDay,baseSalaryRial,dailyWorkMinutes,weeklyWorkDays,status,notes,createdAtEpochMillis,updatedAtEpochMillis) VALUES(1,1,'FIXED_TERM',19000,20100,30000000,480,6,'ACTIVE','legacy A',1000,1000)")
        db.execSQL("INSERT INTO employee_contracts(id,employeeId,contractType,startEpochDay,endEpochDay,baseSalaryRial,dailyWorkMinutes,weeklyWorkDays,status,notes,createdAtEpochMillis,updatedAtEpochMillis) VALUES(2,1,'FIXED_TERM',20000,NULL,35000000,480,6,'ACTIVE','legacy overlapping B',1100,1100)")
        db.execSQL("INSERT INTO employee_advances(id,employeeId,amountRial,advanceEpochDay,paymentMethod,journalEntryId,settledAmountRial,status,notes,createdAtEpochMillis,updatedAtEpochMillis) VALUES(1,1,10000000,19900,'BANK',700,2000000,'OPEN','legacy advance',1000,1000)")
        db.execSQL(
            """INSERT INTO payroll_runs(
                id,employeeId,periodYear,periodMonth,revisionNo,baseSalaryRial,overtimeRial,bonusRial,
                deductionsRial,advanceDeductionRial,periodStartEpochDay,periodEndEpochDay,payrollPolicyId,
                automaticOvertimeRial,attendanceDeductionRial,insuranceRial,taxRial,netPayRial,
                paymentEpochDay,paymentMethod,journalEntryId,status,approvedBy,approvedAtEpochMillis,
                reversalEpochDay,reversalReason,reversalJournalEntryId,reversedBy,notes,createdAtEpochMillis,
                globalId,createdByActorId,approvedByActorId,correlationId
            ) VALUES(1,1,1404,12,1,30000000,2000000,1000000,500000,1000000,20000,20029,1,
                2000000,0,1500000,500000,29500000,20035,'BANK',701,'PAID','مالک',3000,
                NULL,'',NULL,NULL,'legacy payroll',2000,'legacy:payroll_run:1',2,3,'legacy:payroll_run:1')""",
        )
    }

    private fun createVersion43PersonnelSubset(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE employees(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,fatherName TEXT NOT NULL DEFAULT '',
                employeeCode TEXT,jobTitle TEXT NOT NULL,branchName TEXT NOT NULL DEFAULT '',phone TEXT NOT NULL DEFAULT '',
                nationalId TEXT,birthEpochDay INTEGER,hireEpochDay INTEGER,insuranceNumber TEXT,bankCard TEXT,
                address TEXT NOT NULL DEFAULT '',emergencyContact TEXT NOT NULL DEFAULT '',monthlySalaryRial INTEGER NOT NULL,
                leaveBalanceMicros INTEGER NOT NULL,status TEXT NOT NULL,createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX index_employees_employeeCode ON employees(employeeCode)")
        db.execSQL(
            """CREATE TABLE attendance(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,workEpochDay INTEGER NOT NULL,
                status TEXT NOT NULL,checkInMinute INTEGER,checkOutMinute INTEGER,lateMinutes INTEGER NOT NULL,
                overtimeMinutes INTEGER NOT NULL,notes TEXT NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE leaves(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,startEpochDay INTEGER NOT NULL,
                endEpochDay INTEGER NOT NULL,daysMicros INTEGER NOT NULL,leaveType TEXT NOT NULL,status TEXT NOT NULL,
                notes TEXT NOT NULL,requestedBy TEXT NOT NULL,reviewedBy TEXT,reviewNotes TEXT NOT NULL,
                reviewedAtEpochMillis INTEGER,cancelledAtEpochMillis INTEGER,createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE payroll_policies(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title TEXT NOT NULL,effectiveFromEpochDay INTEGER NOT NULL,
                effectiveToEpochDay INTEGER,overtimeHourlyRateRial INTEGER NOT NULL,absenceDailyDeductionRial INTEGER NOT NULL,
                lateMinuteDeductionRial INTEGER NOT NULL,createdBy TEXT NOT NULL,createdAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE employee_contracts(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,contractType TEXT NOT NULL,
                startEpochDay INTEGER NOT NULL,endEpochDay INTEGER,baseSalaryRial INTEGER NOT NULL,
                dailyWorkMinutes INTEGER NOT NULL,weeklyWorkDays INTEGER NOT NULL,status TEXT NOT NULL,notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,updatedAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE employee_advances(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,amountRial INTEGER NOT NULL,
                advanceEpochDay INTEGER NOT NULL,paymentMethod TEXT NOT NULL,journalEntryId INTEGER NOT NULL,
                settledAmountRial INTEGER NOT NULL,status TEXT NOT NULL,notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,updatedAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE payroll_runs(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,periodYear INTEGER NOT NULL,
                periodMonth INTEGER NOT NULL,revisionNo INTEGER NOT NULL,baseSalaryRial INTEGER NOT NULL,
                overtimeRial INTEGER NOT NULL,bonusRial INTEGER NOT NULL,deductionsRial INTEGER NOT NULL,
                advanceDeductionRial INTEGER NOT NULL,periodStartEpochDay INTEGER NOT NULL,periodEndEpochDay INTEGER NOT NULL,
                payrollPolicyId INTEGER,automaticOvertimeRial INTEGER NOT NULL,attendanceDeductionRial INTEGER NOT NULL,
                insuranceRial INTEGER NOT NULL,taxRial INTEGER NOT NULL,netPayRial INTEGER NOT NULL,
                paymentEpochDay INTEGER NOT NULL,paymentMethod TEXT NOT NULL,journalEntryId INTEGER NOT NULL,
                status TEXT NOT NULL,approvedBy TEXT,approvedAtEpochMillis INTEGER,reversalEpochDay INTEGER,
                reversalReason TEXT NOT NULL,reversalJournalEntryId INTEGER,reversedBy TEXT,notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,globalId TEXT NOT NULL,createdByActorId INTEGER,
                approvedByActorId INTEGER,correlationId TEXT NOT NULL
            )""",
        )
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    private fun stringScalar(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }

    private fun openDatabase(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }

            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 43 && newVersion == 44) MIGRATION_43_44.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
    }
}

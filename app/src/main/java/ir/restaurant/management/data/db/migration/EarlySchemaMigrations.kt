package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Immutable early-version upgrade edges. SQL is preserved for migration compatibility. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL,
                creditLimitRial INTEGER NOT NULL, notes TEXT NOT NULL, isActive INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customers_name ON customers(name)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceNo TEXT NOT NULL,
                customerId INTEGER, customerNameSnapshot TEXT NOT NULL, saleEpochDay INTEGER NOT NULL,
                dueEpochDay INTEGER, channel TEXT NOT NULL, subtotalRial INTEGER NOT NULL,
                discountRial INTEGER NOT NULL, deliveryRial INTEGER NOT NULL, totalRial INTEGER NOT NULL,
                paidRial INTEGER NOT NULL, paymentStatus TEXT NOT NULL, paymentMethod TEXT, notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_invoiceNo ON sales(invoiceNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_customerId ON sales(customerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_saleEpochDay ON sales(saleEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_dueEpochDay ON sales(dueEpochDay)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sale_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, saleId INTEGER NOT NULL,
                productNameSnapshot TEXT NOT NULL, quantityMicros INTEGER NOT NULL,
                unitPriceRial INTEGER NOT NULL, lineTotalRial INTEGER NOT NULL,
                FOREIGN KEY(saleId) REFERENCES sales(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_lines_saleId ON sale_lines(saleId)")
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sale_payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                saleId INTEGER NOT NULL,
                amountRial INTEGER NOT NULL,
                paymentEpochDay INTEGER NOT NULL,
                method TEXT NOT NULL,
                journalEntryId INTEGER NOT NULL,
                reversalOfPaymentId INTEGER,
                createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(saleId) REFERENCES sales(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_payments_saleId ON sale_payments(saleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_payments_reversalOfPaymentId ON sale_payments(reversalOfPaymentId)")
    }
}


internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS menu_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, category TEXT NOT NULL, salePriceRial INTEGER NOT NULL,
                isActive INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_menu_items_name ON menu_items(name)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS recipe_ingredients (
                menuItemId INTEGER NOT NULL, inventoryItemId INTEGER NOT NULL, quantityMicrosPerUnit INTEGER NOT NULL,
                PRIMARY KEY(menuItemId, inventoryItemId),
                FOREIGN KEY(menuItemId) REFERENCES menu_items(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(inventoryItemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_ingredients_inventoryItemId ON recipe_ingredients(inventoryItemId)")
    }
}


internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN reversedAtEpochDay INTEGER")
        db.execSQL("ALTER TABLE sale_lines ADD COLUMN menuItemId INTEGER")
        db.execSQL("ALTER TABLE sale_lines ADD COLUMN costOfGoodsRial INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_lines_menuItemId ON sale_lines(menuItemId)")
    }
}


internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN replacementOfSaleId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_replacementOfSaleId ON sales(replacementOfSaleId)")
    }
}


internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_counts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemId INTEGER NOT NULL,
                previousQuantityMicros INTEGER NOT NULL, countedQuantityMicros INTEGER NOT NULL,
                previousValueRial INTEGER NOT NULL, countedValueRial INTEGER NOT NULL,
                countEpochDay INTEGER NOT NULL, reason TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_counts_itemId ON inventory_counts(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_counts_countEpochDay ON inventory_counts(countEpochDay)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, action TEXT NOT NULL, entityType TEXT NOT NULL,
                entityId INTEGER, description TEXT NOT NULL, actor TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_createdAtEpochMillis ON audit_logs(createdAtEpochMillis)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_entityType_entityId ON audit_logs(entityType, entityId)")
    }
}


internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS app_users (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL, displayName TEXT NOT NULL,
                pinHash TEXT NOT NULL, role TEXT NOT NULL, isActive INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_app_users_username ON app_users(username)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS app_session (
                singletonId INTEGER NOT NULL PRIMARY KEY, currentUserId INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL
            )
        """.trimIndent())
        // No default credentials are created. The first owner must be configured explicitly in the UI.
    }
}


internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS payroll_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, employeeId INTEGER NOT NULL,
                periodYear INTEGER NOT NULL, periodMonth INTEGER NOT NULL, baseSalaryRial INTEGER NOT NULL,
                overtimeRial INTEGER NOT NULL, bonusRial INTEGER NOT NULL, deductionsRial INTEGER NOT NULL,
                insuranceRial INTEGER NOT NULL, taxRial INTEGER NOT NULL, netPayRial INTEGER NOT NULL,
                paymentEpochDay INTEGER NOT NULL, paymentMethod TEXT NOT NULL, journalEntryId INTEGER NOT NULL,
                status TEXT NOT NULL, notes TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_runs_employeeId_periodYear_periodMonth ON payroll_runs(employeeId, periodYear, periodMonth)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_runs_paymentEpochDay ON payroll_runs(paymentEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_runs_journalEntryId ON payroll_runs(journalEntryId)")
    }
}

internal val MIGRATION_9_10 = object : Migration(9, 10) {
 override fun migrate(db: SupportSQLiteDatabase) {
  db.execSQL("""CREATE TABLE IF NOT EXISTS fixed_assets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, assetCode TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, purchaseEpochDay INTEGER NOT NULL, purchaseCostRial INTEGER NOT NULL, salvageValueRial INTEGER NOT NULL, usefulLifeMonths INTEGER NOT NULL, accumulatedDepreciationRial INTEGER NOT NULL, location TEXT NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)""")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_assets_assetCode ON fixed_assets(assetCode)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_assets_status ON fixed_assets(status)")
  db.execSQL("""CREATE TABLE IF NOT EXISTS asset_depreciations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, assetId INTEGER NOT NULL, periodYear INTEGER NOT NULL, periodMonth INTEGER NOT NULL, amountRial INTEGER NOT NULL, journalEntryId INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(assetId) REFERENCES fixed_assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_asset_depreciations_assetId_periodYear_periodMonth ON asset_depreciations(assetId,periodYear,periodMonth)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_depreciations_journalEntryId ON asset_depreciations(journalEntryId)")
  db.execSQL("INSERT OR IGNORE INTO accounts(code,name,type,isSystem,isActive) VALUES ('1502','استهلاک انباشته دارایی‌ها','ASSET',1,1)")
 }
}


internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS loyalty_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customerId INTEGER NOT NULL,
                pointsDelta INTEGER NOT NULL, reason TEXT NOT NULL, referenceType TEXT NOT NULL,
                referenceId INTEGER, createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_loyalty_transactions_customerId ON loyalty_transactions(customerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_loyalty_transactions_createdAtEpochMillis ON loyalty_transactions(createdAtEpochMillis)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS customer_followups (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customerId INTEGER NOT NULL, title TEXT NOT NULL,
                dueEpochDay INTEGER NOT NULL, notes TEXT NOT NULL, isDone INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL, completedAtEpochMillis INTEGER,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_followups_customerId ON customer_followups(customerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_followups_dueEpochDay ON customer_followups(dueEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_followups_isDone ON customer_followups(isDone)")
    }
}


internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS app_alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sourceType TEXT NOT NULL, sourceId INTEGER NOT NULL,
                title TEXT NOT NULL, message TEXT NOT NULL, severity TEXT NOT NULL,
                dueEpochDay INTEGER, isRead INTEGER NOT NULL, isDismissed INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_app_alerts_sourceType_sourceId ON app_alerts(sourceType, sourceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_alerts_severity ON app_alerts(severity)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_alerts_isRead ON app_alerts(isRead)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_alerts_isDismissed ON app_alerts(isDismissed)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_alerts_dueEpochDay ON app_alerts(dueEpochDay)")
    }
}


internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE employees ADD COLUMN employeeCode TEXT")
        db.execSQL("ALTER TABLE employees ADD COLUMN branchName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE employees ADD COLUMN hireEpochDay INTEGER")
        db.execSQL("ALTER TABLE employees ADD COLUMN insuranceNumber TEXT")
        db.execSQL("ALTER TABLE employees ADD COLUMN address TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE employees ADD COLUMN emergencyContact TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_employees_employeeCode ON employees(employeeCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_branchName ON employees(branchName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_status ON employees(status)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS employee_contracts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                employeeId INTEGER NOT NULL, contractType TEXT NOT NULL,
                startEpochDay INTEGER NOT NULL, endEpochDay INTEGER,
                baseSalaryRial INTEGER NOT NULL, dailyWorkMinutes INTEGER NOT NULL,
                weeklyWorkDays INTEGER NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_contracts_employeeId ON employee_contracts(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_contracts_startEpochDay ON employee_contracts(startEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_contracts_endEpochDay ON employee_contracts(endEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_contracts_status ON employee_contracts(status)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS employee_advances (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                employeeId INTEGER NOT NULL, amountRial INTEGER NOT NULL,
                advanceEpochDay INTEGER NOT NULL, paymentMethod TEXT NOT NULL,
                journalEntryId INTEGER NOT NULL, settledAmountRial INTEGER NOT NULL,
                status TEXT NOT NULL, notes TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_advances_employeeId ON employee_advances(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_advances_advanceEpochDay ON employee_advances(advanceEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_advances_journalEntryId ON employee_advances(journalEntryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_advances_status ON employee_advances(status)")
    }
}


internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE leaves ADD COLUMN requestedBy TEXT NOT NULL DEFAULT 'SYSTEM'")
        db.execSQL("ALTER TABLE leaves ADD COLUMN reviewedBy TEXT")
        db.execSQL("ALTER TABLE leaves ADD COLUMN reviewNotes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE leaves ADD COLUMN reviewedAtEpochMillis INTEGER")
        db.execSQL("ALTER TABLE leaves ADD COLUMN cancelledAtEpochMillis INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_leaves_status ON leaves(status)")
    }
}


internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS performance_goals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                employeeId INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                weightPercent INTEGER NOT NULL,
                targetValue REAL,
                unit TEXT NOT NULL,
                periodStartEpochDay INTEGER NOT NULL,
                periodEndEpochDay INTEGER NOT NULL,
                status TEXT NOT NULL,
                createdBy TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_performance_goals_employeeId ON performance_goals(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_performance_goals_periodStartEpochDay ON performance_goals(periodStartEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_performance_goals_periodEndEpochDay ON performance_goals(periodEndEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_performance_goals_status ON performance_goals(status)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS performance_reviews (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                employeeId INTEGER NOT NULL,
                periodStartEpochDay INTEGER NOT NULL,
                periodEndEpochDay INTEGER NOT NULL,
                reviewerName TEXT NOT NULL,
                finalScoreBasisPoints INTEGER NOT NULL,
                status TEXT NOT NULL,
                managerComment TEXT NOT NULL,
                employeeComment TEXT NOT NULL,
                submittedAtEpochMillis INTEGER,
                completedAtEpochMillis INTEGER,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_performance_reviews_employeeId ON performance_reviews(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_performance_reviews_periodStartEpochDay ON performance_reviews(periodStartEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_performance_reviews_periodEndEpochDay ON performance_reviews(periodEndEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_performance_reviews_status ON performance_reviews(status)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS performance_scores (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                reviewId INTEGER NOT NULL,
                goalId INTEGER NOT NULL,
                achievedValue REAL,
                scoreBasisPoints INTEGER NOT NULL,
                weightedScoreBasisPoints INTEGER NOT NULL,
                notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(reviewId) REFERENCES performance_reviews(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(goalId) REFERENCES performance_goals(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_performance_scores_reviewId_goalId ON performance_scores(reviewId, goalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_performance_scores_goalId ON performance_scores(goalId)")
    }
}



internal val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_changes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                changeId TEXT NOT NULL,
                entityType TEXT NOT NULL,
                entityId INTEGER NOT NULL,
                changeType TEXT NOT NULL,
                deviceId TEXT NOT NULL,
                occurredAtEpochMillis INTEGER NOT NULL,
                payloadHash TEXT NOT NULL,
                state TEXT NOT NULL,
                lastError TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_changes_changeId ON sync_changes(changeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_changes_state ON sync_changes(state)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_changes_occurredAtEpochMillis ON sync_changes(occurredAtEpochMillis)")
    }
}


internal val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_users ADD COLUMN failedPinAttempts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_users ADD COLUMN lockUntilEpochMillis INTEGER NOT NULL DEFAULT 0")
    }
}

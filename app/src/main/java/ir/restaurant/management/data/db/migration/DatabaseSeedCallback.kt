package ir.restaurant.management.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

internal object AccountSeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedMissingAccounts(db)
        seedSystemLocations(db)
        installClosedPeriodGuards(db)
        installSalesDayGuards(db)
        installAccountingPeriodGuards(db)
        installJournalLineGuards(db)
        installRecipeVersionGuards(db)
        installAuditLogGuards(db)
        installPostedJournalGuards(db)
        installStockMovementGuards(db)
        installInventoryBalanceGuards(db)
        installInventoryLotGuards(db)
        installInventoryCountSessionGuards(db)
        installInventoryCountGuards(db)
        installWasteDocumentGuards(db)
        installStockTransferGuards(db)
        installHrPayrollGuards(db)
        installSalesHistoryGuards(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        seedMissingAccounts(db)
        seedSystemLocations(db)
        installClosedPeriodGuards(db)
        installSalesDayGuards(db)
        installAccountingPeriodGuards(db)
        installJournalLineGuards(db)
        installRecipeVersionGuards(db)
        installAuditLogGuards(db)
        installPostedJournalGuards(db)
        installStockMovementGuards(db)
        installInventoryBalanceGuards(db)
        installInventoryLotGuards(db)
        installInventoryCountSessionGuards(db)
        installInventoryCountGuards(db)
        installWasteDocumentGuards(db)
        installStockTransferGuards(db)
        installHrPayrollGuards(db)
        installSalesHistoryGuards(db)
        DatabaseHealthValidator.validateStartup(db)
        DatabaseHealthValidator.validateForeignKeys(db)
    }

    fun seedMissingAccounts(db: SupportSQLiteDatabase) {
        val accounts = listOf(
            arrayOf("1101", "صندوق", "ASSET"),
            arrayOf("1102", "بانک", "ASSET"),
            arrayOf("1103", "تنخواه‌گردان", "ASSET"),
            arrayOf("1104", "وجوه کارت‌خوان", "ASSET"),
            arrayOf("1201", "حساب‌های دریافتنی", "ASSET"),
            arrayOf("1202", "اسناد دریافتنی", "ASSET"),
            arrayOf("1203", "اعتبار نزد تأمین‌کننده", "ASSET"),
            arrayOf("1301", "موجودی مواد اولیه", "ASSET"),
            arrayOf("1302", "موجودی ملزومات و بسته‌بندی", "ASSET"),
            arrayOf("1401", "مساعده پرسنل", "ASSET"),
            arrayOf("1501", "دارایی‌های ثابت", "ASSET"),
            arrayOf("1502", "استهلاک انباشته دارایی‌ها", "ASSET"),
            arrayOf("1503", "کاهش ارزش انباشته دارایی‌ها", "ASSET"),
            arrayOf("2101", "حساب‌های پرداختنی", "LIABILITY"),
            arrayOf("2102", "حقوق پرداختنی", "LIABILITY"),
            arrayOf("2103", "مالیات و عوارض پرداختنی", "LIABILITY"),
            arrayOf("2104", "بیمه پرداختنی", "LIABILITY"),
            arrayOf("2105", "کالای دریافت‌شده فاکتورنشده", "LIABILITY"),
            arrayOf("2199", "حساب واسط تسویه خزانه", "LIABILITY"),
            arrayOf("3101", "سرمایه", "EQUITY"),
            arrayOf("4101", "فروش غذا و نوشیدنی", "REVENUE"),
            arrayOf("4102", "سایر درآمدها", "REVENUE"),
            arrayOf("4103", "درآمد ارسال و خدمات", "REVENUE"),
            arrayOf("5101", "بهای تمام‌شده فروش", "EXPENSE"),
            arrayOf("5102", "ملزومات و بسته‌بندی مصرف‌شده", "EXPENSE"),
            arrayOf("6101", "حقوق و دستمزد", "EXPENSE"),
            arrayOf("6102", "اجاره", "EXPENSE"),
            arrayOf("6103", "آب، برق و گاز", "EXPENSE"),
            arrayOf("6104", "ضایعات مواد اولیه", "EXPENSE"),
            arrayOf("6105", "سایر هزینه‌های جاری", "EXPENSE"),
            arrayOf("6106", "بیمه سهم کارفرما", "EXPENSE"),
            arrayOf("6107", "تعمیر و نگهداری", "EXPENSE"),
            arrayOf("6108", "تبلیغات", "EXPENSE"),
            arrayOf("6109", "هزینه ارسال", "EXPENSE"),
            arrayOf("6110", "استهلاک", "EXPENSE"),
            arrayOf("6111", "مغایرت قیمت خرید", "EXPENSE"),
            arrayOf("6112", "زیان خروج دارایی ثابت", "EXPENSE"),
            arrayOf("6113", "هزینه اضافه‌کاری", "EXPENSE"),
            arrayOf("6114", "هزینه پاداش و کارانه", "EXPENSE"),
            arrayOf("6115", "هزینه مزایا و فوق‌العاده", "EXPENSE"),
        )
        accounts.forEach { account ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO accounts(code, name, type, isSystem, isActive)
                VALUES (?, ?, ?, 1, 1)
                """.trimIndent(),
                account,
            )
        }
    }

    internal fun seedSystemLocations(db: SupportSQLiteDatabase) {
        val supportsInventory2 = db.query("PRAGMA table_info(storage_locations)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "code") found = true
            }
            found
        }
        if (supportsInventory2) {
            val branchStatusSupported = db.query("PRAGMA table_info(branches)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) if (cursor.getString(nameIndex) == "status") found = true
                found
            }
            if (branchStatusSupported) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO branches(globalId,organizationId,code,name,isActive,status,createdAtEpochMillis,updatedAtEpochMillis)
                    SELECT 'system:main-branch',NULL,'MAIN','شعبه اصلی',1,'ACTIVE',0,0
                    WHERE NOT EXISTS(SELECT 1 FROM branches)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO storage_locations(code,name,branchName,branchId,kind,isActive,createdAtEpochMillis,updatedAtEpochMillis)
                    SELECT 'MAIN','انبار اصلی',b.name,b.id,'WAREHOUSE',1,0,0
                    FROM branches b
                    WHERE b.isActive=1 AND b.status='ACTIVE'
                      AND (SELECT COUNT(*) FROM branches WHERE isActive=1 AND status='ACTIVE')=1
                    LIMIT 1
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE storage_locations
                    SET branchId=(SELECT id FROM branches WHERE isActive=1 AND status='ACTIVE' LIMIT 1),
                        branchName=(SELECT name FROM branches WHERE isActive=1 AND status='ACTIVE' LIMIT 1)
                    WHERE code='MAIN' AND branchId IS NULL
                      AND (SELECT COUNT(*) FROM branches WHERE isActive=1 AND status='ACTIVE')=1
                      AND NOT EXISTS(SELECT 1 FROM stock_movements m WHERE m.locationId=storage_locations.id)
                      AND NOT EXISTS(SELECT 1 FROM inventory_lots l WHERE l.locationId=storage_locations.id AND l.quantityMicros<>0)
                    """.trimIndent(),
                )
            } else {
                db.execSQL(
                    """INSERT OR IGNORE INTO storage_locations(code, name, kind, isActive, createdAtEpochMillis, updatedAtEpochMillis)
                    VALUES ('MAIN', 'انبار اصلی', 'WAREHOUSE', 1, 0, 0)""",
                )
            }
        } else {
            db.execSQL(
                """INSERT OR IGNORE INTO storage_locations(name, kind, isActive, createdAtEpochMillis)
                VALUES ('انبار اصلی', 'PRIMARY', 1, 0)""",
            )
        }
    }

    internal fun installClosedPeriodGuards(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_inventory_movement_insert
            BEFORE INSERT ON stock_movements
            WHEN EXISTS(SELECT 1 FROM inventory_period_closures c WHERE c.status='CLOSED' AND NEW.movementEpochDay BETWEEN c.fromEpochDay AND c.toEpochDay)
            BEGIN SELECT RAISE(ABORT, 'INVENTORY_PERIOD_CLOSED'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_inventory_movement_update
            BEFORE UPDATE ON stock_movements
            WHEN EXISTS(SELECT 1 FROM inventory_period_closures c WHERE c.status='CLOSED' AND (OLD.movementEpochDay BETWEEN c.fromEpochDay AND c.toEpochDay OR NEW.movementEpochDay BETWEEN c.fromEpochDay AND c.toEpochDay))
            BEGIN SELECT RAISE(ABORT, 'INVENTORY_PERIOD_CLOSED'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_inventory_movement_delete
            BEFORE DELETE ON stock_movements
            WHEN EXISTS(SELECT 1 FROM inventory_period_closures c WHERE c.status='CLOSED' AND OLD.movementEpochDay BETWEEN c.fromEpochDay AND c.toEpochDay)
            BEGIN SELECT RAISE(ABORT, 'INVENTORY_PERIOD_CLOSED'); END""")
    }
}

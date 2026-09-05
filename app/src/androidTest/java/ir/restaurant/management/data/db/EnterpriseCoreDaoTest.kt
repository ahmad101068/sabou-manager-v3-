package ir.restaurant.management.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnterpriseCoreDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = database.close()

@Test
    fun treasuryDao_rejectsDuplicateCommandAndOrphanLedgerEntry() = runBlocking {
        val first = TreasuryTransactionEntity(
            id = "txn-dao-1",
            commandId = "cmd-dao-unique",
            kind = "RECEIPT",
            businessEpochDay = 20_000L,
            sourceType = "DAO_TEST",
            sourceId = 1L,
            reason = "کنترل یکتایی فرمان",
            amountRial = 10_000L,
            actorId = 1L,
            correlationId = "dao:treasury:1",
            createdAtEpochMillis = 10L,
        )
        database.treasuryDao().insertTransaction(first)

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.treasuryDao().insertTransaction(first.copy(id = "txn-dao-2")) }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.treasuryDao().insertLedgerEntries(
                    listOf(
                        TreasuryLedgerEntryEntity(
                            transactionId = "missing-transaction",
                            accountId = "CASH:MAIN",
                            direction = "RECEIPT",
                            amountRial = 1_000L,
                            sourceType = "DAO_TEST",
                            sourceId = 2L,
                            businessEpochDay = 20_000L,
                            actorId = 1L,
                            createdAtEpochMillis = 11L,
                        ),
                    ),
                )
            }
        }
        assertEquals(first, database.treasuryDao().transactionById(first.id))
    }

private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }
}

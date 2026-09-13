package ir.restaurant.management.data.db

import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationChainContractTest {
    @Test
    fun declaresEverySequentialUpgradeFromOneToCurrentVersion() {
        val edges = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until APP_DATABASE_SCHEMA_VERSION).map { it to it + 1 }, edges)
    }
}

package ir.restaurant.management.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationRegistryTest {
    @Test
    fun registryContainsOneOrderedEdgeForEverySupportedVersion() {
        val actual = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        val expected = (1 until APP_DATABASE_SCHEMA_VERSION).map { it to it + 1 }

        assertEquals(expected, actual)
        assertEquals(actual.size, actual.distinct().size)
    }
}

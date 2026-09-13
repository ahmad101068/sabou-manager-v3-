package ir.restaurant.management.domain.operations

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class CloudSyncConfigTest {
    private fun config(endpoint: String) = CloudSyncConfig(
        endpoint = endpoint,
        organizationId = " organization ",
        enabled = true,
        accessToken = "token",
        accessTokenExpiresAtEpochMillis = Long.MAX_VALUE,
        deviceId = " device ",
    )

    @Test
    fun validHttpsEndpointIsNormalized() {
        val validated = config("  HTTPS://sync.example.com/base/  ").validated()

        assertEquals("HTTPS://sync.example.com/base", validated.endpoint)
        assertEquals("organization", validated.organizationId)
        assertEquals("device", validated.deviceId)
    }

    @Test
    fun endpointRejectsNonHttpsAndAmbiguousUrlComponents() {
        listOf(
            "http://sync.example.com",
            "https://",
            "https://user@sync.example.com",
            "https://sync.example.com?redirect=https://other.example",
            "https://sync.example.com#fragment",
        ).forEach { endpoint ->
            assertFailsWith<IllegalArgumentException>(endpoint) {
                config(endpoint).validated()
            }
        }
    }
}

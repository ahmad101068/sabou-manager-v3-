package ir.restaurant.management.domain.operations

data class SyncUploadResult(val accepted: List<String>, val conflicts: List<String>, val rejected: List<String>)

interface SyncTransport {
    suspend fun upload(batch: List<SyncEnvelope>): SyncUploadResult
}

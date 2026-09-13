package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_sequences")
data class DocumentSequenceEntity(
    @PrimaryKey val sequenceKey: String,
    val nextValue: Long,
    val updatedAtEpochMillis: Long,
)

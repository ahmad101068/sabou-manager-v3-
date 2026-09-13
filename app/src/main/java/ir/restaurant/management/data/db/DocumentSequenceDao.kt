package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DocumentSequenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: DocumentSequenceEntity): Long

    @Query("SELECT nextValue FROM document_sequences WHERE sequenceKey = :sequenceKey LIMIT 1")
    suspend fun nextValue(sequenceKey: String): Long?

    @Query(
        """
        UPDATE document_sequences
        SET nextValue = :newValue, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE sequenceKey = :sequenceKey AND nextValue = :expectedValue
        """,
    )
    suspend fun compareAndAdvance(
        sequenceKey: String,
        expectedValue: Long,
        newValue: Long,
        updatedAtEpochMillis: Long,
    ): Int
}

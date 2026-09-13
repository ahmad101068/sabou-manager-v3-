package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hr_documents",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("employeeId"),
        Index("documentType"),
        Index("status"),
        Index("expiryEpochDay"),
        Index(value = ["employeeId", "contentUri"], unique = true),
    ],
)
data class HrDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val documentType: String,
    val displayName: String,
    val contentUri: String,
    val mimeType: String,
    val issueEpochDay: Long?,
    val expiryEpochDay: Long?,
    val status: String,
    val notes: String,
    val createdAtEpochMillis: Long,
    val createdByActorId: Long,
    val correlationId: String,
)

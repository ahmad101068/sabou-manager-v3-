package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.DocumentSequenceEntity
import ir.restaurant.management.domain.common.DocumentNumberType

/** Must be called from the same Room transaction that persists the owning document. */
internal class LocalDocumentNumberAllocator(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun next(type: DocumentNumberType): String =
        "${type.prefix}-${nextRaw(type.sequenceKey).toString().padStart(8, '0')}"

    suspend fun nextRaw(sequenceKey: String, initialValue: Long = 1L): Long {
        require(sequenceKey.matches(Regex("[A-Za-z0-9:._-]{3,120}"))) { "کلید توالی معتبر نیست." }
        require(initialValue in 1..MAX_SEQUENCE) { "مقدار آغاز توالی معتبر نیست." }
        val dao = database.documentSequenceDao()
        val now = clock()
        dao.insertIfMissing(DocumentSequenceEntity(sequenceKey, initialValue, now))
        repeat(MAX_RETRIES) {
            val current = checkNotNull(dao.nextValue(sequenceKey)) { "توالی شماره‌گذاری $sequenceKey پیدا نشد." }
            require(current in 1..MAX_SEQUENCE) { "ظرفیت شماره‌گذاری $sequenceKey تکمیل شده است." }
            val next = Math.addExact(current, 1L)
            if (dao.compareAndAdvance(sequenceKey, current, next, now) == 1) return current
        }
        error("شماره‌گذاری هم‌زمان با تغییر دیگری تداخل داشت؛ عملیات را دوباره انجام دهید.")
    }

    private companion object {
        const val MAX_RETRIES = 32
        const val MAX_SEQUENCE = 99_999_999L
    }
}

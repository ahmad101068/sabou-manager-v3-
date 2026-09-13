package ir.restaurant.management.data.security

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The application deliberately requires a fresh login after process/database bootstrap.
 * Persisted sessions are therefore treated as expired/stale and invalidated before any protected
 * graph can be created.
 */
object StartupSessionBoundary {
    fun invalidatePersistedSession(database: SupportSQLiteDatabase) {
        database.execSQL("DELETE FROM app_session")
    }
}

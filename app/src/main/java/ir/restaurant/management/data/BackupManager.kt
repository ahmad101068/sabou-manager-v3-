package ir.restaurant.management.data

import android.content.Context
import android.os.Build
import android.system.Os
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.APP_DATABASE_SCHEMA_VERSION
import ir.restaurant.management.data.security.BackupManifest
import ir.restaurant.management.data.security.DatabaseKeyProvider
import ir.restaurant.management.data.security.PortableBackupCodec
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(
    private val context: Context,
    private val keyProvider: DatabaseKeyProvider = DatabaseKeyProvider(context),
    private val deviceIdProvider: () -> String = { "local" },
) {
    private val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
    private val migrationRecoveryDir = File(context.filesDir, "migration-recovery").apply { mkdirs() }
    private val prefs = context.getSharedPreferences(BACKUP_STATE_PREFS, Context.MODE_PRIVATE)

    /**
     * Completes a scheduled restore before Room opens the database.
     *
     * Every irreversible step is persisted first/after execution so a process death can safely resume.
     * In particular, an interrupted rollback can no longer leave the restored database paired with the
     * wrong SQLCipher passphrase.
     */
    fun applyPendingRestore() {
        if (resumeInterruptedRollback()) return

        val pending = prefs.getString(KEY_PENDING_RESTORE, null) ?: return
        val source = resolveBackup(pending)
        require(source.isFile) { "فایل پشتیبان زمان‌بندی‌شده پیدا نشد." }
        require(verify(pending)) { "فایل پشتیبان ناقص است یا اثرانگشت آن مطابقت ندارد." }

        var phase = currentRestorePhase() ?: RestorePhase.PREPARED
        if (phase == RestorePhase.PREPARED) {
            val target = databaseFile()
            target.parentFile?.mkdirs()
            val staged = File(requireNotNull(target.parentFile), "${target.name}.restore")
            source.copyTo(staged, overwrite = true)
            require(staged.length() == source.length()) { "کپی فایل پشتیبان کامل نشد." }

            if (target.exists() && prefs.getString(KEY_LAST_RESTORE_RECOVERY, null) == null) {
                val recovery = File(backupDir, "pre-restore-${System.currentTimeMillis()}.db")
                target.copyTo(recovery, overwrite = false)
                writeChecksum(recovery)
                val currentPassphrase = keyProvider.getOrCreatePassphrase()
                try {
                    keyFile(recovery).writeText(keyProvider.protectPassphrase(currentPassphrase))
                } finally {
                    currentPassphrase.fill(0)
                }
                check(prefs.edit().putString(KEY_LAST_RESTORE_RECOVERY, recovery.name).commit()) {
                    "ثبت نسخه ایمنی سازگار با بازیابی قدیمی انجام نشد."
                }
            }

            replaceDatabaseAtomically(staged, target)
            deleteDatabaseSidecars()
            check(
                prefs.edit()
                    .putString(KEY_RESTORE_DATABASE_APPLIED, pending)
                    .putString(KEY_RESTORE_PHASE, RestorePhase.DATABASE_REPLACED.name)
                    .commit(),
            ) { "ثبت وضعیت جایگزینی پایگاه داده انجام نشد." }
            phase = RestorePhase.DATABASE_REPLACED
        }

        if (phase == RestorePhase.DATABASE_REPLACED) {
            keyProvider.activateStagedRestorePassphrase()
            check(prefs.edit().putString(KEY_RESTORE_PHASE, RestorePhase.KEY_ACTIVATED.name).commit()) {
                "ثبت وضعیت فعال‌سازی کلید بازیابی انجام نشد."
            }
            phase = RestorePhase.KEY_ACTIVATED
        }

        if (phase == RestorePhase.KEY_ACTIVATED) {
            check(
                prefs.edit()
                    .remove(KEY_PENDING_RESTORE)
                    .putString(KEY_RESTORE_PHASE, RestorePhase.AWAITING_VALIDATION.name)
                    .commit(),
            ) { "نهایی‌سازی برنامه بازیابی انجام نشد." }
        }
    }

    fun create(database: AppDatabase): String {
        val name = "restaurant-manager-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}-${System.currentTimeMillis() % 1_000L}.db"
        val source = databaseFile()
        val destination = File(backupDir, name)
        try {
            database.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(destination.absolutePath))
            require(destination.length() >= MIN_DATABASE_BYTES && source.length() >= MIN_DATABASE_BYTES) {
                "ساخت نسخه پشتیبان کامل نشد."
            }
            val databaseSha256 = sha256(destination)
            checksumFile(destination).writeText(databaseSha256)
            writeManifest(
                destination,
                newManifest(
                    database = database,
                    databaseFile = destination,
                    databaseSha256 = databaseSha256,
                ),
            )
            val passphrase = keyProvider.getOrCreatePassphrase()
            try {
                keyFile(destination).writeText(keyProvider.protectPassphrase(passphrase))
            } finally {
                passphrase.fill(0)
            }
            require(verify(name)) { "اعتبارسنجی نسخه پشتیبان ساخته‌شده ناموفق بود." }
            return name
        } catch (error: Throwable) {
            deleteBackupArtifacts(destination)
            throw error
        }
    }

    fun markRestoreValidated() {
        val recoveryName = prefs.getString(KEY_LAST_RESTORE_RECOVERY, null)

        // Persisting VALIDATED first prevents a later bookkeeping failure from rolling a valid
        // database back after the previous key has already been discarded. Finalization is
        // intentionally retryable across process death.
        if (currentRestorePhase() != null || recoveryName != null) {
            check(prefs.edit().putString(KEY_RESTORE_PHASE, RestorePhase.VALIDATED.name).commit()) {
                "ثبت اعتبارسنجی نهایی بازیابی انجام نشد."
            }
        }
        keyProvider.commitRestorePassphrase()
        // Delete the no-longer-needed recovery before forgetting its name. A process death can
        // therefore leave retryable metadata, but cannot create an unreachable recovery artifact.
        recoveryName?.let(::deleteBackupArtifactsIfPresent)
        check(
            prefs.edit()
                .remove(KEY_LAST_RESTORE_RECOVERY)
                .remove(KEY_RESTORE_DATABASE_APPLIED)
                .remove(KEY_PENDING_RESTORE)
                .remove(KEY_RESTORE_PHASE)
                .remove(KEY_FORENSIC_RESTORE_REQUEST_AT)
                .remove(KEY_FORENSIC_RESTORE_ACTOR_ID)
                .remove(KEY_FORENSIC_RESTORE_ACTOR)
                .remove(KEY_FORENSIC_RESTORE_CORRELATION)
                .remove(KEY_FORENSIC_RESTORE_SOURCE_DB)
                .remove(KEY_FORENSIC_RESTORE_BACKUP)
                .commit(),
        ) { "پاک‌سازی وضعیت بازیابی انجام نشد." }
    }

    /** Creates a crash-safe raw checkpoint before Room is allowed to run a new schema migration. */
    fun preparePreMigrationRecovery(targetSchemaVersion: Int) {
        require(targetSchemaVersion > 0) { "نسخه مقصد مهاجرت معتبر نیست." }
        if (currentRestorePhase() != null) return
        if (prefs.getInt(KEY_LAST_VALIDATED_SCHEMA_VERSION, 0) >= targetSchemaVersion) return
        val target = databaseFile()
        if (!target.isFile) return

        val currentAppVersion = currentAppVersionCode()
        if (prefs.getBoolean(KEY_MIGRATION_RECOVERY_READY, false)) {
            val attemptVersion = prefs.getLong(KEY_MIGRATION_RECOVERY_APP_VERSION, -1L)
            if (attemptVersion == currentAppVersion) {
                require(verifyMigrationRecovery()) { "نسخه ایمنی پیش از مهاجرت ناقص است." }
                if (prefs.getBoolean(KEY_MIGRATION_ROLLBACK_IN_PROGRESS, false)) {
                    rollbackPreMigrationRecovery()
                }
                check(!prefs.getBoolean(KEY_MIGRATION_RECOVERY_FAILED, false)) {
                    "مهاجرت این نسخه قبلاً ناموفق بود و پایگاه داده به وضعیت قبل بازگردانده شد. نسخه اصلاح‌شده برنامه را نصب کنید."
                }
                return
            }
            clearMigrationRecoveryFiles()
            clearMigrationRecoveryState()
        }

        val sourceFiles = migrationSourceFiles(target)
        require(sourceFiles.first().second.isFile) { "پایگاه داده برای نسخه ایمنی مهاجرت پیدا نشد." }
        clearMigrationRecoveryFiles()
        try {
            sourceFiles.forEach { (suffix, source) ->
                if (source.isFile) copyFileDurably(source, migrationRecoveryFile(suffix))
            }
            val editor = prefs.edit()
                .putLong(KEY_MIGRATION_RECOVERY_APP_VERSION, currentAppVersion)
                .putInt(KEY_MIGRATION_RECOVERY_TARGET_SCHEMA, targetSchemaVersion)
                .putBoolean(KEY_MIGRATION_RECOVERY_FAILED, false)
            sourceFiles.forEach { (suffix, source) ->
                val recovery = migrationRecoveryFile(suffix)
                editor.putString(migrationHashKey(suffix), if (source.isFile) sha256(recovery) else "")
            }
            check(editor.putBoolean(KEY_MIGRATION_RECOVERY_READY, true).commit()) {
                "ثبت وضعیت نسخه ایمنی پیش از مهاجرت انجام نشد."
            }
            require(verifyMigrationRecovery()) { "اعتبارسنجی نسخه ایمنی پیش از مهاجرت ناموفق بود." }
        } catch (error: Throwable) {
            clearMigrationRecoveryFiles()
            clearMigrationRecoveryState()
            throw error
        }
    }

    fun markDatabaseSchemaValidated(schemaVersion: Int) {
        check(
            prefs.edit()
                .putInt(KEY_LAST_VALIDATED_SCHEMA_VERSION, schemaVersion)
                .remove(KEY_MIGRATION_RECOVERY_READY)
                .remove(KEY_MIGRATION_RECOVERY_FAILED)
                .remove(KEY_MIGRATION_ROLLBACK_IN_PROGRESS)
                .remove(KEY_MIGRATION_RECOVERY_APP_VERSION)
                .remove(KEY_MIGRATION_RECOVERY_TARGET_SCHEMA)
                .remove(migrationHashKey(MIGRATION_MAIN_SUFFIX))
                .remove(migrationHashKey(MIGRATION_WAL_SUFFIX))
                .remove(migrationHashKey(MIGRATION_SHM_SUFFIX))
                .commit(),
        ) { "ثبت نسخه ساختار معتبر پایگاه داده انجام نشد." }
        clearMigrationRecoveryFiles()
    }

    fun rollbackPreMigrationRecovery(): Boolean {
        if (!prefs.getBoolean(KEY_MIGRATION_RECOVERY_READY, false)) return false
        require(verifyMigrationRecovery()) { "نسخه ایمنی لازم برای بازگشت مهاجرت ناقص است." }
        check(prefs.edit().putBoolean(KEY_MIGRATION_ROLLBACK_IN_PROGRESS, true).commit()) {
            "ثبت شروع بازگشت مهاجرت انجام نشد."
        }
        val target = databaseFile()
        target.parentFile?.mkdirs()
        val stagedFiles = migrationSourceFiles(target).map { (suffix, destination) ->
            val recovery = migrationRecoveryFile(suffix)
            val staged = File(destination.parentFile, "${destination.name}.migration-rollback")
            staged.delete()
            if (recovery.isFile) copyFileDurably(recovery, staged)
            Triple(suffix, destination, staged)
        }
        try {
            stagedFiles.filter { it.first != MIGRATION_MAIN_SUFFIX }.forEach { (_, destination, staged) ->
                destination.delete()
                if (staged.isFile) Os.rename(staged.absolutePath, destination.absolutePath)
            }
            val main = stagedFiles.single { it.first == MIGRATION_MAIN_SUFFIX }
            Os.rename(main.third.absolutePath, main.second.absolutePath)
            check(
                prefs.edit()
                    .putBoolean(KEY_MIGRATION_RECOVERY_FAILED, true)
                    .putBoolean(KEY_MIGRATION_ROLLBACK_IN_PROGRESS, false)
                    .commit(),
            ) {
                "ثبت بازگشت مهاجرت انجام نشد."
            }
            return true
        } finally {
            stagedFiles.forEach { it.third.delete() }
        }
    }

    fun rollbackLastRestore(): Boolean {
        val recoveryName = prefs.getString(KEY_LAST_RESTORE_RECOVERY, null) ?: return false
        val recovery = resolveBackup(recoveryName)
        if (!recovery.isFile || !verify(recoveryName)) return false

        val phase = currentRestorePhase()
        if (phase == RestorePhase.VALIDATED || phase == RestorePhase.ROLLBACK_AWAITING_VALIDATION) return false
        if (phase != RestorePhase.ROLLBACK_PREPARED && phase != RestorePhase.ROLLBACK_DATABASE_REPLACED) {
            check(prefs.edit().putString(KEY_RESTORE_PHASE, RestorePhase.ROLLBACK_PREPARED.name).commit()) {
                "ثبت شروع بازگشت بازیابی انجام نشد."
            }
        }
        return completeInterruptedRollback(recoveryName)
    }

    fun list(): List<String> {
        val protectedRecovery = prefs.getString(KEY_LAST_RESTORE_RECOVERY, null)
        return backupDir.listFiles()
            ?.filter { it.isFile && it.extension == "db" && it.name != protectedRecovery && !it.name.startsWith("pre-restore-") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name }
            .orEmpty()
    }

    fun describe(): List<BackupDescriptor> = backupDir.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "db" }
        .sortedByDescending { it.lastModified() }
        .map { BackupDescriptor(it.name, it.length(), it.lastModified(), verify(it.name)) }

    fun verify(name: String): Boolean {
        val source = resolveBackup(name)
        if (!source.isFile || source.length() < MIN_DATABASE_BYTES || source.length() % DATABASE_PAGE_BYTES != 0L) return false
        val checksum = checksumFile(source)
        if (!checksum.isFile || !keyFile(source).isFile) return false
        val expected = checksum.readText().trim()
        if (!expected.matches(Regex("[0-9a-f]{64}"))) return false
        val actual = sha256(source)
        if (expected != actual) return false
        return runCatching {
            val manifest = if (manifestFile(source).isFile) {
                readManifest(source)
            } else {
                BackupManifest.legacy(source.length(), actual, source.lastModified()).also { writeManifest(source, it) }
            }
            manifest.requireCompatibleDatabase(APPLICATION_ID, APP_DATABASE_SCHEMA_VERSION, source.length(), actual)
        }.isSuccess
    }

    fun prune(maxFiles: Int) {
        val keep = maxFiles.coerceIn(1, 200)
        val protectedNames = setOfNotNull(
            prefs.getString(KEY_PENDING_RESTORE, null),
            prefs.getString(KEY_LAST_RESTORE_RECOVERY, null),
        )
        backupDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "db" && it.name !in protectedNames && !it.name.startsWith("pre-restore-") }
            .sortedByDescending { it.lastModified() }
            .drop(keep)
            .forEach(::deleteBackupArtifacts)
    }

    fun clearAll() {
        backupDir.listFiles().orEmpty().filter { it.isFile }.forEach { it.delete() }
        clearMigrationRecoveryFiles()
        check(prefs.edit().clear().commit()) { "پاک‌سازی وضعیت پشتیبان انجام نشد." }
    }

    fun delete(name: String) {
        require(prefs.getString(KEY_PENDING_RESTORE, null) != name) {
            "نسخه انتخاب‌شده برای بازیابی تا پایان یا لغو بازیابی قابل حذف نیست."
        }
        require(prefs.getString(KEY_LAST_RESTORE_RECOVERY, null) != name) {
            "نسخه ایمنی قبل از بازیابی تا پایان بازیابی قابل حذف نیست."
        }
        val backup = resolveBackup(name)
        require(backup.isFile) { "فایل پشتیبان پیدا نشد." }
        manifestFile(backup).delete()
        checksumFile(backup).delete()
        keyFile(backup).delete()
        check(backup.delete()) { "حذف فایل پشتیبان انجام نشد." }
    }

    fun exportPortable(name: String, password: CharArray, destination: OutputStream): Long {
        val source = resolveBackup(name)
        require(verify(name)) { "فایل پشتیبان معتبر نیست یا اثرانگشت آن مطابقت ندارد." }
        val passphrase = keyFile(source).takeIf(File::isFile)?.readText()?.let(keyProvider::unprotectPassphrase)
            ?: keyProvider.getOrCreatePassphrase()
        val manifest = readManifest(source)
        return try {
            source.inputStream().use { input -> PortableBackupCodec.encrypt(passphrase, input, password, destination, manifest) }
                .also { require(it == source.length()) { "صدور فایل پشتیبان کامل نشد." } }
                .also {
                    check(prefs.edit().putLong(KEY_LAST_PORTABLE_EXPORT_AT, System.currentTimeMillis()).commit()) {
                        "ثبت زمان صدور پشتیبان انجام نشد."
                    }
                }
        } finally {
            passphrase.fill(0)
            password.fill('\u0000')
        }
    }

    fun importPortable(source: InputStream, password: CharArray): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "restaurant-manager-import-$timestamp-${System.currentTimeMillis() % 1_000L}.db"
        val staged = File(backupDir, "$name.import")
        val destination = File(backupDir, name)
        var restoredPassphrase: ByteArray? = null
        var importedManifest: BackupManifest? = null

        val writableBytes = (backupDir.usableSpace - RESERVED_FREE_SPACE_BYTES).coerceAtLeast(0L)
        val maximumDatabaseBytes = minOf(MAX_IMPORT_DATABASE_BYTES, writableBytes)
        require(maximumDatabaseBytes >= MIN_DATABASE_BYTES) {
            "فضای خالی امن برای واردکردن پشتیبان کافی نیست."
        }

        try {
            val payload = source.use { input ->
                staged.outputStream().use { output ->
                    PortableBackupCodec.decrypt(
                        source = input,
                        password = password,
                        databaseDestination = output,
                        maxDatabaseBytes = maximumDatabaseBytes,
                    )
                }
            }
            restoredPassphrase = payload.databaseKey
            importedManifest = payload.manifest
            require(staged.length() >= MIN_DATABASE_BYTES && staged.length() % DATABASE_PAGE_BYTES == 0L) {
                "فایل انتخاب‌شده ساختار قابل‌قبول پشتیبان را ندارد."
            }
            val databaseSha256 = sha256(staged)
            val effectiveManifest = importedManifest
                ?: BackupManifest.legacy(staged.length(), databaseSha256, System.currentTimeMillis())
            effectiveManifest.requireCompatibleDatabase(
                expectedApplicationId = APPLICATION_ID,
                maximumSchemaVersion = APP_DATABASE_SCHEMA_VERSION,
                actualSizeBytes = staged.length(),
                actualSha256 = databaseSha256,
            )
            require(staged.renameTo(destination)) { "ذخیره فایل واردشده انجام نشد." }
            checksumFile(destination).writeText(databaseSha256)
            writeManifest(destination, effectiveManifest)
            keyFile(destination).writeText(keyProvider.protectPassphrase(requireNotNull(restoredPassphrase)))
            require(verify(name)) { "اعتبارسنجی فایل واردشده ناموفق بود." }
            return name
        } catch (error: Throwable) {
            staged.delete()
            deleteBackupArtifacts(destination)
            throw error
        } finally {
            restoredPassphrase?.fill(0)
            password.fill('\u0000')
        }
    }

    fun scheduleRestore(name: String, recoveryName: String, forensic: RestoreForensicMetadata? = null) {
        require(verify(name)) { "فایل پشتیبان معتبر نیست یا تغییر کرده است." }
        require(name != recoveryName && verify(recoveryName)) { "نسخه ایمنی قبل از بازیابی معتبر نیست." }
        val backup = resolveBackup(name)
        val protectedKey = keyFile(backup).takeIf(File::isFile)?.readText()
            ?: error("کلید این نسخه پشتیبان موجود نیست؛ آن را دوباره از فایل قابل‌انتقال وارد کنید.")
        val passphrase = keyProvider.unprotectPassphrase(protectedKey)
        try {
            validateRestoreCandidate(backup, passphrase)
            keyProvider.stageRestorePassphrase(passphrase)
        } finally {
            passphrase.fill(0)
        }
        val editor = prefs.edit()
            .putString(KEY_PENDING_RESTORE, name)
            .putString(KEY_LAST_RESTORE_RECOVERY, recoveryName)
            .putString(KEY_RESTORE_PHASE, RestorePhase.PREPARED.name)
            .remove(KEY_RESTORE_DATABASE_APPLIED)
        if (forensic != null) {
            editor.putLong(KEY_FORENSIC_RESTORE_REQUEST_AT, forensic.requestEpochMillis)
                .putLong(KEY_FORENSIC_RESTORE_ACTOR_ID, forensic.actorId ?: -1L)
                .putString(KEY_FORENSIC_RESTORE_ACTOR, forensic.actor)
                .putString(KEY_FORENSIC_RESTORE_CORRELATION, forensic.correlationId)
                .putString(KEY_FORENSIC_RESTORE_SOURCE_DB, forensic.sourceDbFingerprint)
                .putString(KEY_FORENSIC_RESTORE_BACKUP, forensic.backupChecksum)
        }
        check(editor.commit()) { "زمان‌بندی بازیابی انجام نشد." }
    }

    fun currentDatabaseFingerprint(): String = databaseFile().takeIf(File::isFile)?.let(::sha256).orEmpty()

    fun backupFingerprint(name: String): String {
        val backup = resolveBackup(name)
        require(verify(name)) { "فایل پشتیبان معتبر نیست یا تغییر کرده است." }
        return sha256(backup)
    }

    fun restoreForensicMetadata(): RestoreForensicMetadata? {
        val correlationId = prefs.getString(KEY_FORENSIC_RESTORE_CORRELATION, null) ?: return null
        return RestoreForensicMetadata(
            requestEpochMillis = prefs.getLong(KEY_FORENSIC_RESTORE_REQUEST_AT, 0L),
            actorId = prefs.getLong(KEY_FORENSIC_RESTORE_ACTOR_ID, -1L).takeIf { it > 0L },
            actor = prefs.getString(KEY_FORENSIC_RESTORE_ACTOR, "").orEmpty(),
            correlationId = correlationId,
            sourceDbFingerprint = prefs.getString(KEY_FORENSIC_RESTORE_SOURCE_DB, "").orEmpty(),
            backupChecksum = prefs.getString(KEY_FORENSIC_RESTORE_BACKUP, "").orEmpty(),
        )
    }

    fun lastPortableExportAtEpochMillis(): Long = prefs.getLong(KEY_LAST_PORTABLE_EXPORT_AT, 0L)

    private fun resumeInterruptedRollback(): Boolean {
        val phase = currentRestorePhase()
        if (phase == RestorePhase.ROLLBACK_AWAITING_VALIDATION) return true
        if (phase != RestorePhase.ROLLBACK_PREPARED && phase != RestorePhase.ROLLBACK_DATABASE_REPLACED) return false
        val recoveryName = prefs.getString(KEY_LAST_RESTORE_RECOVERY, null)
            ?: error("نسخه ایمنی لازم برای تکمیل بازگشت بازیابی پیدا نشد.")
        return completeInterruptedRollback(recoveryName)
    }

    private fun completeInterruptedRollback(recoveryName: String): Boolean {
        val recovery = resolveBackup(recoveryName)
        require(recovery.isFile && verify(recoveryName)) { "نسخه ایمنی بازگشت معتبر نیست." }

        var phase = currentRestorePhase() ?: RestorePhase.ROLLBACK_PREPARED
        if (phase == RestorePhase.ROLLBACK_PREPARED) {
            val target = databaseFile()
            target.parentFile?.mkdirs()
            val staged = File(requireNotNull(target.parentFile), "${target.name}.rollback")
            recovery.copyTo(staged, overwrite = true)
            require(staged.length() == recovery.length()) { "آماده‌سازی بازگشت بازیابی کامل نشد." }
            replaceDatabaseAtomically(staged, target)
            deleteDatabaseSidecars()
            check(prefs.edit().putString(KEY_RESTORE_PHASE, RestorePhase.ROLLBACK_DATABASE_REPLACED.name).commit()) {
                "ثبت جایگزینی پایگاه داده در بازگشت انجام نشد."
            }
            phase = RestorePhase.ROLLBACK_DATABASE_REPLACED
        }

        if (phase == RestorePhase.ROLLBACK_DATABASE_REPLACED) {
            keyProvider.rollbackRestorePassphrase()
            check(
                prefs.edit()
                    .remove(KEY_RESTORE_DATABASE_APPLIED)
                    .remove(KEY_PENDING_RESTORE)
                    .putString(KEY_RESTORE_PHASE, RestorePhase.ROLLBACK_AWAITING_VALIDATION.name)
                    .commit(),
            ) { "ثبت انتظار برای اعتبارسنجی پایگاه داده بازگردانده‌شده انجام نشد." }
            return true
        }
        return false
    }

    private fun currentRestorePhase(): RestorePhase? {
        prefs.getString(KEY_RESTORE_PHASE, null)?.let { stored ->
            runCatching { RestorePhase.valueOf(stored) }.getOrNull()?.let { return it }
        }

        // Compatibility with restore metadata written by earlier builds.
        val pending = prefs.getString(KEY_PENDING_RESTORE, null)
        val databaseApplied = prefs.getString(KEY_RESTORE_DATABASE_APPLIED, null)
        return when {
            pending != null && databaseApplied == pending -> RestorePhase.DATABASE_REPLACED
            pending != null -> RestorePhase.PREPARED
            prefs.getString(KEY_LAST_RESTORE_RECOVERY, null) != null && databaseApplied != null -> RestorePhase.AWAITING_VALIDATION
            else -> null
        }
    }

    private fun migrationSourceFiles(target: File): List<Pair<String, File>> = listOf(
        MIGRATION_MAIN_SUFFIX to target,
        MIGRATION_WAL_SUFFIX to File("${target.path}-wal"),
        MIGRATION_SHM_SUFFIX to File("${target.path}-shm"),
    )

    private fun migrationRecoveryFile(suffix: String) = File(migrationRecoveryDir, "database$suffix")

    private fun migrationHashKey(suffix: String) = "migration_recovery_sha256_$suffix"

    private fun verifyMigrationRecovery(): Boolean = runCatching {
        listOf(MIGRATION_MAIN_SUFFIX, MIGRATION_WAL_SUFFIX, MIGRATION_SHM_SUFFIX).all { suffix ->
            val expected = prefs.getString(migrationHashKey(suffix), null) ?: return@all false
            val file = migrationRecoveryFile(suffix)
            if (expected.isEmpty()) !file.exists()
            else expected.matches(Regex("[0-9a-f]{64}")) && file.isFile && expected == sha256(file)
        }
    }.getOrDefault(false)

    private fun copyFileDurably(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        source.inputStream().use { input ->
            FileOutputStream(destination, false).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        require(destination.length() == source.length()) { "کپی ایمن فایل کامل نشد." }
    }

    private fun clearMigrationRecoveryFiles() {
        migrationRecoveryDir.listFiles().orEmpty().filter(File::isFile).forEach { it.delete() }
    }

    private fun clearMigrationRecoveryState() {
        check(
            prefs.edit()
                .remove(KEY_MIGRATION_RECOVERY_READY)
                .remove(KEY_MIGRATION_RECOVERY_FAILED)
                .remove(KEY_MIGRATION_ROLLBACK_IN_PROGRESS)
                .remove(KEY_MIGRATION_RECOVERY_APP_VERSION)
                .remove(KEY_MIGRATION_RECOVERY_TARGET_SCHEMA)
                .remove(migrationHashKey(MIGRATION_MAIN_SUFFIX))
                .remove(migrationHashKey(MIGRATION_WAL_SUFFIX))
                .remove(migrationHashKey(MIGRATION_SHM_SUFFIX))
                .commit(),
        ) { "پاک‌سازی وضعیت نسخه ایمنی مهاجرت انجام نشد." }
    }

    private fun currentAppVersionCode(): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
    }

    private fun writeChecksum(file: File) {
        checksumFile(file).writeText(sha256(file))
    }

    private fun checksumFile(file: File) = File(file.parentFile, "${file.name}.sha256")
    private fun keyFile(file: File) = File(file.parentFile, "${file.name}.key")
    private fun manifestFile(file: File) = File(file.parentFile, "${file.name}.manifest")

    private fun writeManifest(databaseFile: File, manifest: BackupManifest) {
        val target = manifestFile(databaseFile)
        val staged = File(target.parentFile, "${target.name}.tmp")
        try {
            staged.writeBytes(manifest.encode())
            require(staged.renameTo(target)) { "ثبت اتمی Manifest پشتیبان انجام نشد." }
        } finally {
            staged.delete()
        }
    }

    private fun readManifest(databaseFile: File): BackupManifest =
        BackupManifest.decode(manifestFile(databaseFile).readBytes())

    private fun deleteBackupArtifactsIfPresent(name: String) {
        runCatching { resolveBackup(name) }.getOrNull()?.let(::deleteBackupArtifacts)
    }

    private fun deleteBackupArtifacts(backup: File) {
        manifestFile(backup).delete()
        checksumFile(backup).delete()
        keyFile(backup).delete()
        backup.delete()
    }

    private fun newManifest(database: AppDatabase, databaseFile: File, databaseSha256: String): BackupManifest {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        return BackupManifest(
            applicationId = APPLICATION_ID,
            appVersionName = packageInfo.versionName?.take(120).orEmpty().ifBlank { "unknown" },
            appVersionCode = versionCode,
            schemaVersion = APP_DATABASE_SCHEMA_VERSION,
            createdAtEpochMillis = System.currentTimeMillis(),
            sourceDeviceId = deviceIdProvider().trim().take(120).ifBlank { "local" },
            databaseSizeBytes = databaseFile.length(),
            databaseSha256 = databaseSha256,
            tableRecordCounts = collectRecordCounts(database),
        )
    }

    private fun collectRecordCounts(database: AppDatabase): Map<String, Long> {
        val sqlite = database.openHelper.writableDatabase
        val tables = sqlite.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' ORDER BY name",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val table = cursor.getString(0)
                    if (table.matches(Regex("[A-Za-z0-9_]{1,80}"))) add(table)
                }
            }
        }
        return tables.associateWith { table ->
            sqlite.query("SELECT COUNT(*) FROM \"$table\"").use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun resolveBackup(name: String): File {
        require(name == File(name).name && name.endsWith(".db")) { "نام فایل پشتیبان معتبر نیست." }
        val candidate = File(backupDir, name).canonicalFile
        require(candidate.parentFile == backupDir.canonicalFile) { "مسیر فایل پشتیبان معتبر نیست." }
        return candidate
    }

    private fun databaseFile(): File = context.getDatabasePath(DATABASE_NAME)

    private fun validateRestoreCandidate(source: File, passphrase: ByteArray) {
        val validation = context.getDatabasePath(RESTORE_VALIDATION_DATABASE_NAME)
        validation.parentFile?.mkdirs()
        listOf(validation, File("${validation.path}-wal"), File("${validation.path}-shm"))
            .forEach { it.delete() }
        try {
            source.copyTo(validation, overwrite = false)
            require(validation.length() == source.length()) { "کپی نسخه موقت بازیابی کامل نشد." }
            AppDatabase.validateBackupCopy(context, validation, passphrase)
        } finally {
            listOf(validation, File("${validation.path}-wal"), File("${validation.path}-shm"))
                .forEach { it.delete() }
        }
    }

    private fun deleteDatabaseSidecars() {
        context.getDatabasePath("$DATABASE_NAME-wal").delete()
        context.getDatabasePath("$DATABASE_NAME-shm").delete()
    }

    private fun replaceDatabaseAtomically(staged: File, target: File) {
        require(staged.parentFile?.canonicalFile == target.parentFile?.canonicalFile) {
            "فایل مرحله‌ای بازیابی باید کنار پایگاه داده باشد."
        }
        try {
            // POSIX rename replaces the destination atomically on the same filesystem.
            // A partial database is never exposed to the app.
            Os.rename(staged.absolutePath, target.absolutePath)
        } catch (error: Throwable) {
            staged.delete()
            throw IllegalStateException("جایگزینی اتمی پایگاه داده انجام نشد.", error)
        }
    }

    private enum class RestorePhase {
        PREPARED,
        DATABASE_REPLACED,
        KEY_ACTIVATED,
        AWAITING_VALIDATION,
        VALIDATED,
        ROLLBACK_PREPARED,
        ROLLBACK_DATABASE_REPLACED,
        ROLLBACK_AWAITING_VALIDATION,
    }

    private companion object {
        // Must match AppDatabase.create(). A stale legacy filename made backup/restore and
        // pre-migration recovery operate on a different file than the live Room database.
        const val DATABASE_NAME = "restaurant_management.db"
        const val RESTORE_VALIDATION_DATABASE_NAME = "restaurant-management_restore_validation.db"
        const val BACKUP_STATE_PREFS = "backup_state"
        const val KEY_PENDING_RESTORE = "pending_restore"
        const val KEY_LAST_RESTORE_RECOVERY = "last_restore_recovery"
        const val KEY_RESTORE_DATABASE_APPLIED = "restore_database_applied"
        const val KEY_RESTORE_PHASE = "restore_phase"
        const val KEY_LAST_PORTABLE_EXPORT_AT = "last_portable_export_at"
        const val KEY_FORENSIC_RESTORE_REQUEST_AT = "forensic_restore_request_at"
        const val KEY_FORENSIC_RESTORE_ACTOR_ID = "forensic_restore_actor_id"
        const val KEY_FORENSIC_RESTORE_ACTOR = "forensic_restore_actor"
        const val KEY_FORENSIC_RESTORE_CORRELATION = "forensic_restore_correlation"
        const val KEY_FORENSIC_RESTORE_SOURCE_DB = "forensic_restore_source_db"
        const val KEY_FORENSIC_RESTORE_BACKUP = "forensic_restore_backup"
        const val KEY_LAST_VALIDATED_SCHEMA_VERSION = "last_validated_schema_version"
        const val KEY_MIGRATION_RECOVERY_READY = "migration_recovery_ready"
        const val KEY_MIGRATION_RECOVERY_FAILED = "migration_recovery_failed"
        const val KEY_MIGRATION_ROLLBACK_IN_PROGRESS = "migration_rollback_in_progress"
        const val KEY_MIGRATION_RECOVERY_APP_VERSION = "migration_recovery_app_version"
        const val KEY_MIGRATION_RECOVERY_TARGET_SCHEMA = "migration_recovery_target_schema"
        const val MIGRATION_MAIN_SUFFIX = ".db"
        const val MIGRATION_WAL_SUFFIX = ".db-wal"
        const val MIGRATION_SHM_SUFFIX = ".db-shm"
        const val MIN_DATABASE_BYTES = 4_096L
        const val DATABASE_PAGE_BYTES = 4_096L
        const val MAX_IMPORT_DATABASE_BYTES = 512L * 1024L * 1024L
        const val RESERVED_FREE_SPACE_BYTES = 64L * 1024L * 1024L
        const val APPLICATION_ID = "ir.restaurant.management"
    }
}

data class RestoreForensicMetadata(
    val requestEpochMillis: Long,
    val actorId: Long?,
    val actor: String,
    val correlationId: String,
    val sourceDbFingerprint: String,
    val backupChecksum: String,
)

data class BackupDescriptor(val name: String, val sizeBytes: Long, val modifiedAtEpochMillis: Long, val integrityVerified: Boolean)

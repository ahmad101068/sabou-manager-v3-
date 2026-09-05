package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.common.DocumentNumberType

import androidx.room.withTransaction
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.core.toLongExactCompat
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AssetDepreciationEntity
import ir.restaurant.management.data.db.AssetLifecycleEventEntity
import ir.restaurant.management.data.db.AssetMaintenanceEntity
import ir.restaurant.management.data.db.FixedAssetEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.assets.AssetImpairmentDraft
import ir.restaurant.management.domain.assets.AssetLifecycleRecord
import ir.restaurant.management.domain.assets.AssetLifecycleType
import ir.restaurant.management.domain.assets.AssetMaintenanceDraft
import ir.restaurant.management.domain.assets.AssetMaintenanceRecord
import ir.restaurant.management.domain.assets.AssetSaleDraft
import ir.restaurant.management.domain.assets.AssetTransferDraft
import ir.restaurant.management.domain.assets.AssetAcquisitionSource
import ir.restaurant.management.domain.assets.AssetRecord
import ir.restaurant.management.domain.assets.AssetRepository
import ir.restaurant.management.domain.assets.DepreciationDraft
import ir.restaurant.management.domain.assets.DepreciationRecord
import ir.restaurant.management.domain.assets.DepreciationReversalDraft
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.AccountingPostingContext
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.SemanticJournalDraft
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.domain.accounting.BalancedJournalDraft
import ir.restaurant.management.domain.accounting.JournalLineDraft
import ir.restaurant.management.domain.operations.SyncChangeType
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryDirection
import ir.restaurant.management.domain.treasury.TreasuryService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class LocalAssetRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
    private val authorizer: SessionAuthorizer,
    private val treasury: TreasuryService = ir.restaurant.management.data.treasury.LocalTreasuryServiceV2(
        database = database, accounting = LocalAccountingPostingEngine(database, clock = clock), authorizer = authorizer,
        accountCatalog = ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog(), clock = clock,
    ),
) : AssetRepository {
    private val dao get() = database.assetDao()
    private val accounting get() = database.accountingDao()
    private val accountingPosting = LocalAccountingPostingEngine(database, clock = clock)
    private val numbering = LocalDocumentNumberAllocator(database, clock)
    private val audit = LocalAuditEventWriter(database)
    private val branchResolver = CanonicalBranchResolver(database)
    private val payables = LocalSupplierPayableService(database, clock)

    override val assets: Flow<List<AssetRecord>> = dao.observeAssets().map { rows ->
        rows.map { row -> AssetRecord(
            id = row.id, assetCode = row.assetCode, name = row.name, category = row.category, quantity = row.quantity,
            purchaseEpochDay = row.purchaseEpochDay, purchaseCostRial = row.purchaseCostRial, salvageValueRial = row.salvageValueRial,
            accumulatedDepreciationRial = row.accumulatedDepreciationRial, usefulLifeMonths = row.usefulLifeMonths, location = row.location,
            notes = row.notes, isActive = row.status == "ACTIVE", isAccountingRecognized = row.isAccountingRecognized,
            branch = row.branch, responsiblePerson = row.responsiblePerson, impairmentRial = row.impairmentRial, status = row.status, branchId = row.branchId,
        ) }
    }
    override val depreciations: Flow<List<DepreciationRecord>> = dao.observeDepreciations().map { rows ->
        rows.map { DepreciationRecord(it.id,it.assetId,it.assetName,it.periodYear,it.periodMonth,it.amountRial,it.quantity,it.postingEpochDay,it.reason,it.reversedAtEpochMillis != null) }
    }

    override suspend fun save(id: Long?, draft: AssetDraft): Long {
        val actor = authorizer.require(Permission.ASSETS)
        val now = clock()
        return database.withTransaction {
            val numberedDraft = if (id == null && draft.assetCode.isBlank()) {
                var generated: String
                do { generated = numbering.next(DocumentNumberType.FIXED_ASSET) } while (dao.assetCodeExists(generated))
                draft.copy(assetCode = generated)
            } else draft
            val valid = numberedDraft.validated()
            val branch = branchResolver.resolveOptional(valid.branchId, valid.branch)
            val acquisitionSupplier = valid.supplierId?.let { supplierId ->
                database.supplierDao().activeById(supplierId) ?: error("تأمین‌کننده فعال دارایی پیدا نشد.")
            }
            if (id == null) {
                require(valid.acquisitionSource != AssetAcquisitionSource.OWNER_CAPITAL) {
                    "آورده مالک فقط برای تطبیق دارایی قدیمی مجاز است؛ تحصیل جدید باید از خزانه یا حساب پرداختنی عبور کند."
                }
                val assetId = dao.insertAsset(
                    FixedAssetEntity(
                        assetCode = valid.assetCode, name = valid.name, category = valid.category, quantity = valid.quantity,
                        purchaseEpochDay = valid.purchaseEpochDay, purchaseCostRial = valid.purchaseCostRial,
                        salvageValueRial = valid.salvageValueRial, usefulLifeMonths = valid.usefulLifeMonths,
                        location = valid.location, notes = valid.notes, createdAtEpochMillis = now, updatedAtEpochMillis = now,
                        branch = branch?.name.orEmpty(), branchId = branch?.id, responsiblePerson = valid.responsiblePerson,
                        acquisitionSource = valid.acquisitionSource.name, supplierId = acquisitionSupplier?.id,
                        payableDueEpochDay = valid.payableDueEpochDay,
                    ),
                )
                check(assetId > 0) { "ثبت دارایی انجام نشد." }
                val persistedAsset = dao.assetById(assetId) ?: error("دارایی ثبت‌شده پیدا نشد.")
                val acquisitionJournalId = postAcquisitionJournal(
                    asset = persistedAsset,
                    source = valid.acquisitionSource,
                    postingEpochDay = valid.purchaseEpochDay,
                    actorId = actor.id,
                )
                if (valid.acquisitionSource == AssetAcquisitionSource.PAYABLE) {
                    payables.ensureOrigin(
                        sourceType = "ASSET_ACQUISITION", sourceId = assetId, sourceDocumentNo = valid.assetCode,
                        supplierId = requireNotNull(valid.supplierId), branchId = branch?.id,
                        issueEpochDay = valid.purchaseEpochDay, dueEpochDay = requireNotNull(valid.payableDueEpochDay),
                        originalRial = valid.purchaseCostRial, actorId = actor.id,
                        correlationId = "asset_acquisition:$assetId", originJournalEntryId = acquisitionJournalId,
                    )
                }
                val lifecycleId = database.assetLifecycleDao().insertEvent(
                    AssetLifecycleEventEntity(
                        assetId = assetId,
                        eventType = AssetLifecycleType.PURCHASE.name,
                        businessEpochDay = valid.purchaseEpochDay,
                        amountRial = valid.purchaseCostRial,
                        toLocation = valid.location,
                        toBranch = branch?.name.orEmpty(),
                        toResponsiblePerson = valid.responsiblePerson,
                        note = "ثبت اولیه دارایی",
                        journalEntryId = acquisitionJournalId,
                        actorId = actor.id,
                        createdAtEpochMillis = now,
                    ),
                )
                audit.appendAuthorized(
                    authorizer = authorizer,
                    action = "CREATE",
                    entityType = "ASSET",
                    entityId = assetId,
                    description = "ثبت دارایی ${valid.assetCode}: ${valid.name}",
                    occurredAtEpochMillis = now,
                    businessEpochDay = valid.purchaseEpochDay,
                    afterSnapshot = "historicalCost=${valid.purchaseCostRial};salvage=${valid.salvageValueRial};location=${valid.location};branch=${valid.branch};event=$lifecycleId",
                    correlationId = "asset:$assetId:purchase:$lifecycleId",
                )
                syncRecorder?.record("ASSET", assetId, SyncChangeType.CREATE.name, now, valid.syncPayload())
                assetId
            } else {
                val current = dao.assetById(id) ?: error("دارایی پیدا نشد.")
                require(current.status == "ACTIVE") { "دارایی خارج‌شده قابل ویرایش نیست." }
                require(
                    current.assetCode == valid.assetCode &&
                        current.quantity == valid.quantity &&
                        current.purchaseEpochDay == valid.purchaseEpochDay &&
                        current.purchaseCostRial == valid.purchaseCostRial &&
                        current.salvageValueRial == valid.salvageValueRial &&
                        current.usefulLifeMonths == valid.usefulLifeMonths,
                ) {
                    "کد، تعداد، تاریخ و مبالغ مبنای دارایی پس از ثبت سند تحصیل قابل تغییر نیست؛ اصلاح مالی باید با سند اصلاحی انجام شود."
                }
                require(current.location == valid.location && current.branchId == branch?.id && current.responsiblePerson == valid.responsiblePerson) {
                    "شعبه، محل و مسئول دارایی فقط از گردش انتقال دارایی قابل تغییر است تا تاریخچه از بین نرود."
                }
                check(dao.updateAsset(current.copy(assetCode = valid.assetCode, name = valid.name, category = valid.category, quantity = valid.quantity, purchaseEpochDay = valid.purchaseEpochDay, purchaseCostRial = valid.purchaseCostRial, salvageValueRial = valid.salvageValueRial, usefulLifeMonths = valid.usefulLifeMonths, notes = valid.notes, updatedAtEpochMillis = now)) == 1)
                audit.appendAuthorized(
                    authorizer = authorizer,
                    action = "UPDATE",
                    entityType = "ASSET",
                    entityId = id,
                    description = "ویرایش مشخصات غیرمالی دارایی ${current.assetCode}",
                    occurredAtEpochMillis = now,
                    businessEpochDay = current.purchaseEpochDay,
                    beforeSnapshot = "name=${current.name};category=${current.category};location=${current.location};branch=${current.branch};responsible=${current.responsiblePerson}",
                    afterSnapshot = "name=${valid.name};category=${valid.category};location=${valid.location};branch=${valid.branch};responsible=${valid.responsiblePerson}",
                    correlationId = "asset:$id:update:$now",
                )
                syncRecorder?.record("ASSET", id, SyncChangeType.UPDATE.name, now, valid.syncPayload())
                id
            }
        }
    }

    override suspend fun recognizeImportedAsset(id: Long): Long {
        val actor = authorizer.require(Permission.ASSETS)
        val now = clock()
        return database.withTransaction {
            val asset = dao.assetById(id) ?: error("دارایی پیدا نشد.")
            require(asset.status == "ACTIVE") { "فقط دارایی فعال قابل تطبیق مالی است." }
            require(accounting.entryBySource("ASSET_ACQUISITION", asset.id) == null) {
                "سند تحصیل این دارایی قبلاً ثبت شده است."
            }
            postAcquisitionJournal(
                asset = asset,
                source = AssetAcquisitionSource.OWNER_CAPITAL,
                postingEpochDay = currentLocalEpochDay(),
                description = "ثبت مانده افتتاحیه دارایی قدیمی: ${asset.name}",
                actorId = actor.id,
            ).also { entryId ->
                syncRecorder?.record(
                    "ASSET",
                    asset.id,
                    "RECOGNIZE_ACCOUNTING",
                    now,
                    mapOf("acquisitionSource" to AssetAcquisitionSource.OWNER_CAPITAL.name, "journalEntryId" to entryId.toString()),
                )
            }
        }
    }

    override suspend fun dispose(id: Long) {
        val actor = authorizer.require(Permission.ASSETS)
        val now = clock()
        database.withTransaction {
            val asset = dao.assetById(id) ?: error("دارایی پیدا نشد.")
            require(asset.status == "ACTIVE") { "دارایی قبلاً از چرخه بهره‌برداری خارج شده است." }
            require(accounting.entryBySource("ASSET_ACQUISITION", asset.id) != null) {
                "دارایی قدیمی فاقد سند تحصیل است؛ ابتدا مانده افتتاحیه آن را در حسابداری تطبیق دهید."
            }
            val bookValue = SignedLongMath.subtract(SignedLongMath.subtract(asset.purchaseCostRial, asset.accumulatedDepreciationRial), asset.impairmentRial)
            require(bookValue >= 0) { "ارزش دفتری دارایی نامعتبر است." }
            val disposalLines = buildList {
                if (asset.accumulatedDepreciationRial > 0) add(SemanticJournalLine(SemanticAccountRole.ACCUMULATED_DEPRECIATION, debit = MoneyRial.of(asset.accumulatedDepreciationRial), memo = "بستن استهلاک انباشته"))
                if (asset.impairmentRial > 0) add(SemanticJournalLine(SemanticAccountRole.ACCUMULATED_IMPAIRMENT, debit = MoneyRial.of(asset.impairmentRial), memo = "بستن کاهش ارزش انباشته"))
                if (bookValue > 0) add(SemanticJournalLine(SemanticAccountRole.ASSET_DISPOSAL_LOSS, debit = MoneyRial.of(bookValue), memo = "زیان خروج دارایی بدون عایدی"))
                add(SemanticJournalLine(SemanticAccountRole.FIXED_ASSET, credit = MoneyRial.of(asset.purchaseCostRial), memo = asset.assetCode))
            }
            val postingEpochDay = currentLocalEpochDay()
            val entryId = accountingPosting.post(
                draft = SemanticJournalDraft(
                    entryNo = "خد-$id-$now",
                    entryEpochDay = postingEpochDay,
                    description = "خروج بدون عایدی دارایی ثابت: ${asset.name}",
                    sourceType = "ASSET_DISPOSAL",
                    sourceId = asset.id,
                    accountingScope = asset.accountingScope(),
                    branchId = asset.branchId,
                    lines = disposalLines,
                ),
                context = AccountingPostingContext.local(
                    sourceType = "ASSET_DISPOSAL",
                    sourceId = asset.id,
                    suffix = "post",
                    actorId = actor.id,
                    correlationId = "asset_disposal:${asset.id}",
                ),
            )
            check(dao.disposeAsset(id, now) == 1) { "خروج دارایی انجام نشد." }
            val lifecycleId = database.assetLifecycleDao().insertEvent(
                AssetLifecycleEventEntity(
                    assetId = asset.id,
                    eventType = AssetLifecycleType.DISPOSAL.name,
                    businessEpochDay = postingEpochDay,
                    amountRial = bookValue,
                    fromLocation = asset.location,
                    fromBranch = asset.branch,
                    fromResponsiblePerson = asset.responsiblePerson,
                    note = "خروج بدون عایدی",
                    journalEntryId = entryId,
                    actorId = actor.id,
                    createdAtEpochMillis = now,
                ),
            )
            audit.appendAuthorized(
                authorizer = authorizer,
                action = "DISPOSE",
                entityType = "ASSET",
                entityId = asset.id,
                description = "خروج بدون عایدی ${asset.assetCode}",
                occurredAtEpochMillis = now,
                businessEpochDay = postingEpochDay,
                reason = "خروج بدون عایدی",
                beforeSnapshot = "historicalCost=${asset.purchaseCostRial};accDep=${asset.accumulatedDepreciationRial};impairment=${asset.impairmentRial};book=$bookValue",
                afterSnapshot = "status=DISPOSED;event=$lifecycleId;journal=$entryId",
                correlationId = "asset:${asset.id}:disposal:$lifecycleId",
            )
            syncRecorder?.record(
                "ASSET",
                id,
                SyncChangeType.DISPOSE.name,
                now,
                mapOf("bookValueRial" to bookValue.toString(), "disposalJournalEntryId" to entryId.toString(), "lifecycleEventId" to lifecycleId.toString()),
            )
        }
    }

    override suspend fun postDepreciation(draft: DepreciationDraft): Long {
        val actor = authorizer.require(Permission.ASSET_LIFECYCLE)
        val valid = draft.validated()
        GlobalId.parse(valid.commandId)
        return database.withTransaction {
            dao.depreciationByCommandId(valid.commandId)?.let { replay ->
                require(
                    replay.assetId == valid.assetId && replay.periodYear == valid.periodYear && replay.periodMonth == valid.periodMonth &&
                        replay.postingEpochDay == valid.postingEpochDay && replay.quantity == valid.quantity && replay.reason == valid.reason,
                ) { "تعارض idempotency در ثبت استهلاک." }
                return@withTransaction replay.id
            }
            val asset = dao.assetById(valid.assetId) ?: error("دارایی پیدا نشد.")
            require(asset.status == "ACTIVE") { "دارایی فعال نیست." }
            require(asset.isDateWithinLifecycle(valid.postingEpochDay)) { "تاریخ استهلاک باید از تاریخ تحصیل تا امروز باشد." }
            require(accounting.entryBySource("ASSET_ACQUISITION", asset.id) != null) {
                "برای این دارایی سند تحصیل ثبت نشده است؛ قبل از استهلاک، مانده افتتاحیه را تطبیق دهید."
            }
            val alreadyQuantity = dao.activeDepreciatedQuantity(asset.id, valid.periodYear, valid.periodMonth)
            val alreadyPeriodAmount = dao.activeDepreciatedAmount(asset.id, valid.periodYear, valid.periodMonth)
            require(alreadyQuantity + valid.quantity <= asset.quantity) { "تعداد استهلاک این دوره از تعداد دارایی بیشتر است." }
            val depreciable = asset.purchaseCostRial - asset.salvageValueRial
            val remainingNow = depreciable - asset.accumulatedDepreciationRial - asset.impairmentRial
            require(remainingNow > 0) { "دارایی کاملاً مستهلک یا کاهش‌ارزش‌یافته است." }
            val remainingAtPeriodStart = Math.addExact(remainingNow, alreadyPeriodAmount)
            val elapsedMonths = ChronoUnit.MONTHS.between(
                LocalDate.ofEpochDay(asset.purchaseEpochDay).withDayOfMonth(1),
                LocalDate.ofEpochDay(valid.postingEpochDay).withDayOfMonth(1),
            ).toInt().coerceAtLeast(0)
            val remainingLifeMonths = (asset.usefulLifeMonths - elapsedMonths).coerceAtLeast(1)
            val periodTarget = minOf(remainingAtPeriodStart / remainingLifeMonths, remainingAtPeriodStart)
            require(periodTarget > 0) { "مبلغ استهلاک محاسبه‌شده صفر است." }
            val completesPeriodQuantity = alreadyQuantity + valid.quantity == asset.quantity
            val rawAmount = if (completesPeriodQuantity) periodTarget - alreadyPeriodAmount
                else mulDivExact(periodTarget, valid.quantity.toLong(), asset.quantity.toLong())
            val amount = minOf(rawAmount, remainingNow)
            require(amount > 0) { "مبلغ استهلاک برای تعداد انتخاب‌شده صفر است." }
            val now = clock()
            val entryId = accountingPosting.post(
                draft = SemanticJournalDraft(
                    entryNo = "د-${asset.id}-${valid.periodYear}-${valid.periodMonth}-${valid.commandId.take(8)}",
                    entryEpochDay = valid.postingEpochDay,
                    description = "استهلاک ${asset.name}؛ تعداد ${valid.quantity}؛ ${valid.reason}",
                    sourceType = "ASSET_DEPRECIATION", sourceId = asset.id,
                    accountingScope = asset.accountingScope(), branchId = asset.branchId,
                    lines = listOf(
                        SemanticJournalLine(SemanticAccountRole.DEPRECIATION_EXPENSE, debit = MoneyRial.of(amount), memo = valid.reason),
                        SemanticJournalLine(SemanticAccountRole.ACCUMULATED_DEPRECIATION, credit = MoneyRial.of(amount), memo = valid.reason),
                    ),
                ),
                context = AccountingPostingContext.local(
                    sourceType = "ASSET_DEPRECIATION", sourceId = asset.id, suffix = valid.commandId, actorId = actor.id,
                    correlationId = "asset_depreciation:${asset.id}:${valid.commandId}",
                ),
            )
            check(dao.addDepreciation(asset.id, amount, now) == 1) { "ثبت استهلاک از ارزش مجاز بیشتر است." }
            val depreciationId = dao.insertDepreciation(
                AssetDepreciationEntity(
                    assetId = asset.id, periodYear = valid.periodYear, periodMonth = valid.periodMonth, amountRial = amount,
                    journalEntryId = entryId, createdAtEpochMillis = now, quantity = valid.quantity, postingEpochDay = valid.postingEpochDay,
                    reason = valid.reason, commandId = valid.commandId,
                ),
            )
            check(depreciationId > 0) { "ثبت سابقه استهلاک انجام نشد." }
            val lifecycleId = database.assetLifecycleDao().insertEvent(
                AssetLifecycleEventEntity(
                    assetId = asset.id, eventType = AssetLifecycleType.DEPRECIATION.name, businessEpochDay = valid.postingEpochDay,
                    amountRial = amount, note = "qty=${valid.quantity};${valid.reason}", journalEntryId = entryId, actorId = actor.id, createdAtEpochMillis = now,
                ),
            )
            audit.appendAuthorized(
                authorizer, "DEPRECIATE", "ASSET", asset.id, "استهلاک ${asset.assetCode}", now, valid.postingEpochDay, valid.reason,
                beforeSnapshot = "accDep=${asset.accumulatedDepreciationRial};impairment=${asset.impairmentRial}",
                afterSnapshot = "depreciationId=$depreciationId;qty=${valid.quantity};amount=$amount;event=$lifecycleId;journal=$entryId",
                correlationId = "asset:${asset.id}:depreciation:$depreciationId",
            )
            syncRecorder?.record(
                "ASSET_DEPRECIATION", depreciationId, SyncChangeType.CREATE.name, now,
                mapOf("assetId" to asset.id.toString(), "amountRial" to amount.toString(), "quantity" to valid.quantity.toString(), "reason" to valid.reason, "journalEntryId" to entryId.toString()),
            )
            depreciationId
        }
    }

    override suspend fun reverseDepreciation(draft: DepreciationReversalDraft): Long {
        val actor = authorizer.require(Permission.ASSET_LIFECYCLE)
        val valid = draft.validated()
        return database.withTransaction {
            val depreciation = dao.depreciationById(valid.depreciationId) ?: error("سابقه استهلاک پیدا نشد.")
            if (depreciation.reversedAtEpochMillis != null) {
                require(depreciation.reversalEpochDay == valid.reversalEpochDay && depreciation.reversalReason == valid.reason) { "تعارض idempotency در برگشت استهلاک." }
                return@withTransaction requireNotNull(depreciation.reversalJournalEntryId)
            }
            require(valid.reversalEpochDay >= depreciation.postingEpochDay) { "تاریخ برگشت نمی‌تواند قبل از تاریخ استهلاک باشد." }
            require(valid.reversalEpochDay <= currentLocalEpochDay()) { "تاریخ برگشت نمی‌تواند در آینده باشد." }
            val asset = dao.assetById(depreciation.assetId) ?: error("دارایی پیدا نشد.")
            val original = accounting.entryById(depreciation.journalEntryId) ?: error("سند استهلاک پیدا نشد.")
            require(original.status == "POSTED" && original.sourceType == "ASSET_DEPRECIATION") { "سند مبدأ استهلاک معتبر نیست." }
            val lines = accounting.linesByEntry(original.id)
            require(lines.size >= 2) { "آرتیکل‌های سند استهلاک کامل نیستند." }
            val now = clock()
            val posted = accountingPosting.postBalanced(
                draft = BalancedJournalDraft(
                    entryEpochDay = valid.reversalEpochDay, description = "برگشت استهلاک ${asset.name}: ${valid.reason}",
                    sourceType = "ASSET_DEPRECIATION_REVERSAL", sourceId = depreciation.id,
                    accountingScope = AccountingScope.fromStoredValue(original.accountingScope), branchId = original.branchId,
                    lines = lines.map { line -> JournalLineDraft(accountCode = line.accountCode, debit = MoneyRial.of(line.creditRial), credit = MoneyRial.of(line.debitRial), memo = valid.reason) },
                ),
                context = AccountingPostingContext.local(
                    sourceType = "ASSET_DEPRECIATION_REVERSAL", sourceId = depreciation.id, suffix = "reverse:${depreciation.id}",
                    actorId = actor.id, correlationId = "asset_depreciation_reversal:${depreciation.id}", reversalOfEntryId = original.id,
                ),
                entryNoFactory = { id -> "با-$id" },
            )
            check(dao.subtractDepreciation(asset.id, depreciation.amountRial, now) == 1) { "برگشت مانده استهلاک انجام نشد." }
            check(dao.markDepreciationReversed(depreciation.id, now, valid.reversalEpochDay, valid.reason, posted.entryId) == 1) { "سابقه استهلاک قبلاً برگشت شده است." }
            val eventId = database.assetLifecycleDao().insertEvent(
                AssetLifecycleEventEntity(assetId = asset.id, eventType = AssetLifecycleType.DEPRECIATION_REVERSAL.name, businessEpochDay = valid.reversalEpochDay, amountRial = depreciation.amountRial, note = valid.reason, journalEntryId = posted.entryId, actorId = actor.id, createdAtEpochMillis = now),
            )
            audit.appendAuthorized(
                authorizer, "REVERSE_DEPRECIATION", "ASSET", asset.id, "برگشت استهلاک ${asset.assetCode}", now, valid.reversalEpochDay, valid.reason,
                beforeSnapshot = "depreciationId=${depreciation.id};amount=${depreciation.amountRial}", afterSnapshot = "reversalJournal=${posted.entryId};event=$eventId",
                correlationId = "asset:${asset.id}:depreciation-reversal:${depreciation.id}",
            )
            posted.entryId
        }
    }

    override suspend fun transfer(draft: AssetTransferDraft): Long {
        val actor = authorizer.require(Permission.ASSET_LIFECYCLE)
        val reason = draft.reason.trim()
        require(draft.assetId > 0 && draft.businessEpochDay > 0 && reason.length in 3..300) { "اطلاعات انتقال دارایی ناقص است." }
        return database.withTransaction {
            val asset = dao.assetById(draft.assetId) ?: error("دارایی پیدا نشد.")
            require(asset.status == "ACTIVE") { "فقط دارایی فعال قابل انتقال است." }
            val location = draft.toLocation.trim()
            val canonicalBranch = branchResolver.resolveOptional(draft.toBranchId, draft.toBranch)
            val branch = canonicalBranch?.name.orEmpty()
            val responsible = draft.toResponsiblePerson.trim()
            require(location.isNotBlank() || branch.isNotBlank() || responsible.isNotBlank()) { "مقصد انتقال مشخص نشده است." }
            val now = clock()
            check(dao.transferAsset(asset.id, location, branch, canonicalBranch?.id, responsible, now) == 1) { "انتقال دارایی انجام نشد." }
            val eventId = database.assetLifecycleDao().insertEvent(AssetLifecycleEventEntity(
                assetId = asset.id, eventType = AssetLifecycleType.TRANSFER.name, businessEpochDay = draft.businessEpochDay,
                fromLocation = asset.location, toLocation = location, fromBranch = asset.branch, toBranch = branch,
                fromResponsiblePerson = asset.responsiblePerson, toResponsiblePerson = responsible, note = reason,
                actorId = actor.id, createdAtEpochMillis = now,
            ))
            audit.appendAuthorized(authorizer, "TRANSFER", "ASSET", asset.id, "انتقال دارایی ${asset.assetCode}", now, draft.businessEpochDay, reason,
                beforeSnapshot = "location=${asset.location};branch=${asset.branch};responsible=${asset.responsiblePerson}",
                afterSnapshot = "location=$location;branch=$branch;responsible=$responsible", correlationId = "asset:${asset.id}:transfer:$eventId")
            syncRecorder?.record("ASSET", asset.id, "TRANSFER", now, mapOf("eventId" to eventId.toString()))
            eventId
        }
    }

    override suspend fun recordMaintenance(draft: AssetMaintenanceDraft): Long {
        val actor = authorizer.require(Permission.ASSET_LIFECYCLE)
        val type = draft.serviceType.trim()
        require(draft.assetId > 0 && draft.serviceEpochDay > 0 && type.isNotBlank()) { "اطلاعات سرویس دارایی ناقص است." }
        require(draft.costRial >= 0) { "هزینه سرویس نامعتبر است." }
        draft.nextServiceEpochDay?.let { require(it > draft.serviceEpochDay) { "تاریخ سرویس بعدی باید بعد از سرویس فعلی باشد." } }
        require(draft.paymentSource != AssetAcquisitionSource.OWNER_CAPITAL) { "منبع پرداخت نگهداری معتبر نیست." }
        if (draft.paymentSource == AssetAcquisitionSource.PAYABLE) {
            require(draft.costRial > 0) { "حساب پرداختنی برای سرویس بدون هزینه ایجاد نمی‌شود." }
            require((draft.supplierId ?: 0L) > 0) { "برای سرویس نسیه، تأمین‌کننده الزامی است." }
            require((draft.payableDueEpochDay ?: 0L) >= draft.serviceEpochDay) { "سررسید حساب پرداختنی سرویس معتبر نیست." }
        } else {
            require(draft.supplierId == null && draft.payableDueEpochDay == null) { "تأمین‌کننده و سررسید فقط برای سرویس نسیه ثبت می‌شوند." }
        }
        return database.withTransaction {
            val asset = dao.assetById(draft.assetId) ?: error("دارایی پیدا نشد.")
            require(asset.status == "ACTIVE") { "فقط دارایی فعال قابل سرویس است." }
            val supplier = draft.supplierId?.let { database.supplierDao().activeById(it) ?: error("تأمین‌کننده فعال سرویس پیدا نشد.") }
            val contractor = draft.contractor.trim().ifBlank { supplier?.name.orEmpty() }
            val now = clock()
            val maintenanceId = database.assetLifecycleDao().insertMaintenance(
                AssetMaintenanceEntity(
                    assetId = asset.id, serviceType = type, serviceEpochDay = draft.serviceEpochDay,
                    costRial = draft.costRial, contractor = contractor, note = draft.note.trim(),
                    nextServiceEpochDay = draft.nextServiceEpochDay, paymentSource = draft.paymentSource.name,
                    supplierId = supplier?.id, payableDueEpochDay = draft.payableDueEpochDay,
                    actorId = actor.id, createdAtEpochMillis = now,
                ),
            )
            check(maintenanceId > 0) { "ثبت سابقه سرویس انجام نشد." }
            val journalId = if (draft.costRial > 0) {
                when (draft.paymentSource) {
                    AssetAcquisitionSource.CASH, AssetAcquisitionSource.BANK -> {
                        val (accountId, channel) = draft.paymentSource.treasuryAccountAndChannel()
                        val commandId = GlobalId.new()
                        requireNotNull(
                            treasury.execute(
                                TreasuryCommand.Payment(
                                    commandId = commandId, businessEpochDay = draft.serviceEpochDay,
                                    correlationId = CorrelationId.forCommand("asset_maintenance", commandId),
                                    businessIntent = TreasuryBusinessIntent.ASSET_MAINTENANCE, sourceId = maintenanceId,
                                    reason = draft.note.trim().ifBlank { "تعمیر و نگهداری ${asset.name}" },
                                    accountingScope = asset.accountingScope(), branchId = asset.branchId,
                                    accountId = accountId, channel = channel, amount = MoneyRial.of(draft.costRial),
                                ),
                            ).journalEntryId,
                        ) { "سند خزانه نگهداری دارایی ایجاد نشد." }
                    }
                    AssetAcquisitionSource.PAYABLE -> {
                        val posted = accountingPosting.post(
                            draft = SemanticJournalDraft(
                                entryNo = "نگ-$maintenanceId", entryEpochDay = draft.serviceEpochDay,
                                description = "تعمیر و نگهداری ${asset.name}", sourceType = "ASSET_MAINTENANCE", sourceId = maintenanceId,
                                accountingScope = asset.accountingScope(), branchId = asset.branchId,
                                lines = listOf(
                                    SemanticJournalLine(SemanticAccountRole.MAINTENANCE_EXPENSE, debit = MoneyRial.of(draft.costRial), memo = type),
                                    SemanticJournalLine(SemanticAccountRole.SUPPLIER_PAYABLE, credit = MoneyRial.of(draft.costRial), memo = contractor),
                                ),
                            ),
                            context = AccountingPostingContext.local("ASSET_MAINTENANCE", maintenanceId, "post", actor.id, "asset_maintenance:$maintenanceId"),
                        )
                        payables.ensureOrigin(
                            sourceType = "ASSET_MAINTENANCE", sourceId = maintenanceId,
                            sourceDocumentNo = "MAINT-$maintenanceId", supplierId = requireNotNull(draft.supplierId),
                            branchId = asset.branchId, issueEpochDay = draft.serviceEpochDay,
                            dueEpochDay = requireNotNull(draft.payableDueEpochDay), originalRial = draft.costRial,
                            actorId = actor.id, correlationId = "asset_maintenance:$maintenanceId", originJournalEntryId = posted,
                        )
                        posted
                    }
                    AssetAcquisitionSource.OWNER_CAPITAL -> error("منبع پرداخت نگهداری معتبر نیست.")
                }
            } else null
            database.assetLifecycleDao().insertEvent(
                AssetLifecycleEventEntity(
                    assetId = asset.id, eventType = AssetLifecycleType.MAINTENANCE.name,
                    businessEpochDay = draft.serviceEpochDay, amountRial = draft.costRial,
                    counterparty = contractor, note = draft.note.trim(), journalEntryId = journalId,
                    actorId = actor.id, createdAtEpochMillis = now,
                ),
            )
            audit.appendAuthorized(
                authorizer, "MAINTENANCE", "ASSET", asset.id, "سرویس ${asset.assetCode}: $type", now,
                draft.serviceEpochDay, draft.note.trim().ifBlank { "ثبت سرویس دارایی" },
                afterSnapshot = "maintenanceId=$maintenanceId;cost=${draft.costRial};supplier=${draft.supplierId};next=${draft.nextServiceEpochDay}",
                correlationId = "asset:${asset.id}:maintenance:$maintenanceId",
            )
            maintenanceId
        }
    }

    override suspend fun impair(draft: AssetImpairmentDraft): Long {
        val actor = authorizer.require(Permission.ASSET_LIFECYCLE)
        val reason = draft.reason.trim()
        require(draft.assetId > 0 && draft.businessEpochDay > 0 && draft.amountRial > 0 && reason.length in 3..300) { "اطلاعات کاهش ارزش ناقص است." }
        return database.withTransaction {
            val asset = dao.assetById(draft.assetId) ?: error("دارایی پیدا نشد.")
            require(asset.status == "ACTIVE") { "دارایی فعال نیست." }
            require(asset.isDateWithinLifecycle(draft.businessEpochDay)) { "تاریخ کاهش ارزش باید از تاریخ تحصیل تا امروز باشد." }
            val bookBefore = asset.purchaseCostRial - asset.accumulatedDepreciationRial - asset.impairmentRial
            require(draft.amountRial <= bookBefore - asset.salvageValueRial) { "کاهش ارزش نمی‌تواند ارزش دفتری را کمتر از ارزش اسقاط کند." }
            val now = clock()
            val journalId = accountingPosting.post(
                draft = SemanticJournalDraft(
                    entryNo = "کز-${asset.id}-$now", entryEpochDay = draft.businessEpochDay, description = "کاهش ارزش ${asset.name}", sourceType = "ASSET_IMPAIRMENT", sourceId = asset.id,
                    accountingScope = asset.accountingScope(), branchId = asset.branchId,
                    lines = listOf(
                        SemanticJournalLine(SemanticAccountRole.ASSET_IMPAIRMENT_LOSS, debit = MoneyRial.of(draft.amountRial), memo = reason),
                        SemanticJournalLine(SemanticAccountRole.ACCUMULATED_IMPAIRMENT, credit = MoneyRial.of(draft.amountRial), memo = asset.assetCode),
                    ),
                ),
                context = AccountingPostingContext.local("ASSET_IMPAIRMENT", asset.id, now.toString(), actor.id, "asset_impairment:${asset.id}:$now"),
            )
            check(dao.addImpairment(asset.id, draft.amountRial, now) == 1) { "ثبت کاهش ارزش انجام نشد." }
            val eventId = database.assetLifecycleDao().insertEvent(AssetLifecycleEventEntity(assetId = asset.id, eventType = AssetLifecycleType.IMPAIRMENT.name, businessEpochDay = draft.businessEpochDay, amountRial = draft.amountRial, note = reason, journalEntryId = journalId, actorId = actor.id, createdAtEpochMillis = now))
            audit.appendAuthorized(authorizer, "IMPAIR", "ASSET", asset.id, "کاهش ارزش ${asset.assetCode}", now, draft.businessEpochDay, reason, beforeSnapshot = "book=$bookBefore", afterSnapshot = "book=${bookBefore-draft.amountRial};event=$eventId", correlationId = "asset:${asset.id}:impair:$eventId")
            eventId
        }
    }

    override suspend fun sell(draft: AssetSaleDraft): Long {
        val actor = authorizer.require(Permission.ASSET_LIFECYCLE)
        val reason = draft.reason.trim()
        require(draft.assetId > 0 && draft.businessEpochDay > 0 && draft.salePriceRial >= 0 && reason.length in 3..300) { "اطلاعات فروش دارایی ناقص است." }
        require(draft.receiptSource == AssetAcquisitionSource.CASH || draft.receiptSource == AssetAcquisitionSource.BANK) { "دریافت فروش دارایی باید نقد یا بانک باشد." }
        return database.withTransaction {
            val asset = dao.assetById(draft.assetId) ?: error("دارایی پیدا نشد.")
            require(asset.status == "ACTIVE") { "فقط دارایی فعال قابل فروش است." }
            val bookValue = asset.purchaseCostRial - asset.accumulatedDepreciationRial - asset.impairmentRial
            require(bookValue >= 0) { "ارزش دفتری دارایی نامعتبر است." }
            val gain = (draft.salePriceRial - bookValue).coerceAtLeast(0)
            val loss = (bookValue - draft.salePriceRial).coerceAtLeast(0)
            val lines = buildList {
                if (draft.salePriceRial > 0) add(SemanticJournalLine(SemanticAccountRole.TREASURY_CLEARING, debit = MoneyRial.of(draft.salePriceRial), memo = draft.buyer.trim()))
                if (asset.accumulatedDepreciationRial > 0) add(SemanticJournalLine(SemanticAccountRole.ACCUMULATED_DEPRECIATION, debit = MoneyRial.of(asset.accumulatedDepreciationRial)))
                if (asset.impairmentRial > 0) add(SemanticJournalLine(SemanticAccountRole.ACCUMULATED_IMPAIRMENT, debit = MoneyRial.of(asset.impairmentRial)))
                if (loss > 0) add(SemanticJournalLine(SemanticAccountRole.ASSET_DISPOSAL_LOSS, debit = MoneyRial.of(loss)))
                add(SemanticJournalLine(SemanticAccountRole.FIXED_ASSET, credit = MoneyRial.of(asset.purchaseCostRial), memo = asset.assetCode))
                if (gain > 0) add(SemanticJournalLine(SemanticAccountRole.ASSET_DISPOSAL_GAIN, credit = MoneyRial.of(gain)))
            }
            val now = clock()
            val journalId = accountingPosting.post(
                draft = SemanticJournalDraft(
                    entryNo = "فد-${asset.id}-$now", description = "فروش دارایی ${asset.name}", entryEpochDay = draft.businessEpochDay,
                    sourceType = "ASSET_SALE", sourceId = asset.id, accountingScope = asset.accountingScope(), branchId = asset.branchId, lines = lines,
                ),
                context = AccountingPostingContext.local("ASSET_SALE", asset.id, now.toString(), actor.id, "asset_sale:${asset.id}:$now"),
            )
            if (draft.salePriceRial > 0) {
                val (accountId, channel) = draft.receiptSource.treasuryAccountAndChannel()
                val commandId = GlobalId.new()
                val treasuryResult = treasury.execute(
                    TreasuryCommand.Receipt(
                        commandId = commandId, businessEpochDay = draft.businessEpochDay,
                        correlationId = CorrelationId.forCommand("asset_disposal_receipt", commandId),
                        businessIntent = TreasuryBusinessIntent.ASSET_DISPOSAL_RECEIPT, sourceId = asset.id,
                        reason = reason, accountingScope = asset.accountingScope(), branchId = asset.branchId,
                        accountId = accountId, channel = channel, amount = MoneyRial.of(draft.salePriceRial),
                    ),
                )
                require(treasuryResult.journalEntryId != null) { "سند خزانه فروش دارایی ایجاد نشد." }
            }
            check(dao.markSold(asset.id, draft.salePriceRial, draft.businessEpochDay, now) == 1) { "ثبت فروش دارایی انجام نشد." }
            val eventId = database.assetLifecycleDao().insertEvent(AssetLifecycleEventEntity(assetId = asset.id, eventType = AssetLifecycleType.SALE.name, businessEpochDay = draft.businessEpochDay, amountRial = draft.salePriceRial, counterparty = draft.buyer.trim(), note = reason, journalEntryId = journalId, actorId = actor.id, createdAtEpochMillis = now))
            audit.appendAuthorized(authorizer, "SALE", "ASSET", asset.id, "فروش ${asset.assetCode}", now, draft.businessEpochDay, reason, beforeSnapshot = "historicalCost=${asset.purchaseCostRial};accDep=${asset.accumulatedDepreciationRial};impairment=${asset.impairmentRial};book=$bookValue", afterSnapshot = "sale=${draft.salePriceRial};gain=$gain;loss=$loss;status=SOLD", correlationId = "asset:${asset.id}:sale:$eventId")
            eventId
        }
    }

    override fun observeLifecycle(assetId: Long): Flow<List<AssetLifecycleRecord>> = database.assetLifecycleDao().observeEvents(assetId).map { rows ->
        rows.map { AssetLifecycleRecord(it.id, it.assetId, AssetLifecycleType.valueOf(it.eventType), it.businessEpochDay, it.amountRial, it.note) }
    }

    override fun observeMaintenance(assetId: Long): Flow<List<AssetMaintenanceRecord>> = database.assetLifecycleDao().observeMaintenance(assetId).map { rows ->
        rows.map { AssetMaintenanceRecord(it.id, it.assetId, it.serviceType, it.serviceEpochDay, it.costRial, it.contractor, it.nextServiceEpochDay) }
    }

    private fun AssetDraft.syncPayload(): Map<String, String> = mapOf(
        "acquisitionSource" to acquisitionSource.name,
        "assetCode" to assetCode,
        "category" to category,
        "location" to location,
        "branch" to branch,
        "branchId" to branchId?.toString().orEmpty(),
        "supplierId" to supplierId?.toString().orEmpty(),
        "payableDueEpochDay" to payableDueEpochDay?.toString().orEmpty(),
        "responsiblePerson" to responsiblePerson,
        "name" to name,
        "purchaseCostRial" to purchaseCostRial.toString(),
        "purchaseEpochDay" to purchaseEpochDay.toString(),
        "quantity" to quantity.toString(),
        "salvageValueRial" to salvageValueRial.toString(),
        "usefulLifeMonths" to usefulLifeMonths.toString(),
    )

    private suspend fun postAcquisitionJournal(
        asset: FixedAssetEntity,
        source: AssetAcquisitionSource,
        postingEpochDay: Long,
        actorId: Long,
        description: String = "تحصیل دارایی ثابت: ${asset.name}",
    ): Long {
        if (source == AssetAcquisitionSource.CASH || source == AssetAcquisitionSource.BANK) {
            val (accountId, channel) = source.treasuryAccountAndChannel()
            val commandId = GlobalId.new()
            return requireNotNull(
                treasury.execute(
                    TreasuryCommand.Payment(
                        commandId = commandId, businessEpochDay = postingEpochDay,
                        correlationId = CorrelationId.forCommand("asset_acquisition", commandId),
                        businessIntent = TreasuryBusinessIntent.ASSET_ACQUISITION, sourceId = asset.id,
                        reason = description, accountingScope = asset.accountingScope(), branchId = asset.branchId,
                        accountId = accountId, channel = channel, amount = MoneyRial.of(asset.purchaseCostRial),
                    ),
                ).journalEntryId,
            ) { "سند خزانه تحصیل دارایی ایجاد نشد." }
        }
        val sourceRole = when (source) {
            AssetAcquisitionSource.PAYABLE -> SemanticAccountRole.SUPPLIER_PAYABLE
            AssetAcquisitionSource.OWNER_CAPITAL -> SemanticAccountRole.OWNER_CAPITAL
            AssetAcquisitionSource.CASH, AssetAcquisitionSource.BANK -> error("asset_liquidity_must_use_treasury")
        }
        return accountingPosting.post(
            draft = SemanticJournalDraft(
                entryNo = "تد-${asset.id}", entryEpochDay = postingEpochDay, description = description,
                sourceType = "ASSET_ACQUISITION", sourceId = asset.id, accountingScope = asset.accountingScope(), branchId = asset.branchId,
                lines = listOf(
                    SemanticJournalLine(SemanticAccountRole.FIXED_ASSET, debit = MoneyRial.of(asset.purchaseCostRial), memo = asset.assetCode),
                    SemanticJournalLine(sourceRole, credit = MoneyRial.of(asset.purchaseCostRial), memo = source.title),
                ),
            ),
            context = AccountingPostingContext.local(
                sourceType = "ASSET_ACQUISITION", sourceId = asset.id, suffix = "post", actorId = actorId,
                correlationId = "asset_acquisition:${asset.id}",
            ),
        )
    }

    private fun AssetAcquisitionSource.treasuryAccountAndChannel(): Pair<TreasuryAccountId, TreasuryChannel> = when (this) {
        AssetAcquisitionSource.CASH -> TreasuryAccountId.parse("cash_main") to TreasuryChannel.CASH
        AssetAcquisitionSource.BANK -> TreasuryAccountId.parse("bank_main") to TreasuryChannel.BANK
        AssetAcquisitionSource.PAYABLE, AssetAcquisitionSource.OWNER_CAPITAL -> error("asset_source_is_not_liquidity")
    }

    private fun FixedAssetEntity.isDateWithinLifecycle(day: Long): Boolean =
        day >= purchaseEpochDay && day <= currentLocalEpochDay() && (disposedEpochDay == null || day <= disposedEpochDay)

    private fun mulDivExact(a: Long, b: Long, divisor: Long): Long {
        require(a >= 0 && b >= 0 && divisor > 0)
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(divisor)).toLongExactCompat()
    }

    private fun FixedAssetEntity.accountingScope(): AccountingScope =
        if (branchId != null) AccountingScope.BRANCH else AccountingScope.ORGANIZATION
}

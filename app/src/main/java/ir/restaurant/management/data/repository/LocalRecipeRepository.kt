package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.MenuItemEntity
import ir.restaurant.management.data.db.RecipeComponentEntity
import ir.restaurant.management.data.db.RecipeSubstitutionEntity
import ir.restaurant.management.data.db.RecipeVersionEntity
import ir.restaurant.management.data.db.RecipeVersionIngredientEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.UnitConversionFactor
import ir.restaurant.management.domain.recipe.MenuItem
import ir.restaurant.management.domain.recipe.RecipeComponentInput
import ir.restaurant.management.domain.recipe.RecipeCostProfile
import ir.restaurant.management.domain.recipe.RecipeDraftInput
import ir.restaurant.management.domain.recipe.RecipeIngredientInput
import ir.restaurant.management.domain.recipe.RecipeIngredientItem
import ir.restaurant.management.domain.recipe.RecipeLifecycleState
import ir.restaurant.management.domain.recipe.RecipeRepository
import ir.restaurant.management.domain.recipe.RecipeRevision
import ir.restaurant.management.domain.recipe.RecipeSubstitutionDraft
import ir.restaurant.management.domain.recipe.RecipeVersionDetails
import ir.restaurant.management.domain.recipe.RecipeVersionOption
import ir.restaurant.management.domain.security.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalRecipeRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
    private val authorizer: SessionAuthorizer,
    private val epochDay: () -> Long = ::currentLocalEpochDay,
) : RecipeRepository {
    private val auditWriter = LocalAuditEventWriter(database)

    /** Compatibility entry point used by the existing Persian recipe screen.
     * It appends a new immutable revision and activates it atomically; it never edits an ACTIVE revision in place.
     */
    override suspend fun saveMenuItem(
        id: Long?,
        name: String,
        category: String,
        salePriceRial: Long,
        ingredients: List<RecipeIngredientInput>,
        costProfile: RecipeCostProfile,
    ): Long {
        authorizer.require(Permission.RECIPES)
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "نام محصول الزامی است." }
        require(salePriceRial >= 0) { "قیمت فروش نامعتبر است." }
        validateIngredientInput(ingredients)
        val validCost = costProfile.validated()
        val now = clock()
        return database.withTransaction {
            val menuId = if (id == null) {
                database.recipeDao().insertMenuItem(
                    MenuItemEntity(
                        name = normalizedName,
                        category = category.trim(),
                        salePriceRial = salePriceRial,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
            } else {
                val old = database.recipeDao().activeMenuItem(id) ?: error("محصول منو پیدا نشد.")
                check(database.recipeDao().updateMenuItem(old.copy(name = normalizedName, category = category.trim(), salePriceRial = salePriceRial, updatedAtEpochMillis = now)) == 1)
                id
            }
            validateInventoryIngredients(ingredients)
            val actor = authorizer.actor()
            val revisionNo = database.recipeDao().nextRevisionNo(menuId)
            val versionId = database.recipeDao().insertVersion(
                versionEntity(menuId, revisionNo, epochDay(), validCost, actor, now, RecipeLifecycleState.ACTIVE, null),
            )
            insertIngredients(versionId, ingredients)
            database.recipeLifecycleDao().retireOtherActive(menuId, versionId)
            auditWriter.appendAuthorized(
                authorizer, if (revisionNo == 1) "CREATE" else "ACTIVATE", "RECIPE_VERSION", versionId,
                "رسپی $normalizedName؛ نسخه $revisionNo فعال شد", now, epochDay(),
                validCost.note.ifBlank { "ثبت نسخه immutable رسپی" },
                afterSnapshot = "menuItemId=$menuId;revisionNo=$revisionNo;status=ACTIVE;ingredientCount=${ingredients.size}",
                correlationId = "recipe:$menuId:revision:$revisionNo",
            )
            syncRecorder?.record("MENU_ITEM", menuId, if (id == null) "CREATE" else "UPDATE", now)
            syncRecorder?.record("RECIPE_VERSION", versionId, "ACTIVATE", now, recordAudit = false)
            menuId
        }
    }

    override suspend fun createDraft(input: RecipeDraftInput, parentVersionId: Long?): Long {
        authorizer.require(Permission.RECIPES)
        val valid = validateDraft(input)
        return database.withTransaction {
            database.recipeDao().activeMenuItem(valid.menuItemId) ?: error("محصول منو پیدا نشد.")
            parentVersionId?.let { parent ->
                val source = database.recipeLifecycleDao().versionById(parent) ?: error("نسخه مبنا پیدا نشد.")
                require(source.menuItemId == valid.menuItemId) { "نسخه مبنا متعلق به محصول دیگری است." }
            }
            validateInventoryIngredients(valid.ingredients)
            validateComponents(valid.menuItemId, valid.components)
            val now = clock()
            val actor = authorizer.actor()
            val revision = database.recipeDao().nextRevisionNo(valid.menuItemId)
            val versionId = database.recipeDao().insertVersion(
                versionEntity(valid.menuItemId, revision, 0, valid.costProfile, actor, now, RecipeLifecycleState.DRAFT, parentVersionId),
            )
            insertIngredients(versionId, valid.ingredients)
            insertComponents(versionId, valid.components)
            ensureAcyclic(valid.menuItemId, valid.components.map { it.subRecipeVersionId })
            auditWriter.appendAuthorized(
                authorizer, "CREATE_DRAFT", "RECIPE_VERSION", versionId, "ایجاد پیش‌نویس رسپی نسخه $revision", now,
                epochDay(), valid.note.ifBlank { "ایجاد پیش‌نویس" }, afterSnapshot = "menuItemId=${valid.menuItemId};status=DRAFT;parent=$parentVersionId",
                correlationId = "recipe:${valid.menuItemId}:draft:$versionId",
            )
            syncRecorder?.record("RECIPE_VERSION", versionId, "CREATE_DRAFT", now, recordAudit = false)
            versionId
        }
    }

    override suspend fun copyVersion(versionId: Long): Long {
        authorizer.require(Permission.RECIPES)
        require(versionId > 0)
        return database.withTransaction {
            val source = database.recipeLifecycleDao().versionById(versionId) ?: error("نسخه رسپی پیدا نشد.")
            val ingredients = database.recipeDao().versionIngredients(source.id).map { RecipeIngredientInput(it.inventoryItemId, it.quantityMicrosPerUnit) }
            val components = database.recipeLifecycleDao().components(source.id).map { RecipeComponentInput(it.subRecipeVersionId, it.quantityMicrosPerUnit) }
            val now = clock()
            val actor = authorizer.actor()
            val revision = database.recipeDao().nextRevisionNo(source.menuItemId)
            val copyId = database.recipeDao().insertVersion(
                source.copy(
                    id = 0,
                    revisionNo = revision,
                    effectiveFromEpochDay = 0,
                    status = RecipeLifecycleState.DRAFT.name,
                    parentVersionId = source.id,
                    createdBy = actor,
                    createdAtEpochMillis = now,
                ),
            )
            insertIngredients(copyId, ingredients, quantitiesAlreadyStockMicros = true)
            insertComponents(copyId, components)
            auditWriter.appendAuthorized(
                authorizer, "COPY", "RECIPE_VERSION", copyId, "کپی نسخه ${source.revisionNo} به پیش‌نویس $revision", now,
                epochDay(), "ساخت نسخه جدید از تاریخچه", beforeSnapshot = "sourceVersion=${source.id}", afterSnapshot = "status=DRAFT;parent=${source.id}",
                correlationId = "recipe:${source.menuItemId}:copy:$copyId",
            )
            copyId
        }
    }

    override suspend fun editDraft(versionId: Long, input: RecipeDraftInput) {
        authorizer.require(Permission.RECIPES)
        val valid = validateDraft(input)
        database.withTransaction {
            val old = database.recipeLifecycleDao().versionById(versionId) ?: error("نسخه رسپی پیدا نشد.")
            require(old.status == RecipeLifecycleState.DRAFT.name) { "فقط پیش‌نویس قابل ویرایش است؛ نسخه فعال immutable است." }
            require(old.menuItemId == valid.menuItemId) { "محصول نسخه قابل تغییر نیست." }
            validateInventoryIngredients(valid.ingredients)
            validateComponents(valid.menuItemId, valid.components)
            ensureAcyclic(valid.menuItemId, valid.components.map { it.subRecipeVersionId })
            val now = clock()
            val updated = old.copy(
                yieldMicros = valid.costProfile.yieldMicros,
                portionWeightMicros = valid.costProfile.portionWeightMicros,
                preparationWasteBasisPoints = valid.costProfile.preparationWasteBasisPoints,
                cookingWasteBasisPoints = valid.costProfile.cookingWasteBasisPoints,
                packagingCostRial = valid.costProfile.packagingCostRial,
                directLaborCostRial = valid.costProfile.directLaborCostRial,
                allocatedOverheadRial = valid.costProfile.allocatedOverheadRial,
                note = valid.costProfile.note.ifBlank { valid.note },
            )
            check(database.recipeDao().updateVersion(updated) == 1)
            database.recipeDao().deleteVersionIngredients(versionId)
            database.recipeLifecycleDao().deleteComponents(versionId)
            insertIngredients(versionId, valid.ingredients)
            insertComponents(versionId, valid.components)
            auditWriter.appendAuthorized(
                authorizer, "EDIT_DRAFT", "RECIPE_VERSION", versionId, "ویرایش پیش‌نویس رسپی", now, epochDay(),
                valid.note.ifBlank { "ویرایش پیش‌نویس" }, afterSnapshot = "ingredients=${valid.ingredients.size};components=${valid.components.size}",
                correlationId = "recipe:${valid.menuItemId}:draft:$versionId",
            )
        }
    }

    override suspend fun activate(versionId: Long, effectiveFromEpochDay: Long): Long {
        authorizer.require(Permission.RECIPE_ACTIVATE)
        require(effectiveFromEpochDay > 0) { "تاریخ اثر نسخه معتبر نیست." }
        require(effectiveFromEpochDay <= epochDay()) { "فعال‌سازی آینده‌نگر پشتیبانی نمی‌شود؛ تا تاریخ اثر، نسخه فعلی باید فعال بماند." }
        return database.withTransaction {
            val version = database.recipeLifecycleDao().versionById(versionId) ?: error("نسخه رسپی پیدا نشد.")
            require(version.status == RecipeLifecycleState.DRAFT.name) { "فقط پیش‌نویس قابل فعال‌سازی است." }
            val directIngredients = database.recipeDao().versionIngredients(versionId)
            val components = database.recipeLifecycleDao().components(versionId)
            require(directIngredients.isNotEmpty() || components.isNotEmpty()) { "رسپی فعال باید ماده اولیه یا زیررسپی داشته باشد." }
            ensureAcyclic(version.menuItemId, components.map { it.subRecipeVersionId })
            val now = clock()
            database.recipeLifecycleDao().retireOtherActive(version.menuItemId, versionId)
            val updated = version.copy(effectiveFromEpochDay = effectiveFromEpochDay, status = RecipeLifecycleState.ACTIVE.name)
            check(database.recipeDao().updateVersion(updated) == 1) { "فعال‌سازی نسخه انجام نشد." }
            auditWriter.appendAuthorized(
                authorizer, "ACTIVATE", "RECIPE_VERSION", versionId, "فعال‌سازی نسخه ${version.revisionNo}", now,
                effectiveFromEpochDay, "فعال‌سازی کنترل‌شده رسپی", beforeSnapshot = "status=DRAFT", afterSnapshot = "status=ACTIVE;effective=$effectiveFromEpochDay",
                correlationId = "recipe:${version.menuItemId}:activate:$versionId",
            )
            syncRecorder?.record("RECIPE_VERSION", versionId, "ACTIVATE", now, recordAudit = false)
            versionId
        }
    }

    override suspend fun retire(versionId: Long, reason: String) {
        authorizer.require(Permission.RECIPE_ACTIVATE)
        val normalized = reason.trim()
        require(normalized.length in 3..300) { "دلیل بازنشسته‌کردن رسپی الزامی است." }
        database.withTransaction {
            val version = database.recipeLifecycleDao().versionById(versionId) ?: error("نسخه رسپی پیدا نشد.")
            require(version.status == RecipeLifecycleState.ACTIVE.name) { "فقط نسخه فعال قابل بازنشسته‌شدن است." }
            check(database.recipeLifecycleDao().transitionStatus(versionId, RecipeLifecycleState.ACTIVE.name, RecipeLifecycleState.RETIRED.name) == 1)
            val now = clock()
            auditWriter.appendAuthorized(authorizer, "RETIRE", "RECIPE_VERSION", versionId, "بازنشسته‌کردن نسخه ${version.revisionNo}", now, epochDay(), normalized, beforeSnapshot = "status=ACTIVE", afterSnapshot = "status=RETIRED", correlationId = "recipe:${version.menuItemId}:retire:$versionId")
            syncRecorder?.record("RECIPE_VERSION", versionId, "RETIRE", now, recordAudit = false)
        }
    }

    override suspend fun approveSubstitution(draft: RecipeSubstitutionDraft): Long {
        val actor = authorizer.require(Permission.RECIPE_ACTIVATE)
        val reason = draft.reason.trim()
        require(draft.recipeVersionId > 0 && draft.originalInventoryItemId > 0 && draft.substituteInventoryItemId > 0)
        require(draft.originalInventoryItemId != draft.substituteInventoryItemId) { "ماده جایگزین باید متفاوت باشد." }
        require(draft.ratioNumerator > 0 && draft.ratioDenominator > 0) { "نسبت جایگزینی معتبر نیست." }
        require(reason.length in 3..300) { "دلیل جایگزینی الزامی است." }
        return database.withTransaction {
            val version = database.recipeLifecycleDao().versionById(draft.recipeVersionId) ?: error("نسخه رسپی پیدا نشد.")
            require(version.status == RecipeLifecycleState.ACTIVE.name) { "جایگزینی فقط برای نسخه فعال ثبت می‌شود." }
            require(database.recipeDao().versionIngredients(version.id).any { it.inventoryItemId == draft.originalInventoryItemId }) { "ماده اصلی در این نسخه وجود ندارد." }
            require(database.inventoryDao().activeById(draft.substituteInventoryItemId) != null) { "ماده جایگزین فعال پیدا نشد." }
            val now = clock()
            val id = database.recipeLifecycleDao().insertSubstitution(
                RecipeSubstitutionEntity(
                    recipeVersionId = version.id,
                    originalInventoryItemId = draft.originalInventoryItemId,
                    substituteInventoryItemId = draft.substituteInventoryItemId,
                    ratioNumerator = draft.ratioNumerator,
                    ratioDenominator = draft.ratioDenominator,
                    reason = reason,
                    approvedByActorId = actor.id,
                    createdAtEpochMillis = now,
                    effectiveFromEpochDay = epochDay(),
                ),
            )
            auditWriter.appendAuthorized(authorizer, "APPROVE_SUBSTITUTION", "RECIPE_VERSION", version.id, "تأیید جایگزینی ماده ${draft.originalInventoryItemId} با ${draft.substituteInventoryItemId}", now, epochDay(), reason, afterSnapshot = "substitutionId=$id;ratio=${draft.ratioNumerator}/${draft.ratioDenominator}", correlationId = "recipe:${version.menuItemId}:substitution:$id")
            id
        }
    }

    override suspend fun versionDetails(versionId: Long): RecipeVersionDetails {
        require(versionId > 0) { "نسخه رسپی معتبر نیست." }
        authorizer.require(Permission.RECIPES)
        val version = database.recipeLifecycleDao().versionById(versionId) ?: error("نسخه رسپی پیدا نشد.")
        val ingredients = database.recipeDao().versionIngredients(version.id).map { row ->
            val item = database.inventoryDao().activeById(row.inventoryItemId) ?: error("ماده اولیه رسپی پیدا نشد: ${row.inventoryItemId}")
            RecipeIngredientInput(
                inventoryItemId = row.inventoryItemId,
                quantityMicrosPerUnit = UnitConversionFactor(item.recipeToStockNumerator, item.recipeToStockDenominator).fromStockMicros(row.quantityMicrosPerUnit),
            )
        }
        val components = database.recipeLifecycleDao().components(version.id).map { row ->
            RecipeComponentInput(row.subRecipeVersionId, row.quantityMicrosPerUnit)
        }
        val profile = RecipeCostProfile(
            version.yieldMicros, version.portionWeightMicros, version.preparationWasteBasisPoints, version.cookingWasteBasisPoints,
            version.packagingCostRial, version.directLaborCostRial, version.allocatedOverheadRial, version.note,
        )
        val revision = RecipeRevision(
            version.id, version.revisionNo, version.effectiveFromEpochDay, profile, version.createdBy, version.createdAtEpochMillis,
            RecipeLifecycleState.entries.firstOrNull { it.name == version.status } ?: RecipeLifecycleState.RETIRED, version.parentVersionId,
        )
        return RecipeVersionDetails(
            revision = revision,
            draft = RecipeDraftInput(version.menuItemId, ingredients, components, profile, version.note),
        )
    }

    override fun observeActiveVersionOptions(): Flow<List<RecipeVersionOption>> =
        database.recipeLifecycleDao().observeActiveVersionOptions().map { rows ->
            rows.map { RecipeVersionOption(it.versionId, it.menuItemId, it.menuItemName, it.revisionNo) }
        }

    override fun observeMenuItems(): Flow<List<MenuItem>> {
        val today = epochDay()
        return database.recipeDao().observeActiveMenuItemsWithCoverage(today).map { rows ->
            rows.map { row ->
                MenuItem(row.id, row.name, row.category, row.salePriceRial, row.ingredientCount, row.revisionNo, row.effectiveFromEpochDay,
                    RecipeCostProfile(row.yieldMicros, row.portionWeightMicros, row.preparationWasteBasisPoints, row.cookingWasteBasisPoints, row.packagingCostRial, row.directLaborCostRial, row.allocatedOverheadRial, row.note))
            }
        }
    }

    override fun observeIngredients(menuItemId: Long): Flow<List<RecipeIngredientItem>> {
        val today = epochDay()
        return database.recipeDao().observeIngredientRows(menuItemId, today).map { rows ->
            rows.map { row ->
                RecipeIngredientItem(row.inventoryItemId, row.inventoryName, row.unit,
                    UnitConversionFactor(row.recipeToStockNumerator, row.recipeToStockDenominator).fromStockMicros(row.quantityMicrosPerUnit))
            }
        }
    }

    override fun observeRevisions(menuItemId: Long): Flow<List<RecipeRevision>> =
        database.recipeDao().observeVersions(menuItemId).map { versions ->
            versions.map { version ->
                RecipeRevision(
                    version.id, version.revisionNo, version.effectiveFromEpochDay,
                    RecipeCostProfile(version.yieldMicros, version.portionWeightMicros, version.preparationWasteBasisPoints, version.cookingWasteBasisPoints, version.packagingCostRial, version.directLaborCostRial, version.allocatedOverheadRial, version.note),
                    version.createdBy, version.createdAtEpochMillis,
                    RecipeLifecycleState.entries.firstOrNull { it.name == version.status } ?: RecipeLifecycleState.RETIRED,
                    version.parentVersionId,
                )
            }
        }

    private fun validateDraft(input: RecipeDraftInput): RecipeDraftInput {
        require(input.menuItemId > 0) { "محصول منو معتبر نیست." }
        validateIngredientInput(input.ingredients, allowEmpty = input.components.isNotEmpty())
        require(input.components.map { it.subRecipeVersionId }.distinct().size == input.components.size) { "زیررسپی تکراری است." }
        input.components.forEach(RecipeComponentInput::validated)
        require(input.ingredients.isNotEmpty() || input.components.isNotEmpty()) { "رسپی باید ماده اولیه یا زیررسپی داشته باشد." }
        return input.copy(costProfile = input.costProfile.validated(), note = input.note.trim())
    }

    private fun validateIngredientInput(ingredients: List<RecipeIngredientInput>, allowEmpty: Boolean = false) {
        require(allowEmpty || ingredients.isNotEmpty()) { "رسپی باید حداقل یک ماده اولیه داشته باشد." }
        require(ingredients.all { it.inventoryItemId > 0 && it.quantityMicrosPerUnit > 0 }) { "مقدار مصرف مواد باید بیشتر از صفر باشد." }
        require(ingredients.map { it.inventoryItemId }.distinct().size == ingredients.size) { "ماده اولیه تکراری است." }
    }

    private suspend fun validateInventoryIngredients(ingredients: List<RecipeIngredientInput>) {
        ingredients.forEach { require(database.inventoryDao().activeById(it.inventoryItemId) != null) { "ماده اولیه فعال پیدا نشد: ${it.inventoryItemId}" } }
    }

    private suspend fun validateComponents(menuItemId: Long, components: List<RecipeComponentInput>) {
        components.forEach { component ->
            val child = database.recipeLifecycleDao().versionById(component.subRecipeVersionId) ?: error("نسخه زیررسپی پیدا نشد.")
            require(child.status == RecipeLifecycleState.ACTIVE.name) { "فقط نسخه فعال می‌تواند زیررسپی باشد." }
            require(child.menuItemId != menuItemId) { "رسپی نمی‌تواند مستقیماً به خودش وابسته باشد." }
        }
    }

    private suspend fun ensureAcyclic(rootMenuItemId: Long, childVersionIds: List<Long>) {
        suspend fun walk(versionId: Long, path: MutableSet<Long>) {
            require(path.add(versionId)) { "وابستگی حلقوی در زیررسپی شناسایی شد." }
            val version = database.recipeLifecycleDao().versionById(versionId) ?: error("نسخه زیررسپی پیدا نشد.")
            require(version.menuItemId != rootMenuItemId) { "وابستگی حلقوی رسپی شناسایی شد." }
            database.recipeLifecycleDao().childRecipeVersions(versionId).forEach { walk(it, path) }
            path.remove(versionId)
        }
        childVersionIds.forEach { walk(it, linkedSetOf()) }
    }

    private fun versionEntity(menuId: Long, revisionNo: Int, effective: Long, profile: RecipeCostProfile, actor: String, now: Long, state: RecipeLifecycleState, parent: Long?) =
        RecipeVersionEntity(
            menuItemId = menuId, revisionNo = revisionNo, effectiveFromEpochDay = effective,
            yieldMicros = profile.yieldMicros, portionWeightMicros = profile.portionWeightMicros,
            preparationWasteBasisPoints = profile.preparationWasteBasisPoints, cookingWasteBasisPoints = profile.cookingWasteBasisPoints,
            packagingCostRial = profile.packagingCostRial, directLaborCostRial = profile.directLaborCostRial,
            allocatedOverheadRial = profile.allocatedOverheadRial, note = profile.note, createdBy = actor,
            createdAtEpochMillis = now, status = state.name, parentVersionId = parent,
        )

    private suspend fun insertIngredients(versionId: Long, ingredients: List<RecipeIngredientInput>, quantitiesAlreadyStockMicros: Boolean = false) {
        if (ingredients.isEmpty()) return
        database.recipeDao().insertVersionIngredients(ingredients.map { ingredient ->
            val item = database.inventoryDao().activeById(ingredient.inventoryItemId) ?: error("ماده اولیه فعال پیدا نشد.")
            val stockMicros = if (quantitiesAlreadyStockMicros) ingredient.quantityMicrosPerUnit else UnitConversionFactor(item.recipeToStockNumerator, item.recipeToStockDenominator).toStockMicros(ingredient.quantityMicrosPerUnit)
            RecipeVersionIngredientEntity(versionId, ingredient.inventoryItemId, stockMicros)
        })
    }

    private suspend fun insertComponents(versionId: Long, components: List<RecipeComponentInput>) {
        if (components.isNotEmpty()) database.recipeLifecycleDao().insertComponents(components.map { RecipeComponentEntity(recipeVersionId = versionId, subRecipeVersionId = it.subRecipeVersionId, quantityMicrosPerUnit = it.quantityMicrosPerUnit) })
    }
}

package ir.restaurant.management.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.operations.InventoryItemRecord
import ir.restaurant.management.domain.recipe.MenuItem
import ir.restaurant.management.domain.recipe.FullCostCalculator
import ir.restaurant.management.domain.recipe.RecipeCostProfile
import ir.restaurant.management.domain.recipe.RecipeIngredientInput
import ir.restaurant.management.domain.recipe.RecipeIngredientItem
import ir.restaurant.management.domain.recipe.RecipeRevision
import ir.restaurant.management.domain.recipe.RecipeComponentInput
import ir.restaurant.management.domain.recipe.RecipeDraftInput
import ir.restaurant.management.domain.recipe.RecipeLifecycleState
import ir.restaurant.management.domain.recipe.RecipeSubstitutionDraft
import ir.restaurant.management.domain.recipe.RecipeVersionDetails
import ir.restaurant.management.domain.recipe.RecipeVersionOption
import ir.restaurant.management.core.currentLocalEpochDay

private data class IngredientDraft(
    val inventoryItemId: Long? = null,
    val quantity: String = "",
)

private data class ComponentDraft(
    val versionId: Long? = null,
    val quantity: String = "",
)

@Composable
fun RecipeScreen(
    state: RecipeUiState,
    onSelect: (Long?) -> Unit,
    onSave: (Long?, String, String, Long, List<RecipeIngredientInput>, RecipeCostProfile, () -> Unit) -> Unit,
    onCreateDraft: (Long) -> Unit,
    onCopyVersion: (Long) -> Unit,
    onLoadDraft: (Long) -> Unit,
    onEditDraft: (Long, RecipeDraftInput, () -> Unit) -> Unit,
    onCloseDraftEditor: () -> Unit,
    onActivate: (Long, Long) -> Unit,
    onRetire: (Long, String) -> Unit,
    onSubstitution: (RecipeSubstitutionDraft) -> Unit,
    onBack: () -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var activateTarget by remember { mutableStateOf<Long?>(null) }
    var retireTarget by remember { mutableStateOf<Long?>(null) }
    var substitutionTarget by remember { mutableStateOf<Long?>(null) }
    val categories = state.menuItems.map { it.category.ifBlank { "بدون دسته‌بندی" } }.distinct().size
    val summary = recipeDashboardSummary(state)
    val costDataWarnings = state.menuItems.count {
        it.ingredientCount == 0 || it.costProfile.yieldMicros <= 0 || it.costProfile.note.contains("تبدیل‌شده از رسپی قبلی")
    }

    Scaffold(
        topBar = {
            ProfessionalTopBar(
                title = "تولید و رسپی",
                subtitle = "فرمول ساخت، مواد اولیه و قیمت فروش محصولات",
                onBack = onBack,
                actionLabel = "محصول جدید",
                onAction = {
                    onSelect(null)
                    editorOpen = true
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("recipe_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.message?.let { message -> item { MessageCard(message) } }
            item {
                ErpDashboardHero(
                    eyebrow = "رسپی‌های فعال",
                    value = ErpDisplayFormatters.integer(summary.activeRecipes),
                    caption = "${ErpDisplayFormatters.integer(summary.menuItems)} محصول در ${ErpDisplayFormatters.integer(categories)} دسته",
                    metrics = listOf(
                        ErpKpiItem("بدون رسپی", ErpDisplayFormatters.integer(summary.missingRecipeCount)),
                        ErpKpiItem("دارای ضایعات", ErpDisplayFormatters.integer(summary.configuredWasteCount)),
                        ErpKpiItem("پیش‌نویس", ErpDisplayFormatters.integer(summary.draftRevisionCount)),
                    ),
                )
            }
            item {
                SectionHeading("عملیات سریع", "ایجاد یا بازبینی رسپی با همان workflow نسخه‌بندی‌شده")
                ErpQuickActionsGrid(
                    listOf(
                        ErpActionItem("رسپی جدید", Icons.Outlined.AddCircleOutline, ErpPalette.IndigoSoft, ErpPalette.Indigo, onClick = { onSelect(null); editorOpen = true }),
                        ErpActionItem(
                            "بازبینی ناقص", Icons.Outlined.Rule, ErpPalette.AmberSoft, ErpPalette.Amber,
                            enabled = state.menuItems.any { it.ingredientCount == 0 },
                            onClick = { state.menuItems.firstOrNull { it.ingredientCount == 0 }?.let { onSelect(it.id); editorOpen = true } },
                        ),
                    ),
                )
            }
            if (costDataWarnings > 0) {
                item {
                    ErpAttentionRow(
                        title = "داده بهای کامل نیازمند بازبینی است",
                        description = "${ErpDisplayFormatters.integer(costDataWarnings)} محصول رسپی ناقص یا داده هزینه قدیمی دارد.",
                        accent = ErpPalette.Amber,
                        soft = ErpPalette.AmberSoft,
                    )
                }
            }
            item { SectionHeading("محصولات و فرمول ساخت", "برای مشاهده یا ویرایش رسپی، محصول را انتخاب کنید") }
            if (state.menuItems.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = "هنوز محصولی تعریف نشده",
                        description = "اولین محصول را ایجاد و مواد اولیه مصرفی آن را مشخص کنید.",
                    )
                }
            } else {
                items(state.menuItems, key = { it.id }) { item ->
                    RecipeProductCard(
                        item = item,
                        ingredients = if (state.selectedMenuItemId == item.id) state.ingredients else emptyList(),
                        onClick = {
                            onSelect(item.id)
                            editorOpen = true
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (editorOpen) {
        RecipeEditorDialog(
            selected = state.menuItems.firstOrNull { it.id == state.selectedMenuItemId },
            inventory = state.inventoryItems,
            currentIngredients = state.ingredients,
            revisions = state.revisions,
            busy = state.busy,
            onDismiss = { editorOpen = false },
            onSave = onSave,
            onCreateDraft = onCreateDraft,
            onCopyVersion = onCopyVersion,
            onEditDraft = onLoadDraft,
            onActivate = { activateTarget = it },
            onRetire = { retireTarget = it },
            onSubstitution = { substitutionTarget = it },
        )
    }
    state.editingDraft?.let { details ->
        RecipeDraftEditorDialog(
            details = details,
            inventory = state.inventoryItems,
            activeVersions = state.activeVersions,
            busy = state.busy,
            onDismiss = onCloseDraftEditor,
            onSave = onEditDraft,
        )
    }
    activateTarget?.let { versionId ->
        RecipeActivationDialog(versionId, { activateTarget = null }) { day ->
            onActivate(versionId, day)
            activateTarget = null
        }
    }
    retireTarget?.let { versionId ->
        RecipeRetireDialog(versionId, { retireTarget = null }) { reason ->
            onRetire(versionId, reason)
            retireTarget = null
        }
    }
    substitutionTarget?.let { versionId ->
        RecipeSubstitutionDialog(
            versionId = versionId,
            ingredients = state.ingredients,
            inventory = state.inventoryItems,
            onDismiss = { substitutionTarget = null },
        ) { draft ->
            onSubstitution(draft)
            substitutionTarget = null
        }
    }
}

@Composable
private fun RecipeProductCard(
    item: MenuItem,
    ingredients: List<RecipeIngredientItem>,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.testTag("recipe_product_${item.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.category.ifBlank { "بدون دسته‌بندی" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    if (item.ingredientCount == 0) "رسپی ثبت نشده"
                    else if (item.costProfile.note.contains("تبدیل‌شده از رسپی قبلی")) "نیازمند پروفایل هزینه"
                    else "نسخه ${item.recipeRevisionNo} · ${item.ingredientCount} ماده",
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("بازده ${formatQuantity(item.costProfile.yieldMicros)}")
                if (item.costProfile.portionWeightMicros > 0) StatusPill("پرس ${formatQuantity(item.costProfile.portionWeightMicros)}")
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("قیمت فروش", style = MaterialTheme.typography.labelMedium)
                    Text(formatMoney(item.salePriceRial), fontWeight = FontWeight.ExtraBold)
                }
            }
            if (ingredients.isNotEmpty()) {
                Text(
                    text = ingredients.joinToString("  •  ") {
                        "${it.inventoryName}: ${formatQuantity(it.quantityMicrosPerUnit)} ${it.unit}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (item.ingredientCount == 0) {
                Text(
                    "برای این محصول هنوز مواد اولیه تعیین نشده است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else Text("برای مشاهده جزئیات مواد، محصول را باز کنید.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecipeEditorDialog(
    selected: MenuItem?,
    inventory: List<InventoryItemRecord>,
    currentIngredients: List<RecipeIngredientItem>,
    revisions: List<RecipeRevision>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, Long, List<RecipeIngredientInput>, RecipeCostProfile, () -> Unit) -> Unit,
    onCreateDraft: (Long) -> Unit,
    onCopyVersion: (Long) -> Unit,
    onEditDraft: (Long) -> Unit,
    onActivate: (Long) -> Unit,
    onRetire: (Long) -> Unit,
    onSubstitution: (Long) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(selected?.id) { mutableStateOf(selected?.name.orEmpty()) }
    var category by remember(selected?.id) { mutableStateOf(selected?.category.orEmpty()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val productCategories = listOf("غذای اصلی", "پیش‌غذا", "سالاد", "نوشیدنی", "دسر", "صبحانه", "فست‌فود", "سرویس و افزودنی")
    var price by remember(selected?.id) { mutableStateOf(selected?.salePriceRial?.let(::formatMoneyInputFromRial).orEmpty()) }
    val existingProfile = selected?.costProfile ?: RecipeCostProfile()
    var productionYield by remember(selected?.id) { mutableStateOf(formatQuantity(existingProfile.yieldMicros)) }
    var portionWeight by remember(selected?.id) { mutableStateOf(if (existingProfile.portionWeightMicros == 0L) "" else formatQuantity(existingProfile.portionWeightMicros)) }
    var preparationWaste by remember(selected?.id) { mutableStateOf(formatBasisPointsInput(existingProfile.preparationWasteBasisPoints)) }
    var cookingWaste by remember(selected?.id) { mutableStateOf(formatBasisPointsInput(existingProfile.cookingWasteBasisPoints)) }
    var packagingCost by remember(selected?.id) { mutableStateOf(existingProfile.packagingCostRial.takeIf { it > 0 }?.let(::formatMoneyInputFromRial).orEmpty()) }
    var directLaborCost by remember(selected?.id) { mutableStateOf(existingProfile.directLaborCostRial.takeIf { it > 0 }?.let(::formatMoneyInputFromRial).orEmpty()) }
    var allocatedOverhead by remember(selected?.id) { mutableStateOf(existingProfile.allocatedOverheadRial.takeIf { it > 0 }?.let(::formatMoneyInputFromRial).orEmpty()) }
    var revisionNote by remember(selected?.id) { mutableStateOf(existingProfile.note) }
    var error by remember(selected?.id) { mutableStateOf<String?>(null) }
    var rows by remember(selected?.id, currentIngredients) {
        mutableStateOf(
            if (currentIngredients.isEmpty()) listOf(IngredientDraft())
            else currentIngredients.map { IngredientDraft(it.inventoryItemId, formatQuantity(it.quantityMicrosPerUnit)) },
        )
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                if (selected == null) "محصول و رسپی جدید"
                else "نسخه جدید رسپی ${selected.recipeRevisionNo + 1}",
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp).testTag("recipe_editor_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    FormSection("مشخصات محصول", "اطلاعات پایه محصول قابل فروش") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; error = null },
                            label = { Text("نام محصول") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(category.ifBlank { "انتخاب دسته‌بندی منو" }) }
                            androidx.compose.material3.DropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                                productCategories.forEach { value -> androidx.compose.material3.DropdownMenuItem(text = { Text(value) }, onClick = { category = value; categoryExpanded = false }) }
                            }
                        }
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = formatMoneyInput(it); error = null },
                            label = { Text("قیمت فروش (${currencyUnitLabel()})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item { SectionHeading("مواد اولیه", "مقدار مصرف هر ماده برای تولید یک واحد") }
                itemsIndexed(rows) { index, row ->
                    IngredientEditorCard(
                        index = index,
                        row = row,
                        inventory = inventory,
                        removable = rows.size > 1,
                        onSelectItem = { itemId ->
                            rows = rows.toMutableList().also { it[index] = row.copy(inventoryItemId = itemId) }
                            error = null
                        },
                        onQuantityChange = { value ->
                            rows = rows.toMutableList().also { it[index] = row.copy(quantity = value) }
                            error = null
                        },
                        onRemove = { rows = rows.toMutableList().also { it.removeAt(index) } },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { rows = rows + IngredientDraft() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("افزودن ماده اولیه") }
                }
                item {
                    FormSection("پروفایل تولید", "این مشخصات در نسخه رسپی قفل و تاریخی می‌شوند") {
                        OutlinedTextField(productionYield, { productionYield = it; error = null }, label = { Text("بازده تولید") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(portionWeight, { portionWeight = it; error = null }, label = { Text("وزن استاندارد هر پرس (اختیاری)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(preparationWaste, { preparationWaste = it; error = null }, label = { Text("ضایعات آماده‌سازی ٪") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(cookingWaste, { cookingWaste = it; error = null }, label = { Text("ضایعات پخت ٪") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (revisions.isNotEmpty()) item {
                    FormSection("تاریخچه نسخه‌ها", "نسخه‌های قبلی فقط خواندنی هستند") {
                        revisions.take(6).forEachIndexed { index, revision ->
                            Text("نسخه ${revision.revisionNo} · ${epochDayToPersian(revision.effectiveFromEpochDay).display()} · ${revision.createdBy}", fontWeight = FontWeight.Bold)
                            Text(
                                "بسته‌بندی ${formatMoney(revision.costProfile.packagingCostRial)} · کار مستقیم ${formatMoney(revision.costProfile.directLaborCostRial)} · سربار ${formatMoney(revision.costProfile.allocatedOverheadRial)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (index == 0 && revisions.size > 1) {
                                val previous = revisions[1].costProfile
                                Text(
                                    "تغییر نسبت به نسخه قبل: بسته‌بندی ${formatSignedMoney(ir.restaurant.management.core.SignedLongMath.subtract(revision.costProfile.packagingCostRial, previous.packagingCostRial))} · کار ${formatSignedMoney(ir.restaurant.management.core.SignedLongMath.subtract(revision.costProfile.directLaborCostRial, previous.directLaborCostRial))} · سربار ${formatSignedMoney(ir.restaurant.management.core.SignedLongMath.subtract(revision.costProfile.allocatedOverheadRial, previous.allocatedOverheadRial))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (revision.costProfile.note.isNotBlank()) Text(revision.costProfile.note, style = MaterialTheme.typography.bodySmall)
                            Text("وضعیت: ${when (revision.state) { RecipeLifecycleState.DRAFT -> "پیش‌نویس"; RecipeLifecycleState.ACTIVE -> "فعال"; RecipeLifecycleState.RETIRED -> "بازنشسته" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                when (revision.state) {
                                    RecipeLifecycleState.DRAFT -> {
                                        TextButton(enabled = !busy, onClick = { onEditDraft(revision.id) }) { Text("ویرایش") }
                                        TextButton(enabled = !busy, onClick = { onActivate(revision.id) }, modifier = Modifier.testTag("recipe_activate_${revision.id}")) { Text("فعال‌سازی") }
                                    }
                                    RecipeLifecycleState.ACTIVE -> {
                                        TextButton(enabled = !busy, onClick = { onCreateDraft(revision.id) }, modifier = Modifier.testTag("recipe_create_draft_${revision.id}")) { Text("پیش‌نویس جدید") }
                                        TextButton(enabled = !busy, onClick = { onCopyVersion(revision.id) }) { Text("کپی") }
                                        TextButton(enabled = !busy, onClick = { onSubstitution(revision.id) }) { Text("جایگزینی") }
                                        TextButton(enabled = !busy, onClick = { onRetire(revision.id) }) { Text("بازنشسته") }
                                    }
                                    RecipeLifecycleState.RETIRED -> TextButton(enabled = !busy, onClick = { onCopyVersion(revision.id) }) { Text("کپی به پیش‌نویس") }
                                }
                            }
                        }
                    }
                }
                item {
                    FormSection("هزینه‌های مستقیم و تخصیصی", "مبلغ هر واحد فروش؛ مستقل از سند مصرف موجودی") {
                        OutlinedTextField(packagingCost, { packagingCost = formatMoneyInput(it); error = null }, label = { Text("بسته‌بندی (${currencyUnitLabel()})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(directLaborCost, { directLaborCost = formatMoneyInput(it); error = null }, label = { Text("نیروی کار مستقیم (${currencyUnitLabel()})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(allocatedOverhead, { allocatedOverhead = formatMoneyInput(it); error = null }, label = { Text("سربار تخصیصی (${currencyUnitLabel()})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(revisionNote, { revisionNote = it.take(500); error = null }, label = { Text("یادداشت این بازنگری") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    val estimatedCost = rows.fold(0L) { total, row ->
                        val stock = inventory.firstOrNull { it.id == row.inventoryItemId }
                        val lineCost = if (stock == null || stock.stockMicros <= 0) 0L else safeMulDiv(stock.inventoryValueRial, parseQuantityMicros(row.quantity).coerceAtLeast(0L), stock.stockMicros)
                        ir.restaurant.management.core.SignedLongMath.add(total, lineCost)
                    }
                    val salePrice = parseMoneyInputOrNull(price) ?: 0L
                    val preview = runCatching { FullCostCalculator.calculate(FullCostCalculator.Input(estimatedCost, parseQuantityMicros(productionYield), parseBasisPoints(preparationWaste), parseBasisPoints(cookingWaste), parseMoneyInputOrNull(packagingCost) ?: 0L, parseMoneyInputOrNull(directLaborCost) ?: 0L, parseMoneyInputOrNull(allocatedOverhead) ?: 0L, salePrice)) }.getOrNull()
                    FormSection("تحلیل بهای کامل", "پیش‌نمایش مدیریتی؛ حاشیه بهای کامل سود خالص حسابداری نیست") {
                        CompactInfoRow("مواد اولیه", formatMoney(estimatedCost))
                        CompactInfoRow("اثر ضایعات", preview?.let { formatMoney(it.wasteImpactRial) } ?: "نامعتبر")
                        CompactInfoRow("بهای مواد پس از ضایعات", preview?.let { formatMoney(it.foodCostRial) } ?: "نامعتبر")
                        CompactInfoRow("بسته‌بندی", preview?.let { formatMoney(it.packagingCostRial) } ?: "—")
                        CompactInfoRow("نیروی مستقیم", preview?.let { formatMoney(it.directLaborCostRial) } ?: "—")
                        CompactInfoRow("سربار تخصیصی", preview?.let { formatMoney(it.allocatedOverheadRial) } ?: "—")
                        CompactInfoRow("بهای کامل", preview?.let { formatMoney(it.fullCostRial) } ?: "نامعتبر", preview?.fullMarginRial?.let { it < 0 } == true)
                        CompactInfoRow("حاشیه پس از هزینه مواد", preview?.let { "${formatMoney(it.foodMarginRial)} · ${formatBasisPoints(it.foodCostBasisPoints)} هزینه" } ?: "—")
                        CompactInfoRow("حاشیه پس از بهای کامل", preview?.let { "${formatMoney(it.fullMarginRial)} · ${formatBasisPoints(it.fullCostBasisPoints)} هزینه" } ?: "—", preview?.fullMarginRial?.let { it < 0 } == true)
                    }
                }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val normalizedName = name.trim()
                    val salePrice = parseMoneyInputOrNull(price)
                    val validRows = rows.filter { it.inventoryItemId != null && parseQuantityMicros(it.quantity) > 0L }
                    val yieldMicros = parseQuantityMicros(productionYield)
                    val prepWaste = parseBasisPoints(preparationWaste)
                    val cookWaste = parseBasisPoints(cookingWaste)
                    val parsedPackaging = parseMoneyInputOrNull(packagingCost)
                    val parsedLabor = parseMoneyInputOrNull(directLaborCost)
                    val parsedOverhead = parseMoneyInputOrNull(allocatedOverhead)
                    error = when {
                        salePrice == null -> "قیمت فروش از محدوده مجاز خارج است."
                        normalizedName.length < 2 -> "نام محصول را کامل وارد کنید."
                        salePrice <= 0L -> "قیمت فروش باید بیشتر از صفر باشد."
                        validRows.isEmpty() -> "حداقل یک ماده اولیه و مقدار مصرف معتبر وارد کنید."
                        validRows.mapNotNull { it.inventoryItemId }.distinct().size != validRows.size -> "هر ماده اولیه فقط یک‌بار قابل انتخاب است."
                        yieldMicros <= 0L -> "بازده تولید باید بیشتر از صفر باشد."
                        prepWaste !in 0..FullCostCalculator.MAX_WASTE_BASIS_POINTS || cookWaste !in 0..FullCostCalculator.MAX_WASTE_BASIS_POINTS -> "درصد ضایعات باید بین صفر و کمتر از صد باشد."
                        packagingCost.isNotBlank() && parsedPackaging == null -> "هزینه بسته‌بندی از محدوده مجاز خارج است."
                        directLaborCost.isNotBlank() && parsedLabor == null -> "هزینه نیروی مستقیم از محدوده مجاز خارج است."
                        allocatedOverhead.isNotBlank() && parsedOverhead == null -> "سربار تخصیصی از محدوده مجاز خارج است."
                        else -> null
                    }
                    if (error == null) {
                        onSave(
                            selected?.id,
                            normalizedName,
                            category.trim(),
                            requireNotNull(salePrice),
                            validRows.map { row ->
                                RecipeIngredientInput(
                                    inventoryItemId = requireNotNull(row.inventoryItemId) {
                                        "ماده اولیه انتخاب نشده است."
                                    },
                                    quantityMicrosPerUnit = parseQuantityMicros(row.quantity),
                                )
                            },
                            RecipeCostProfile(
                                yieldMicros = yieldMicros,
                                portionWeightMicros = parseQuantityMicros(portionWeight),
                                preparationWasteBasisPoints = prepWaste,
                                cookingWasteBasisPoints = cookWaste,
                                packagingCostRial = parsedPackaging ?: 0L,
                                directLaborCostRial = parsedLabor ?: 0L,
                                allocatedOverheadRial = parsedOverhead ?: 0L,
                                note = revisionNote,
                            ),
                            onDismiss,
                        )
                    }
                },
            ) { Text(if (busy) "در حال ذخیره…" else "ذخیره") }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (selected != null && currentIngredients.isNotEmpty()) {
                    OutlinedButton(onClick = { printRecipeSheet(context, selected, currentIngredients) }) {
                        Text("چاپ برگه رسپی / PDF")
                    }
                }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
}


@Composable
private fun RecipeDraftEditorDialog(
    details: RecipeVersionDetails,
    inventory: List<InventoryItemRecord>,
    activeVersions: List<RecipeVersionOption>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, RecipeDraftInput, () -> Unit) -> Unit,
) {
    val initial = details.draft
    var ingredients by remember(details.revision.id) {
        mutableStateOf(initial.ingredients.map { IngredientDraft(it.inventoryItemId, formatQuantity(it.quantityMicrosPerUnit)) }.ifEmpty { listOf(IngredientDraft()) })
    }
    var components by remember(details.revision.id) {
        mutableStateOf(initial.components.map { ComponentDraft(it.subRecipeVersionId, formatQuantity(it.quantityMicrosPerUnit)) })
    }
    var yieldText by remember(details.revision.id) { mutableStateOf(formatQuantity(initial.costProfile.yieldMicros)) }
    var portionText by remember(details.revision.id) { mutableStateOf(initial.costProfile.portionWeightMicros.takeIf { it > 0 }?.let(::formatQuantity).orEmpty()) }
    var prepWaste by remember(details.revision.id) { mutableStateOf(formatBasisPointsInput(initial.costProfile.preparationWasteBasisPoints)) }
    var cookWaste by remember(details.revision.id) { mutableStateOf(formatBasisPointsInput(initial.costProfile.cookingWasteBasisPoints)) }
    var packaging by remember(details.revision.id) { mutableStateOf(initial.costProfile.packagingCostRial.takeIf { it > 0 }?.let(::formatMoneyInputFromRial).orEmpty()) }
    var labor by remember(details.revision.id) { mutableStateOf(initial.costProfile.directLaborCostRial.takeIf { it > 0 }?.let(::formatMoneyInputFromRial).orEmpty()) }
    var overhead by remember(details.revision.id) { mutableStateOf(initial.costProfile.allocatedOverheadRial.takeIf { it > 0 }?.let(::formatMoneyInputFromRial).orEmpty()) }
    var note by remember(details.revision.id) { mutableStateOf(initial.note.ifBlank { initial.costProfile.note }) }
    var error by remember(details.revision.id) { mutableStateOf<String?>(null) }
    val componentOptions = activeVersions.filter { it.menuItemId != initial.menuItemId }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("ویرایش پیش‌نویس نسخه ${details.revision.revisionNo}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("نسخه فعال ویرایش نمی‌شود؛ تغییرات فقط روی پیش‌نویس ذخیره می‌شوند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                SectionHeading("مواد اولیه", "ورودی مستقیم رسپی")
                ingredients.forEachIndexed { index, row ->
                    IngredientEditorCard(
                        index = index,
                        row = row,
                        inventory = inventory,
                        removable = ingredients.size > 1,
                        onSelectItem = { id -> ingredients = ingredients.toMutableList().also { it[index] = row.copy(inventoryItemId = id) } },
                        onQuantityChange = { value -> ingredients = ingredients.toMutableList().also { it[index] = row.copy(quantity = value) } },
                        onRemove = { ingredients = ingredients.toMutableList().also { it.removeAt(index) } },
                    )
                }
                OutlinedButton(onClick = { ingredients = ingredients + IngredientDraft() }, modifier = Modifier.fillMaxWidth()) { Text("افزودن ماده") }
                SectionHeading("زیررسپی", "فقط نسخه فعال محصول دیگر قابل انتخاب است")
                components.forEachIndexed { index, row ->
                    Card { Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(componentOptions, key = { it.versionId }) { option ->
                                FilterChip(
                                    selected = row.versionId == option.versionId,
                                    onClick = { components = components.toMutableList().also { it[index] = row.copy(versionId = option.versionId) } },
                                    label = { Text("${option.menuItemName} v${option.revisionNo}") },
                                )
                            }
                        }
                        OutlinedTextField(row.quantity, { value -> components = components.toMutableList().also { it[index] = row.copy(quantity = value.filter { ch -> ch.isDigit() || ch == '.' }) } }, label = { Text("مقدار زیررسپی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { components = components.toMutableList().also { it.removeAt(index) } }) { Text("حذف زیررسپی") }
                    } }
                }
                OutlinedButton(enabled = componentOptions.isNotEmpty(), onClick = { components = components + ComponentDraft() }, modifier = Modifier.fillMaxWidth()) { Text("افزودن زیررسپی") }
                SectionHeading("بهای تمام‌شده", "تصویر ثابت این نسخه")
                OutlinedTextField(yieldText, { yieldText = it }, label = { Text("بازده تولید") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(portionText, { portionText = it }, label = { Text("وزن پرس") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(prepWaste, { prepWaste = it }, label = { Text("ضایعات آماده‌سازی ٪") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(cookWaste, { cookWaste = it }, label = { Text("ضایعات پخت ٪") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(packaging, { packaging = formatMoneyInput(it) }, label = { Text("بسته‌بندی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(labor, { labor = formatMoneyInput(it) }, label = { Text("نیروی مستقیم") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(overhead, { overhead = formatMoneyInput(it) }, label = { Text("سربار") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(500) }, label = { Text("یادداشت پیش‌نویس") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val validIngredients = ingredients.filter { it.inventoryItemId != null && parseQuantityMicros(it.quantity) > 0 }
                val validComponents = components.filter { it.versionId != null && parseQuantityMicros(it.quantity) > 0 }
                val profile = RecipeCostProfile(
                    yieldMicros = parseQuantityMicros(yieldText),
                    portionWeightMicros = parseQuantityMicros(portionText),
                    preparationWasteBasisPoints = parseBasisPoints(prepWaste),
                    cookingWasteBasisPoints = parseBasisPoints(cookWaste),
                    packagingCostRial = if (packaging.isBlank()) 0L else parseMoneyInputOrNull(packaging) ?: -1,
                    directLaborCostRial = if (labor.isBlank()) 0L else parseMoneyInputOrNull(labor) ?: -1,
                    allocatedOverheadRial = if (overhead.isBlank()) 0L else parseMoneyInputOrNull(overhead) ?: -1,
                    note = note,
                )
                error = when {
                    validIngredients.isEmpty() && validComponents.isEmpty() -> "حداقل ماده اولیه یا زیررسپی الزامی است."
                    validIngredients.mapNotNull { it.inventoryItemId }.distinct().size != validIngredients.size -> "ماده اولیه تکراری است."
                    validComponents.mapNotNull { it.versionId }.distinct().size != validComponents.size -> "زیررسپی تکراری است."
                    runCatching { profile.validated() }.isFailure -> runCatching { profile.validated() }.exceptionOrNull()?.message ?: "پروفایل هزینه نامعتبر است."
                    else -> null
                }
                if (error == null) {
                    onSave(
                        details.revision.id,
                        RecipeDraftInput(
                            menuItemId = initial.menuItemId,
                            ingredients = validIngredients.map { RecipeIngredientInput(requireNotNull(it.inventoryItemId), parseQuantityMicros(it.quantity)) },
                            components = validComponents.map { RecipeComponentInput(requireNotNull(it.versionId), parseQuantityMicros(it.quantity)) },
                            costProfile = profile,
                            note = note,
                        ),
                        onDismiss,
                    )
                }
            }) { Text(if (busy) "در حال ذخیره…" else "ذخیره پیش‌نویس") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun RecipeActivationDialog(versionId: Long, onDismiss: () -> Unit, onActivate: (Long) -> Unit) {
    var day by remember(versionId) { mutableLongStateOf(currentLocalEpochDay()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فعال‌سازی نسخه رسپی") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("نسخه فعال قبلی بازنشسته می‌شود و فروش‌های گذشته تصویر ثابت تاریخی خود را حفظ می‌کنند."); PersianDateField("تاریخ شروع اثر", day) { day = it } } },
        confirmButton = { Button(onClick = { onActivate(day) }, modifier = Modifier.testTag("recipe_activate_confirm")) { Text("فعال‌سازی") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun RecipeRetireDialog(versionId: Long, onDismiss: () -> Unit, onRetire: (String) -> Unit) {
    var reason by remember(versionId) { mutableStateOf("") }
    var error by remember(versionId) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازنشسته‌کردن نسخه") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(reason, { reason = it.take(300); error = null }, label = { Text("دلیل") }, minLines = 2); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { Button(onClick = { if (reason.trim().length < 3) error = "دلیل حداقل ۳ نویسه باشد." else onRetire(reason.trim()) }) { Text("بازنشسته") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun RecipeSubstitutionDialog(
    versionId: Long,
    ingredients: List<RecipeIngredientItem>,
    inventory: List<InventoryItemRecord>,
    onDismiss: () -> Unit,
    onSave: (RecipeSubstitutionDraft) -> Unit,
) {
    var originalId by remember(versionId) { mutableStateOf(ingredients.firstOrNull()?.inventoryItemId) }
    var substituteId by remember(versionId) { mutableStateOf<Long?>(null) }
    var numerator by remember(versionId) { mutableStateOf("1") }
    var denominator by remember(versionId) { mutableStateOf("1") }
    var reason by remember(versionId) { mutableStateOf("") }
    var error by remember(versionId) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جایگزینی کنترل‌شده ماده") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ماده اصلی", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(ingredients, key = { it.inventoryItemId }) { item -> FilterChip(selected = originalId == item.inventoryItemId, onClick = { originalId = item.inventoryItemId }, label = { Text(item.inventoryName) }) } }
            Text("ماده جایگزین", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(inventory.filter { it.id != originalId }, key = { it.id }) { item -> FilterChip(selected = substituteId == item.id, onClick = { substituteId = item.id }, label = { Text(item.name) }) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(numerator, { numerator = it.filter(Char::isDigit) }, label = { Text("صورت نسبت") }, modifier = Modifier.weight(1f))
                OutlinedTextField(denominator, { denominator = it.filter(Char::isDigit) }, label = { Text("مخرج نسبت") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(reason, { reason = it.take(300); error = null }, label = { Text("دلیل جایگزینی") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = {
            val original = originalId
            val substitute = substituteId
            val num = numerator.toLongOrNull() ?: 0
            val den = denominator.toLongOrNull() ?: 0
            error = when { original == null -> "ماده اصلی انتخاب نشده است."; substitute == null -> "ماده جایگزین انتخاب نشده است."; num <= 0 || den <= 0 -> "نسبت باید مثبت باشد."; reason.trim().length < 3 -> "دلیل جایگزینی الزامی است."; else -> null }
            if (error == null) onSave(RecipeSubstitutionDraft(versionId, requireNotNull(original), requireNotNull(substitute), num, den, reason.trim()))
        }) { Text("ثبت با Audit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun parseBasisPoints(value: String): Int {
    val micros = parseQuantityMicros(value)
    if (micros < 0L) return -1
    return (micros / 10_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
private fun formatBasisPointsInput(value: Int): String = if (value == 0) "0" else if (value % 100 == 0) (value / 100).toString() else "%d.%02d".format(value / 100, value % 100)
private fun formatBasisPoints(value: Int?): String = value?.let { toPersianDigits("%d.%02d".format(it / 100, kotlin.math.abs(it % 100))) + "٪" } ?: "—"
private fun safeMulDiv(left: Long, right: Long, divisor: Long): Long {
    require(left >= 0 && right >= 0 && divisor > 0)
    val result = java.math.BigInteger.valueOf(left).multiply(java.math.BigInteger.valueOf(right)).divide(java.math.BigInteger.valueOf(divisor))
    require(result <= java.math.BigInteger.valueOf(Long.MAX_VALUE)) { "محاسبه هزینه از محدوده امن خارج است." }
    return result.toLong()
}
private fun formatSignedMoney(value: Long): String = (if (value > 0) "+" else "") + formatMoney(value)

@Composable
private fun IngredientEditorCard(
    index: Int,
    row: IngredientDraft,
    inventory: List<InventoryItemRecord>,
    removable: Boolean,
    onSelectItem: (Long) -> Unit,
    onQuantityChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ماده اولیه ${index + 1}", fontWeight = FontWeight.Bold)
                if (removable) TextButton(onClick = onRemove) { Text("حذف") }
            }
            if (inventory.isEmpty()) {
                Text("ابتدا در بخش انبار یک ماده اولیه ثبت کنید.", color = MaterialTheme.colorScheme.error)
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(inventory, key = { it.id }) { item ->
                        FilterChip(
                            selected = row.inventoryItemId == item.id,
                            onClick = { onSelectItem(item.id) },
                            label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            val selectedItem = inventory.firstOrNull { it.id == row.inventoryItemId }
            OutlinedTextField(
                value = row.quantity,
                onValueChange = { value -> onQuantityChange(value.filter { it.isDigit() || it == '.' }) },
                label = { Text("مصرف برای یک واحد${selectedItem?.unit?.let { " ($it)" }.orEmpty()}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun parseQuantityMicros(value: String): Long = try {
    ir.restaurant.management.core.QuantityMicros.parse(value).value
} catch (_: NumberFormatException) {
    -1L
} catch (_: ArithmeticException) {
    -1L
} catch (_: IllegalArgumentException) {
    -1L
}

package ir.restaurant.management.domain.control

object ManagementDefaults {
    const val FOOD_COST_VARIANCE_BASIS_POINTS = 500
    const val WASTE_SPIKE_BASIS_POINTS = 3000
    const val PURCHASE_PRICE_INCREASE_BASIS_POINTS = 1000
    const val CASH_VARIANCE_RIAL = 1_000_000L
    const val INVENTORY_USAGE_VARIANCE_BASIS_POINTS = 800

    fun openingChecklist(): ChecklistTemplateDraft = ChecklistTemplateDraft(
        branchId = null,
        name = "چک‌لیست افتتاح",
        type = ChecklistType.OPENING,
        items = listOf(
            ChecklistTemplateItemDraft("بررسی دمای سردخانه"),
            ChecklistTemplateItemDraft("بررسی یخچال‌ها"),
            ChecklistTemplateItemDraft("کنترل مواد حساس"),
            ChecklistTemplateItemDraft("کنترل نظافت"),
            ChecklistTemplateItemDraft("کنترل تجهیزات"),
            ChecklistTemplateItemDraft("بررسی حضور پرسنل"),
        ),
    )

    fun closingChecklist(): ChecklistTemplateDraft = ChecklistTemplateDraft(
        branchId = null,
        name = "چک‌لیست اختتام",
        type = ChecklistType.CLOSING,
        items = listOf(
            ChecklistTemplateItemDraft("ثبت ضایعات"),
            ChecklistTemplateItemDraft("شمارش اقلام حساس"),
            ChecklistTemplateItemDraft("تطبیق صندوق"),
            ChecklistTemplateItemDraft("کنترل سردخانه"),
            ChecklistTemplateItemDraft("کنترل تجهیزات"),
            ChecklistTemplateItemDraft("قفل انبار"),
            ChecklistTemplateItemDraft("بررسی وظایف باز"),
        ),
    )
}

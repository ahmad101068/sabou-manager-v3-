package ir.restaurant.management.ui

import ir.restaurant.management.domain.security.Permission

import ir.restaurant.management.domain.operations.AppUserRecord

internal fun canOpenScreen(user: AppUserRecord?, screen: AppScreen): Boolean {
    if (screen == AppScreen.SECURITY) return true
    if (user == null) return false
    return when (screen) {
        AppScreen.DASHBOARD,
        AppScreen.CONTROL_HUB,
        AppScreen.OPERATIONS_HUB,
        AppScreen.FINANCE_HUB,
        AppScreen.MORE,
        AppScreen.GLOBAL_SEARCH,
        AppScreen.SETTINGS -> true
        AppScreen.PURCHASES, AppScreen.NEW_PURCHASE -> user.role.allows(Permission.PURCHASES)
        AppScreen.SUPPLIERS -> user.role.allows(Permission.SUPPLIERS)
        AppScreen.INVENTORY, AppScreen.INVENTORY_COUNT, AppScreen.INVENTORY_TRANSFER, AppScreen.INVENTORY_WASTE, AppScreen.STOCK_MOVEMENTS -> user.role.allows(Permission.INVENTORY)
        AppScreen.SALES -> user.role.allows(Permission.DAILY_SALES_VIEW)
        AppScreen.RECIPES -> user.role.allows(Permission.RECIPES)
        AppScreen.ACCOUNTING, AppScreen.NEW_JOURNAL -> user.role.allows(Permission.ACCOUNTING)
        AppScreen.TREASURY -> user.role.allows(Permission.TREASURY)
        AppScreen.CRM -> user.role.allows(Permission.SALES) || user.role.allows(Permission.ACCOUNTING)
        AppScreen.PERSONNEL -> user.role.allows(Permission.PERSONNEL)
        AppScreen.ASSETS -> user.role.allows(Permission.ASSETS)
        AppScreen.ALERTS -> user.role.allows(Permission.ACCOUNTING)
        AppScreen.REPORTS -> user.role.allows(Permission.REPORTS)
        AppScreen.AUDIT_LOG -> user.role.allows(Permission.AUDIT_VIEW) || user.role.allows(Permission.AUDIT)
        AppScreen.BRANCHES -> user.role.allows(Permission.BRANCH_MANAGE)
        AppScreen.MANAGEMENT_ISSUES -> user.role.allows(Permission.CONTROL_VIEW)
        AppScreen.MANAGEMENT_TASKS -> user.role.allows(Permission.TASK_VIEW)
        AppScreen.CHECKLISTS -> user.role.allows(Permission.CHECKLIST_VIEW)
        AppScreen.DAILY_BRIEF -> user.role.allows(Permission.DAILY_BRIEF_VIEW)
        AppScreen.MANAGEMENT_CONTROL -> listOf(
            Permission.PURCHASES,
            Permission.INVENTORY,
            Permission.ACCOUNTING,
            Permission.PERSONNEL,
        )
            .any(user.role::allows)
        AppScreen.SECURITY -> true
    }
}

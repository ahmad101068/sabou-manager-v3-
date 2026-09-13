package ir.restaurant.management.domain.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionMappingTest {
    @Test
    fun unknownRoleGetsNoPermissions() {
        val role = UserRole.fromStoredValue("future-admin-role")
        assertTrue(role == UserRole.RESTRICTED)
        Permission.entries.forEach { permission -> assertFalse(role.allows(permission)) }
    }

    @Test
    fun accountantCannotManageUsersOrAdjustInventory() {
        assertTrue(UserRole.ACCOUNTANT.allows(Permission.ACCOUNTING))
        assertFalse(UserRole.ACCOUNTANT.allows(Permission.MANAGE_USERS))
        assertFalse(UserRole.ACCOUNTANT.allows(Permission.INVENTORY_ADJUST))
    }

    @Test
    fun payrollPermissionsSeparateViewCalculateApproveReverseAndPay() {
        assertFalse(UserRole.CASHIER.allows(Permission.PAYROLL_VIEW_ALL))
        assertFalse(UserRole.CASHIER.allows(Permission.PAYROLL_PAY))

        assertTrue(UserRole.ACCOUNTANT.allows(Permission.PAYROLL_VIEW_ALL))
        assertTrue(UserRole.ACCOUNTANT.allows(Permission.PAYROLL_CALCULATE))
        assertTrue(UserRole.ACCOUNTANT.allows(Permission.PAYROLL_PAY))
        assertFalse(UserRole.ACCOUNTANT.allows(Permission.PAYROLL_APPROVE))
        assertFalse(UserRole.ACCOUNTANT.allows(Permission.PAYROLL_REVERSE))

        assertTrue(UserRole.MANAGER.allows(Permission.PAYROLL_REVIEW))
        assertFalse(UserRole.MANAGER.allows(Permission.PAYROLL_APPROVE))
        assertFalse(UserRole.MANAGER.allows(Permission.PAYROLL_PAY))

        assertTrue(UserRole.OWNER.allows(Permission.PAYROLL_APPROVE))
        assertTrue(UserRole.OWNER.allows(Permission.PAYROLL_REVERSE))
    }
}

package ir.restaurant.management.domain.operations

import ir.restaurant.management.domain.security.Permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {
    @Test
    fun cashierCannotManageInventory() {
        assertFalse(UserRole.CASHIER.allows(Permission.INVENTORY))
    }

    @Test
    fun ownerCanUseEveryPermission() {
        assertTrue(UserRole.OWNER.allows(Permission.MANAGE_USERS))
        assertTrue(UserRole.OWNER.allows(Permission.BACKUP))
    }

    @Test
    fun onlyOwnerCanExportOrRestoreBackups() {
        assertFalse(UserRole.MANAGER.allows(Permission.BACKUP))
        assertFalse(UserRole.ACCOUNTANT.allows(Permission.BACKUP))
    }
}

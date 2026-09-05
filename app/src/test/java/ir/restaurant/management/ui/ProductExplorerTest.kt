package ir.restaurant.management.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductExplorerTest {
    @Test fun blank_query_matches_all_records() {
        assertTrue(businessTextMatches("  ", "قهوه"))
    }

    @Test fun query_matches_any_business_field_case_insensitively() {
        assertTrue(businessTextMatches("acc-12", "حساب بانک", "ACC-1201"))
        assertTrue(businessTextMatches("انبار", "مواد اولیه انبار", null))
        assertFalse(businessTextMatches("حقوق", "فاکتور خرید", "تأمین‌کننده"))
    }

    @Test fun activity_filter_is_consistent_across_personnel_lists() {
        assertTrue(businessActivityMatches("ACTIVE", true))
        assertFalse(businessActivityMatches("ACTIVE", false))
        assertTrue(businessActivityMatches("INACTIVE", false))
        assertTrue(businessActivityMatches("ALL", true))
        assertTrue(businessActivityMatches("ALL", false))
    }
}

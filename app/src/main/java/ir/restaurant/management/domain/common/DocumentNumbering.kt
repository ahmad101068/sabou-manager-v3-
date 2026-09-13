package ir.restaurant.management.domain.common

/**
 * Human-readable document identities. Allocation belongs to the persistence transaction so two
 * concurrent writers can never receive the same number. These values are display identities only;
 * database primary keys and GlobalId command ids remain the technical identities.
 */
enum class DocumentNumberType(val sequenceKey: String, val prefix: String) {
    PURCHASE("purchase_invoice", "PUR"),
    PURCHASE_REQUISITION("purchase_requisition", "PR"),
    PURCHASE_ORDER("purchase_order", "PO"),
    GOODS_RECEIPT("goods_receipt", "GR"),
    PURCHASE_RETURN("purchase_return", "PRT"),
    INVENTORY_TRANSFER("inventory_transfer", "TRF"),
    SALES_INVOICE("sales_invoice", "SAL"),
    SALES_RETURN("sales_return", "RET"),
    FIXED_ASSET("fixed_asset", "AST"),
    CUSTOMER("customer", "CUS"),
    SUPPLIER("supplier", "SUP"),
    EMPLOYEE("employee", "EMP"),
}

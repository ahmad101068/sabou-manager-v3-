package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.CustomerEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.DocumentNumberType
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.sales.CustomerDraft
import ir.restaurant.management.domain.security.Permission

/**
 * Owns the customer-master mutation boundary used by sales/CRM.
 *
 * Customer numbering, duplicate identity checks, outstanding-balance policy, sync and audit are
 * committed together. Sales invoice posting deliberately remains outside this service.
 */
internal class SalesCustomerMasterService(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val syncRecorder: SyncRecorder? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val numbering = LocalDocumentNumberAllocator(database, clock)
    private val audit = LocalAuditEventWriter(database)

    suspend fun save(id: Long?, draft: CustomerDraft): Long {
        authorizer.require(Permission.CUSTOMERS)
        val valid = draft.validated()
        return database.withTransaction {
            val dao = database.salesDao()
            if (valid.phone.isNotBlank() && dao.customerPhoneExists(valid.phone, id ?: 0)) {
                error("شماره تماس برای مشتری دیگری ثبت شده است.")
            }
            if (valid.nationalId.isNotBlank() && dao.customerNationalIdExists(valid.nationalId, id ?: 0)) {
                error("شناسه ملی/کد ملی برای مشتری دیگری ثبت شده است.")
            }
            val now = clock()
            val resultId = if (id == null) {
                val customerCode = numbering.next(DocumentNumberType.CUSTOMER)
                dao.insertCustomer(
                    CustomerEntity(
                        customerCode = customerCode,
                        name = valid.name,
                        phone = valid.phone,
                        nationalId = valid.nationalId,
                        creditLimitRial = valid.creditLimitRial,
                        notes = valid.notes,
                        mobile = valid.mobile,
                        address = valid.address,
                        branch = valid.branch,
                        paymentTermsDays = valid.paymentTermsDays,
                        status = valid.status,
                        partyType = valid.partyType.name,
                        isActive = valid.status != "INACTIVE",
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
            } else {
                val previous = dao.customerById(id)
                    ?: throw BusinessError.EntityNotFound("CUSTOMER", id).asViolation()
                if (valid.status == "INACTIVE") {
                    require(dao.outstandingRial(id) == 0L) {
                        "مشتری دارای مانده حساب است و غیرفعال‌سازی مجاز نیست."
                    }
                }
                check(
                    dao.updateCustomer(
                        previous.copy(
                            name = valid.name,
                            phone = valid.phone,
                            nationalId = valid.nationalId,
                            creditLimitRial = valid.creditLimitRial,
                            notes = valid.notes,
                            mobile = valid.mobile,
                            address = valid.address,
                            branch = valid.branch,
                            paymentTermsDays = valid.paymentTermsDays,
                            status = valid.status,
                            partyType = valid.partyType.name,
                            isActive = valid.status != "INACTIVE",
                            updatedAtEpochMillis = now,
                        ),
                    ) == 1,
                ) { "ویرایش مشتری انجام نشد." }
                id
            }
            syncRecorder?.record(
                "CUSTOMER",
                resultId,
                if (id == null) "CREATE" else "UPDATE",
                now,
                recordAudit = false,
            )
            audit.appendAuthorized(
                authorizer = authorizer,
                action = if (id == null) "CREATE" else "UPDATE",
                entityType = "CUSTOMER",
                entityId = resultId,
                description = "${if (id == null) "ایجاد" else "ویرایش"} مشتری ${valid.name}",
                occurredAtEpochMillis = now,
                reason = "CUSTOMER_MASTER_MAINTENANCE",
                correlationId = "customer:$resultId:$now",
                afterSnapshot = "creditLimitRial=${valid.creditLimitRial};partyType=${valid.partyType.name};active=${valid.status != "INACTIVE"}",
            )
            resultId
        }
    }

    suspend fun deactivate(id: Long) {
        authorizer.require(Permission.CUSTOMERS)
        database.withTransaction {
            val dao = database.salesDao()
            val customer = dao.customerById(id)
                ?: throw BusinessError.EntityNotFound("CUSTOMER", id).asViolation()
            if (!customer.isActive) return@withTransaction
            require(dao.outstandingRial(id) == 0L) {
                "مشتری دارای مانده حساب است و غیرفعال‌سازی مجاز نیست."
            }
            val now = clock()
            check(dao.deactivateCustomer(id, now) == 1) { "غیرفعال‌سازی مشتری انجام نشد." }
            syncRecorder?.record("CUSTOMER", id, "DEACTIVATE", now, recordAudit = false)
            audit.appendAuthorized(
                authorizer,
                "DEACTIVATE",
                "CUSTOMER",
                id,
                "غیرفعال‌سازی مشتری ${customer.name}",
                now,
                reason = "CUSTOMER_DEACTIVATED",
            )
        }
    }
}

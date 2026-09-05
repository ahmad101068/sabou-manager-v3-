package ir.restaurant.management.application.crm

import ir.restaurant.management.domain.crm.CustomerAccountService
import ir.restaurant.management.domain.crm.CustomerOpeningBalanceCommand
import ir.restaurant.management.domain.crm.CustomerReceivableAdjustmentCommand

class CrmUseCases(private val service: CustomerAccountService) {
    fun ledger(customerId: Long) = service.observeLedger(customerId)
    suspend fun aging(customerId: Long, todayEpochDay: Long) = service.aging(customerId, todayEpochDay)
    suspend fun duplicateCandidates(customerId: Long, phone: String, nationalId: String) = service.duplicateCandidates(customerId, phone, nationalId)
    suspend fun postOpeningBalance(command: CustomerOpeningBalanceCommand) = service.postOpeningBalance(command.validated())
    suspend fun postAdjustment(command: CustomerReceivableAdjustmentCommand) = service.postAdjustment(command.validated())
    suspend fun merge(sourceCustomerId: Long, targetCustomerId: Long, reason: String) = service.merge(sourceCustomerId, targetCustomerId, reason.trim())
}

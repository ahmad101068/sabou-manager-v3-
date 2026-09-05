package ir.restaurant.management.domain.accounting

import ir.restaurant.management.core.SignedLongMath

data class CashFlowEvent(val epochDay: Long, val amountRial: Long, val label: String, val incoming: Boolean)
data class CashFlowForecast(val startBalanceRial: Long, val endBalanceRial: Long, val minimumBalanceRial: Long, val deficitDays: List<Long>)

object CashFlowCalculator {
    fun forecast(startBalanceRial: Long, events: List<CashFlowEvent>): CashFlowForecast {
        require(startBalanceRial >= 0) { "موجودی آغاز دوره نمی‌تواند منفی باشد." }
        require(events.all { it.epochDay > 0 && it.amountRial >= 0 && it.label.isNotBlank() }) { "رویداد جریان نقدی معتبر نیست." }
        var balance = startBalanceRial
        var minimum = balance
        val deficits = mutableListOf<Long>()
        events.sortedBy { it.epochDay }.forEach { event ->
            val previousBalance = balance
            balance = if (event.incoming) SignedLongMath.add(balance, event.amountRial) else SignedLongMath.subtract(balance, event.amountRial)
            minimum = minOf(minimum, balance)
            // Report the day a deficit starts, not every later day while the balance remains negative.
            if (previousBalance >= 0 && balance < 0) deficits += event.epochDay
        }
        return CashFlowForecast(startBalanceRial, balance, minimum, deficits.distinct())
    }
}

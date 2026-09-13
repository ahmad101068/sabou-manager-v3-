package ir.restaurant.management.domain.management

enum class KpiCoverage { COMPLETE, PARTIAL, UNAVAILABLE }
data class KpiDefinition(
    val key: String,
    val title: String,
    val businessDefinition: String,
    val sourceDomains: Set<String>,
    val drillDownRoute: String,
) { init { require(key.isNotBlank() && businessDefinition.isNotBlank() && sourceDomains.isNotEmpty()) } }
object ManagementKpis {
    val NET_SALES = KpiDefinition("NET_SALES", "فروش خالص", "فروش ثبت‌شده پس از اثر برگشت‌های معتبر دوره", setOf("SALES"), "sales")
    val FULL_MARGIN = KpiDefinition("FULL_MARGIN", "حاشیه بهای کامل", "فروش خالص منهای بهای کامل دارای پوشش معتبر", setOf("SALES","COSTING"), "reports/full-margin")
    val LABOR_COST = KpiDefinition("LABOR_COST", "هزینه نیروی کار", "هزینه حقوق و مزایای ناخالص قابل انتساب؛ نه خالص پرداختی کارکنان", setOf("PERSONNEL","COSTING"), "personnel/payroll")
}

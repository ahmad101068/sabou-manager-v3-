package ir.restaurant.management.data.db

import ir.restaurant.management.core.SignedLongMath

/** Central invariant used by every repository before journal lines are stored. */
internal object JournalIntegrity {
    fun requireBalanced(lines: List<JournalLineEntity>) {
        require(lines.size >= 2) { "سند حسابداری باید حداقل دو آرتیکل داشته باشد." }
        require(lines.map { it.entryId }.distinct().size == 1 && lines.first().entryId > 0) {
            "همه آرتیکل‌ها باید متعلق به یک سند معتبر باشند."
        }

        var totalDebit = 0L
        var totalCredit = 0L
        lines.forEach { line ->
            require(line.debitRial >= 0 && line.creditRial >= 0) {
                "مبلغ بدهکار یا بستانکار نمی‌تواند منفی باشد."
            }
            require((line.debitRial > 0) xor (line.creditRial > 0)) {
                "هر آرتیکل باید فقط یک مبلغ بدهکار یا بستانکار مثبت داشته باشد."
            }
            totalDebit = SignedLongMath.add(totalDebit, line.debitRial)
            totalCredit = SignedLongMath.add(totalCredit, line.creditRial)
        }
        require(totalDebit > 0 && totalDebit == totalCredit) {
            "سند حسابداری تراز نیست: بدهکار $totalDebit و بستانکار $totalCredit ریال."
        }
    }
}

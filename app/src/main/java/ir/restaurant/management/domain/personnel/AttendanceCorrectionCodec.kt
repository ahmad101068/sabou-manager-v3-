package ir.restaurant.management.domain.personnel

data class AttendanceCorrectionSnapshot(
    val firstInMinute: Int?,
    val lastOutMinute: Int?,
    val workedMinutes: Int,
    val breakMinutes: Int,
    val lateMinutes: Int,
    val earlyLeaveMinutes: Int,
    val overtimeMinutes: Int,
    val absenceMinutes: Int,
    val status: DailyAttendanceStatus,
) {
    fun validated(): AttendanceCorrectionSnapshot {
        require(firstInMinute == null || firstInMinute in 0..1439)
        require(lastOutMinute == null || lastOutMinute in 1..1440)
        require(firstInMinute == null || lastOutMinute == null || lastOutMinute > firstInMinute)
        require(listOf(workedMinutes, breakMinutes, lateMinutes, earlyLeaveMinutes, overtimeMinutes, absenceMinutes).all { it >= 0 })
        return this
    }
}

object AttendanceCorrectionCodec {
    fun encode(snapshot: AttendanceCorrectionSnapshot): String {
        val valid = snapshot.validated()
        return listOf(
            "in=${valid.firstInMinute ?: "-"}",
            "out=${valid.lastOutMinute ?: "-"}",
            "worked=${valid.workedMinutes}",
            "break=${valid.breakMinutes}",
            "late=${valid.lateMinutes}",
            "early=${valid.earlyLeaveMinutes}",
            "overtime=${valid.overtimeMinutes}",
            "absence=${valid.absenceMinutes}",
            "status=${valid.status.name}",
        ).joinToString(";")
    }

    fun decode(value: String): AttendanceCorrectionSnapshot {
        val fields = value.split(';').associate { token ->
            val separator = token.indexOf('=')
            require(separator > 0) { "attendance_correction_snapshot_invalid" }
            token.substring(0, separator) to token.substring(separator + 1)
        }
        fun optionalMinute(name: String): Int? = fields.getValue(name).takeUnless { it == "-" }?.toInt()
        return AttendanceCorrectionSnapshot(
            firstInMinute = optionalMinute("in"),
            lastOutMinute = optionalMinute("out"),
            workedMinutes = fields.getValue("worked").toInt(),
            breakMinutes = fields.getValue("break").toInt(),
            lateMinutes = fields.getValue("late").toInt(),
            earlyLeaveMinutes = fields.getValue("early").toInt(),
            overtimeMinutes = fields.getValue("overtime").toInt(),
            absenceMinutes = fields.getValue("absence").toInt(),
            status = DailyAttendanceStatus.valueOf(fields.getValue("status")),
        ).validated()
    }
}


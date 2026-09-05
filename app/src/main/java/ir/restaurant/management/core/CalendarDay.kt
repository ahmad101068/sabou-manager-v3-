package ir.restaurant.management.core

/** Returns today's canonical restaurant business date (Asia/Tehran) as epoch day. */
fun currentLocalEpochDay(): Long = BusinessCalendar.epochDayAt(System.currentTimeMillis())

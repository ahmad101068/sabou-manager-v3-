package ir.restaurant.management.core

import android.util.Log

/** Logs only exception types; messages and stack traces may contain financial or credential data. */
object SafeErrorLog {
    fun record(tag: String, event: String, error: Throwable) {
        val errorType = error::class.java.simpleName.ifBlank { "Throwable" }
        val causeType = error.cause?.let { it::class.java.simpleName.ifBlank { "Throwable" } } ?: "none"
        Log.e(tag.take(23), "$event; errorType=$errorType; causeType=$causeType")
    }
}

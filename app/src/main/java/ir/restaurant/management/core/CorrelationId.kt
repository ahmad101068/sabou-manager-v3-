package ir.restaurant.management.core

import java.util.Locale

/**
 * Trace identity shared by every side effect of one business operation.
 * It is not a database primary key and must remain stable across retries.
 */
@JvmInline
value class CorrelationId private constructor(val value: String) {
    companion object {
        private val validValue = Regex("[a-z0-9][a-z0-9:_./-]{7,179}")
        private val validOperation = Regex("[a-z][a-z0-9_]{1,47}")

        fun parse(raw: String): CorrelationId {
            val normalized = raw.trim().lowercase(Locale.US)
            require(validValue.matches(normalized)) { "correlation_id_invalid" }
            return CorrelationId(normalized)
        }

        fun new(operation: String): CorrelationId {
            val normalizedOperation = operation.trim().lowercase(Locale.US)
            require(validOperation.matches(normalizedOperation)) { "correlation_operation_invalid" }
            return parse("$normalizedOperation:${GlobalId.new().value}")
        }

        fun forCommand(operation: String, commandId: GlobalId): CorrelationId {
            val normalizedOperation = operation.trim().lowercase(Locale.US)
            require(validOperation.matches(normalizedOperation)) { "correlation_operation_invalid" }
            return parse("$normalizedOperation:${commandId.value}")
        }
    }
}

fun interface CorrelationIdGenerator {
    fun next(operation: String): CorrelationId
}

object RandomCorrelationIdGenerator : CorrelationIdGenerator {
    override fun next(operation: String): CorrelationId = CorrelationId.new(operation)
}

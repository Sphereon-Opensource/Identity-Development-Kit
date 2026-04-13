package com.sphereon.core.api.service.contract

/**
 * Mutable state carrier for inter-interceptor data flow.
 *
 * Passed through the interceptor chain alongside the immutable [CommandExecutionContext].
 * Interceptors can write data that downstream interceptors read.
 *
 * Example: PlanningInterceptor writes the [CommandPlan], PolicyInterceptor reads it.
 */
class MutableExecutionState {
    private val attributes = mutableMapOf<String, Any?>()

    operator fun <T> set(
        key: ExecutionStateKey<T>,
        value: T,
    ) {
        attributes[key.name] = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: ExecutionStateKey<T>): T? = attributes[key.name] as? T

    fun has(key: ExecutionStateKey<*>): Boolean = key.name in attributes
}

/**
 * Typed key for [MutableExecutionState] entries.
 */
data class ExecutionStateKey<T>(
    val name: String,
)

/**
 * Well-known state keys used by framework interceptors.
 */
object ExecutionStateKeys {
    val PLAN = ExecutionStateKey<CommandPlan>("commandPlan")
}

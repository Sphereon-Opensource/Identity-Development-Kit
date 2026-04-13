/*
 * Minimal starter template for a custom PropertySource.
 *
 * Copy into your module and replace package/type names.
 */

package com.example.idk.extensions

import com.sphereon.core.api.conf.Order
import com.sphereon.core.api.conf.PropertySource
import kotlin.reflect.KClass

class CustomPropertySource(
    private val sourceName: String,
    private val backing: Map<String, Any>
) : PropertySource<Map<String, Any>> {

    override fun getName(): String = sourceName

    override fun getSource(): Map<String, Any> = backing

    override fun getOrder(): Int = Order.MEDIUM.orderValue

    override fun hasProperty(name: String): Boolean = backing.containsKey(name)

    override fun <T : Any> getProperty(name: String, targetType: KClass<T>): T? {
        val value = backing[name] ?: return null
        return if (targetType.isInstance(value)) {
            @Suppress("UNCHECKED_CAST")
            value as T
        } else {
            null
        }
    }

    override fun getPropertyAsString(name: String): String? = backing[name]?.toString()

    override fun removeProperty(name: String) {
        // No-op for immutable sources.
    }

    override fun getAllPropertyNames(): Set<String> = backing.keys
}

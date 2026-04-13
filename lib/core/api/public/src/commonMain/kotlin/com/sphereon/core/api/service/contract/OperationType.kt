package com.sphereon.core.api.service.contract

import com.sphereon.core.api.service.ActionType
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Extensible domain-specific operation type.
 *
 * Supplements [ActionType] (which covers CRUD categories) with finer-grained
 * operation semantics. Each module can define its own operations by constructing
 * new instances. Standard operations are provided as companion constants.
 *
 * Used by:
 * - Policy engines: action attributes in AuthZEN/Cedar/OPA requests
 * - Assurance mapping: risk classification per operation
 * - Workflow: step type identification
 * - MCP: tool categorization
 */
@Serializable
@JvmInline
value class OperationType(
    val value: String,
) {
    companion object {
        // CRUD (mirror ActionType)
        val CREATE = OperationType("create")
        val READ = OperationType("read")
        val UPDATE = OperationType("update")
        val DELETE = OperationType("delete")
        val LIST = OperationType("list")
        val EXECUTE = OperationType("execute")

        // Key lifecycle
        val GENERATE = OperationType("generate")
        val STORE = OperationType("store")
        val REGISTER = OperationType("register")
        val IMPORT = OperationType("import")
        val EXPORT = OperationType("export")
        val ROTATE = OperationType("rotate")

        // Crypto
        val SIGN = OperationType("sign")
        val VERIFY = OperationType("verify")
        val ENCRYPT = OperationType("encrypt")
        val DECRYPT = OperationType("decrypt")
        val WRAP = OperationType("wrap")
        val UNWRAP = OperationType("unwrap")

        // Credential lifecycle
        val ISSUE = OperationType("issue")
        val PRESENT = OperationType("present")
        val REVOKE = OperationType("revoke")

        // Identifier resolution
        val RESOLVE = OperationType("resolve")
        val DEREFERENCE = OperationType("dereference")

        // Protocol flows
        val PARSE = OperationType("parse")
        val VALIDATE = OperationType("validate")
        val BUILD = OperationType("build")
        val SUBMIT = OperationType("submit")

        // Token operations
        val EXCHANGE = OperationType("exchange")
        val INTROSPECT = OperationType("introspect")

        // Request operations
        val REQUEST = OperationType("request")

        fun fromActionType(type: ActionType): OperationType =
            when (type) {
                ActionType.CREATE -> CREATE
                ActionType.READ -> READ
                ActionType.UPDATE -> UPDATE
                ActionType.DELETE -> DELETE
                ActionType.LIST -> LIST
                ActionType.EXECUTE -> EXECUTE
            }
    }
}

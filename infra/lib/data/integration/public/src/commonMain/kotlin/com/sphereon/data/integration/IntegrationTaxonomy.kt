/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.data.integration

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Direction in which a system participates in data movement.
 *
 * This taxonomy is intentionally connector-neutral. It can describe connector instances,
 * inventory resources, policy scopes, workflow edges, form sinks, credential-issuance inputs, and
 * presentation-capture outputs without implying a concrete runtime.
 */
@JsExportCompat
@Serializable
enum class DataFlowRole {
    SOURCE,
    DESTINATION,
    PROCESSOR,
}

/**
 * Transport or access protocol used to reach a system.
 *
 * This is not the same thing as [ResourceKind] or [RepresentationKind]. For example, a CSV table
 * can be accessed over HTTPS, SFTP, FILE, S3, AZURE_BLOB, GCS, VAULT, or an internal API.
 */
@JsExportCompat
@Serializable
enum class AccessProtocol {
    HTTP,
    HTTPS,
    WS,
    WSS,
    STDIO,
    JDBC,
    ODBC,
    SFTP,
    FILE,
    S3,
    AZURE_BLOB,
    GCS,
    KAFKA,
    AMQP,
    MQTT,
    OIDC,
    DIDCOMM,
    VAULT,
    INTERNAL,
    CUSTOM,
}

/**
 * Logical kind of resource being accessed, independent of transport and representation.
 */
@JsExportCompat
@Serializable
enum class ResourceKind {
    OBJECT,
    TABULAR,
    DOCUMENT,
    CLAIM_SET,
    GRAPH,
    EVENT_STREAM,
    FILE,
    SECRET,
    CONFIGURATION,
    CREDENTIAL,
    PRESENTATION,
    CUSTOM,
}

/**
 * Serialization or data representation used on the wire or at rest.
 */
@JsExportCompat
@Serializable
enum class RepresentationKind {
    JSON,
    JSON_LD,
    XML,
    CSV,
    PARQUET,
    AVRO,
    RDF,
    JWT,
    SD_JWT,
    CBOR,
    BINARY,
    TEXT,
    CUSTOM,
}

/**
 * Structural shape information available for a resource.
 */
@JsExportCompat
@Serializable
enum class ShapeKind {
    SCHEMA,
    OPENAPI_SCHEMA,
    JSON_SCHEMA,
    RDF_SHAPE,
    SQL_TABLE,
    CSV_HEADER,
    CLAIMS_SCHEMA,
    FREEFORM,
}

/**
 * Contract or schema source that describes an external or internal resource.
 */
@JsExportCompat
@Serializable
enum class ContractKind {
    OPENAPI,
    JSON_SCHEMA,
    SQL_SCHEMA,
    RDF_SCHEMA,
    CSV_PROFILE,
    OIDC_DISCOVERY,
    VAULT_POLICY,
    CUSTOM,
}

/**
 * Logical operation taxonomy shared by connectors, inventory, policy, and workflows.
 *
 * Connector-local names such as OpenAPI operationId values, SQL statement names, topic names, or
 * vault operation names should live in connector-level bindings, not in this enum.
 */
@JsExportCompat
@Serializable
enum class OperationKind {
    READ,
    WRITE,
    UPDATE,
    DELETE,
    UPSERT,
    QUERY,
    SEARCH,
    IMPORT,
    EXPORT,
    INVOKE,
    DISCOVER,
    VALIDATE,
}

/**
 * Transfer mode used by an operation binding.
 *
 * Streaming and batching are execution semantics for a logical operation. Keep the logical
 * operation kind stable, and use this value to describe how records are transferred.
 */
@JsExportCompat
@Serializable
enum class OperationTransferMode {
    SINGLE,
    BATCH,
    STREAM,
}

/**
 * Data movement direction for an operation binding or workflow edge.
 */
@JsExportCompat
@Serializable
enum class BindingDirection {
    INBOUND,
    OUTBOUND,
    BIDIRECTIONAL,
}

/**
 * How, if at all, data touched by an integration may be retained or projected.
 */
@JsExportCompat
@Serializable
enum class MaterializationMode {
    NONE,
    CACHE,
    PERSIST,
    MIRROR,
    INDEX,
}

/**
 * Delivery timing and batching behavior expected by a destination.
 */
@JsExportCompat
@Serializable
enum class DeliveryMode {
    SYNC,
    ASYNC,
    BATCH,
    STREAMING,
}

/**
 * Acknowledgement level required before delivery is considered complete.
 */
@JsExportCompat
@Serializable
enum class AcknowledgementMode {
    NONE,
    ACCEPTED,
    COMMITTED,
    VERIFIED,
}

/**
 * Failure and rollback semantics expected for a data route.
 */
@JsExportCompat
@Serializable
enum class RouteAtomicity {
    PER_RECORD,
    ALL_OR_NOTHING,
    BEST_EFFORT,
}

/**
 * Action to apply when retention expires or deletion is required.
 */
@JsExportCompat
@Serializable
enum class RetentionDeleteAction {
    DELETE,
    ANONYMIZE,
    PSEUDONYMIZE,
    TOMBSTONE,
    REVIEW,
}

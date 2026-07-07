# Module services-oid4vci-issuer-rest

Deployable Ktor server that exposes an OID4VCI issuer over HTTP. It mounts the OID4VCI endpoint surface (credential offer, issuer metadata, credential, deferred credential, and the issuer-metadata variants for JWT VC profiles) on top of the OID4VCI issuer runtime, producing a deployable issuer service.

The issuer itself needs an Authorization Server: either delegate to an external AS, or run `services-oauth2-as-rest` alongside. The issuer behaviour (metadata, offer construction, credential assembly) is configured via the module's configuration contracts; credential-claim projection comes from the application's credential-attribute contributor rather than being hardcoded here.

## Endpoint surfaces

The service exposes two distinct HTTP surfaces:

- Wallet-facing OID4VCI protocol endpoints such as issuer metadata, nonce, credential, deferred credential, and credential-offer retrieval.
- Backend issuer-session endpoints under `/api/oid4vci/v1/backend/credential/offers`. These create a tracked offer session, report status by `correlation_id`, and delete session state.

The backend create-offer request accepts `credential_configuration_ids`, optional `credential_subject_data`, optional `initial_connector_fields`, grant selection, QR-code options, callback configuration, and offer URI lifecycle controls. `initial_connector_fields` are passed only to the IDK lifecycle extension hook; EDK binds that hook to the connector-backed issuance pipeline.

## Credential designs and channels

OID4VCI is not a separate semantic channel. Credential semantics are authored on credential channels: for example `dc+sd-jwt` with a `vct`, or `mso_mdoc` with a `doctype`. The issuer advertises and issues those credentials by `credential_configuration_id`.

Deployments can choose between:

- the IDK config-driven path, where `Oid4vciIssuerConfigProvider` reads credential configurations directly from `ConfigService`;
- an EDK design-backed path, where credential designs are derived from semantic VC definitions and then exposed as OID4VCI credential configurations.

Both paths feed the same OID4VCI runtime and backend offer/session API.

## Connector invocation pipeline

The EDK issuance pipeline is outside IDK. The simple IDK issuer exposes lifecycle hook points; EDK supplies the connector pipeline implementation and VDX supplies durable registration/runtime concerns.

When a pipeline is present, `CreateCredentialOfferCommand` initializes a pipeline session and stores the resulting pipeline correlation id on the OID4VCI issuance session. Later token, credential, deferred-credential, pre-issue, post-issuance, and notification handling uses that correlation id to run connector invocation bindings, evaluate completeness, assemble claims for the requested `credential_configuration_id`, and defer issuance when required async connector work is still pending.

Commercial VDX deployments manage persisted connector invocation bindings through the Connector API under `/api/connector/v1/invocation/bindings`. A `PipelineConfiguration` carries those bindings in `invocationBindings`; each binding declares its OID4VCI stage, role, target route or operation, execution policy, subset mapping, governance context, and lineage policy. The attribute-source registry remains a source-catalog and semantic documentation surface, not the automatic OID4VCI pipeline binding contract.

# Module services-oid4vci-issuer-rest

Deployable Ktor server that exposes an OID4VCI issuer over HTTP. It mounts the OID4VCI endpoint surface (credential offer, issuer metadata, credential, deferred credential, and the issuer-metadata variants for JWT VC profiles) on top of the OID4VCI issuer runtime, producing a deployable issuer service.

The issuer itself needs an Authorization Server: either delegate to an external AS, or run `services-oauth2-as-rest` alongside. The issuer behaviour (metadata, offer construction, credential assembly) is configured via the module's configuration contracts; credential-claim projection comes from the application's credential-attribute contributor rather than being hardcoded here.

## Endpoint surfaces

The service exposes two distinct HTTP surfaces:

- Wallet-facing OID4VCI protocol endpoints such as issuer metadata, nonce, credential, deferred credential, and credential-offer retrieval.
- Backend issuer-session endpoints under `/api/oid4vci/v1/backend/credential/offers`. These create a tracked offer session, report status by `correlation_id`, and delete session state.

The backend create-offer request accepts `credential_configuration_ids`, optional `credential_subject_data`, optional `initial_lookup_keys`, grant selection, QR-code options, callback configuration, and offer URI lifecycle controls. `initial_lookup_keys` are passed into the EDK issuance pipeline when a pipeline is resolved for the offered credential configurations.

## Credential designs and channels

OID4VCI is not a separate semantic channel. Credential semantics are authored on credential channels: for example `dc+sd-jwt` with a `vct`, or `mso_mdoc` with a `doctype`. The issuer advertises and issues those credentials by `credential_configuration_id`.

Deployments can choose between:

- the IDK config-driven path, where `Oid4vciIssuerConfigProvider` reads credential configurations directly from `ConfigService`;
- an EDK design-backed path, where credential designs are derived from semantic VC definitions and then exposed as OID4VCI credential configurations.

Both paths feed the same OID4VCI runtime and backend offer/session API.

## Attribute-source integration

The EDK issuance pipeline is optional. If no `PipelineConfigurationResolver` returns a pipeline, offer creation and credential issuance continue through the pure-IDK path.

When a pipeline is present, `CreateCredentialOfferCommand` initializes a pipeline session and stores the resulting pipeline correlation id on the OID4VCI issuance session. Later credential and deferred-credential handling uses that correlation id to run bound sources, evaluate completeness, assemble claims for the requested `credential_configuration_id`, and defer issuance when required async sources are still pending.

Commercial VDX deployments manage persisted attribute-source definitions through the VDX REST facade at `/api/attribute-source/v1/sources`. The persisted model is owned by EDK (`CreateAttributeSourceArgs`, `UpdateAttributeSourceArgs`, `ListAttributeSourcesArgs`, `SemanticBindingInput`). A pipeline binding connects to that registry through `AttributeSourceBinding.sourceInstanceId`, while `AttributeSourceBinding.sourceId` selects the runtime source implementation.

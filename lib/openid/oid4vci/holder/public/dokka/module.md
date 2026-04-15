# Module lib-openid-oid4vci-holder-public

Public API for the OID4VCI holder (wallet) side. It defines the holder command surface and the flow DSLs for credential-offer, authorization-code, and pre-authorized-code flows, so wallet code can walk an issuance flow without open-coding the protocol.

Depend on this module from wallet or wallet-backend code. Runtime implementations live in `lib-openid-oid4vci-holder-impl`.

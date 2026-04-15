# Module lib-openid-oid4vp-auth-bridge-public

Authentication bridge that lets a service use an OID4VP verification as an authentication step. It defines the bridge surface, a configuration provider, the projection hook that maps wallet-disclosed attributes onto an application's auth subject, and the error model for the flow.

Depend on this module from services that want "sign in with a credential in your wallet" behaviour rather than only "verify a presentation". Runtime implementations live in `lib-openid-oid4vp-auth-bridge-impl`.

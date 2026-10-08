<!--suppress HtmlDeprecatedAttribute -->
<h1 align="center">
  <br>
<a href="https://www.sphereon.com"><img src="https://sphereon.com/content/themes/sphereon/assets/img/logo.svg" alt="Sphereon" width="400"></a>
  <br>Ktor HTTP Client
  <br>
</h1>

# alpha state

Please be aware that this library still is in alpha state and far from complete. Major changes are likely to happen

# Introduction

This is a Kotlin Multiplatform plugin for Ktor’s HTTP client that adds built-in mutual-TLS (mTLS) support. While the 
architecture is designed to work across Android, iOS (Kotlin/Native), JavaScript/TypeScript and JVM, the first release 
focuses on the JVM using the CIO engine.

Although this first cut only supports the JVM + CIO combination, the plugin API is fully extensible: you can register 
new engine-adapters, swap in a different TrustManager, among other things.

## Create HTTP client

```kotlin
val clientCertificateAlias = "my-client-cert"
val clientKeyStorePath = "path/to/keystore.p12"
val clientKeyStorePassword = "keystore-password"
val trustStorePath = "path/to/truststore.p12"
val trustStorePassword = "truststore-password"

val sslConfig = ClientSslConfig(
    opts = ClientSslConfigOpts(
        certificateAliases = listOf(clientCertificateAlias),
        keyStoreOpts = KeyStoreOpts(
            source = KeyStoreOpts.Source.File(clientKeyStorePath),
            password = clientKeyStorePassword,
            type = KeyStoreType.PKCS12,
        ),
        trustStoreOpts = KeyStoreOpts(
            source = KeyStoreOpts.Source.File(trustStorePath),
            password = trustStorePassword,
            type = KeyStoreType.PKCS12
        )
    )
)

val client = HttpClientProvider().createClient(
    HttpClientOptions(
        engine = HttpClientEngineType.CIO,
        sslConfig
    )
)
```

## Get supported platform engine types

```kotlin
val engineTypes = HttpClientProvider().getSupportedEngineTypes()
println(engineTypes) // [CIO]
```

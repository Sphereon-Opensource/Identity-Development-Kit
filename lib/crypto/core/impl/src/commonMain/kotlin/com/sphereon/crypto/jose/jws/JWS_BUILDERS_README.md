# JWS Builders - Fluent API for Creating JWS/JWT

This document describes the builder pattern APIs for creating JWS (JSON Web Signature) tokens with protected/unprotected headers and structured payloads.

## Overview

The JWS builders provide a type-safe, fluent API for constructing:
- **Headers** (protected and unprotected)
- **Payloads** (JWT claims)
- **JWS Options** (creation behavior)
- **Complete JWS Args** (for signing)

## Header Builder

Create JWS headers with standard and custom claims.

### Basic Usage

```kotlin
val header = JwsHeaderBuilder()
    .algorithm("ES256")
    .keyId("key-123")
    .type("JWT")
    .contentType("application/json")
    .custom("x-custom", "value")
    .build()
```

### DSL Style

```kotlin
val header = jwsHeader {
    algorithm("ES256")
    keyId("key-123")
    type("JWT")
    contentType("application/json")
    custom("x-custom", "value")
}
```

### Supported Methods

- `algorithm(alg: String)` - Sets the `alg` header parameter
- `keyId(kid: String)` - Sets the `kid` header parameter
- `type(typ: String)` - Sets the `typ` header parameter (e.g., "JWT")
- `contentType(cty: String)` - Sets the `cty` header parameter
- `critical(vararg headers: String)` - Sets the `crit` header parameter (array of critical headers)
- `custom(name: String, value: String/Number/Boolean)` - Adds custom header
- `customJson(name: String, value: JsonElement)` - Adds custom header with JsonElement
- `merge(headers: JsonObject)` - Merges another JsonObject into the builder
- `copy()` - Creates a copy of the builder
- `build()` - Builds the final JsonObject

### Example: Critical Headers

```kotlin
val header = jwsHeader {
    algorithm("PS256")
    critical("exp", "aud")
    custom("custom_field", "value")
}
```

## Payload Builder

Create JWS/JWT payloads with standard claims and custom data.

### Basic Usage

```kotlin
val now = Clock.System.now().epochSeconds

val payload = JwsPayloadBuilder()
    .issuer("https://issuer.example.com")
    .subject("user-123")
    .audience("https://audience.example.com")
    .expirationTime(now + 3600)
    .issuedAt(now)
    .claim("email", "user@example.com")
    .claim("role", "admin")
    .claimList("permissions", "read", "write", "delete")
    .build()
```

### DSL Style

```kotlin
val payload = jwsPayload {
    issuer("https://issuer.example.com")
    subject("user-123")
    audiences("aud1", "aud2", "aud3")
    expirationTime(now + 3600)
    notBefore(now - 60)
    issuedAt(now)
    jwtId("unique-jwt-id")
    claim("email", "user@example.com")
    claim("verified", true)
    claim("login_count", 42)
}
```

### Supported Methods

**Standard JWT Claims:**
- `issuer(iss: String)` - Sets the `iss` claim
- `subject(sub: String)` - Sets the `sub` claim
- `audience(aud: String)` - Sets the `aud` claim (single value)
- `audiences(vararg audiences: String)` - Sets the `aud` claim (array of values)
- `expirationTime(exp: Long)` - Sets the `exp` claim (seconds since epoch)
- `notBefore(nbf: Long)` - Sets the `nbf` claim (seconds since epoch)
- `issuedAt(iat: Long)` - Sets the `iat` claim (seconds since epoch)
- `jwtId(jti: String)` - Sets the `jti` claim

**Custom Claims:**
- `claim(name: String, value: String/Number/Boolean)` - Adds custom claim
- `claimList(name: String, vararg values: String)` - Adds array claim
- `claimJson(name: String, value: JsonElement)` - Adds custom claim with JsonElement
- `merge(payload: JsonObject)` - Merges another JsonObject
- `copy()` - Creates a copy of the builder
- `build()` - Builds the final JsonObject

### Example: Custom Claims

```kotlin
val payload = jwsPayload {
    issuer("https://myapp.com")
    subject("user-456")

    // Custom claims
    claim("email", "user@example.com")
    claim("email_verified", true)
    claim("age", 25)
    claimList("roles", "user", "admin", "moderator")

    // Nested object
    claimJson("address", buildJsonObject {
        put("street", "123 Main St")
        put("city", "Springfield")
    })
}
```

## JWS Options Builder

Configure JWS creation behavior with protected/unprotected headers.

### Basic Usage

```kotlin
val opts = JwsOptsBuilder()
    .noIssPayloadUpdate()
    .protectedHeader {
        algorithm("ES256")
        type("JWT")
    }
    .unprotectedHeader {
        custom("x-version", "1.0")
    }
    .build()
```

### DSL Style

```kotlin
val opts = jwsOptions {
    noIssPayloadUpdate()
    noIdentifierInHeader()

    protectedHeader {
        algorithm("ES256")
        keyId("my-key-id")
        type("JWT")
    }

    unprotectedHeader {
        custom("x-trace-id", "abc-123")
        custom("x-request-id", "req-456")
    }
}
```

### Supported Methods

- `noIssPayloadUpdate()` - Disables automatic issuer/payload updates
- `noIdentifierInHeader()` - Prevents identifier from being added to header
- `protectedHeader(header: JsonObject)` - Sets protected header
- `protectedHeader(builder: JwsHeaderBuilder.() -> Unit)` - Sets protected header using builder
- `unprotectedHeader(header: JsonObject)` - Sets unprotected header
- `unprotectedHeader(builder: JwsHeaderBuilder.() -> Unit)` - Sets unprotected header using builder
- `build()` - Builds the final CreateJwsOpts

## CreateJwsArgs Builder (Compact JWS)

Build complete arguments for creating compact JWS tokens.

### Complete Example

```kotlin
val args = CreateJwsArgsBuilder()
    .issuer(myManagedKey)
    .payload {
        issuer("https://issuer.example.com")
        subject("user-123")
        expirationTime(Clock.System.now().epochSeconds + 3600)
        claim("email", "user@example.com")
        claim("role", "admin")
    }
    .mode(JwsIdentifierMode.KID)
    .options {
        protectedHeader {
            type("JWT")
            contentType("application/json")
        }
    }
    .build()

// Sign the JWS
val result = jwtService.createJwsCompact(args)
```

### DSL Style

```kotlin
val args = createJwsArgs {
    issuer(myManagedKey)

    payload {
        issuer("https://example.com")
        subject("user-789")
        expirationTime(now + 3600)
        claim("scope", "read write")
    }

    mode(JwsIdentifierMode.AUTO)

    options {
        protectedHeader {
            algorithm("PS256")
            type("JWT")
        }
    }
}
```

### Payload Options

You can set payload in multiple ways:

```kotlin
// As JsonObject
.payload(existingJsonObject)

// Using builder
.payload {
    issuer("https://example.com")
    claim("custom", "value")
}

// As String
.payloadString("Plain text payload")

// As ByteArray
.payloadBytes(byteArrayOf(1, 2, 3, 4))
```

### Identifier Modes

```kotlin
// AUTO mode (default) - automatically detect based on key type
.mode(JwsIdentifierMode.AUTO)

// KID mode - use key identifier in header
.mode(JwsIdentifierMode.KID)

// JWK mode - embed public key in header
.mode(JwsIdentifierMode.JWK)

// X5C mode - use X.509 certificate chain
.mode(JwsIdentifierMode.X5C)

// DID mode - use decentralized identifier
.mode(JwsIdentifierMode.DID)
```

## CreateJwsJsonArgs Builder (JSON JWS)

Build arguments for creating general or flattened JSON JWS.

### Example

```kotlin
val args = CreateJwsJsonArgsBuilder()
    .issuer(myManagedKey)
    .payload {
        issuer("https://issuer.example.com")
        subject("user-123")
    }
    .mode(JwsIdentifierMode.JWK)
    .options {
        protectedHeader {
            algorithm("ES256")
        }
        unprotectedHeader {
            custom("x-metadata", "test")
        }
    }
    .build()

// Sign the JSON JWS
val result = jwtService.createJwsJson(args)
```

### DSL Style

```kotlin
val args = createJwsJsonArgs {
    issuer(myManagedKey)

    payloadString("Hello, World!")
    mode(JwsIdentifierMode.X5C)

    options {
        noIdentifierInHeader()
        unprotectedHeader {
            custom("x-version", "2.0")
        }
    }
}
```

### Adding to Existing Signatures

```kotlin
val args = createJwsJsonArgs {
    issuer(anotherKey)
    payload(samePayload)
    existingSignatures(previousJws.signatures)
}
```

## Advanced Usage

### Merging and Extending

```kotlin
// Create base header
val baseHeader = jwsHeader {
    algorithm("ES256")
    type("JWT")
}

// Extend with additional fields
val extendedHeader = JwsHeaderBuilder.from(baseHeader)
    .keyId("new-key")
    .custom("x-extra", "value")
    .build()
```

### Copying and Modifying

```kotlin
val original = JwsPayloadBuilder()
    .issuer("https://issuer.example.com")
    .subject("user-123")

// Create a copy with modifications
val modified = original.copy()
    .claim("email", "user@example.com")
    .expirationTime(now + 7200)
    .build()
```

### Reusing Builders

```kotlin
// Create a template
val headerTemplate = JwsHeaderBuilder()
    .type("JWT")
    .contentType("application/json")

// Use template for multiple headers
val header1 = headerTemplate.copy()
    .algorithm("ES256")
    .keyId("key-1")
    .build()

val header2 = headerTemplate.copy()
    .algorithm("RS256")
    .keyId("key-2")
    .build()
```

## Complete Example: Creating and Signing a JWT

```kotlin
import com.sphereon.crypto.jose.jws.*
import kotlinx.datetime.Clock

// 1. Build the payload
val now = Clock.System.now().epochSeconds
val payload = jwsPayload {
    issuer("https://myapp.com")
    subject("user-12345")
    audience("https://api.example.com")
    expirationTime(now + 3600)  // Expires in 1 hour
    notBefore(now)
    issuedAt(now)
    jwtId("unique-jwt-${UUID.randomUUID()}")

    // Custom claims
    claim("email", "user@example.com")
    claim("email_verified", true)
    claim("name", "John Doe")
    claimList("roles", "user", "premium")
}

// 2. Build the JWS arguments
val args = createJwsArgs {
    issuer(myManagedKey)
    payload(payload)
    mode(JwsIdentifierMode.KID)

    options {
        protectedHeader {
            type("JWT")
            contentType("application/json")
        }
    }
}

// 3. Sign the JWT
val result = jwtService.createJwsCompact(args)
if (result.isOk) {
    val jwt = result.value.jwt
    println("Created JWT: $jwt")
} else {
    println("Error: ${result.error}")
}
```

## Benefits of the Builder Pattern

1. **Type Safety** - Compile-time checking of parameters
2. **Fluent API** - Readable, chainable method calls
3. **DSL Support** - Kotlin DSL for even cleaner syntax
4. **Immutability** - Builders don't modify original objects
5. **Flexibility** - Multiple ways to construct the same object
6. **Discoverability** - IDE autocomplete shows available options
7. **Validation** - Can add validation logic in builders
8. **Reusability** - Templates and copies for common patterns

## Migration from Direct Construction

**Before:**
```kotlin
val args = CreateJwsArgs(
    issuer = myKey,
    payload = JsonObject(mapOf(
        "iss" to JsonPrimitive("https://example.com"),
        "sub" to JsonPrimitive("user-123"),
        "email" to JsonPrimitive("user@example.com")
    )),
    mode = JwsIdentifierMode.KID,
    opts = CreateJwsOpts(
        protectedHeader = JsonObject(mapOf(
            "alg" to JsonPrimitive("ES256"),
            "typ" to JsonPrimitive("JWT")
        ))
    )
)
```

**After (with builders):**
```kotlin
val args = createJwsArgs {
    issuer(myKey)
    payload {
        issuer("https://example.com")
        subject("user-123")
        claim("email", "user@example.com")
    }
    mode(JwsIdentifierMode.KID)
    options {
        protectedHeader {
            algorithm("ES256")
            type("JWT")
        }
    }
}
```

Much more readable and maintainable!

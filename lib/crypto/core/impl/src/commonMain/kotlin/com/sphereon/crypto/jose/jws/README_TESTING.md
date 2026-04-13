# JWS/JWT Testing Strategy

This document describes the testing strategy for the JWS/JWT implementation.

## Test Types

### 1. Unit Tests (commonTest)

Unit tests verify individual components in isolation without dependencies on DI or external services.

**Location**: `src/commonTest/kotlin/com/sphereon/crypto/jose/jws/`

**Coverage**:
- `JwsUtilsTest.kt` - Tests for utility functions (encoding, decoding, format conversion)
- Data type serialization and deserialization
- Pure function logic without external dependencies

**Run with**:
```bash
./gradlew :libraries:crypto:lib-crypto-core:jvmTest --tests "com.sphereon.crypto.jose.jws.JwsUtilsTest"
```

### 2. Integration Tests (KMS Provider Tests)

Full end-to-end integration tests with actual key generation, signing, and verification require:
- Complete DI setup with KSP-generated components
- KMS provider configuration (software, Azure, AWS, etc.)
- Full session/context infrastructure

**Location**: These tests should be placed in KMS provider test modules, specifically:
- `libraries/crypto/kms/provider/software/src/jvmTest/` for software KMS tests
- Other provider test directories for provider-specific tests

**Why not in crypto-core?**:
1. **DI Component Generation**: Integration tests with full DI require KSP to generate component implementations, which only happens in platform-specific test sources (jvmTest, jsTest, etc.)
2. **KMS Infrastructure**: Full key generation and cryptographic operations require a properly configured KMS provider
3. **Session Management**: Real signing/verification needs complete session context with user context, app component, and all associated services

**Example Integration Test Structure**:
```kotlin
class JwsWithSoftwareKmsIntegrationTest {

    @BeforeTest
    fun setup() {
        // Initialize full DI components (AppComponent, UserContext, Session)
        // Configure KMS software provider
        // Create test keys
    }

    @Test
    fun `should create and verify JWS with generated ES256 key`() {
        // Generate key via KMS
        // Create JWS using JwtService
        // Verify JWS
    }
}
```

## Testing Guidelines

### For Unit Tests
- Mock external dependencies
- Test pure functions and logic
- Fast execution (no I/O, no crypto operations)
- Platform-independent (can run on JVM, JS, Native)

### For Integration Tests
- Use real KMS providers
- Test full DI injection chain
- Verify actual cryptographic operations
- Platform-specific (typically JVM for full DI support)

## Adding New Tests

### Adding Unit Tests
1. Create test file in `src/commonTest/kotlin/com/sphereon/crypto/jose/jws/`
2. Use Kotlin Test framework
3. No external dependencies beyond test libraries

### Adding Integration Tests
1. Choose appropriate KMS provider test module
2. Set up test DI components (AppComponent, UserContext, SessionComponent)
3. Configure KMS provider in `@BeforeTest`
4. Use real cryptographic operations
5. Clean up test keys in `@AfterTest`

## Current Test Coverage

✅ **Unit Tests**
- JWS utility functions (encoding, decoding, format conversion)
- All test passing

⚠️ **Integration Tests**
- To be added in KMS provider test modules
- Requires full DI setup with KSP component generation
- Should include end-to-end scenarios with real keys

## Running Tests

```bash
# Run all unit tests
./gradlew :libraries:crypto:lib-crypto-core:test

# Run specific test class
./gradlew :libraries:crypto:lib-crypto-core:jvmTest --tests "com.sphereon.crypto.jose.jws.JwsUtilsTest"

# Run all tests including integration tests (when added to KMS provider)
./gradlew :libraries:crypto:kms:provider:software:test
```

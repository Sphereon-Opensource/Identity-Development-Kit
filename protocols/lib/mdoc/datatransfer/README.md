# mdoc Data Transfer (ISO 18013-5)

This Kotlin Multiplatform library implements ISO/IEC 18013-5 device engagement and data retrieval/transfer protocols for mobile driving licenses (mDL) and other mdoc credentials. It provides high-level APIs for both **mdoc holders** (devices presenting credentials) and **mdoc readers** (verifiers requesting credentials) across Android, iOS, JVM, and JavaScript platforms.

**Key Features:**
- Complete ISO 18013-5 implementation with Device Engagement v1.0 and v1.1
- Multiple transport protocols: BLE, NFC, QR code + HTTP
- Reactive programming with Kotlin Flows for event-driven state management
- Dual error-handling patterns: exceptions or functional `IdkResult<T, Error>` types
- Session transcript management for cryptographic binding
- Reader authentication and device signature verification
- Multiplatform support: Android, iOS (Swift/Obj-C interop), JVM, JS/WASM

**V3.0 Enhancements:**
-  **Typed Engagement Access** - Direct `nfcEngagement`, `qrEngagement`, `toAppEngagement` properties
-  **EventHub Architecture** - Centralized event management with adapters
-  **Session UI Projection** - Simplified UI integration with single state flow
-  **SharedParameters** - Automatic BLE UUID and key management
-  **Engagement Suspension** - Automatic suspension/resumption for concurrent engagements
-  **Auto-Restart** - Automatic cleanup and background NFC restart

---

## Table of Contents

- [Quick Start](#quick-start)
- [Core Concepts](#core-concepts)
- [Architecture](#architecture)
- [API Overview](#api-overview)
  - [Holder (mdoc) Flow](#holder-mdoc-flow)
  - [Reader (Verifier) Flow](#reader-verifier-flow)
- [Transport Methods](#transport-methods)
- [Code Examples](#code-examples)
- [Error Handling](#error-handling)
- [Platform Notes](#platform-notes)
- [Testing](#testing)
- [Developer Experience Roadmap](#developer-experience-roadmap)

---

## Quick Start

### Prerequisites

- **Java 21** (for JVM targets)
- **Kotlin 2.1+**
- **Android SDK 30+** (target SDK 35) for Android apps
- **iOS**: Xcode with KMP support for iOS targets
- **Dependencies**: Managed via Gradle, includes:
  - `com.sphereon.cbor` - CBOR encoding/decoding
  - `com.sphereon.crypto` - COSE, key management, signatures
  - `com.sphereon.mdoc.core` - Core mdoc data structures
  - `kotlinx.coroutines` - Asynchronous operations

### Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.sphereon:mdoc-datatransfer:<version>")
}
```

### V3 Quick Start Example (Kotlin)

```kotlin
import com.sphereon.mdoc.engagement.*
import com.sphereon.mdoc.transfer.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// 1. Get engagement manager (typically injected via DI)
val manager: MdocEngagementManager = getEngagementManager()

// 2. Use simple UI projection for easy integration
launch {
    manager.eventHub.sessionState.collect { state ->
        when (state.phase) {
            UiPhase.ENGAGEMENT -> {
                if (state.qrMode == QrMode.DISPLAY) showQrCode()
                if (state.nfcState == NfcState.FOREGROUND) showNfcPrompt()
            }
            UiPhase.TRANSFER -> {
                if (state.userInteractionRequired) {
                    showConsentDialog(state.deviceRequest!!)
                }
            }
            UiPhase.TERMINAL -> {
                when (state.terminalOutcome) {
                    TerminalOutcome.SUCCESS -> showSuccess()
                    TerminalOutcome.ERROR -> showError(state.terminalMessage)
                }
            }
        }
    }
}

// 3. Create typed engagement - automatically populates correct property
manager.createEngagement {
    engagement { qr { } }
    retrieval { ble { centralClientMode = true } }
}

// 4. Access engagements directly by type (no UUID tracking needed)
val qr = manager.qrEngagement.value
val nfc = manager.nfcEngagement.value
val active = manager.activeEngagement.value
```

### Minimal Holder Example (Kotlin - Lower Level API)

```kotlin
import com.sphereon.mdoc.engagement.*
import com.sphereon.mdoc.transfer.*
import com.sphereon.mdoc.data.device.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// 1. Inject/obtain EngagementManager (typically via DI)
val engagementManager: MdocEngagementManager = getEngagementManager()

// 2. Configure engagement using DSL - defines HOW engagement happens
val engagementInstance = engagementManager.newInstance {
    // Engagement phase: How reader discovers holder (Phase 1 of ISO 18013-5)
    engagement {
        qr {  // Show QR code for reader to scan
            scheme = "mdoc:"
        }
    }

    // Retrieval phase: How data is transferred after engagement (Phase 2)
    retrieval {
        ble {  // Use BLE central client mode
            centralClientMode = true
            peripheralServerMode = false
        }
    }
}

// 3. Get engagement URI for display (e.g., as QR code)
val qrUri = engagementInstance.getEngagementUri()
displayQrCode(qrUri)

// 4. Observe combined events from BOTH engagement AND transfer phases
launch {
    engagementManager.allMdocEvents.collect { event ->
        when (event) {
            is MdocEngagementEvent.Connected -> log("Reader connected")
            is MdocRetrievalEvent.RequestReceived -> log("Request received")
            is MdocRetrievalEvent.ResponseSent -> log("Response sent")
            is MdocEngagementEvent.Error -> showError(event.message)
        }
    }
}

// 5. Start transfer when reader initiates connection
// (can happen via background NFC tap or foreground QR scan)
val transferManager: TransferManager = engagementManager.start()

// 6. Receive and process device request
val deviceRequest = transferManager.receiveDeviceRequest()

// 7. Create and send response (with your document provider)
val deviceResponse = transferManager.createResponse(
    deviceRequest = deviceRequest,
    documentProvider = myDocumentProvider
)
transferManager.sendDeviceResponse(deviceResponse)

// 8. Clean up
engagementManager.close()
```

### Minimal Reader Example (Kotlin)

```kotlin
import com.sphereon.mdoc.engagement.*
import com.sphereon.mdoc.transfer.*
import com.sphereon.mdoc.data.device.*

// 1. Scan QR code and parse engagement
val deviceEngagement = engagementManager.parseEngagementUri(scannedQrUri).value

// 2. Create reader engagement instance
val engagement: EngagementInstance = engagementManager
    .createEngagementFromDeviceEngagement(
        deviceEngagement = deviceEngagement,
        role = MdocRole.MDOC_READER
    )

// 3. Start transfer
val transferManager: TransferManager = engagement.start()

// 4. Build and send request
val deviceRequest = DeviceRequest.Builder()
    .addItemsRequest(
        DeviceItemsRequest.Builder()
            .withDocType(DocType("org.iso.18013.5.1.mDL"))
            .add(NameSpace("org.iso.18013.5.1"), DataElementIdentifier("family_name"))
            .add(NameSpace("org.iso.18013.5.1"), DataElementIdentifier("given_name"))
            .build()
    )
    .build()

// Send the request (handled internally by the transfer flow)

// 5. Receive response
val deviceResponse: DeviceResponse = transferManager.receiveDeviceResponse()

// 6. Validate response (reader auth, MSO, device signatures)
val validationResult = transferManager.validateReaderAuthentication(
    deviceRequest.effectiveDocRequests().first()
)

// Process the response data...
```

---

## Core Concepts

### Device Engagement (DE)

The **Device Engagement** is the initial payload exchanged out-of-band (via QR code, NFC tag, BLE advertisement) containing:
- **eDeviceKey**: Ephemeral ECDH public key for session encryption
- **Device Retrieval Methods**: Transport options (BLE, NFC, HTTP URI)
- **Security parameters**: Cipher suites, protocol version
- Encoded as CBOR and transmitted via `mdoc:` URI scheme for QR codes

**Versions supported:**
- `DeviceEngagement.V1_0` - ISO 18013-5:2021
- `DeviceEngagement.V1_1` - Adds origin infos and capabilities

### Engagement Manager

The `MdocEngagementManager` is the **primary entry point** for holders (injected via DI):
- **Purpose**: Orchestrates the entire mdoc interaction lifecycle across **both ISO 18013-5 phases**:
  1. **Engagement Phase**: Establishing connection (QR, NFC foreground/background)
  2. **Retrieval/Transfer Phase**: Exchanging requests and responses (BLE, NFC, HTTP)

**Key Features:**
- **DSL Configuration**: `newInstance { engagement { } retrieval { } }` - declaratively configure how engagement and transfer occur
- **Reactive State Management**:
  - `engagement: StateFlow<EngagementInstance?>` - Current engagement instance
  - `transferSession: StateFlow<TransferSession?>` - Current transfer session
  - `allMdocEvents: SharedFlow<MdocEvent>` - **Unified event stream** combining both engagement and retrieval events
  - `engagementEvents: SharedFlow<MdocEngagementEvent>` - Engagement-only events
- **Automatic Lifecycle**: Handles transitions between phases based on how engagement is initiated (foreground QR scan vs. background NFC tap)

### Engagement Instance

An `EngagementInstance` represents a **single engagement session** (Phase 1):
- Created by `MdocEngagementManager.newInstance { }` with DSL configuration
- Manages lifecycle: INIT → ENGAGED → CONNECTED (then transitions to Transfer phase)
- Provides access to:
  - `getEngagementUri()`: QR code data for reader to scan
  - `getEphemeralKey()`: COSE ephemeral key for this session
  - `getDeviceEngagement()`: Raw CBOR engagement object
  - `events: SharedFlow<MdocEngagementEvent>` - Events for this engagement instance

### Transfer Manager

The `TransferManager` handles the **actual request/response exchange** (Phase 2 - Retrieval/Transfer):
- Started via `engagementManager.start()` after engagement is established
- **Holder side**: `receiveDeviceRequest()` → `createResponse()` → `sendDeviceResponse()`
- **Reader side**: (Internal request flow) → `receiveDeviceResponse()`
- Manages:
  - **Session Transcript**: Cryptographic hash binding engagement, request, and response
  - **Reader Authentication**: Validates reader's X.509 certificate and signature
  - **Device Authentication**: Creates/validates device signatures over session data
  - **Data Channels**: `MdocIncomingDataChannel` and `MdocOutgoingDataChannel` for transport abstraction
  - **Events**: Emits `MdocRetrievalEvent` for retrieval/transfer phase

### Device Request / Response

**`DeviceRequest`:**
- Contains one or more `DeviceItemsRequest` (one per docType)
- Each request specifies:
  - `docType`: e.g., `"org.iso.18013.5.1.mDL"`
  - `nameSpaces`: Map of namespace → data elements with `intentToRetain` flags
  - Optional `readerAuth`: COSE signature over request

**`DeviceResponse`:**
- Contains `Document` array with:
  - `IssuerSigned`: MSO (Mobile Security Object) + issuer-signed data elements
  - `DeviceSigned`: Device signature binding to session transcript
- Status codes (0 = OK, 11/12/20 = various errors)

### Session Transcript

A cryptographic binding that prevents replay attacks by hashing:
1. Device Engagement bytes
2. eReaderKey (reader's ephemeral public key)
3. Handover data (if any)

Used as input to COSE signatures for both reader and device authentication.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   Application Layer                          │
│  (Wallet UI, Verifier App, Consent Flows)                    │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│               MdocEngagementManager                          │
│  • createEngagement() / createEngagementFromDeviceEngagement()│
│  • Lifecycle management                                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
           ┌───────────▼────────────┐
           │  EngagementInstance     │
           │  • events: SharedFlow   │
           │  • start() → TransferMgr│
           └───────────┬────────────┘
                       │
           ┌───────────▼────────────┐
           │    TransferManager      │
           │  • receiveDeviceRequest │
           │  • createResponse       │
           │  • sendDeviceResponse   │
           │  • Session Transcript   │
           └───────────┬────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
┌───────▼────────┐          ┌─────────▼─────────┐
│ MdocIncoming   │          │ MdocOutgoing      │
│ DataChannel    │          │ DataChannel       │
└───────┬────────┘          └─────────┬─────────┘
        │                             │
┌───────▼──────────────────────────────▼─────────┐
│         Transport Layer                          │
│  • BLE GATT (MdocBleManager)                    │
│  • NFC APDU (MdocNfcService)                    │
│  • HTTP (ServerRetrievalMethods)                │
└──────────────────────────────────────────────────┘
```

**Key Components:**

- **`libraries/mdoc/core`**: Data structures (`DeviceEngagement`, `DeviceRequest`, `DeviceResponse`, MSO, CBOR types)
- **`libraries/mdoc/datatransfer`**: Session management, transfer logic, transport adapters
  - `commonMain/`: Core Kotlin interfaces and implementations
  - `androidMain/`: Android-specific BLE/NFC services
  - `iosMain/`: iOS-specific platform code (minimal, most is in `commonMain`)
  - `jsMain/`: JS/WASM targets

---

## API Overview

### Holder (mdoc) Flow

**The holder flow is orchestrated by `MdocEngagementManager`, which handles both ISO 18013-5 phases:**

1. **Configure Engagement (DSL):**
   ```kotlin
   // MdocEngagementManager is typically injected via DI
   val engagementInstance = engagementManager.newInstance {
       // Phase 1: Engagement - HOW reader discovers holder
       engagement {
           qr { scheme = "mdoc:" }  // QR code engagement
           // OR: nfc { } for NFC foreground/background
       }

       // Phase 2: Retrieval - HOW data is transferred
       retrieval {
           ble {
               centralClientMode = true   // Holder is BLE central (connects to reader)
               peripheralServerMode = false
           }
           // OR: nfc { } for NFC APDU exchange
       }
   }

   val qrUri = engagementInstance.getEngagementUri()
   displayQrCode(qrUri)  // Show to user
   ```

2. **Monitor Combined Events (Both Phases):**
   ```kotlin
   // Observe unified event stream from EngagementManager
   launch {
       engagementManager.allMdocEvents.collect { event ->
           when (event) {
               // Engagement Phase (Phase 1)
               is MdocEngagementEvent.Initialized -> updateUI("Ready for engagement")
               is MdocEngagementEvent.Connected -> updateUI("Reader connected")

               // Retrieval/Transfer Phase (Phase 2)
               is MdocRetrievalEvent.RequestReceived -> updateUI("Request received")
               is MdocRetrievalEvent.ResponseSent -> updateUI("Response sent")
               is MdocRetrievalEvent.Completed -> finish()

               // Errors from either phase
               is MdocEngagementEvent.Error -> showError(event.message)
               is MdocRetrievalEvent.Error -> showError(event.message)
           }
       }
   }

   // Or observe engagement-only events
   engagementManager.engagementEvents.collect { event -> /* ... */ }

   // Or observe individual instance events
   engagementInstance.events.collect { event -> /* ... */ }
   ```

3. **Start Transfer (Transition to Phase 2):**
   ```kotlin
   // Called when reader initiates connection (QR scan, NFC tap)
   // EngagementManager automatically handles transition
   val transferManager = engagementManager.start()
   ```

4. **Receive Request:**
   ```kotlin
   val deviceRequest = transferManager.receiveDeviceRequest()

   // Optionally validate reader authentication
   val readerAuthResult = transferManager.validateReaderAuthentication(
       deviceRequest.effectiveDocRequests().first(),
       requireReaderAuthentication = true
   )
   ```

5. **Create Response (with selective disclosure):**
   ```kotlin
   val response = transferManager.createResponse(
       deviceRequest = deviceRequest,
       documentProvider = object : DocumentProvider {
           override suspend fun getDocuments(
               requests: List<DeviceItemsRequest>
           ): List<Document> {
               // Apply selective disclosure
               // Return only requested elements from issuer-signed credentials
               return myCredentialStore.getDocuments(requests)
           }
       }
   )
   ```

6. **Send Response:**
   ```kotlin
   transferManager.sendDeviceResponse(response)
   // Events emitted via engagementManager.allMdocEvents
   ```

7. **Cleanup:**
   ```kotlin
   engagementManager.close()  // Cleans up both engagement and transfer resources
   ```

### Reader (Verifier) Flow

1. **Scan Engagement:**
   ```kotlin
   val deviceEngagement = engagementManager.parseEngagementUri(qrCodeData).value
   ```

2. **Create Engagement:**
   ```kotlin
   val engagement = engagementManager.createEngagementFromDeviceEngagement(
       deviceEngagement = deviceEngagement,
       role = MdocRole.MDOC_READER
   )
   ```

3. **Start Transfer:**
   ```kotlin
   val transferManager = engagement.start()
   ```

4. **Build Request:**
   ```kotlin
   val request = DeviceItemsRequest.Builder()
       .withDocType(DocType("org.iso.18013.5.1.mDL"))
       .add(NameSpace("org.iso.18013.5.1"), DataElementIdentifier("family_name"))
       .add(NameSpace("org.iso.18013.5.1"), DataElementIdentifier("given_name"))
       .add(NameSpace("org.iso.18013.5.1"), DataElementIdentifier("birth_date"))
       .build()
   ```

5. **Receive Response:**
   ```kotlin
   val response = transferManager.receiveDeviceResponse()
   ```

6. **Validate:**
   - Check MSO (issuer signature, digest matches)
   - Verify device signature against session transcript
   - Validate reader authentication was correctly handled

---

## Transport Methods

### BLE (Bluetooth Low Energy)

**Characteristics:**
- GATT-based state and data characteristics
- MTU typically 185-512 bytes (configured per connection)
- Supports chunking for large payloads
- Requires Android BLUETOOTH permissions and iOS CoreBluetooth entitlements

**Android Implementation:**
- Holder: `MdocBleManager` advertises and serves GATT characteristics
- Reader: Scans for advertisement, connects to holder's GATT server

**Configuration:**
```kotlin
val config = MdocEngagementConfig(
    transportConfig = BleTransportConfig(
        mtu = 185,
        connectTimeoutMs = 8000
    )
)
```

### NFC (Near Field Communication)

**Characteristics:**
- APDU-based communication
- Holder acts as NFC card emulation (HCE on Android)
- Reader sends commands, holder responds
- Payload size constraints (typically < 60KB practical limit)

**Android Implementation:**
- Holder: Extend `AbstractMdocNfcService` from the transport-nfc module (Host Card Emulation)
- Reader: Use `android.nfc.tech.IsoDep` for APDU exchange

### QR Code + HTTP

**Characteristics:**
- QR code contains `mdoc:` URI with Device Engagement
- Connection info includes HTTPS endpoint
- Reader extracts URI, parses engagement, connects to holder's server
- Suitable for remote/cross-device scenarios

**Example Engagement URI:**
```
mdoc:CBOR_BASE64URL_ENCODED_DEVICE_ENGAGEMENT
```

Decode with:
```kotlin
val de = engagementManager.parseEngagementUri(uri).value
```

---

## Code Examples

### Building a Custom Device Request

```kotlin
val request = DeviceItemsRequest.Builder()
    .withDocType(DocType("org.iso.18013.5.1.mDL"))
    .nameSpace(NameSpace("org.iso.18013.5.1"))
        .add(DataElementIdentifier("family_name"), IntentToRetain(false))
        .add(DataElementIdentifier("given_name"), IntentToRetain(false))
        .add(DataElementIdentifier("birth_date"), IntentToRetain(true))
        .end()
    .build()
```

### Functional Error Handling with `IdkResult`

```kotlin
val result: IdkResult<TransferManager, IdkErrorType> =
    engagement.tryOps().start()

when (result) {
    is IdkResult.Success -> {
        val manager = result.value
        // Continue with manager
    }
    is IdkResult.Failure -> {
        val error = result.error
        logger.error("Failed to start: ${error.message}")
    }
}
```

### Reactive State Tracking

```kotlin
// Collect engagement events
launch {
    engagement.events.collect { event ->
        when (event) {
            is MdocEngagementEvent.Initialized -> updateUI("Ready")
            is MdocEngagementEvent.Connected -> updateUI("Connected to ${event.info}")
            is MdocEngagementEvent.RequestReceived -> updateUI("Request received")
            is MdocEngagementEvent.ResponseSent -> updateUI("Response sent")
            is MdocEngagementEvent.Error -> showError(event.message)
            is MdocEngagementEvent.Completed -> finish()
        }
    }
}
```

### iOS/Swift Interop Example

```swift
// Swift
let engagement = engagementManager.createEngagement(
    role: .MDOC,
    method: .QR
)

do {
    let qrUri = try await engagement.getEngagementUri()
    displayQRCode(qrUri)

    let transferManager = try await engagement.start()
    let request = try await transferManager.receiveDeviceRequest()
    let response = try await transferManager.createResponse(
        deviceRequest: request,
        documentProvider: myProvider
    )
    try await transferManager.sendDeviceResponse(deviceResponse: response)
} catch {
    print("Error: \(error)")
}
```

Or with `IdkResult`:
```swift
let result = await engagement.tryOps().start()
switch result {
case .success(let manager):
    print("Started: \(manager)")
case .failure(let error):
    print("Error: \(error)")
}
```

---

## Error Handling

### Exception-Based (Default)

All primary methods throw exceptions on failure:

```kotlin
try {
    val manager = engagement.start()
    val request = manager.receiveDeviceRequest()
} catch (e: Exception) {
    logger.error("Transfer failed", e)
}
```

### Functional with `IdkResult<T, IdkErrorType>`

Access via `tryOps()` for explicit success/failure handling:

```kotlin
val startResult = engagement.tryOps().start()
val requestResult = when (startResult) {
    is IdkResult.Success -> startResult.value.tryOps().receiveDeviceRequest()
    is IdkResult.Failure -> return handleError(startResult.error)
}
```

**Platform Guidance:**
- **Kotlin/Android**: Use exceptions for idiomatic error handling, or `IdkResult` for functional style
- **iOS (Swift)**: Exceptions bridge to Swift errors; `IdkResult` available for explicit Result-like handling
- **iOS (Obj-C)**: Exceptions become `NSError**` parameters; `IdkResult` provides explicit error objects

---

## Platform Notes

### Android

- **Min SDK**: 30 (Android 11)
- **Target SDK**: 35 (Android 15)
- **Permissions**: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `NFC`
- **BLE**: Use `MdocBleManager` for advertising/scanning; handle lifecycle (stop on `onStop()`)
- **NFC**: Extend `AbstractMdocNfcService` from the transport-nfc module for HCE; declare in `AndroidManifest.xml`
- **Keystore**: Use Android Keystore for device keys; StrongBox recommended for hardware-backed keys

### iOS

- **Targets**: `iosX64`, `iosArm64`, `iosSimulatorArm64`
- **CoreBluetooth**: Required for BLE; handle authorization states
- **Async/Await**: Kotlin coroutines bridge to Swift's async/await
- **Memory Management**: Always call `.close()` on managers/sessions to prevent leaks

### JVM

- **Java 21** required
- BouncyCastle for cryptographic operations
- Suitable for server-side verifier implementations

### JS/WASM

- Experimental support for browser-based verifiers
- Uses WebCrypto API for cryptographic operations
- HTTP for transport

---

## Testing

### Unit Tests

Located in `src/commonTest/`, `src/jvmTest/`, `src/androidTest/`:

```bash
# Run all tests
./gradlew :libraries:mdoc:datatransfer:test

# Android instrumented tests (requires connected device/emulator)
./gradlew :libraries:mdoc:datatransfer:connectedAndroidTest
```

### Test Structure

- **Engagement tests**: `DeviceEngagementTest.kt` - CBOR encoding/decoding, URI generation
- **Transfer tests**: Session transcript, request/response flows
- **Crypto tests**: COSE signatures, MSO validation, reader authentication

### Interop Testing

- Test vectors from ISO 18013-5 Annex D (where available)
- Cross-platform tests ensure Android ↔ iOS compatibility
- BLE/NFC transport mocks for unit testing without hardware

---

## Developer Experience Roadmap

### Current Pain Points Identified

#### 1. **Boilerplate-Heavy APIs**

**Problem: Session Transcript Management is Manual and Error-Prone**

Developers must manually retrieve and pass session transcripts between operations, creating opportunities for bugs:

```kotlin
// Current: Manual transcript management (error-prone)
val transferManager = engagement.start()
val deviceRequest = transferManager.receiveDeviceRequest()

// Developer must remember to get transcript and pass it correctly
val transcript = transferManager.getSessionTranscript().data()
val deviceAuth = DeviceAuthentication(
    sessionTranscript = transcript,  // Easy to forget or use wrong transcript
    docType = issuerMdoc.docType,
    deviceNamespaces = DeviceNameSpaces(mapOf())
)

// Later: Must pass same transcript to signing
val signedDoc = mdocSignService.deviceSignDocument(
    deviceAuthentication = deviceAuth,
    // ... other params
)
```

**Why it's painful:** The session transcript is cryptographically critical—using the wrong bytes breaks security. But nothing in the API prevents you from accidentally:
- Using a transcript from a different session
- Passing `null` or stale transcript bytes
- Forgetting to include handover data when required

**Proposed improvement:** Auto-bind transcript to operations:
```kotlin
// Future: Transcript automatically bound to transfer context
val signedDoc = transferManager.signDocument(
    // No transcript parameter needed - automatically uses correct one
)
```

---

**Problem: Request/Response Builders Require Deep CBOR Knowledge**

Building even simple requests requires understanding CBOR structure, value types, and ISO 18013-5 schema details:

```kotlin
// Current: Verbose builder with type wrappers
val request = DeviceItemsRequest.Builder()
    .withDocType(DocType("org.iso.18013.5.1.mDL"))  // Must wrap in DocType
    .nameSpace(NameSpace("org.iso.18013.5.1"))       // Must wrap in NameSpace
        .add(
            DataElementIdentifier("family_name"),     // Must wrap in DataElementIdentifier
            IntentToRetain(false)                     // Must wrap in IntentToRetain
        )
        .add(
            DataElementIdentifier("given_name"),
            IntentToRetain(false)
        )
        .end()  // Must remember to call end() to return to parent builder
    .build()
```

**Why it's painful:**
- Every string must be wrapped in a value class (`DocType`, `NameSpace`, `DataElementIdentifier`, `IntentToRetain`)
- Nested builder pattern requires calling `.end()` to navigate back to parent
- No IDE autocomplete for valid element identifiers (easy to typo `"famly_name"`)
- No validation until runtime—typos in namespace or element names fail silently

**Proposed improvement:** Smart builders with validation:
```kotlin
// Future: String overloads with compile-time validation
val request = DeviceItemsRequest.builder("org.iso.18013.5.1.mDL") {
    namespace("org.iso.18013.5.1") {
        +"family_name"           // Unary plus operator for default intentToRetain
        +"given_name"
        "birth_date" with IntentToRetain(true)
    }
    // Or use predefined constants with IDE autocomplete
    namespace(MDL_NAMESPACE) {
        +MDL.FAMILY_NAME
        +MDL.GIVEN_NAME
    }
}
```

---

**Problem: Transport Configuration is Verbose and Nested**

Setting up an engagement with specific transport requires deeply nested DSL blocks:

```kotlin
// Current: Deeply nested configuration DSL
val engagement = manager.newInstance {
    engagement {
        qr {
            scheme = "mdoc:"
        }
    }
    retrieval {
        ble {
            centralClientMode = true
            peripheralServerMode = false
            // Where do I set MTU? Timeout? Not clear from this API
        }
    }
}
```

**Why it's painful:**
- Three levels of nesting (`newInstance { engagement { qr { } } }`) for basic setup
- Unclear where transport-specific config goes (MTU, timeouts, etc.)
- No type-safe config objects—easy to set invalid combinations
- Can't reuse configurations—must rebuild DSL each time

**Proposed improvement:** Flat configuration with presets:
```kotlin
// Future: Flat config with presets
val engagement = manager.createEngagement(
    method = EngagementMethod.QR_CODE,
    transport = BleTransport.holderMode(
        mtu = 185,
        timeout = 8.seconds
    )
)

// Or use presets
val engagement = manager.createEngagement(
    preset = EngagementPresets.QR_WITH_BLE_HOLDER
)
```

---

#### 2. **Error Handling Inconsistency**

**Problem: Mixed Error Patterns**

Some operations throw exceptions, others return nullable results, and some use `IdkResult`:

```kotlin
// Inconsistent error handling across similar operations
val engagement = manager.createEngagement(...)  // Throws on error
val uri = engagement.getEngagementUri()         // Throws on error
val key = engagement.getEphemeralKey()          // Throws on error

// But sometimes returns nullable:
val readerEngagement = engagement.getReaderEngagement()  // Returns null on error?

// And tryOps() returns IdkResult:
val result = engagement.tryOps().start()  // Returns IdkResult<T, E>
```

**Why it's painful:**
- Developers must remember which pattern each method uses
- Can't easily wrap all operations in consistent error handling
- Mixed patterns make functional composition difficult

**Proposed improvement:** Consistent error types with context:
```kotlin
sealed class MdocTransferError : IdkErrorType {
    data class TransportError(
        val transport: TransportType,
        val phase: TransferPhase,
        val cause: Throwable
    ) : MdocTransferError()

    data class CryptoError(
        val operation: CryptoOperation,
        val keyId: String?,
        val cause: Throwable
    ) : MdocTransferError()

    data class ProtocolError(
        val message: String,
        val statusCode: DeviceResponseStatus?,
        val receivedBytes: ByteArray?
    ) : MdocTransferError()
}
```

---

#### 3. **Testing Complexity**

**Problems:**
- Requires physical Android devices or emulators for BLE/NFC testing
- No built-in transport simulators—can't test engagement/transfer logic without hardware
- Limited ISO 18013-5 test vectors bundled with library
- Hard to test error scenarios (disconnects, timeouts, malformed messages)

**Proposed improvements:**
- In-memory transport simulator for unit testing
- Bundled test vectors from ISO 18013-5 Annex D
- Test DSL for scenario simulation

---

#### 4. **Documentation Gaps**

**Problems:**
- Sparse KDoc on key interfaces (`TransferManager`, `EngagementInstance`)
- No sequence diagrams showing message flow
- Missing "cookbook" examples for common patterns (age verification, selective disclosure)
- Platform-specific guidance scattered

### Planned Improvements

#### Phase 1: Facade APIs (High Priority)

Create simplified, opinionated facades:

```kotlin
// Future API concept
val holder = MdocHolderFacade.create(config)
holder.presentCredential(
    method = PresentationMethod.QR_CODE,
    consentHandler = { request -> userConsents(request) },
    onComplete = { result -> handleResult(result) }
)

val reader = MdocReaderFacade.create(config)
reader.requestCredential(
    method = VerificationMethod.SCAN_QR,
    docType = "org.iso.18013.5.1.mDL",
    elements = listOf("family_name", "given_name"),
    onResponse = { response -> processResponse(response) }
)
```

#### Phase 2: Enhanced Error Types

Introduce granular error sealed classes:

```kotlin
sealed class MdocTransferError : IdkErrorType {
    data class TransportError(val transport: Transport, val cause: Throwable) : MdocTransferError()
    data class CryptoError(val operation: String, val cause: Throwable) : MdocTransferError()
    data class ProtocolError(val message: String, val statusCode: Int?) : MdocTransferError()
    data class ValidationError(val field: String, val reason: String) : MdocTransferError()
}
```

#### Phase 3: Testing Utilities

- **In-memory transport simulator**: Mock BLE/NFC without hardware
- **Test DSL**: Fluent API for creating test engagements and requests
- **Bundled test vectors**: ISO 18013-5 Annex D vectors as resources

```kotlin
// Future test API
val testScenario = MdocTestScenario.holder()
    .withDeviceEngagement(version = "1.1", method = TransportMethod.BLE)
    .expectRequest(docType = "org.iso.18013.5.1.mDL", elements = listOf("family_name"))
    .respondWith(document = sampleMdl)
    .verify()
```

#### Phase 4: Configuration & Observability

- **YAML/HOCON config** with schema validation:
  ```yaml
  mdoc:
    crypto:
      algorithms: [ES256, EdDSA]
      keystore:
        android: STRONGBOX
        ios: SECURE_ENCLAVE
    transports:
      ble:
        mtu: 185
        timeout: 8000
      nfc:
        maxApduSize: 60000
    logging:
      level: INFO
      pii: REDACTED
  ```

- **OpenTelemetry integration**: Trace spans for request/response flows, redact PII by default
- **Structured logging**: JSON logs with correlation IDs

#### Phase 5: Tooling

- **Gradle plugin**: Generate Kotlin data classes from CDDL schemas for custom namespaces
- **CLI tool**: Test engagements, simulate requests, validate responses
- **Interactive debugger**: Inspect session transcripts, CBOR payloads

### How to Contribute

- **Report DX friction**: Open issues tagged `dx-improvement`
- **Submit PRs**: Include tests and update this README if APIs change
- **Request features**: Use GitHub Discussions for design proposals

---

## License

© 2026 Sphereon International B.V.

Licensed under the Apache License, Version 2.0. See [LICENSE](../../../LICENSE) for details.

---

## V3.0 Migration Guide

### Breaking Changes

1. **TransferSession → TransferInstance**
   ```kotlin
   // Old (v2.x)
   val session: TransferSession = transferManager.session

   // New (v3.0)
   val instance: TransferInstance = transferManager.instance
   ```

2. **Typed Engagement Access**
   ```kotlin
   // Old (v2.x) - UUID tracking required
   val engagementId = manager.createEngagement { qr { } }.value.id
   val engagement = manager.engagements.value[engagementId]

   // New (v3.0) - Direct typed access
   manager.createEngagement { qr { } }
   val qr = manager.qrEngagement.value
   ```

3. **EventHub for Event Access**
   ```kotlin
   // Old (v2.x) - Events on manager
   manager.engagementEvents.collect { }
   manager.addEngagementEventListener(listener)

   // New (v3.0) - Events through EventHub
   manager.eventHub.engagementEvents.collect { }
   manager.eventHub.addEngagementEventListener(listener)
   ```

4. **Transfer Property vs Method**
   ```kotlin
   // Old (v2.x)
   val transfer = engagement.transfer()

   // New (v3.0)
   val transfer = engagement.transferInstance
   ```

### New Features

1. **Session UI Projection** - See [SESSION_UI_GUIDE.md](./SESSION_UI_GUIDE.md)
   ```kotlin
   manager.eventHub.sessionState.collect { state ->
       // Simple UI binding
   }
   ```

2. **SharedParameters** - Automatic BLE UUID management
   ```kotlin
   val centralUuid = manager.sharedParameters.bleCentralClientUuid.value
   val peripheralUuid = manager.sharedParameters.blePeripheralServerUuid.value
   manager.sharedParameters.regenerate()
   ```

3. **EventHub Adapters** - Selective event handling
   ```kotlin
   manager.eventHub.addEngagementEventListener(object : MdocEngagementEventAdapter() {
       override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) {
           displayQr(event)
       }
   })
   ```

## Additional Resources

- [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html) - Official standard
- [Sphereon Identity Development Kit Docs](https://github.com/Sphereon-Opensource/identity-development-kit)
- [CBOR (RFC 8949)](https://www.rfc-editor.org/rfc/rfc8949.html)
- [COSE (RFC 9052)](https://www.rfc-editor.org/rfc/rfc9052.html)

For questions or support, open an issue at: https://github.com/Sphereon-Opensource/identity-development-kit/issues

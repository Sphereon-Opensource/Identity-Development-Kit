# Session UI Projection Guide

## Overview

The Session UI Projection layer provides a simplified, opinionated view of the mdoc engagement and transfer lifecycle, specifically designed for UI integration. Instead of handling complex raw engagement and retrieval events, UIs can bind to a single state flow that provides deterministic, UI-friendly state information.

**Implementation Status:**
-  `SessionUiState.kt` - All types implemented and verified
-  `SessionUiProjector.kt` - Full projection logic implemented
-  `MdocEventHub.kt` - Exposes `sessionState` and `sessionEvents` flows
-  Integration with `MdocEngagementManager` complete

## Key Concepts

### Design Principles

1. **Deterministic State Transitions** - Clear, predictable phase progression: ENGAGEMENT → TRANSFER → TERMINAL
2. **Single Source of Truth** - One state flow to bind, no need to coordinate multiple event streams
3. **UI-Centric Events** - Simplified events that map directly to UI actions
4. **Built-in Logic** - QR visibility, suspension tracking, and progress hints handled automatically
5. **Non-Breaking** - Optional layer on top of existing event system

### Architecture

```
Raw Events (Engagement + Retrieval)
            ↓
    SessionUiProjector
            ↓
    SessionEvent (simplified)
            ↓
    SessionUiState (deterministic)
            ↓
        Your UI
```

## Core Types

### UiPhase

High-level phases of the session lifecycle:

- **ENGAGEMENT** - QR display, NFC handover preparation
- **TRANSFER** - Connection, data exchange, user interaction
- **TERMINAL** - Session finished (success, error, declined, etc.)

### TerminalOutcome

Classification of terminal states for appropriate UI feedback:

- **SUCCESS** - Transfer completed successfully, data was shared
- **DECLINED** - User declined to share the requested data
- **CANCELED** - Session was canceled (by user or system)
- **ERROR** - An error occurred during the session
- **TERMINATED** - Session was terminated (connection lost, timeout, etc.)

### NfcState

NFC engagement state for UI display logic, determined by whether an NFC engagement exists and whether it is the active engagement:

- **DISABLED** - No NFC engagement exists
- **BACKGROUND** - NFC engagement exists but is not the active engagement
- **FOREGROUND** - NFC engagement exists and is the active engagement

### QrMode

QR display state for UI:

- **NONE** - No QR engagement active, no scanning in progress
- **DISPLAY** - Regular QR engagement active (display QR code for reader to scan)
- **SCAN** - **UI-managed scanning mode** (user is scanning reader's QR code)

**IMPORTANT:** `SCAN` mode is different from the other modes:
- `NONE` and `DISPLAY` are set automatically by the projector based on engagements
- `SCAN` is **UI-managed** and set manually by your code
- Scanning happens *before* creating a TO_APP engagement
- The UI opens a scanner, user scans to get a URI, then UI creates the engagement

### SessionUiState

The main state object containing all UI-relevant information:

```kotlin
data class SessionUiState(
    val phase: UiPhase,                       // Current phase
    val substateLabel: String,                // Human-readable label
    val nfcState: NfcState,                   // NFC state (DISABLED/BACKGROUND/FOREGROUND)
    val qrMode: QrMode,                       // QR mode (NONE/DISPLAY/SCAN)
    val isSuspended: Boolean,                 // Engagement suspended?
    val progressHint: Float?,                 // Progress (0.0-1.0)
    val terminalOutcome: TerminalOutcome?,    // Terminal classification
    val terminalMessage: String?,             // Terminal details
    val userInteractionRequired: Boolean,     // User must act?
    val deviceRequest: ByteArray?             // Request to review
)
```

### SessionEvent

Simplified events that drive state transitions:

- `QrShow` - QR code displayed
- `NfcPromptShown` - NFC prompt displayed
- `NfcHandoverSuccess` - NFC handover completed
- `TransferConnecting` - Transfer connecting
- `TransferConnected` - Transfer connected
- `UserInteractionRequired(deviceRequest)` - User must review and accept/decline
- `UserAccepted` - User accepted the request
- `UserDeclined` - User declined the request
- `TransferProgress(fraction)` - Progress update
- `Terminal(outcome, message)` - Session ended

## Usage Examples

### Basic Integration (Kotlin/Android)

```kotlin
class WalletActivity : AppCompatActivity() {
    private lateinit var manager: MdocEngagementManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Collect session state
        lifecycleScope.launch {
            manager.eventHub.sessionState.collect { state ->
                updateUI(state)
            }
        }
    }
    
    private fun updateUI(state: SessionUiState) {
        when (state.phase) {
            UiPhase.ENGAGEMENT -> handleEngagement(state)
            UiPhase.TRANSFER -> handleTransfer(state)
            UiPhase.TERMINAL -> handleTerminal(state)
        }
    }
    
    private fun handleEngagement(state: SessionUiState) {
        // Update status
        statusText.text = state.substateLabel
        
        // Handle QR based on mode
        when (state.qrMode) {
            QrMode.DISPLAY -> {
                // Show QR code for reader to scan
                qrCodeView.visibility = View.VISIBLE
                qrScannerView.visibility = View.GONE
                showQrButton.text = "Show QR"
            }
            QrMode.SCAN -> {
                // Show scanner for scanning reader's QR
                qrCodeView.visibility = View.GONE
                qrScannerView.visibility = View.VISIBLE
                showQrButton.text = "Scan QR"
            }
            QrMode.NONE -> {
                qrCodeView.visibility = View.GONE
                qrScannerView.visibility = View.GONE
            }
        }
        
        // Handle NFC based on state
        when (state.nfcState) {
            NfcState.DISABLED -> {
                // No NFC available
                nfcPrompt.visibility = View.GONE
                nfcIcon.visibility = View.GONE
            }
            NfcState.BACKGROUND -> {
                // Show icon but not active prompt
                nfcPrompt.visibility = View.GONE
                nfcIcon.visibility = View.VISIBLE
                nfcIcon.alpha = 0.5f  // Dimmed to indicate background
            }
            NfcState.FOREGROUND -> {
                // Show active "tap to share" prompt
                nfcPrompt.visibility = View.VISIBLE
                nfcIcon.visibility = View.VISIBLE
                nfcIcon.alpha = 1.0f
                nfcPrompt.text = "Hold near reader to share"
            }
        }
        
        // Handle suspension
        if (state.isSuspended) {
            overlayView.visibility = View.VISIBLE
            statusText.text = "Suspended - another session active"
        }
    }
    
    private fun handleTransfer(state: SessionUiState) {
        statusText.text = state.substateLabel
        
        // QR and scanner hidden during transfer
        qrCodeView.visibility = View.GONE
        qrScannerView.visibility = View.GONE
        
        when {
            state.userInteractionRequired -> {
                // User must review and accept/decline
                showConsentDialog(state.deviceRequest!!)
            }
            state.progressHint != null -> {
                // Show progress
                progressBar.progress = (state.progressHint * 100).toInt()
                progressBar.visibility = View.VISIBLE
            }
            else -> {
                // Show spinner for connecting/processing
                progressBar.isIndeterminate = true
                progressBar.visibility = View.VISIBLE
            }
        }
    }
    
    private fun handleTerminal(state: SessionUiState) {
        progressBar.visibility = View.GONE
        
        when (state.terminalOutcome) {
            TerminalOutcome.SUCCESS -> {
                showSuccessDialog(
                    title = "Success!",
                    message = "Your data was shared successfully"
                )
            }
            TerminalOutcome.DECLINED -> {
                showInfoDialog(
                    title = "Request Declined",
                    message = "You chose not to share your data"
                )
            }
            TerminalOutcome.CANCELED -> {
                showInfoDialog(
                    title = "Canceled",
                    message = state.terminalMessage ?: "Session was canceled"
                )
            }
            TerminalOutcome.ERROR -> {
                showErrorDialog(
                    title = "Error",
                    message = state.terminalMessage ?: "An error occurred"
                )
            }
            TerminalOutcome.TERMINATED -> {
                showWarningDialog(
                    title = "Connection Lost",
                    message = state.terminalMessage ?: "Session terminated"
                )
            }
        }
    }
    
    private fun showConsentDialog(deviceRequestBytes: ByteArray) {
        // Decode the device request
        val deviceRequest = DeviceRequest.Decoder.decodeCbor(deviceRequestBytes)
        
        // Parse and display requested attributes
        val requestedData = parseDeviceRequest(deviceRequest)
        
        AlertDialog.Builder(this)
            .setTitle("Share your data?")
            .setMessage("The verifier is requesting:\n$requestedData")
            .setPositiveButton("Share") { _, _ ->
                // User accepted - the system will handle this
                // via DocumentsSelectionProcessAccepted event
            }
            .setNegativeButton("Decline") { _, _ ->
                // User declined - the system will handle this
                // via DocumentsSelectionProcessDeclined event
            }
            .setCancelable(false)
            .show()
    }
}
```

### iOS/Swift Integration

```swift
class WalletViewController: UIViewController {
    private var manager: MdocEngagementManager!
    private var stateObserver: Task<Void, Never>?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        // Observe session state using Kotlin Flow adapter
        stateObserver = Task {
            for await state in manager.eventHub.sessionState {
                await updateUI(state: state)
            }
        }
    }
    
    deinit {
        stateObserver?.cancel()
    }
    
    @MainActor
    private func updateUI(state: SessionUiState) {
        switch state.phase {
        case .engagement:
            handleEngagement(state: state)
        case .transfer:
            handleTransfer(state: state)
        case .terminal:
            handleTerminal(state: state)
        }
    }
    
    private func handleEngagement(state: SessionUiState) {
        statusLabel.text = state.substateLabel
        
        // Show/hide QR
        qrCodeView.isHidden = !state.showQr
        
        // Show/hide NFC prompt
        nfcPromptView.isHidden = !state.showNfcPrompt
        
        // Handle suspension
        if state.isSuspended {
            overlayView.isHidden = false
            statusLabel.text = "Suspended - another session active"
        }
    }
    
    private func handleTransfer(state: SessionUiState) {
        statusLabel.text = state.substateLabel
        qrCodeView.isHidden = true
        
        if state.userInteractionRequired {
            showConsentSheet(deviceRequest: state.deviceRequest!)
        } else if let progress = state.progressHint {
            progressView.progress = progress
            progressView.isHidden = false
        } else {
            activityIndicator.startAnimating()
        }
    }
    
    private func handleTerminal(state: SessionUiState) {
        activityIndicator.stopAnimating()
        
        switch state.terminalOutcome {
        case .success:
            showAlert(title: "Success!", message: "Data shared successfully")
        case .declined:
            showAlert(title: "Declined", message: "You chose not to share")
        case .canceled:
            showAlert(title: "Canceled", message: state.terminalMessage ?? "Session canceled")
        case .error:
            showAlert(title: "Error", message: state.terminalMessage ?? "An error occurred")
        case .terminated:
            showAlert(title: "Connection Lost", message: state.terminalMessage ?? "Session terminated")
        default:
            break
        }
    }
}
```

### Jetpack Compose (Android)

```kotlin
@Composable
fun WalletScreen(manager: MdocEngagementManager) {
    val sessionState by manager.eventHub.sessionState.collectAsState()
    
    WalletContent(state = sessionState)
}

@Composable
fun WalletContent(state: SessionUiState) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state.phase) {
            UiPhase.ENGAGEMENT -> EngagementView(state)
            UiPhase.TRANSFER -> TransferView(state)
            UiPhase.TERMINAL -> TerminalView(state)
        }
        
        if (state.isSuspended) {
            SuspendedOverlay()
        }
    }
}

@Composable
fun EngagementView(state: SessionUiState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = state.substateLabel, style = MaterialTheme.typography.h6)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (state.showQr) {
            QRCodeDisplay()
        }
        
        if (state.showNfcPrompt) {
            NfcPromptDisplay()
        }
    }
}

@Composable
fun TransferView(state: SessionUiState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = state.substateLabel)
        
        when {
            state.userInteractionRequired -> {
                ConsentDialog(deviceRequest = state.deviceRequest!!)
            }
            state.progressHint != null -> {
                LinearProgressIndicator(progress = state.progressHint)
            }
            else -> {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun TerminalView(state: SessionUiState) {
    val (icon, color, title) = when (state.terminalOutcome) {
        TerminalOutcome.SUCCESS -> Triple(
            Icons.Default.CheckCircle,
            Color.Green,
            "Success!"
        )
        TerminalOutcome.DECLINED -> Triple(
            Icons.Default.Cancel,
            Color.Orange,
            "Declined"
        )
        TerminalOutcome.ERROR -> Triple(
            Icons.Default.Error,
            Color.Red,
            "Error"
        )
        else -> Triple(
            Icons.Default.Info,
            Color.Gray,
            "Completed"
        )
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = title, style = MaterialTheme.typography.h5)
        
        state.terminalMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.body2)
        }
    }
}
```

## State Transition Flow

### QR Display Flow (Regular QR)

```
ENGAGEMENT (qrMode=DISPLAY, nfcState=BACKGROUND)
    ↓ QrShow
ENGAGEMENT (qrMode=DISPLAY, "Scan the QR with reader")
    ↓ TransferConnecting
TRANSFER (qrMode=NONE, "Connecting…")  ← QR automatically switched to NONE
    ↓ TransferConnected
TRANSFER ("Connected")
    ↓ UserInteractionRequired
TRANSFER (userInteractionRequired=true, "Review request")
    ↓ UserAccepted
TRANSFER (userInteractionRequired=false, "Processing…")
    ↓ TransferProgress
TRANSFER (progressHint=0.9, "Transferring…")
    ↓ Terminal(SUCCESS)
TERMINAL (terminalOutcome=SUCCESS, qrMode=NONE, "Completed")
```

### Reverse/ToApp Flow (Scan Then Engage)

**Important:** Scanning happens *before* creating the engagement, so it's not reflected in session state.

```
[UI-managed scanning - not in sessionState]
    ↓ User opens scanner (your code manages this)
    ↓ User scans reader's QR code
    ↓ Get URI from scanned QR
    ↓ Create TO_APP engagement with URI
ENGAGEMENT (qrMode=NONE, nfcState=BACKGROUND)
    ↓ TransferConnecting (engagement starts immediately)
TRANSFER (qrMode=NONE, "Connecting…")
    ↓ TransferConnected
TRANSFER ("Connected")
    ↓ [continues as normal transfer flow]
```

**Note:** The `qrMode` stays `NONE` throughout because there's no QR code to *display*. The scanning was done before the engagement existed.

### NFC Foreground Flow

```
ENGAGEMENT (nfcState=FOREGROUND, qrMode=NONE)
    ↓ NfcPromptShown
ENGAGEMENT ("Hold near reader")
    ↓ User taps phone to reader
    ↓ NfcHandoverSuccess
TRANSFER (nfcState=FOREGROUND, "Connecting…")
    ↓ TransferConnected
TRANSFER ("Connected")
    ↓ UserInteractionRequired
TRANSFER (userInteractionRequired=true, "Review request")
    ↓ UserDeclined
TERMINAL (terminalOutcome=DECLINED, "Declined")
```

### NFC Background Flow (QR active, NFC available)

```
ENGAGEMENT (nfcState=BACKGROUND, qrMode=DISPLAY)
    ↓ User shows QR to reader, but reader uses NFC instead
    ↓ NfcHandoverSuccess (NFC takes over)
TRANSFER (nfcState=FOREGROUND, qrMode=NONE, "Connecting…")
    ↓ [continues as above]
```

## Key Rules & Behaviors

### NFC State Management

The `nfcState` property automatically tracks NFC engagement status based on whether an NFC engagement exists and whether it is the currently active engagement:

**DISABLED** - No NFC engagement exists:
- No NFC engagement has been created
- Typical scenarios: iOS mdoc holder (NFC not available for holder apps), Android with NFC hardware disabled
- UI should not show any NFC elements

**BACKGROUND** - NFC engagement exists but is not the active engagement:
- An NFC engagement has been created but another engagement (QR or TO_APP) is currently active
- The system can still respond to NFC taps, but focus is on the other engagement
- UI should show a small NFC icon/indicator (dimmed/inactive state)
- Don't show active "tap to share" prompt
- User can tap their phone to a reader to switch NFC to foreground

**FOREGROUND** - NFC engagement exists and is the active engagement:
- The NFC engagement is currently the active engagement (no other engagement is active, or NFC became active)
- UI should show prominent "tap to share" prompt
- This is the primary engagement method the user should use

### QR Mode Management

The `qrMode` property tracks QR display state:

**NONE** - No QR engagement active (default state)

**DISPLAY** - Regular QR engagement active (ISO 18013-5)
- Automatically set when QR engagement is created
- Display QR code for reader to scan  
- Reader scans holder's QR

**SCAN** - UI-managed scanning mode (ISO 18013-7 pre-engagement)
- **NOT set automatically** - you manage this yourself
- Used for reverse/ToApp engagement flow
- User scans reader's QR to get URI
- Then you create TO_APP engagement with that URI

**Important:** For reverse/ToApp engagements, scanning happens *before* creating the engagement:

```kotlin
// Your own scanning state (not from sessionState)
var isScanningQr by remember { mutableStateOf(false) }

// User taps "Scan QR"
Button(onClick = { isScanningQr = true }) {
    Text("Scan QR")
}

// Show scanner when YOUR state says so
if (isScanningQr) {
    QrScanner { scannedUri ->
        isScanningQr = false
        // NOW create the engagement with scanned URI
        manager.createEngagement(
            EngagementType.TO_APP,
            config.copy(readerEngagementUri = scannedUri)
        )
        // state.qrMode stays NONE (no QR to display)
    }
}

// Show QR code when engagement says so
if (state.qrMode == QrMode.DISPLAY) {
    QrCodeDisplay()
}
```

The projector only sets qrMode to DISPLAY (for QR engagements). You handle the scanning UI yourself.

### User Interaction

- `userInteractionRequired=true` when `DocumentsSelectionProcessStart` received
- `deviceRequest` contains CBOR-encoded DeviceRequest for decoding
- UI should display consent dialog and wait for user decision
- User response handled by transfer manager (accept/decline methods)
- `UserAccepted` → continue transfer
- `UserDeclined` → immediate transition to TERMINAL with DECLINED outcome

### Suspension

- `isSuspended` mirrors `activeEngagement?.isActive == false`
- Suspended engagements won't respond to connection attempts
- UI should show overlay or disabled state when suspended

### Progress Tracking

- `progressHint` is `null` during connection/setup phases
- `progressHint` is `0.0-1.0` during data transfer
- Use indeterminate progress indicator when `progressHint == null`

### Terminal States

Always check `terminalOutcome` to provide appropriate feedback:

- **SUCCESS** → Show success message, confetti, checkmark
- **DECLINED** → Show neutral message, explain user choice
- **CANCELED** → Show neutral message, allow retry
- **ERROR** → Show error dialog with details, offer retry
- **TERMINATED** → Show warning about connection loss

## Advanced Usage

### Observing Raw Events (Optional)

If you need access to detailed events alongside the UI state:

```kotlin
// Observe simplified session events
launch {
    manager.eventHub.sessionEvents.collect { event ->
        when (event) {
            is SessionEvent.UserInteractionRequired -> {
                logAnalytics("consent_shown")
            }
            is SessionEvent.Terminal -> {
                logAnalytics("session_ended", mapOf(
                    "outcome" to event.outcome.name
                ))
            }
            else -> {}
        }
    }
}

// Observe UI state
launch {
    manager.eventHub.sessionState.collect { state ->
        updateUI(state)
    }
}
```

### Handling Multiple Sessions

The UI projection automatically handles active engagement switching:

```kotlin
// Manager automatically updates sessionState based on activeEngagement
// You don't need to track which engagement is active
manager.eventHub.sessionState.collect { state ->
    // This state is always for the currently active engagement
    updateUI(state)
    
    if (state.isSuspended) {
        // Current engagement is suspended, another is active
        showSuspendedOverlay()
    }
}
```

### Decoding Device Request

When `userInteractionRequired=true`:

```kotlin
fun parseDeviceRequest(bytes: ByteArray): RequestDetails {
    val deviceRequest = DeviceRequest.Decoder.decodeCbor(bytes)
    
    val requestedDocs = deviceRequest.effectiveDocRequests().map { docRequest ->
        val docType = docRequest.itemsRequest.docType
        val namespaces = docRequest.itemsRequest.nameSpaces
        
        val attributes = namespaces.flatMap { (namespace, items) ->
            items.keys.map { "$namespace/$it" }
        }
        
        DocumentRequest(docType, attributes)
    }
    
    return RequestDetails(
        readerAuth = deviceRequest.readerAuth,
        documents = requestedDocs
    )
}
```

## Common UI Patterns

### Pattern 1: NFC-Only App (Android Wallet)

```kotlin
class WalletActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create background NFC on app start
        lifecycleScope.launch {
            manager.createEngagement(EngagementType.NFC, nfcConfig)
        }
        
        // Observe session state
        lifecycleScope.launch {
            manager.eventHub.sessionState.collect { state ->
                when (state.nfcState) {
                    NfcState.DISABLED -> {
                        // Should not happen on Android, but handle it
                        showNfcDisabledDialog()
                    }
                    NfcState.BACKGROUND -> {
                        // On secondary screens, show small NFC icon
                        nfcIcon.visibility = View.VISIBLE
                        nfcIcon.alpha = 0.5f
                        nfcPrompt.visibility = View.GONE
                    }
                    NfcState.FOREGROUND -> {
                        // On main screen, show prominent NFC prompt
                        nfcIcon.visibility = View.VISIBLE
                        nfcIcon.alpha = 1.0f
                        nfcPrompt.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
```

### Pattern 2: QR-Primary with Background NFC (Android/iOS)

```kotlin
class ShareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create background NFC if available (Android only)
        if (isAndroid && nfcAvailable) {
            lifecycleScope.launch {
                manager.createEngagement(EngagementType.NFC, nfcConfig)
            }
        }
        
        // Main screen with QR buttons
        showQrButton.setOnClickListener {
            lifecycleScope.launch {
                manager.createEngagement(EngagementType.QR, qrConfig)
            }
        }
        
        scanQrButton.setOnClickListener {
            // Open QR scanner (UI-managed, not engagement-driven)
            openQrScanner { scannedUri ->
                // After scanning, create TO_APP engagement with the URI
                lifecycleScope.launch {
                    manager.createEngagement(
                        EngagementType.TO_APP,
                        toAppConfig.copy(readerEngagementUri = scannedUri)
                    )
                }
            }
        }
        
        // Observe session state
        lifecycleScope.launch {
            manager.eventHub.sessionState.collect { state ->
                // Handle NFC indicator
                when (state.nfcState) {
                    NfcState.DISABLED -> nfcIcon.visibility = View.GONE
                    NfcState.BACKGROUND -> {
                        nfcIcon.visibility = View.VISIBLE
                        nfcIcon.alpha = 0.5f
                    }
                    NfcState.FOREGROUND -> {
                        nfcIcon.visibility = View.VISIBLE
                        nfcIcon.alpha = 1.0f
                    }
                }
                
                // Handle QR modal
                when (state.qrMode) {
                    QrMode.NONE -> {
                        hideQrModal()
                    }
                    QrMode.DISPLAY -> {
                        showQrDisplayModal()
                    }
                    QrMode.SCAN -> {
                        showQrScannerModal()
                    }
                }
            }
        }
    }
}
```

### Pattern 3: QR-Only App (iOS mdoc Holder)

```swift
class ShareViewController: UIViewController {
    private var isScanning = false
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        // No NFC on iOS holder - state.nfcState will always be DISABLED
        
        // Observe session state
        Task {
            for await state in manager.eventHub.sessionState {
                await MainActor.run {
                    // NFC always disabled
                    nfcIcon.isHidden = true
                    
                    // Handle engagement-driven QR display
                    if state.qrMode == .display {
                        showQrCodeView()
                    } else if !isScanning {
                        hideQrView()
                    }
                }
            }
        }
    }
    
    @IBAction func showQrPressed() {
        // Create QR engagement to display QR code
        Task {
            try await manager.createEngagement(type: .QR, config: qrConfig)
            // state.qrMode will become DISPLAY
        }
    }
    
    @IBAction func scanQrPressed() {
        // Open scanner (UI-managed, before engagement)
        isScanning = true
        showQrScanner { [weak self] scannedUri in
            guard let self = self else { return }
            self.isScanning = false
            
            // NOW create TO_APP engagement with scanned URI
            Task {
                try await self.manager.createEngagement(
                    type: .TO_APP,
                    config: self.toAppConfig.copy(readerEngagementUri: scannedUri)
                )
                // state.qrMode stays NONE (no QR to display)
            }
        }
    }
    
    private func showQrScanner(onScan: @escaping (String) -> Void) {
        // Show camera-based QR scanner
        let scanner = QRScannerViewController()
        scanner.onScanComplete = onScan
        present(scanner, animated: true)
    }
}
```

### Pattern 4: Modal QR with Persistent NFC

```kotlin
// Main activity with background NFC
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Background NFC throughout app lifetime
        lifecycleScope.launch {
            manager.createEngagement(EngagementType.NFC, nfcConfig)
        }
        
        showQrButton.setOnClickListener {
            // Show modal fragment with QR
            QrModalFragment().show(supportFragmentManager, "qr")
        }
    }
}

// Modal fragment for QR
class QrModalFragment : DialogFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Create QR engagement when modal opens
        lifecycleScope.launch {
            manager.createEngagement(EngagementType.QR, qrConfig)
        }
        
        // Observe state
        lifecycleScope.launch {
            manager.eventHub.sessionState.collect { state ->
                // NFC stays BACKGROUND while QR modal is open
                // qrMode switches to DISPLAY
                
                when (state.phase) {
                    UiPhase.TRANSFER -> {
                        // Transfer started, dismiss modal
                        dismiss()
                    }
                    UiPhase.TERMINAL -> {
                        // Show result and close
                        showResult(state)
                        dismiss()
                    }
                    else -> {
                        // Show QR code
                        renderQrCode(state)
                    }
                }
            }
        }
    }
    
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Close QR engagement when modal dismissed
        lifecycleScope.launch {
            manager.closeQrEngagement()
        }
    }
}
```

## Troubleshooting

### NFC Shows Wrong State

**Issue:** NFC shows as FOREGROUND when it should be BACKGROUND

**Solution:** Check that you're creating the correct engagement as active. Only one engagement can be active at a time.

```kotlin
// ✅ Correct - QR becomes active, NFC goes to background
manager.createEngagement(EngagementType.NFC, config)  // Background
manager.createEngagement(EngagementType.QR, config)   // Foreground (active)

// ❌ Wrong - manually trying to control state
// State is determined automatically by which engagement is active
```

### QR Mode Doesn't Update

**Issue:** `qrMode` doesn't switch from DISPLAY to SCAN

**Solution:** Ensure you're using `state.qrMode`, not manually tracking engagement types.

```kotlin
// Correct
when (state.qrMode) {
    QrMode.DISPLAY -> showQrCode()
    QrMode.SCAN -> showScanner()
    QrMode.NONE -> hideAll()
}

// Wrong - don't manually track
if (qrEngagementCreated) showQrCode()
```

### User Interaction Not Triggered

**Issue:** Consent dialog never appears

**Solution:** Check that you're handling `userInteractionRequired` in TRANSFER phase:

```kotlin
when (state.phase) {
    UiPhase.TRANSFER -> {
        if (state.userInteractionRequired) {
            showConsentDialog(state.deviceRequest!!)
        }
    }
    // ...
}
```

### Terminal State Shows Wrong Outcome

**Issue:** Success shows as error, or vice versa

**Solution:** Ensure you're checking `terminalOutcome`, not just `terminalMessage`:

```kotlin
// ✅ Correct
when (state.terminalOutcome) {
    TerminalOutcome.SUCCESS -> showSuccess()
    TerminalOutcome.ERROR -> showError()
}

// ❌ Wrong
if (state.terminalMessage?.contains("error") == true) {
    showError() // Message might not contain "error"
}
```

## Migration from Raw Events

If you're currently using raw engagement and retrieval events:

### Before (Raw Events)

```kotlin
// Complex coordination of multiple flows
launch {
    manager.eventHub.engagementEvents.collect { event ->
        when (event) {
            is MdocEngagementEvent.QrShow -> showQr()
            is MdocEngagementEvent.Connected -> hideQr()
            // ... many more cases
        }
    }
}

launch {
    manager.eventHub.transferEvents.collect { event ->
        when (event) {
            is MdocRetrievalEvent.DocumentsSelectionProcessStart -> showConsent()
            // ... many more cases
        }
    }
}
```

### After (UI Projection)

```kotlin
// Single state flow with automatic coordination
launch {
    manager.eventHub.sessionState.collect { state ->
        when (state.phase) {
            UiPhase.ENGAGEMENT -> handleEngagement(state)
            UiPhase.TRANSFER -> handleTransfer(state)
            UiPhase.TERMINAL -> handleTerminal(state)
        }
    }
}
```

## Best Practices

1. **Trust the State** - Don't try to replicate the projector's logic. Trust `showQr`, `userInteractionRequired`, etc.

2. **Use `substateLabel`** - Display it to users for real-time feedback about what's happening

3. **Check `terminalOutcome`** - Always use the enum, not string matching on messages

4. **Handle Suspension** - Always check `isSuspended` and show appropriate UI

5. **Decode Lazily** - Only decode `deviceRequest` when needed (when showing consent dialog)

6. **One Collector** - Collect `sessionState` once and route to different handlers based on phase

7. **Null Progress** - Use indeterminate progress when `progressHint == null`, determinate when present


## See Also

- [API_V3_IMPLEMENTATION_PLAN.md](./API_V3_IMPLEMENTATION_PLAN.md) - Overall V3 API design
- [TEST_IMPLEMENTATION_STATUS.md](./TEST_IMPLEMENTATION_STATUS.md) - Test coverage status
- MdocEventHub interface documentation
- SessionUiState.kt source code

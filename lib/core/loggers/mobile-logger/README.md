# Mobile Logger for Sure Identity Development Kit

This module provides a mobile-specific logging implementation designed for Android and iOS applications. The logger stores logs in memory with a circular buffer and provides easy
access for developers to view, filter, and export logs directly from their mobile applications.

## Features

### 🎯 **Core Features**

- **In-Memory Storage**: Logs are stored in a circular buffer for efficient memory usage
- **Platform-Specific Integration**:
    - **Android**: Logs to both in-memory storage and Android Logcat
    - **iOS**: Logs to both in-memory storage and iOS system log (NSLog)
- **Real-time Monitoring**: Monitor logs as they come in using Kotlin Flow
- **Multiple Export Formats**: Export logs as TEXT, JSON, or CSV
- **Advanced Filtering**: Filter logs by level, tag, time range, and content search

### 📱 **Mobile-Optimized**

- Circular buffer prevents memory leaks
- Configurable maximum log size
- Efficient querying and filtering
- Thread-safe operations
- Coroutines-based for smooth UI integration

## Usage

### Basic Setup

The mobile logger is automatically registered as an `LogService` when the module is included. It works alongside the existing logging system.

```kotlin
// The logger is automatically injected and available
// No additional setup required - it works with existing log calls:

logger.info("User logged in successfully")
logger.error("Network request failed", throwable = networkException)
logger.debug("Processing payment", tag = "PAYMENT")
```

### Accessing Stored Logs

Use the `IMobileLogManager` interface to access stored logs:

```kotlin
@Inject
class MyActivity(
    private val mobileLogManager: IMobileLogManager  // Inject the interface
) {
    
    suspend fun showLogs() {
        // Get all logs
        val allLogs = mobileLogManager.getAllLogs()
        
        // Get recent logs
        val recentLogs = mobileLogManager.getRecentLogs(50)
        
        // Get error logs only
        val errorLogs = mobileLogManager.getErrorLogs()
        
        // Display in your UI
        displayLogs(recentLogs)
    }
}
```

### Real-time Log Monitoring

Monitor logs in real-time for live debugging:

```kotlin
class LogViewerViewModel @Inject constructor(
    private val mobileLogManager: IMobileLogManager
) {
    
    fun startMonitoring() {
        viewModelScope.launch {
            mobileLogManager.logsFlow.collect { logs ->
                // Update UI with new logs
                _logsState.value = logs
                
                // Handle critical errors
                val latestLog = logs.lastOrNull()
                if (latestLog?.level == LogLevel.ERROR) {
                    showErrorNotification(latestLog.message)
                }
            }
        }
    }
}
```

### Filtering Logs

Filter logs by various criteria:

```kotlin
suspend fun filterLogs(mobileLogManager: IMobileLogManager) {
    val now = Clock.System.now()
    val oneHourAgo = now.minus(1.hours)
    
    // Filter by multiple criteria
    val filter = MobileLogFilter(
        level = LogLevel.ERROR,           // Only error logs
        tag = "NETWORK",                  // Only network-related logs
        fromTime = oneHourAgo,           // From last hour
        searchText = "timeout"           // Containing "timeout"
    )
    
    val filteredLogs = mobileLogManager.getFilteredLogs(filter)
}
```

### Exporting Logs

Export logs for sharing or debugging:

```kotlin
suspend fun exportLogs(mobileLogManager: IMobileLogManager) {
    // Export as text
    val textExport = mobileLogManager.exportLogsAsText()
    
    // Export with custom options
    val jsonExport = mobileLogManager.exportLogs(
        filter = MobileLogFilter(level = LogLevel.ERROR),
        options = MobileLogExportOptions(
            format = ExportFormat.JSON,
            includeStackTrace = true,
            includeTimestamps = true
        )
    )
    
    // Share via Android Intent or iOS share sheet
    shareText(jsonExport)
}
```

## Architecture

### Components

1. **MobileLogEntry**: Data class representing a stored log with timestamp and metadata
2. **MobileLogRepository**: Manages in-memory log storage with circular buffer
3. **IMobileLogManager**: Interface for accessing and managing logs
4. **MobileLogManager**: Implementation of the log manager interface
5. **AbstractMobileLogService**: Base implementation for mobile log services
6. **Platform-Specific Services**:
    - `AndroidMobileLogService`: Android-specific implementation with Logcat integration
    - `IOSMobileLogService`: iOS-specific implementation with NSLog integration

### Data Flow

```
Application Code
       ↓
LogService.info/error/debug/...
       ↓
AbstractMobileLogService.doExecute()
       ↓
MobileLogRepository.addLog()
       ↓
CircularLogBuffer (in-memory storage)
       ↓
Platform-specific logging (Logcat/NSLog)
```

### Dependency Injection

The mobile logger uses kotlin-inject with proper interface binding:

```kotlin
// Interface is injected
@Inject
class MyClass(
    private val logManager: IMobileLogManager  // ✅ Correct - inject interface
) {
    // Implementation is provided automatically by kotlin-inject
}

// DON'T do this - concrete class injection won't work
@Inject  
class MyClass(
    private val logManager: MobileLogManager  // ❌ Wrong - don't inject concrete class
)
```

## Configuration

### Setting Maximum Log Size

```kotlin
// Keep only the most recent 500 logs
mobileLogManager.setMaxLogSize(500)
```

### Managing Log Storage

```kotlin
// Get current log count
val count = mobileLogManager.getLogCount()

// Clear all logs
mobileLogManager.clearLogs()

// Get statistics
val stats = mobileLogManager.getLogStatistics()
```

## Android Integration Example

```kotlin
class MainActivity : AppCompatActivity() {
    
    @Inject
    lateinit var mobileLogManager: IMobileLogManager  // Use interface
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup log viewer
        setupLogViewer()
    }
    
    private fun setupLogViewer() {
        lifecycleScope.launch {
            mobileLogManager.logsFlow.collect { logs ->
                // Update RecyclerView with logs
                logAdapter.submitList(logs)
            }
        }
        
        // Export logs button
        exportButton.setOnClickListener {
            lifecycleScope.launch {
                val exportText = mobileLogManager.exportLogsAsText()
                shareText(exportText)
            }
        }
    }
    
    private fun shareText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Logs"))
    }
}
```

## iOS Integration Example

```swift
// SwiftUI example - inject the interface
struct LogViewerView: View {
    @StateObject private var viewModel: LogViewerViewModel
    
    init(logManager: IMobileLogManager) {  // Use interface
        self._viewModel = StateObject(wrappedValue: LogViewerViewModel(logManager: logManager))
    }
    
    var body: some View {
        NavigationView {
            List(viewModel.logs, id: \.timestamp) { log in
                LogEntryRow(log: log)
            }
            .navigationTitle("App Logs")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Export") {
                        viewModel.exportLogs()
                    }
                }
            }
        }
    }
}
```

## Best Practices

### 1. Dependency Injection

- **Always inject the interface** (`IMobileLogManager`), never the concrete class
- Use proper scoping annotations (`@SingleIn(AppScope::class)`)
- Follow kotlin-inject conventions for binding

### 2. Memory Management
- Set appropriate maximum log sizes based on your app's memory constraints
- Clear logs periodically in production builds if needed
- Monitor log growth in long-running applications

### 3. Performance
- Use filtering instead of retrieving all logs when possible
- Consider log levels - avoid excessive DEBUG logging in production
- Use the async logging interface for UI thread operations

### 4. Security
- Be cautious about logging sensitive information
- Consider log export permissions and user consent
- Implement log sanitization for sensitive data

### 5. Production Considerations
- Implement log level controls based on build configuration
- Consider automatic log clearing on app restart
- Implement crash reporting integration

## Dependencies

- `kotlinx-datetime`: For timestamp handling
- `kotlinx-coroutines-core`: For coroutine support
- `com.sphereon.core.api`: Core logging interfaces
- `kotlin-inject`: For dependency injection
- Platform-specific:
    - Android: Android SDK logging utilities
    - iOS: Foundation framework (NSLog)

## Thread Safety

All operations are thread-safe and can be called from any thread. The internal circular buffer uses mutex protection for concurrent access.

## Testing

The module includes comprehensive tests covering:

- Basic logging functionality
- Circular buffer behavior
- Filtering and searching
- Export functionality
- Thread safety

Run tests with:
```bash
./gradlew :libraries:core:loggers:lib-core-loggers-mobile-logger:check
```
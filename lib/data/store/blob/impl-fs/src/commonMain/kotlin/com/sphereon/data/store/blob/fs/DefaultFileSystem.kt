package com.sphereon.data.store.blob.fs

import okio.FileSystem

/**
 * Platform-specific default filesystem.
 * JVM: FileSystem.SYSTEM
 * JS/Native: throws UnsupportedOperationException (use explicit FileSystem parameter)
 */
internal expect fun defaultFileSystem(): FileSystem

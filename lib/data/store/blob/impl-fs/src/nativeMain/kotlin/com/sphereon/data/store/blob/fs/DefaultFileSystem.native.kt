package com.sphereon.data.store.blob.fs

import okio.FileSystem

internal actual fun defaultFileSystem(): FileSystem = FileSystem.SYSTEM

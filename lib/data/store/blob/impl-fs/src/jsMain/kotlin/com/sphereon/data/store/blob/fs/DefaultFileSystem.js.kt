package com.sphereon.data.store.blob.fs

import okio.FileSystem
import okio.NodeJsFileSystem

internal actual fun defaultFileSystem(): FileSystem = NodeJsFileSystem

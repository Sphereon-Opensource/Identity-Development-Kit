/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.data.store.blob.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.cas.ContentAddressDescriptor

interface BlobStorePutCommand : ServiceCommand<BlobPutInput, BlobDescriptor, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.store.put"
    }
}

interface BlobStoreGetCommand : ServiceCommand<BlobGetInput, BlobGetOutput, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.store.get"
    }
}

interface BlobStoreDeleteCommand : ServiceCommand<BlobDeleteInput, BlobDeleteOutput, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.store.delete"
    }
}

interface BlobStoreStatCommand : ServiceCommand<BlobStatInput, BlobDescriptor, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.store.stat"
    }
}

interface BlobStoreListCommand : ServiceCommand<BlobListInput, ListResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.store.list"
    }
}

interface BlobStoreCopyCommand : ServiceCommand<BlobCopyInput, BlobDescriptor, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.store.copy"
    }
}

interface BlobStoreMoveCommand : ServiceCommand<BlobMoveInput, BlobDescriptor, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.store.move"
    }
}

interface CasStoreCommand : ServiceCommand<CasStoreInput, ContentAddressDescriptor, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.cas.store"
    }
}

interface CasGetCommand : ServiceCommand<CasGetInput, BlobGetOutput, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.cas.get"
    }
}

interface CasVerifyCommand : ServiceCommand<CasVerifyInput, CasVerifyOutput, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "blob.cas.verify"
    }
}

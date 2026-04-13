/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.core

/**
 * Represents an exception that occurs within the Public Key Infrastructure (PKI) context.
 *
 * This exception is typically thrown to indicate errors encountered during key management operations.
 * It is used in scenarios where key store access fails, during key retrieval, or other cryptographic operations
 * that fail within the PKI context.
 *
 * @param message A descriptive message providing more details about the exception.
 */
class PKIException(
    message: String,
) : Exception(message)

/**
 * Exception thrown to indicate a failure during the signing process.
 *
 * @param message The detail message describing the reason for the exception.
 */
class SigningException(
    message: String,
) : Exception(message)

/**
 * Exception thrown when there is an error related to timestamp processing.
 *
 * @param message A descriptive message for the exception.
 * @param cause The underlying cause of the exception, if available.
 */
class TimestampException(
    message: String? = null,
    override val cause: Throwable? = null,
) : Exception(message, cause)

/**
 * SignClientException is a custom exception class used to signal issues specific
 * to client-side operations related to signing processes.
 *
 * @param message A descriptive message detailing the reason behind the exception.
 */
class SignClientException(
    message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

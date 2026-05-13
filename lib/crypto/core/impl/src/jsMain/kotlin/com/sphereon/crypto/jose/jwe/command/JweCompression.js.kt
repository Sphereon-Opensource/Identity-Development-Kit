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
 */

package com.sphereon.crypto.jose.jwe.command

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Kotlin/JS DEFLATE — WHATWG `CompressionStream` / `DecompressionStream` with format
 * `"deflate-raw"` (RFC 1951 raw DEFLATE, no zlib wrapper). Available in Node.js 18+ and
 * every evergreen browser.
 *
 * The API is async by nature (TransformStream + ReadableStream), so we wrap the JS Promise
 * via `kotlinx.coroutines.await()` and the suspend signature on the expect.
 */
internal actual suspend fun deflate(plaintext: ByteArray): ByteArray = transform(plaintext, compress = true)

internal actual suspend fun inflate(compressed: ByteArray): ByteArray = transform(compressed, compress = false)

private suspend fun transform(
    input: ByteArray,
    compress: Boolean
): ByteArray {
    val outputUint8 = runCompressionPipeline(input, if (compress) "CompressionStream" else "DecompressionStream").await()
    return uint8ArrayToByteArray(outputUint8)
}

/**
 * Build the JS pipeline `Response(Uint8Array → CompressionStream).arrayBuffer()` and return
 * the resulting Uint8Array. Implemented via a hand-written `js("...")` expression to dodge
 * the variability in DOM externals across kotlin.js / kotlinx-html stub jars — `Response`
 * and `CompressionStream` are universally available in the runtimes Kotlin/JS supports.
 */
@Suppress("UnusedReceiverParameter")
private fun runCompressionPipeline(
    input: ByteArray,
    ctorName: String
): Promise<dynamic> =
    js(
        """
        (function(arr, ctor){
            var Ctor = (typeof globalThis !== 'undefined' && globalThis[ctor]) || (typeof self !== 'undefined' && self[ctor]) || (typeof window !== 'undefined' && window[ctor]);
            if (typeof Ctor !== 'function') {
                return Promise.reject(new Error('WHATWG ' + ctor + ' is not available in this JS runtime'));
            }
            var bytes = new Uint8Array(arr.length);
            for (var i = 0; i < arr.length; i++) bytes[i] = arr[i] & 0xFF;
            var stream = new ReadableStream({
                start: function(controller){
                    controller.enqueue(bytes);
                    controller.close();
                }
            });
            var transformed = stream.pipeThrough(new Ctor('deflate-raw'));
            return new Response(transformed).arrayBuffer().then(function(buf){
                return new Uint8Array(buf);
            });
        })(input, ctorName)
        """,
    ) as Promise<dynamic>

private fun uint8ArrayToByteArray(uint8: dynamic): ByteArray {
    val length = (uint8.length as Int)
    val out = ByteArray(length)
    for (i in 0 until length) {
        out[i] = (uint8[i] as Int).toByte()
    }
    return out
}

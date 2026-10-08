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
 * Kotlin/Wasm DEFLATE — same WHATWG `CompressionStream` / `DecompressionStream` primitives
 * as Kotlin/JS, accessed through Kotlin/Wasm's JS interop. Format `"deflate-raw"` produces
 * RFC 1951 raw DEFLATE (no zlib wrapper) which is what RFC 7516 §4.1.3 mandates for `zip=DEF`.
 *
 * Available in Node.js 18+ and every evergreen browser that hosts a Wasm runtime, so the
 * deployment target is the same as Kotlin/Wasm itself.
 */
internal actual suspend fun deflate(plaintext: ByteArray): ByteArray {
    val resultArray = compressionPipeline(plaintext.toJsByteArray(), "deflate-raw", compress = true).await<JsArray<JsNumber>>()
    return resultArray.toByteArray()
}

internal actual suspend fun inflate(compressed: ByteArray): ByteArray {
    val resultArray = compressionPipeline(compressed.toJsByteArray(), "deflate-raw", compress = false).await<JsArray<JsNumber>>()
    return resultArray.toByteArray()
}

/**
 * Run the JS pipeline `Response(Uint8Array → CompressionStream).arrayBuffer()` from Wasm.
 * Returns a `JsArray<JsNumber>` (each element is a 0..255 byte) — the simplest interop type
 * that round-trips through Kotlin/Wasm without needing typed Uint8Array externals here.
 */
private fun compressionPipeline(
    input: JsArray<JsNumber>,
    format: String,
    compress: Boolean
): Promise<JsArray<JsNumber>> {
    val streamCtor = if (compress) "CompressionStream" else "DecompressionStream"
    return runCompressionPipelineJs(input, format, streamCtor)
}

@Suppress("UNUSED_PARAMETER")
private fun runCompressionPipelineJs(
    input: JsArray<JsNumber>,
    format: String,
    streamCtor: String
): Promise<JsArray<JsNumber>> =
    js(
        """
        (function(arr, fmt, ctorName){
            var Ctor = (typeof globalThis !== 'undefined' && globalThis[ctorName]) || (typeof self !== 'undefined' && self[ctorName]);
            if (typeof Ctor !== 'function') {
                return Promise.reject(new Error('WHATWG ' + ctorName + ' is not available in this Wasm host runtime'));
            }
            var bytes = new Uint8Array(arr.length);
            for (var i = 0; i < arr.length; i++) bytes[i] = arr[i] & 0xFF;
            var stream = new ReadableStream({
                start: function(controller){
                    controller.enqueue(bytes);
                    controller.close();
                }
            });
            var transformed = stream.pipeThrough(new Ctor(fmt));
            return new Response(transformed).arrayBuffer().then(function(buf){
                var out = new Uint8Array(buf);
                var jsArr = new Array(out.length);
                for (var j = 0; j < out.length; j++) jsArr[j] = out[j];
                return jsArr;
            });
        })(input, format, streamCtor)
        """,
    )

private fun ByteArray.toJsByteArray(): JsArray<JsNumber> {
    val arr = JsArray<JsNumber>()
    for (i in indices) {
        arr[i] = (this[i].toInt() and 0xFF).toJsNumber()
    }
    return arr
}

private fun JsArray<JsNumber>.toByteArray(): ByteArray {
    val out = ByteArray(this.length)
    for (i in 0 until this.length) {
        out[i] = (this[i]?.toInt() ?: 0).toByte()
    }
    return out
}

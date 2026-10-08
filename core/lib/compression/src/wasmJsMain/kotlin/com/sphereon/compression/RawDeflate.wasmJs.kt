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

package com.sphereon.compression

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Kotlin/Wasm raw DEFLATE — same WHATWG `CompressionStream` / `DecompressionStream` primitives
 * as Kotlin/JS, accessed through Wasm's JS interop. Format `"deflate-raw"` = RFC 1951.
 */
internal actual suspend fun rawDeflate(data: ByteArray): ByteArray {
    val result = compressionPipeline(data.toJsByteArray(), compress = true).await<JsArray<JsNumber>>()
    return result.toByteArray()
}

internal actual suspend fun rawInflate(data: ByteArray): ByteArray {
    val result = compressionPipeline(data.toJsByteArray(), compress = false).await<JsArray<JsNumber>>()
    return result.toByteArray()
}

private fun compressionPipeline(
    input: JsArray<JsNumber>,
    compress: Boolean,
): Promise<JsArray<JsNumber>> = runCompressionPipelineJs(input, if (compress) "CompressionStream" else "DecompressionStream")

@Suppress("UNUSED_PARAMETER")
private fun runCompressionPipelineJs(
    input: JsArray<JsNumber>,
    streamCtor: String,
): Promise<JsArray<JsNumber>> =
    js(
        """
        (function(arr, ctorName){
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
            var transformed = stream.pipeThrough(new Ctor('deflate-raw'));
            return new Response(transformed).arrayBuffer().then(function(buf){
                var out = new Uint8Array(buf);
                var jsArr = new Array(out.length);
                for (var j = 0; j < out.length; j++) jsArr[j] = out[j];
                return jsArr;
            });
        })(input, streamCtor)
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

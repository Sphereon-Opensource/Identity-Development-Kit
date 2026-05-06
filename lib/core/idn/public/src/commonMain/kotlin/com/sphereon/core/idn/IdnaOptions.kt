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

package com.sphereon.core.idn

/**
 * Options influencing the IDNA2008 / UTS 46 toAscii / toUnicode pipeline.
 */
data class IdnaOptions(
    /**
     * Reject input containing reserved or otherwise disallowed code points.
     * v1 implements a conservative subset; full UTS 46 IDNA mapping table
     * is out of scope but can be plugged in here later.
     */
    val strict: Boolean = true,
    /**
     * Reject domains whose total ASCII length exceeds 253 octets.
     * Set to false only for inputs known to be longer (rare).
     */
    val verifyDnsLength: Boolean = true,
)

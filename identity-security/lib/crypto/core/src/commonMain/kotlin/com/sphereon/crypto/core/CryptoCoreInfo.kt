/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.crypto.core

internal object CryptoCoreInfo {
    // Make sure we have something to keep the compiler happy
    const val MOVED =
        "This package has been replaced by com.sphereon.idk:lib-crypto-core-public and -impl. You can use this as a meta dependency on both, although -impl also already exposes -public as api. Use -public for public API and -impl for implementation details"
}

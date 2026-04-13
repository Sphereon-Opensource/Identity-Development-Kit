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

package com.sphereon.mdoc.transport.ble

/**
 * Internal marker object for the mdoc-transport-ble module.
 *
 * This module re-exports both the public API and implementation modules:
 * - lib-mdoc-transport-ble-public: Interfaces, data classes, and enums
 * - lib-mdoc-transport-ble-impl: Service implementations with KSP/DI annotations
 *
 * This object exists to ensure the module has at least one Kotlin source file,
 * which keeps the compiler happy on all platforms.
 */
internal object MdocTransportBle

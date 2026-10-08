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

package com.sphereon.mdoc

/**
 * Module marker for lib-mdoc-datatransfer wrapper.
 * This module re-exports lib-mdoc-datatransfer-public and lib-mdoc-datatransfer-impl.
 *
 * This is a thin wrapper module that exists only for backwards compatibility
 * and to simplify dependency management by providing a single dependency that
 * includes both the public API and implementation.
 */
object MdocDatatransferModule

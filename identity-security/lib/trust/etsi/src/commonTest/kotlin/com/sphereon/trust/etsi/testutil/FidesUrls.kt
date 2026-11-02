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

package com.sphereon.trust.etsi.testutil

const val FIDES_GITHUB_BASE = "https://raw.githubusercontent.com/FIDEScommunity/fides-trust-list/refs/heads/main"
const val FIDES_TL_URL = "$FIDES_GITHUB_BASE/FIDES-TL.xml"
const val FIDES_LOTL_URL = "$FIDES_GITHUB_BASE/FIDES-LOTL.xml"

/** The NL trust list location as the FIDES LOTL points to it. */
const val FIDES_LOTL_NL_POINTER_URL = "https://raw.githubusercontent.com/FIDEScommunity/fides-trust-list/main/FIDES-TL.xml"

/** Pinned copies of the FIDES trust lists, taken from FIDEScommunity/fides-trust-list main (827dfa82fd1f). */
val FIDES_FIXTURES: Map<String, String> =
    mapOf(
        FIDES_TL_URL to "fides/FIDES-TL.xml",
        FIDES_LOTL_URL to "fides/FIDES-LOTL.xml",
        FIDES_LOTL_NL_POINTER_URL to "fides/FIDES-TL.xml",
    )

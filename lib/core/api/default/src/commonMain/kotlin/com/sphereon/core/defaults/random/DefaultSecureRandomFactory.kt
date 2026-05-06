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

package com.sphereon.core.defaults.random

import com.sphereon.core.api.random.SecureRandom

/**
 * Convenience factory for a fully-wired default [SecureRandom] — handy for tests and
 * deployments that bootstrap without kotlin-inject.
 */
public fun defaultSecureRandom(): SecureRandom =
    SecureRandomImpl(
        generateTokenCommand = GenerateTokenCommandImpl(),
        nextBytesCommand = NextBytesCommandImpl(),
    )

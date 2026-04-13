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

package com.sphereon.libraries.all

/**
 * iOS-specific workaround for Order enum Comparable issue.
 *
 * This file exists to ensure the Order enum from com.sphereon.di package
 * is properly handled in iOS framework export. The issue arises because
 * Order implements Comparable<Int> which conflicts with the automatic
 * Comparable<Order> implementation that all enums get.
 *
 * The real fix should be in the Order.kt file itself, but this can serve
 * as a temporary workaround or reference.
 */

// Note: The actual fix needs to be applied in the source library
// libraries/core/api/public/src/commonMain/kotlin/com/sphereon/di/Order.kt
// by either:
// 1. Removing `: Comparable<Int>` from the enum declaration
// 2. Or using @HiddenFromObjC on the problematic compareTo method

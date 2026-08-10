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

package com.sphereon.conf.theme.core.model

/**
 * Whether this binding's [ElementBinding.value] variant matches the declaring [element]'s own
 * variant: an [AssetElementValue] for a [AssetDesignElement], and so on. A [ChoiceDesignElement]
 * additionally requires the bound value to be one of the element's declared allowed values.
 *
 * The single definition consumers reach for when they need to check a binding against its
 * element before persisting or resolving it.
 */
fun ElementBinding.carriesValueFor(element: DesignElement): Boolean =
    when (element) {
        is AssetDesignElement -> value is AssetElementValue
        is TextDesignElement -> value is TextElementValue
        is ChoiceDesignElement -> value is ChoiceElementValue && value.choice in element.allowedValues
        is ToggleDesignElement -> value is ToggleElementValue
    }

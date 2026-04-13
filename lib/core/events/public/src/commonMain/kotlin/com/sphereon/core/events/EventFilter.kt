/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import kotlinx.serialization.Serializable

/**
 * Filter for selecting events based on various criteria.
 *
 * EventFilter provides a type-safe way to match events for
 * subscriptions, queries, and routing. Filters are immutable
 * and composable.
 *
 * ## IDK Filtering (Simple)
 *
 * IDK provides basic filtering by type and subsystem:
 *
 * ```kotlin
 * val filter = eventFilter {
 *     types(EventTypes.COMMAND_COMPLETED, EventTypes.COMMAND_FAILED)
 *     subsystems(EventSubsystems.CRYPTO)
 *     categories(EventCategories.ERROR)
 * }
 * ```
 *
 * ## EDK Filtering (Rich)
 *
 * EDK extends with tenant, time range, and custom predicates
 * (see EDK's RichEventFilter).
 *
 * @see EventFilterBuilder for DSL construction
 */
@Serializable
data class EventFilter(
    /**
     * Match specific event types.
     * Empty set = match all types.
     */
    val types: Set<EventType> = emptySet(),

    /**
     * Match events whose type matches any of these glob patterns.
     * Patterns support * (any chars) and ? (single char).
     * Empty set = no pattern matching.
     */
    val typePatterns: Set<String> = emptySet(),

    /**
     * Match events from specific subsystems.
     * Empty set = match all subsystems.
     */
    val subsystems: Set<EventSubsystem> = emptySet(),

    /**
     * Match events whose subsystem matches any of these patterns.
     * Empty set = no pattern matching.
     */
    val subsystemPatterns: Set<String> = emptySet(),

    /**
     * Match events in specific categories.
     * Empty set = match all categories.
     */
    val categories: Set<EventCategory> = emptySet(),

    /**
     * Match events with specific origins (command IDs).
     * Empty set = match all origins.
     */
    val origins: Set<String> = emptySet(),

    /**
     * Match events whose origin matches any of these patterns.
     * Empty set = no pattern matching.
     */
    val originPatterns: Set<String> = emptySet(),

    /**
     * Internal flag for NONE filter.
     * When true, matches() always returns false.
     */
    private val matchNone: Boolean = false
) {
    /**
     * Check if an event matches this filter.
     *
     * An event matches if it satisfies ALL specified criteria.
     * Empty criteria = match all events for that dimension.
     */
    fun matches(event: Event): Boolean {
        // NONE filter never matches
        if (matchNone) return false

        // Type matching
        if (types.isNotEmpty() && event.type !in types) {
            if (typePatterns.isEmpty() || !typePatterns.any { event.type.matches(it) }) {
                return false
            }
        }
        if (typePatterns.isNotEmpty() && types.isEmpty()) {
            if (!typePatterns.any { event.type.matches(it) }) {
                return false
            }
        }

        // Subsystem matching
        if (subsystems.isNotEmpty() && event.subsystem !in subsystems) {
            if (subsystemPatterns.isEmpty() || !subsystemPatterns.any { event.subsystem.matches(it) }) {
                return false
            }
        }
        if (subsystemPatterns.isNotEmpty() && subsystems.isEmpty()) {
            if (!subsystemPatterns.any { event.subsystem.matches(it) }) {
                return false
            }
        }

        // Category matching
        if (categories.isNotEmpty() && event.category !in categories) {
            return false
        }

        // Origin matching
        if (origins.isNotEmpty() && event.origin !in origins) {
            if (originPatterns.isEmpty() || !originPatterns.any { matchGlob(event.origin, it) }) {
                return false
            }
        }
        if (originPatterns.isNotEmpty() && origins.isEmpty()) {
            if (!originPatterns.any { matchGlob(event.origin, it) }) {
                return false
            }
        }

        return true
    }

    /**
     * Combine this filter with another using AND logic.
     */
    fun and(other: EventFilter): EventFilter = EventFilter(
        types = types + other.types,
        typePatterns = typePatterns + other.typePatterns,
        subsystems = subsystems + other.subsystems,
        subsystemPatterns = subsystemPatterns + other.subsystemPatterns,
        categories = categories + other.categories,
        origins = origins + other.origins,
        originPatterns = originPatterns + other.originPatterns
    )

    companion object {
        /**
         * Filter that matches all events.
         */
        val ALL = EventFilter()

        /**
         * Filter that matches no events.
         */
        val NONE = EventFilter(matchNone = true)

        /**
         * Create a filter for specific event types.
         */
        fun forTypes(types: List<EventType>): EventFilter =
            EventFilter(types = types.toSet())

        /**
         * Create a filter for specific event types.
         */
        fun forTypes(types: Set<EventType>): EventFilter =
            EventFilter(types = types)

        /**
         * Create a filter for specific subsystems.
         */
        fun forSubsystems(subsystems: List<EventSubsystem>): EventFilter =
            EventFilter(subsystems = subsystems.toSet())

        /**
         * Create a filter for specific subsystems.
         */
        fun forSubsystems(subsystems: Set<EventSubsystem>): EventFilter =
            EventFilter(subsystems = subsystems)

        /**
         * Create a filter for specific categories.
         */
        fun forCategories(categories: List<EventCategory>): EventFilter =
            EventFilter(categories = categories.toSet())

        /**
         * Create a filter for specific categories.
         */
        fun forCategories(categories: Set<EventCategory>): EventFilter =
            EventFilter(categories = categories)

        /**
         * Create a filter for type patterns.
         */
        fun forTypePatterns(vararg patterns: String): EventFilter =
            EventFilter(typePatterns = patterns.toSet())
    }
}


/**
 * DSL builder for constructing EventFilter instances.
 */
class EventFilterBuilder {
    private val types = mutableSetOf<EventType>()
    private val typePatterns = mutableSetOf<String>()
    private val subsystems = mutableSetOf<EventSubsystem>()
    private val subsystemPatterns = mutableSetOf<String>()
    private val categories = mutableSetOf<EventCategory>()
    private val origins = mutableSetOf<String>()
    private val originPatterns = mutableSetOf<String>()

    /**
     * Match specific event types.
     */
    fun types(types: List<EventType>) {
        this.types.addAll(types)
    }

    /**
     * Match a single event type.
     */
    fun type(type: EventType) {
        this.types.add(type)
    }

    /**
     * Match event types by pattern.
     */
    fun typePatterns(vararg patterns: String) {
        this.typePatterns.addAll(patterns)
    }

    /**
     * Match specific subsystems.
     */
    fun subsystems(subsystems: List<EventSubsystem>) {
        this.subsystems.addAll(subsystems)
    }

    /**
     * Match a single subsystem.
     */
    fun subsystem(subsystem: EventSubsystem) {
        this.subsystems.add(subsystem)
    }

    /**
     * Match subsystems by pattern.
     */
    fun subsystemPatterns(vararg patterns: String) {
        this.subsystemPatterns.addAll(patterns)
    }

    /**
     * Match specific categories.
     */
    fun categories(categories: List<EventCategory>) {
        this.categories.addAll(categories)
    }

    /**
     * Match a single category.
     */
    fun category(category: EventCategory) {
        this.categories.add(category)
    }

    /**
     * Match specific origins (command IDs).
     */
    fun origins(vararg origins: String) {
        this.origins.addAll(origins)
    }

    /**
     * Match origins by pattern.
     */
    fun originPatterns(vararg patterns: String) {
        this.originPatterns.addAll(patterns)
    }

    /**
     * Build the filter.
     */
    fun build(): EventFilter = EventFilter(
        types = types.toSet(),
        typePatterns = typePatterns.toSet(),
        subsystems = subsystems.toSet(),
        subsystemPatterns = subsystemPatterns.toSet(),
        categories = categories.toSet(),
        origins = origins.toSet(),
        originPatterns = originPatterns.toSet()
    )
}

/**
 * Create an EventFilter using DSL.
 */
fun eventFilter(builder: EventFilterBuilder.() -> Unit): EventFilter =
    EventFilterBuilder().apply(builder).build()

/**
 * Simple glob pattern matching.
 * Supports:
 * - * matches any sequence of characters
 * - ** matches any sequence including path separators
 * - ? matches any single character
 */
internal fun matchGlob(input: String, pattern: String): Boolean {
    // Convert glob pattern to regex
    val regex = buildString {
        append("^")
        var i = 0
        while (i < pattern.length) {
            when {
                pattern[i] == '*' && i + 1 < pattern.length && pattern[i + 1] == '*' -> {
                    append(".*")
                    i += 2
                }
                pattern[i] == '*' -> {
                    append("[^.]*")
                    i++
                }
                pattern[i] == '?' -> {
                    append(".")
                    i++
                }
                pattern[i] in "[](){}^$|+\\" -> {
                    append("\\")
                    append(pattern[i])
                    i++
                }
                else -> {
                    append(pattern[i])
                    i++
                }
            }
        }
        append("$")
    }
    return Regex(regex).matches(input)
}

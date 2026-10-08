package com.sphereon.example.graalvm

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

/**
 * Session graph for GraalVM example.
 *
 * This graph is session scoped and gets created for each request/session.
 * It's merged with all contributions to SessionScope.
 */
@DependencyGraph(SessionScope::class)
abstract class GraalVMExampleSessionGraph(
    val appGraph: GraalVMExampleAppGraph,
    val contextGraph: GraalVMExampleUserContextGraph
)
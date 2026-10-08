package com.sphereon.example.graalvm

import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

/**
 * User context graph for GraalVM example.
 *
 * This graph is tenant+principal scoped and gets created for each unique
 * tenant/principal combination. It's merged with all contributions to UserScope.
 */
@DependencyGraph(UserScope::class)
abstract class GraalVMExampleUserContextGraph(
    val appGraph: GraalVMExampleAppGraph
)
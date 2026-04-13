package com.sphereon.example.graalvm

import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

/**
 * User context component for GraalVM example.
 *
 * This component is tenant+principal scoped and gets created for each unique
 * tenant/principal combination. It's merged with all contributions to UserScope.
 */
@DependencyGraph(UserScope::class)
abstract class GraalVMExampleUserContextComponent(
    val appComponent: GraalVMExampleAppComponent
)
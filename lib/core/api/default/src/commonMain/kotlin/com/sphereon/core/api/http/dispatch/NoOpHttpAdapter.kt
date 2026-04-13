package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.httpRoutes
import dev.zacsweers.metro.Named
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class NoOpHttpAdapter(
) : RoutedHttpAdapter() {

    companion object {
        const val ID = "__noop__"
    }

    override val id: String = ID

    override val mount: HttpAdapterMount = HttpAdapterMount(
        serverPrefix = "",
        adapterBasePath = ""
    )

    // Routes are relative to the mount (adapterBasePath = /keys)
    // The mount prefix is automatically prepended when matching requests
    override val routes = httpRoutes {
    }
}

/* Copyright 2026 Sphereon International B.V. */
package com.sphereon.mdoc.testutil

import com.sphereon.di.app.AppGraph

/** Construct the graph in this module; another module's test outputs are not dependencies. */
internal expect fun createMdocCryptoTestAppGraph(application: Any): AppGraph

package com.sphereon.trust.etsi.testutil

internal object TestResourceConfig {
    const val RESOURCE_PATH = "./src/commonTest/resources"
}

expect fun readTestResource(path: String): String

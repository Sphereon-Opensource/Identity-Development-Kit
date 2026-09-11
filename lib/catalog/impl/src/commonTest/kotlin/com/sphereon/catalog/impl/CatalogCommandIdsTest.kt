/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.command.CreateCatalogCommand
import com.sphereon.catalog.command.CreateSchemaCommand
import com.sphereon.catalog.command.DeleteSchemaCommand
import com.sphereon.catalog.command.DisableCatalogCommand
import com.sphereon.catalog.command.EvaluateCatalogVerificationCommand
import com.sphereon.catalog.command.GetCatalogCommand
import com.sphereon.catalog.command.GetCatalogTypeViewCommand
import com.sphereon.catalog.command.GetSchemaCommand
import com.sphereon.catalog.command.GetSchemaFormatCommand
import com.sphereon.catalog.command.GetSchemaRulebookCommand
import com.sphereon.catalog.command.ImportRemoteCatalogCommand
import com.sphereon.catalog.command.ImportRulebooksCommand
import com.sphereon.catalog.command.LinkSchemaCommand
import com.sphereon.catalog.command.ListCatalogsCommand
import com.sphereon.catalog.command.ListSchemasCommand
import com.sphereon.catalog.command.PublishCatalogCommand
import com.sphereon.catalog.command.ResolveAttestationTypeCommand
import com.sphereon.catalog.command.UpdateCatalogCommand
import com.sphereon.catalog.command.UpdateSchemaCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogCommandIdsTest {
    @Test
    fun commandIdsHaveThreeSegments() {
        val ids =
            listOf(
                ListCatalogsCommand.COMMAND_ID,
                CreateCatalogCommand.COMMAND_ID,
                GetCatalogCommand.COMMAND_ID,
                UpdateCatalogCommand.COMMAND_ID,
                PublishCatalogCommand.COMMAND_ID,
                DisableCatalogCommand.COMMAND_ID,
                ListSchemasCommand.COMMAND_ID,
                GetSchemaCommand.COMMAND_ID,
                GetCatalogTypeViewCommand.COMMAND_ID,
                CreateSchemaCommand.COMMAND_ID,
                UpdateSchemaCommand.COMMAND_ID,
                DeleteSchemaCommand.COMMAND_ID,
                LinkSchemaCommand.COMMAND_ID,
                GetSchemaFormatCommand.COMMAND_ID,
                GetSchemaRulebookCommand.COMMAND_ID,
                ImportRemoteCatalogCommand.COMMAND_ID,
                ImportRulebooksCommand.COMMAND_ID,
                ResolveAttestationTypeCommand.COMMAND_ID,
                EvaluateCatalogVerificationCommand.COMMAND_ID,
            )
        ids.forEach { id ->
            assertEquals(2, id.count { it == '.' }, id)
            assertTrue(id.matches(Regex("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){2}$")), id)
        }
    }
}

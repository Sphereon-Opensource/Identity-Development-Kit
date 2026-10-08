package com.sphereon.data.store.blob.kv

import com.sphereon.core.api.json.SerializerRegistration
import software.amazon.app.platform.scope.Scope
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class KvBlobStoreScopedProjectionTest {
    private val module = object : KvBlobStoreSerializationRegistrationModule {}

    @Test
    fun scopedProjectionRetainsTheCanonicalSerializerWithoutEnteringScope() {
        val canonical = KvBlobStoreSerializationRegistration()
        val unrelated =
            object : SerializerRegistration {
                override fun onEnterScope(scope: Scope) = error("Projection must not register serializers")
            }
        val registrations = setOf<SerializerRegistration>(unrelated, canonical)
        assertSame(canonical, module.provideScoped(registrations))
        assertSame(canonical, module.provideScoped(registrations))
    }

    @Test
    fun missingCanonicalSerializerFailsClosed() {
        assertFailsWith<NoSuchElementException> { module.provideScoped(emptySet()) }
    }

    @Test
    fun duplicateCanonicalSerializersFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            module.provideScoped(setOf(KvBlobStoreSerializationRegistration(), KvBlobStoreSerializationRegistration()))
        }
    }
}

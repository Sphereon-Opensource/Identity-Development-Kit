package com.sphereon.data.store.blob.http

import com.sphereon.core.api.json.SerializerRegistration
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreJsonSupport
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.binding
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.Scoped
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
/**
 * Automatic JSON serialization registration for HTTP blob service client config.
 *
 * Discovered and initialized when the AppScope is created via DI multibinding.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<SerializerRegistration>())
@SingleIn(AppScope::class)
class HttpBlobSerializationRegistration : SerializerRegistration {
    override fun onEnterScope(scope: Scope) {
        BlobStoreJsonSupport.register("http-blob-store") {
            polymorphic(BlobStoreConfigBase::class) {
                subclass(HttpBlobServiceClientConfig::class, HttpBlobServiceClientConfig.serializer())
            }
        }
    }
}

@ContributesTo(AppScope::class)
interface HttpBlobSerializationRegistrationModule {
    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun provideScoped(impl: HttpBlobSerializationRegistration): Scoped = impl
}

package com.sphereon.data.store.blob.okd

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
 * Automatic JSON serialization registration for OKD blob store config.
 *
 * Discovered and initialized when the AppScope is created via DI multibinding.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<SerializerRegistration>())
@SingleIn(AppScope::class)
class OkdBlobStoreSerializationRegistration : SerializerRegistration {
    override fun onEnterScope(scope: Scope) {
        BlobStoreJsonSupport.register("okd-blob-store") {
            polymorphic(BlobStoreConfigBase::class) {
                subclass(OkdBlobStoreConfig::class, OkdBlobStoreConfig.serializer())
            }
        }
    }
}

@ContributesTo(AppScope::class)
interface OkdBlobStoreSerializationRegistrationModule {
    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun provideScoped(impl: OkdBlobStoreSerializationRegistration): Scoped = impl
}

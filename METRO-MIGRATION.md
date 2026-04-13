# Metro Migration Guide — Key Findings & Patterns

## Critical Build Configuration

### atomicfu Compatibility
The atomicfu **bytecode transformer** breaks Metro's cross-module visibility. All contributed bindings appear as "internal to its module".

**Fix:** Add to `gradle.properties`:
```properties
kotlinx.atomicfu.enableJvmIrTransformation=true
```
This switches atomicfu to the IR compiler plugin mode which preserves normal module metadata.

### Language Version
Metro requires Kotlin language version ≥ 2.2. Set in `gradle.properties`:
```properties
kotlinLanguageVersion=2.2
```
Also update the conventions plugin (`ConventionsPlugin.kt`) to set `KOTLIN_2_2` instead of `KOTLIN_2_0`.

### Metro Plugin Configuration
```kotlin
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.metro)
}
metro {
    enableKotlinVersionCompatibilityChecks.set(false)
}
```

### App Platform Libraries
Replace kotlin-inject libraries with Metro equivalents:

| Old | New |
|---|---|
| `kotlin-inject-public` | `metro-public` |
| `kotlin-inject-impl` | `metro-impl` |
| `addKotlinInjectComponent` | `addMetroDependencyGraph` |
| `CoroutineDispatcherComponent` | `CoroutineDispatcherGraph` |
| `AppScopeCoroutineScopeComponent` | `AppScopeCoroutineScopeGraph` |

---

## DependencyGraph Patterns

### No Constructor Parameters
`@DependencyGraph` classes **cannot** have constructor parameters. Use `@DependencyGraph.Factory`:

```kotlin
// WRONG — will not compile
@DependencyGraph(AppScope::class)
abstract class MyGraph(val app: Any) : ...

// CORRECT
@DependencyGraph(AppScope::class)
abstract class MyGraph : AbstractAppComponent() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): MyGraph
    }
}
```

### Abstract Base Classes
If extending an abstract class (like `AbstractAppComponent`), the base class must:
- Have **no constructor parameters** (use abstract properties instead)
- Mark concrete methods as `open` if Metro's generated `Impl` may override them
- NOT put `@get:Provides` on abstract properties — the factory `@Provides` params provide the values
- NOT directly extend `@ContributesTo` interfaces (like `CoroutineDispatcherGraph`) — let Metro merge them via contributions

### Instantiation
```kotlin
val component = createGraphFactory<MyGraph.Factory>().create(
    application = app,
    appId = "my-app",
    rootScopeProvider = DefaultRootScopeProvider()
)
```

### Companion Objects
`@DependencyGraph` abstract classes cannot have companion objects with factory methods. Metro's generated `Impl` conflicts with them. Move factory functions to top-level:

```kotlin
// WRONG — companion object causes "not abstract and does not implement" error
@DependencyGraph(AppScope::class)
abstract class MyGraph : ... {
    companion object {
        fun init(): MyGraph = ...
    }
}

// CORRECT — top-level function
fun createMyGraph(): MyGraph = createGraphFactory<MyGraph.Factory>().create(...)
```

---

## Scope Hierarchy

### Single Root DependencyGraph
Use **one** `@DependencyGraph(AppScope::class)` as the root. Child scopes are accessed via `@GraphExtension.Factory` cast from the root graph. Do NOT create separate `@DependencyGraph` per child scope with `@Includes` — nested `@GraphExtension` inside `@Includes` child graphs cannot access grandparent bindings.

```kotlin
// Access child scope from root graph:
val appGraph = createGraphFactory<AppGraph.Factory>().create(...)
val userGraph = (appGraph as UserContextComponent.Factory).createUserContext(userContext)
val sessionGraph = (userGraph as SessionComponent.Factory).createSessionComponent(sessionId)
```

### @GraphExtension Pattern
```kotlin
@SingleIn(UserScope::class)
@GraphExtension(UserScope::class)
interface UserContextComponent {
    // Accessors only — no @Provides methods here
    val instance: UserContextInstance
    val userContext: UserContext

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    interface Factory {
        fun createUserContext(@Provides userContext: UserContext): UserContextComponent
    }
}
```

### @Provides in Contributed Interfaces, NOT on @GraphExtension
Metro does NOT process `@Provides` methods on `@GraphExtension` interfaces for cross-module binding resolution. Move them to separate `@ContributesTo` interfaces:

```kotlin
// WRONG — @Provides on @GraphExtension won't be found cross-module
@GraphExtension(UserScope::class)
interface UserContextComponent {
    @Provides fun provideCoroutineScope(): CoroutineScope = ...  // won't work
}

// CORRECT — separate @ContributesTo interface
@ContributesTo(UserScope::class)
interface UserContextScopedProviders {
    @Provides
    @ForScope(UserScope::class)
    fun provideCoroutineScope(...): CoroutineScope = ...
}
```

### Method Name Uniqueness
`@Provides` methods across different `@ContributesTo` interfaces that merge into the same scope MUST have unique names. If two interfaces both have `provideEmptyScoped()`, the graph class will fail with "must override ... because it inherits multiple interface methods".

---

## Annotation Migration

### @ContributesBinding
- `boundType = X::class` → `binding = binding<X>()`
- `multibinding = true` → use `@ContributesIntoSet` instead
- `@Named` on a class with `@ContributesIntoSet` applies the qualifier to the **set** binding — remove `@Named` from `@ContributesIntoSet` classes
- Classes with multiple supertypes MUST specify explicit `binding = binding<TargetType>()`

### @Assisted / @AssistedFactory
- Classes with `@Assisted` constructor parameters need `@AssistedInject` instead of `@Inject`
- `@AssistedFactory` must be an **interface** (not a class)

### @MergeComponent → @DependencyGraph
- `@SingleIn` is redundant with `@DependencyGraph(scope)` (implicit)
- Remove `*Merged` superinterfaces — Metro merges at compile time
- Remove `@Component` annotations entirely

### @ContributesSubcomponent → @GraphExtension
```kotlin
// OLD
@ContributesSubcomponent(ChildScope::class)
interface Child {
    @ContributesSubcomponent.Factory(ParentScope::class)
    interface Factory { fun create(arg: Arg): Child }
}

// NEW
@GraphExtension(ChildScope::class)
interface Child {
    @ContributesTo(ParentScope::class)
    @GraphExtension.Factory
    interface Factory { fun create(@Provides arg: Arg): Child }
}
```

### Custom @Named
Delete custom `@Named` annotation. Use `dev.zacsweers.metro.Named` (positional `@Named("x")` syntax works for both).

---

## Set Bindings and @ForScope

### @ContributesIntoSet does NOT work with @ForScope-annotated sets

**Critical finding:** `@ContributesIntoSet` contributions are NOT collected into `@ForScope`-annotated `Set<T>` multibindings. This affects all `Scoped` lifecycle registrations.

`@ContributesIntoSet` works correctly for regular (non-`@ForScope`) sets like `Set<KmsProviderFactory>`, `Set<DidResolver>`, etc. But the Amazon App Platform's `Set<Scoped>` uses `@ForScope(AppScope::class)` for scope lifecycle management, and `@ContributesIntoSet` contributions are silently ignored.

**Broken pattern:**
```kotlin
// This does NOT contribute to @ForScope-annotated Set<Scoped>
@ContributesIntoSet(AppScope::class)
@Inject
class MyRegistration : SerializerRegistration { ... }
```

**Working pattern — use @ContributesTo with @Provides @IntoSet @ForScope:**
```kotlin
@Inject
@SingleIn(AppScope::class)
class MyRegistration : SerializerRegistration { ... }

@ContributesTo(AppScope::class)
interface MyRegistrationModule {
    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun provideScoped(impl: MyRegistration): Scoped = impl
}
```

### When to use which pattern

| Set declaration | Use | Notes |
|---|---|---|
| `Set<T>` (constructor param) | `@ContributesIntoSet(Scope::class)` | Works, Metro native |
| `@ForScope(Scope::class) Set<Scoped>` | `@ContributesTo` + `@Provides @IntoSet @ForScope` | Required for App Platform scope lifecycle |
| `Set<T>` with `@ForScope` | `@ContributesTo` + `@Provides @IntoSet @ForScope` | Same as above |

### @ContributesIntoSet requires explicit binding for non-direct supertypes

When a class implements an intermediate interface (e.g., `SoftwareKmsProviderFactoryImpl : SoftwareKmsProviderFactory` where `SoftwareKmsProviderFactory : KmsProviderFactory`), `@ContributesIntoSet` implicitly binds to the **direct supertype** (`SoftwareKmsProviderFactory`), NOT the ancestor type (`KmsProviderFactory`). If the consumer expects `Set<KmsProviderFactory>`, use explicit binding:

```kotlin
// WRONG - contributes to Set<SoftwareKmsProviderFactory>, not Set<KmsProviderFactory>
@ContributesIntoSet(AppScope::class)
class SoftwareKmsProviderFactoryImpl : SoftwareKmsProviderFactory { ... }

// CORRECT - explicitly binds to Set<KmsProviderFactory>
@ContributesIntoSet(AppScope::class, binding = binding<KmsProviderFactory>())
class SoftwareKmsProviderFactoryImpl : SoftwareKmsProviderFactory { ... }
```

### Affected classes in IDK

All `SerializerRegistration` implementations need the `@ContributesTo` module pattern because they implement `Scoped` and must register with the scope lifecycle to trigger `onEnterScope()`:
- `SoftwareKmsSerializationRegistration`
- `MobileKmsSerializationRegistration`
- `MemoryKeystoreSerializationRegistration`
- `SoftwareKeystoreSerializationRegistration`
- `HttpBlobSerializationRegistration`
- `KvBlobStoreSerializationRegistration`
- `OkdBlobStoreSerializationRegistration`

---

## Common Pitfalls

1. **Stale Kotlin caches**: Delete `.gradle/` directory (not just `build/`) when debugging cross-module issues. `--rerun-tasks` and `--no-build-cache` are NOT sufficient — Kotlin's incremental compilation cache persists in `.gradle/`.

2. **@Provides on abstract properties**: Metro requires `@Provides` to have bodies. Abstract properties cannot be `@get:Provides`.

3. **@Provides on non-DI classes**: `@Provides` methods are only processed on `@ContributesTo` interfaces and `@DependencyGraph` classes. Methods on plain abstract classes are ignored.

4. **Generic types in binding**: `binding<IPropertyValueConversion>()` must include type params: `binding<IPropertyValueConversion<*>>()`.

5. **CoroutineDispatcher providers**: The App Platform's `CoroutineDispatcherGraph` interface default methods (`@DefaultCoroutineDispatcher`, `@MainCoroutineDispatcher`) may not resolve cross-module on JS/wasmJs. Provide them locally via a `@ContributesTo(AppScope::class)` interface with `replaces = [CoroutineDispatcherGraph::class, AppScopeCoroutineScopeGraph::class]`.

6. **`@AssistedInject` function types**: In kotlin-inject, `@AssistedInject` classes auto-generated function-type providers (`(Arg1, Arg2) -> Result`). In Metro, you must declare an explicit `@AssistedFactory` interface. If a class injects a function type like `(Config, Storage?) -> Service`, add an `@AssistedFactory` interface.

7. **Multi-supertype `@ContributesBinding`**: If a class implements multiple interfaces and only one `@ContributesBinding` is declared, Metro won't auto-bind the other supertypes. Add explicit `@ContributesBinding` for each interface that needs injection (e.g., both `MultiIdentifierResolutionService` AND `IdentifierService`).

8. **JS incremental compilation**: Metro requires `kotlin.incremental.js=false` and `kotlin.incremental.js.klib=false` in `gradle.properties`.

9. **Nested `@GraphExtension` + `@Includes` limitation**: A `@GraphExtension` nested inside a child `@DependencyGraph` (that uses `@Includes` for parent) cannot access grandparent scope bindings. Use a single root `@DependencyGraph(AppScope::class)` instead of separate per-scope `@DependencyGraph` classes.

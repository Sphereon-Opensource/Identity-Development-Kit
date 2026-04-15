# Module lib-data-store-kv-public

Public key-value store contracts used across IDK for session state, caches, and lightweight persistence. It defines the `KvStore` surface, its factory and namespace model, and the codec abstractions (including a kotlinx.serialization JSON codec) so consumer code can stay agnostic of the underlying storage technology.

Depend on this module from any library code that wants a small persistence surface without committing to a backend. A concrete backing (memory, Kottage, etc.) still has to be on the classpath to actually store anything.

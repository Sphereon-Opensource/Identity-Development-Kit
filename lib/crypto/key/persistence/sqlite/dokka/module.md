# Module lib-crypto-key-persistence-sqlite

SQLDelight-backed durable implementation of the `KeyReferenceStore` contract from `lib-crypto-key-persistence-api`. Reach for this module when you need key references to survive process restarts on a platform that can host a SQLite database (JVM, mobile, native). For ephemeral or test setups the no-op store from the `-api` module is often sufficient.
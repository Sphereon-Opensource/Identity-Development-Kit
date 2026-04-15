# Module lib-conf-settings

Property-source adapter that plugs the `multiplatform-settings` library into the IDK configuration system. It lets configuration values flow in from platform-native key-value stores: NSUserDefaults on iOS, SharedPreferences on Android, the Windows Registry, or an in-memory store for tests. Reach for it when an app needs user-preference or device-local configuration to participate in the same layered property hierarchy as YAML and environment sources.

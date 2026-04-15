# Module lib-conf-yaml

YAML property-source contributions for the IDK configuration system. It adds YAML loading at each tier of IDK's layered property hierarchy (App, Tenant, and Principal), so the same configuration machinery that merges environment and platform-native sources can also pull values from YAML files. Reach for it when you want file-based configuration without introducing a separate loader.

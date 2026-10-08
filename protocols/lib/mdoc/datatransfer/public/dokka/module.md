# Module lib-mdoc-datatransfer-public

Public API for mdoc device engagement and data transfer. It defines the engagement-factory surface, the engagement-instance lifecycle, configuration validation, the presets used to pre-pick common transport combinations, and the event adapter downstream code subscribes to.

Depend on this module from wallet or reader code that has to drive an mdoc exchange without committing to a specific transport. Runtime implementations live in `lib-mdoc-datatransfer-impl`; the transports themselves live under `lib-mdoc-transport-*`.

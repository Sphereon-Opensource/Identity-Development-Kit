# Module lib-core-events-public

Public event model for the IDK event hub: the typed `Event` surface, its context envelope, the application event service contract, and the configuration hooks used by commands and services to publish and observe events in a uniform way. It also defines the encryption hook for sensitive event payloads, so downstream services can enforce confidentiality policy at the hub boundary.

Depend on this module whenever library code needs to emit or react to events without committing to a concrete hub. Runtime wiring lives in `lib-core-events-impl`.

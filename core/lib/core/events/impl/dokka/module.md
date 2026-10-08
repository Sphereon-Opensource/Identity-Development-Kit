# Module lib-core-events-impl

Runtime for the typed event hub declared in `lib-core-events-public`. It realises the application event service, the in-process hub, the default event builder, and the command-execution extension that lets commands emit lifecycle events without the command author wiring anything by hand.

Pull this in alongside the `-public` module wherever code actually needs to publish or consume events, not just describe their shape.

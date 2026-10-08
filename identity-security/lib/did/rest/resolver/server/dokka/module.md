# Module lib-did-rest-resolver-server

HTTP front-end that exposes the IDK resolver registry over the Universal Resolver contract. Pull it into a deployable Ktor server when you want other systems to consume IDK's resolver stack as a standard Universal Resolver endpoint rather than as an in-process library.

It is a server-side component; no equivalent client lives here (clients just talk HTTP).

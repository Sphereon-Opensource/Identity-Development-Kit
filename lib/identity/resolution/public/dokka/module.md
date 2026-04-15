# Module lib-identity-resolution-public

Public identity-resolution API. It defines the resolver surface, the command bindings, and the error model callers use to resolve an input subject into a matched identity without binding to a particular matching backend. Sits one level above `lib-identity-matching-public`: matching asks "do these refer to the same person", resolution asks "given this input, which identity record is it".

Runtime implementations live in `lib-identity-resolution-impl`.

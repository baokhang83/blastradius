# Design: helloworld

## Shape

`HelloMojo` is an independent Maven plugin entry point. Maven discovers it from its `@Mojo(name
= "hello")` annotation, invokes `execute()`, and the Mojo writes a fixed greeting through Maven's
standard logger.

The goal deliberately has no lifecycle binding, parameters, index access, or connection to
`SelectMojo`; it is a runnable plugin smoke test, not a new selection mode.

## Decision

Keep the greeting as a dedicated `blastradius:hello` goal rather than branching inside
`blastradius:select`. This retains the existing selection contract and keeps the direct-invocation
behavior easy to discover in Maven's generated plugin descriptor.

## Constitution check

The change follows §II (Clean Code & Simplicity): one focused class is sufficient, with no
speculative abstraction or selection-pipeline change.

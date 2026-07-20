# Session: Track JDK 25 virtual and hidden class loads

- **intent:** Track JDK 25 virtual and hidden class loads
- **started:** 2026-07-20

## Decision: Attribute JDK 25 child-thread and hidden-class loads to their test

- **where:** `blastradius-core tracking boundary`
- **why:** An inheritable test identity preserves attribution for a virtual thread created by
  the test. Hidden classes bypass `ClassFileTransformer`, so a loaded-class delta at the test
  boundary adds their stable source name to the dependency index.
- **alternative:** Parse a null transformer class name from class bytes — rejected because the
  JVM does not call the transformer for a hidden-class definition.
- **design:** `../design.md#compatibility-boundaries`
- **trust:** ✓ verified by the JDK 25 subprocess integration test.

## Knowledge transfer

- `InheritableThreadLocal` copies its value when a virtual child thread is created; the test must
  join that child before JUnit finishes the parent test.
- A hidden class has a synthetic runtime name, but its source-class portion before the final `/`
  is the key the selector needs. Its bytecode is unavailable through the transformer, while the
  persisted index deliberately consumes only dependency names.

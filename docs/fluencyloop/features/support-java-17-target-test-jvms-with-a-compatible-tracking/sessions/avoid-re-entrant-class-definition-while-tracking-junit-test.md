# Session: Avoid re-entrant class definition while tracking JUnit test discovery, without losing before-all dependency attribution.

- **intent:** Avoid re-entrant class definition while tracking JUnit test discovery, without losing before-all dependency attribution.
- **started:** 2026-07-31

## Decision: Defer test-class instrumentation until after JUnit discovery

- **where:** `DependencyTrackingAgent and AmbientClassInstrumenter`
- **why:** Defining a nested test class through its loader during ASM frame computation can re-enter that loader and cause a duplicate class definition. Retransforming already-defined tests at the first test boundary avoids the lifecycle hazard while preserving callbacks needed for classes loaded in BeforeAll.
- **alternative:** Never instrument test classes — rejected: it loses attribution for application classes first loaded before a test boundary.
- **design:** ../design.md#test-discovery-without-re-entrant-class-definition
- **constitution:** §III, §VI
- **trust:** ✓ verified

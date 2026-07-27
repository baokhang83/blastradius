# Session: attribute discovery-loaded project classes by runtime execution probes

- **intent:** attribute discovery-loaded project classes by runtime execution probes
- **started:** 2026-07-27

## Decision: decision

- **where:** `DependencyTrackingAgent and AmbientClassInstrumenter`
- **why:** Classes loaded during JUnit discovery are not attributable by load time. Retransform only project output classes at the first test boundary and record later method, field, type, and class-literal use under the active test. Any class that cannot be transformed stays ambient so SELECT retains the conservative fallback.
- **alternative:** A compiled-test-class reference scan — rejected: it cannot observe indirect calls or dynamic runtime paths and would weaken the tracking model.
- **design:** [`../design.md`](../design.md)
- **constitution:** §III, §IV, §V
- **trust:** ⚠ not independently verified

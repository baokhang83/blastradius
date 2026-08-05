# Session: record bounded direct-invocation-reference fallback decision

- **intent:** record bounded direct-invocation-reference fallback decision
- **started:** 2026-08-05

## Knowledge transfer

- **Dynamic index blind spot:** a dependency appears in the index only when the relevant class
  executes while a test identity is current. A cached string-dispatch API can return a prior parse
  result without executing its parser, leaving a real dependency absent from the test record.
  **Status:** documented.
- **Bounded hybrid rule:** direct invocation-owner metadata is useful only as an optional,
  single-hop expansion from a class the test dynamically executed. It produces an auditable,
  conservative reason without claiming a whole-program reachability proof. **Status:** follow-up
  in #225.
- **Cache instrumentation limit:** a generic cache hook cannot recover a cache value's producer,
  key, invalidation, wrappers, or concurrency semantics. A precise adapter would be specific to a
  demonstrated library case. **Status:** documented.

## Decision: pursue a bounded direct-invocation-reference prototype

- **where:** `docs/fluencyloop/features/220-decide-whether-to-mitigate-string-dispatched-cached-depe/research.md`
- **why:** The concentrated jsoup QueryParser misses justify measuring a one-hop, class-level conservative fallback that remains explainable and optional.
- **alternative:** Mandatory whole-program call graph and generic cache instrumentation — rejected: broad reachability modelling and cache semantics would add speculative complexity without a second proven case.
- **design:** ../design.md#decision
- **constitution:** §II, §III, §IV, §V, §VIII
- **trust:** ✓ verified

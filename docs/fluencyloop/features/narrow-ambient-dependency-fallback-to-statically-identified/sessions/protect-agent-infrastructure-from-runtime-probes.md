# Session: protect agent infrastructure from runtime probes

- **intent:** protect agent infrastructure from runtime probes
- **started:** 2026-07-27

## Decision: decision

- **where:** `DependencyTrackingAgent retransformation eligibility`
- **why:** The runtime callback obtains the active test through TestBoundaryListener. Instrumenting that listener injects the callback into currentTest itself, creating unbounded self-recursion. Exclude the tracking package from ambient retransformation while continuing to attribute ordinary project classes.
- **alternative:** Add only a thread-local reentrancy guard — rejected: it would mask instrumentation of the callback path instead of preventing the agent from transforming its own control infrastructure.
- **design:** [`../design.md`](../design.md)
- **constitution:** §IV, §V
- **trust:** ⚠ not independently verified

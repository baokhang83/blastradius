# Session: track and select bounded direct invocation references

- **intent:** track and select bounded direct invocation references
- **started:** 2026-08-05

## Knowledge transfer

- **Runtime gate and static expansion:** direct invocation owners are extracted from a project
  class's bytecode during the existing transform, but are added to a test only when the class
  executes while that test identity is active. This preserves dynamic observation as the entry
  condition while covering a skipped cache-miss branch. **Status:** implemented.
- **Auditable one-hop selection:** the index retains `test → executed source → direct targets`.
  A direct-reference selection therefore carries both the source and changed target, while an
  ordinary dynamic dependency match retains precedence. **Status:** implemented.
- **Index compatibility:** direct references require format 3. Older persisted indexes are not
  considered current and keep the established full-suite fallback behavior. **Status:**
  implemented.

## Decision: gate one-hop static references behind dynamic execution

- **where:** `blastradius-core/src/main/java/io/github/baokhang83/blastradius/core/tracking/DependencyTrackingAgent.java and blastradius-core/src/main/java/io/github/baokhang83/blastradius/core/selection/SelectionEngine.java`
- **why:** Static direct owners are only associated after their source class executes for a test, preserving a bounded explainable expansion that covers cached branches without claiming transitive reachability.
- **alternative:** A whole-program call graph or per-invocation instrumentation — rejected: both broaden analysis or miss a cache-hit branch while adding more hot-path work.
- **design:** ../design.md#design-decisions
- **constitution:** §II, §III, §IV, §V, §VIII
- **trust:** ✓ verified

## Decision: enable the bounded fallback by default

- **where:** `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/mojo/SelectMojo.java and blastradius-gradle-plugin/src/main/java/io/github/baokhang83/blastradius/gradle/BlastradiusPlugin.java`
- **why:** The user chose the conservative, explainable one-hop safety net as the normal product policy; format-3 compatibility still keeps older indexes on the existing safe fallback path.
- **alternative:** Default the rule off until a replay completes — rejected: that would leave the known cached-dispatch gap unprotected despite an explicit user decision to prefer safe over-selection.
- **design:** ../design.md#design-decisions
- **constitution:** §III, §IV, §V
- **trust:** ✓ verified

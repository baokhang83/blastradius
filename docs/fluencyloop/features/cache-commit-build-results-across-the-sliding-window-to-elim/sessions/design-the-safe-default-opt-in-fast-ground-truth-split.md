# Session: design the safe-default / opt-in fast-ground-truth split

- **intent:** design the safe-default / opt-in fast-ground-truth split
- **started:** 2026-07-28

<!--
FluencyLoop Stage 3 — a session is a slice of the build. One block per meaningful decision,
appended at the slice boundary as it's taught. No `commits:` field: the feature is a branch,
so the PR view derives commits live from git.

Each decision is a `## Decision:` heading followed by a bullet list — one bullet per field, so
it renders one-per-line as real Markdown (plain `key: value` lines collapse into a single
paragraph when rendered). Fields:

  where        — file/area the decision lives in (NOT a line number — survives refactoring)
  why          — the rationale, taught live before it was written
  alternative  — the rejected option and why (this is what makes it rationale, not description)
  design       — (optional) ../design.md#anchor — the diagram this decision shaped or used
  constitution — (optional) §N — the principle this decision serves or trades off against
  trust        — ✓ verified | ⚠ not independently verified  (about the DECISION, never the person)

Delete this comment and the example below once real decisions land.
-->

---

## Knowledge transfer

- **The redundancy that's free to remove:** `RunCommand.analyzePair` currently checks out a
  pair's head commit and runs a discardable, agent-free "probe" build purely to detect a build
  failure before delegating to `GroundTruthResolver.resolve()` — which then runs its *own*
  separate agent-free full build internally and discards the `BuildResult`, keeping only the
  per-test outcomes. Those two builds are identical in every respect (same commit, same command,
  no agent) and always were — the duplication exists only because `GroundTruthResolver` never
  told its caller what its own build did. Exposing that `BuildResult` via a new
  `GroundTruthResolution` wrapper removes an entire build with zero change to what's measured.
  status: documented.
- **Why the deeper win (unifying build types) is a real trade, not just an optimization:** in a
  strictly linear sliding window, every (commit, build-type) pair is only ever requested once —
  a base build for commit N is agent-attached, a ground-truth build for the same commit N (when
  it's also some other pair's head) is agent-free. Caching only pays off across pairs if those
  two build types collapse into one, which means the ground-truth oracle stops being built
  independently of the tracking agent it's meant to validate. That's a genuine soundness/speed
  fork, not a free win like the probe dedup. status: documented.
- **Why gating it behind a flag resolves the constitution tension instead of just moving it:**
  §III's actual language forbids a design decision that *silently* weakens soundness in favor of
  speed. A default-off, explicitly named `--fast-ground-truth` flag means the default run stays
  exactly as sound as today, and anyone who opts in is making a visible, per-run choice — the
  opposite of silent. status: documented.


## Decision: safe mode stays the default; unifying build types is gated behind --fast-ground-truth

- **where:** `blastradius-validator/.../cli/RunConfig.java, RunCommand.java`
- **why:** unifying build types unconditionally would make every default run trade away the ground-truth build's independence from the tracking agent — exactly the silently-weakens-soundness-for-speed failure constitution §III names. A default-off, explicit flag keeps the default run's soundness unchanged and makes the trade visible and opt-in on runs that ask for it; read as compliance with §III's concern (silence), not an exception to it.
- **alternative:** unify build types unconditionally, no flag, agent always attached — rejected: reaches the same N+1 build count on every run, but applies the trade to runs that never asked for it
- **design:** ../design.md#constitution-tension-and-how-the-design-resolves-it
- **constitution:** §III
- **trust:** ✓ verified

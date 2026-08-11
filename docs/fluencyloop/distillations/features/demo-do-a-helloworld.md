# demo-do-a-helloworld

**Before:** The `blastradius-validator` CLI (`Main`) exposed a single subcommand, `run`, for
executing history-window selection analysis.

**After:** The CLI also exposes `hello [name]`, a minimal demo subcommand that prints a greeting
(`Hello, World!` by default, or `Hello, <name>!`). It has no interaction with dependency tracking,
selection, or the validator pipeline — it exists as a FluencyLoop workflow demo, not a product
capability.

**Product consequence:** None. This does not change selection behavior, index formats, or any
CI-facing contract.

# Hello World feature delta

Before this feature, the validator CLI accepted only its historical `run` command. It now also
accepts `hello` and writes `Hello, World!` to standard output. The command is deliberately kept
independent from the historical replay path, so the existing validator behavior is unchanged.

The greeting's output is supplied through a small command boundary, making the visible behavior
directly testable without replacing the process-wide standard-output stream.

# Selection metrics history

The Maven plugin appends one versioned sample per report to `.blastradius/metrics.jsonl`.
Each sample describes selection counts and the estimated time saved at that point in time.
Timing coverage explains whether the estimate is complete. Audit misses remain unavailable until
an audit result exists. Persist or collect the file to retain samples across CI runners.

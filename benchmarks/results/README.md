# Benchmark result storage

Each execution creates a UTC-timestamped subdirectory here. Raw k6 time-series
files are gzip-compressed JSON; summaries, database plans, pgbench output,
runtime counters, logs, and environment metadata remain plain text or JSON.

Result directories are intentionally ignored because they can be large and may
contain host metadata. Archive the complete directory with the release or test
record that it supports. Measured analysis is retained under
[`benchmarks/analysis`](../analysis), including the
[final 100K-claim report](../analysis/20260831T045941Z.md); its raw evidence is not
included in a normal Git clone. A reported result must be traceable to the
complete runner output and that run's environment metadata. Do not reconstruct
missing raw data from the report or silently substitute a different run.

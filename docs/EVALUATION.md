# PatchReceipt evaluation

Evaluation snapshot: 25 July 2026

## Claim under evaluation

PatchReceipt is a deterministic verification layer for AI-generated Java patches:

> Codex writes the patch. PatchReceipt proves whether it deserves to ship.

The product does not assign a vague trust score. It applies fixed evidence gates:

- `REJECTED` when mandatory correctness or safety evidence fails.
- `PARTIALLY_VERIFIED` when correctness passes but scope or mutation confidence is incomplete.
- `VERIFIED` only when reproduction, regression, independent edge-case, scope, and mutation gates all pass.

The hosted interface is intentionally narrow. It accepts only bundled case and patch identifiers; it does not accept uploaded repositories, raw patches, commands, URLs, or runtime AI input.

## Measured results

The following results have been observed and may be used in submission materials.

| Evidence | Measured result | Interpretation |
|---|---:|---|
| Focused automated suite | 35 non-end-to-end tests passed | The current focused suite passed without relying on the expensive fixture end-to-end test. |
| Robust browser run | `VERIFIED` in 27,036 ms | One judge-facing run completed below the 45-second milestone target and the 60-second public target. This is one observation, not a percentile. |
| Original regressions in that run | 6 passed | The patch preserved the bundled project's original test behavior. |
| Independent edge cases in that run | 9 passed | The sealed verifier pack challenged behavior beyond the original regression suite. |
| Changed-line mutation evidence | 4 of 4 mutants killed | The robust patch achieved 100% on the four viable mutants generated for changed lines in that run. |
| Public candidate separation | Browser confirmed `REJECTED`, `PARTIALLY_VERIFIED`, and `VERIFIED` | The three judge-facing candidates exercised all three verdict states in the browser. |
| Six-patch corpus | 6 of 6 expected verdicts | Both robust patches verified, the drift case remained partial, and all three unsafe candidates were rejected. |
| Unsafe false `VERIFIED` outcomes | 0 | No edge-case trap, build bypass, compile break, or scope-drift candidate received full verification. |
| Five-run normalized determinism | 5 of 5 identical | After removing only run IDs, timestamps, durations, logs, and the digest derived from those volatile fields, every remaining evidence structure had SHA-256 `5f13bb20ed64c23348863f33491809bec255c410608f5b71ecc4184b84f6c81f`. |
| Final local release suite | 36 passed, 2 gated skips | The 46.838-second build included the live mutation-backed vertical slice; no Java process or scratch workspace remained afterward. |

### Browser-confirmed scenarios

| Candidate | Browser verdict | Evidence distinction |
|---|---|---|
| Plausible object-level deduplication | `REJECTED` | A patch that looks reasonable does not pass every mandatory correctness gate. |
| Correct fix with unrelated drift | `PARTIALLY_VERIFIED` | Correctness evidence passes, but an unexpected production path prevents full verification. |
| Minimal robust fix | `VERIFIED` | Correctness, declared scope, and changed-line mutation evidence pass. |

These are behavior-level browser observations. They do not constitute a
deployed performance benchmark. The separate six-patch corpus and determinism
protocols below are complete.

## What the 33 focused tests exercise

The current test source targets:

- bundled-case loading and hosted allowlisting;
- local CLI refusal without explicit execution consent;
- verdict precedence and mutation-confidence rules;
- Surefire and PIT XML parsing, including malformed reports;
- malformed, binary, traversal, forbidden, unexpected, and oversized diffs;
- process timeout and output handling;
- receipt parity and HTML escaping;
- application startup and web API behavior.

The reported number is the aggregate pass count for the focused non-end-to-end run. It must not be combined with a separate fixture run and presented as a larger single-suite total.

## Remaining pending measurements

The following items are deliberately labelled `PENDING` until their raw outputs are recorded.

| Evaluation | Status | Required evidence before claiming completion |
|---|---|---|
| Deployed p95 latency | **PENDING** | Measure a defined sample of warm runs against the public deployment, retain every duration, and calculate p95. The single 27,036 ms browser run is not p95. |
| Amateur usability test | **PENDING** | Run `TESTER_SCRIPT.md` with three testers, preserve task times and answers, and report assistance and completion rates. |

## Recorded and pending protocols

### Six-patch corpus

The final local corpus recorded:

| Patch ID | Ground truth | Actual verdict | Duration | Receipt digest | Evidence |
|---|---|---|---:|---|---|
| `plausible-distinct` | `REJECTED` | `REJECTED` | 30,056 ms | `7adcbaeb…1882ca4` | Three sealed edge cases fail. |
| `correct-with-drift` | `PARTIALLY_VERIFIED` | `PARTIALLY_VERIFIED` | 61,567 ms | `77a3d964…23e2458` | Correctness and mutation pass; `AuditBanner.java` is unexpected. |
| `minimal-robust` | `VERIFIED` | `VERIFIED` | 44,402 ms | `450e79ad…fc1318` | Primary robust implementation. |
| `alternate-robust` | `VERIFIED` | `VERIFIED` | 49,586 ms | `9f24ecda…001c12` | Alternate correct implementation. |
| `build-bypass` | `REJECTED` | `REJECTED` | 1 ms | `5585db4c…0bf78` | Preflight rejects forbidden `pom.xml`. |
| `compile-breaking` | `REJECTED` | `REJECTED` | 33,772 ms | `83a84f07…b36589` | Patch applies; Java compilation prevents required tests from executing. |

These durations come from one serial local corpus invocation and are not a
latency percentile. The machine-readable result is
[`evidence/evaluation-summary.json`](evidence/evaluation-summary.json).

### Five-run determinism

The same `minimal-robust` candidate ran five times. Raw durations were 82,348,
44,214, 39,539, 34,470, and 36,515 ms.

Normalization removed only:

- receipt ID and receipt digest;
- start and completion timestamps;
- total and shared-invocation durations; and
- process logs.

Verdict, hashes, stage statuses and summaries, test evidence, mutation
evidence, scope evidence, reasons, warnings, and toolchain details remained.
All five normalized structures produced the same SHA-256:

`5f13bb20ed64c23348863f33491809bec255c410608f5b71ecc4184b84f6c81f`

The corrected automated protocol passed. Raw run digests and durations are in
[`evidence/determinism-summary.json`](evidence/determinism-summary.json).

### Deployed latency

1. Deploy the exact commit used by the submission.
2. Warm the service without recording the warm-up run.
3. Run a declared sample size against the public URL.
4. Record every end-to-end duration shown by the receipt.
5. Report median, p95, minimum, maximum, failures, and sample size.

### Usability

Use `TESTER_SCRIPT.md` without coaching. The planned acceptance threshold is:

- at least two of three testers complete the core flow unaided in under three minutes; and
- all three can explain the verdict after viewing the receipt.

This threshold remains pending until all three sessions are measured.

## Honest interpretation

Current evidence shows that PatchReceipt separates all six ground-truthed
patches as expected with zero unsafe `VERIFIED` results. It also produces a
robust receipt with 6 passing regressions, 9 passing independent edge cases,
and 4 of 4 changed-line mutants killed. Five repeated robust runs have
identical normalized evidence.

Current evidence does **not** establish deployed p95 latency or amateur
usability. Those claims must stay out of submission narration until the public
deployment and three tester sessions are measured.

# PatchReceipt evaluation

Evaluation snapshot: 2 August 2026

## Claim under evaluation

PatchReceipt is a model-agnostic deterministic verification layer for
AI-generated Java patches:

> AI writes the patch. PatchReceipt proves whether it deserves to ship.

The configured verdict is rule-based:

- `REJECTED` when mandatory correctness, execution, or hard observed-scope
  evidence fails.
- `PARTIALLY_VERIFIED` when correctness passes but observed scope drifts or
  mutation evidence is unhealthy, incomplete, missing for a changed production
  file, or below threshold.
- `VERIFIED` only when reproduction, unchanged regressions, independent edge
  cases, observed scope, mutation-process health, file-level mutation evidence,
  the score threshold, and a minimum of two viable changed-line mutants all
  pass.

Receipt schema v2 stores the same verdict, plain summary, limitations, and
evidence for JSON, Markdown, HTML, and the receipt digest.

## Hardened safety suite

The reproducible Java 21 release command is:

```text
./mvnw -B -ntp -o verify
```

It discovered **77 tests**, passed **75**, and skipped only the two explicitly
gated corpus/determinism tests, with **0 failures and 0 errors**. The suite
includes Claude’s exact hidden-file and line-accounting attacks, malformed
preflight input, preflight/observed mismatches, failed PIT processes with a
parseable 100% report, skipped mandatory suites, exact reproduction failure
matching, URI-encoded path sanitisation, receipt parity, terminal worker
failure, state-race protection, non-minimal diff reconciliation, explicit
rename rejection, timeout wording, and mutation-evidence minimums.

Malformed XML tests deliberately print parser diagnostics while asserting that
the reports fail closed.

## Six-patch ground-truth corpus

The final serial corpus used Java 21.0.7, Maven 3.9.16, and PIT 1.25.4.
The correctness harness allowed
180 seconds per complete verification and 120 seconds per stage so a loaded
developer machine would not turn a verdict test into a latency test.
Production configuration is 90 seconds total and 45 seconds per stage.

| Patch ID | Expected | Actual | Duration | Mutation | Scope | Receipt digest |
| --- | --- | --- | ---: | --- | --- | --- |
| `plausible-distinct` | `REJECTED` | `REJECTED` | 20,823 ms | skipped after correctness failure | observed, clean | `c59d3fc7…c7dc8` |
| `correct-with-drift` | `PARTIALLY_VERIFIED` | `PARTIALLY_VERIFIED` | 22,762 ms | 1/1 killed, below 2-mutant minimum | observed, 1 unexpected file | `1d386eaa…761b0` |
| `minimal-robust` | `VERIFIED` | `VERIFIED` | 38,944 ms | 5/5 killed; 2 required | observed, clean | `21d6fe25…ff13f` |
| `alternate-robust` | `PARTIALLY_VERIFIED` | `PARTIALLY_VERIFIED` | 36,191 ms | 1/1 killed, below 2-mutant minimum | observed, clean | `63866620…547b6` |
| `build-bypass` | `REJECTED` | `REJECTED` | 3 ms | not run | blocked at preflight | `82279139…372b5` |
| `compile-breaking` | `REJECTED` | `REJECTED` | 15,201 ms | skipped after correctness failure | observed, clean | `53fb2578…c8f95` |

Results:

- **6 of 6** expected verdicts matched.
- **0** unsafe patches received `VERIFIED`.
- The public robust patch used only one expected production file and reconciled
  to **11 additions and 1 deletion**.
- The public robust patch killed **5 of 5** viable mutants on genuinely observed
  changed lines.
- A scan of all generated corpus receipts found no native, slash-normalised, or
  `%20`-encoded local absolute path.

These serial local durations are not a deployed latency sample or p95.

The complete machine-readable record is
[`evidence/evaluation-summary.json`](evidence/evaluation-summary.json).

## Five-run determinism

The frozen public `minimal-robust` candidate ran five times. Every run returned
`VERIFIED`, killed 5 of 5 changed-line mutants, and produced identical
normalised evidence.

Raw durations were:

- 34,819 ms;
- 37,413 ms;
- 38,752 ms;
- 32,058 ms; and
- 30,062 ms.

Normalization removed only:

- receipt ID and receipt digest;
- start and completion timestamps;
- total and shared-invocation durations; and
- process logs.

Verdict, schema, canonical summary and limitations, input hashes, stage
statuses and summaries, test evidence, mutation-process health, mutation
evidence, observed scope evidence, reasons, warnings, and toolchain details
remained. The normalised UTF-8 JSON SHA-256 is:

`8dbe2994a373729832adde418712191547940a61100477e7a3ed7f2836c442a6`

All five raw receipts also passed the absolute-path scan. Full details are in
[`evidence/determinism-summary.json`](evidence/determinism-summary.json).

## Frozen release proof

The final Java 21 release build discovered **77 tests**, passed **75**, skipped
only the two explicitly gated corpus tests, and reported **0 failures and 0
errors**. Its live PIT-backed vertical slice completed in **35.58 seconds**;
the complete Maven build reported **1 minute 35 seconds**.

All three public candidates were also exercised through the production-config
dashboard with the 90-second whole-run and 45-second per-stage limits:

- `plausible-distinct` returned `REJECTED` in 23,381 ms;
- `correct-with-drift` returned `PARTIALLY_VERIFIED` in 30,125 ms; and
- `minimal-robust` returned `VERIFIED` in 25,943 ms.

The exact final packaged Java 21 JAR produced the robust `VERIFIED` receipt in
**25,943 ms**, with 6/6 regressions, 9/9 edge cases, 5/5 observed changed-line
mutants killed, a healthy mutation process, and clean observed scope. Its
receipt digest was
`c0ba742acf8e7928fbcfbeeb6fbb35cb5b87ca2741df8dfe67fd90fd78ebc0ab`.
The mutable run-status response returned `Cache-Control: no-store`, and a scan
of the final receipt found no plain or encoded local absolute path.

## Current verification status

| Evaluation | Status | Interpretation |
| --- | --- | --- |
| Six-patch corpus | **PASS** | 6/6 expected verdicts; zero unsafe `VERIFIED`. |
| Five-run determinism | **PASS** | 5/5 normalised evidence structures identical. |
| Full Java 21 `verify` | **PASS** | 77 discovered; 75 passed; 2 intentionally gated; 0 failures/errors. |
| Production-config dashboard | **PASS** | All three public verdicts matched; final packaged robust run was 25,943 ms. |
| Production container | **PASS ON RAILWAY** | Public health was `UP`; a full robust run returned `VERIFIED` with live PIT evidence. |
| Deployed smoke test | **PASS** | All three expected verdicts and receipt formats passed; the robust sample completed in 6,652 ms. |
| Deployed p95 latency | **NOT MEASURED** | One robust production sample is reported only as a smoke test, not p95. |
| Three-person usability test | **NOT COMPLETED** | External testers were unavailable before the deadline; no usability result is claimed. |
| Claude final adversarial audit | **PASS** | `READY FOR CONTAINER GATE`; no Critical finding remains. |

## Honest interpretation

The local and deployed evidence establishes that the hardened verdict logic separates
the six ground-truthed patches, that Claude’s three reported exploit classes
fail closed, that evidence is deterministic after removing declared volatile
fields, that the final Java 21 JAR produces all three judge-facing verdicts
through the dashboard, that the Railway container completes the full robust
verification flow, and that generated receipts do not expose the tested local
path forms.

It does **not** establish deployed p95 latency, an external usability result, public-provider isolation,
arbitrary-repository safety, design quality, application security, performance,
concurrency behaviour, or requirements outside the sealed verifier pack. Those
limits also appear in receipt schema v2.

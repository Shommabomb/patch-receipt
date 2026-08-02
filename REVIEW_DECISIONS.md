# Review decisions

Claude findings are copied unedited under `reviews/claude/`. Codex records every disposition here.

## Milestone 2 independent review

Source: `reviews/claude/milestone-2.md`

The review is accepted as a deployment blocker. Public deployment remains
blocked until the critical findings, accepted important findings, complete
verification suite, container run, and focused re-review pass.

| Finding | Disposition | Decision |
| --- | --- | --- |
| C1 | Accepted | Add post-apply filesystem scope reconciliation and reject preflight/observed mismatches. |
| C2 | Accepted | Parse empty context correctly, validate hunk counts, fail closed, and use observed changed lines for PIT. |
| C3 | Accepted | Require a healthy PIT process and make failed/partial runs inconclusive. |
| I1 | Accepted | Skipped mandatory tests cannot pass; summaries use the executed pass count. |
| I2 | Accepted | Centralise evidence sanitisation for workspace, Maven, application, and user-home paths. |
| I3 | Accepted | Snapshot state before receipt and guard terminal snapshots. |
| I4 | Accepted | Add a terminal failed state, worker failure handling, stale-job expiry, and a client polling deadline. |
| I5 | Accepted | Forward manifest PIT targets and pin the invoked PIT plugin version. |
| I6 | Accepted | Disclose changed production files without viable mutants and withhold full verification. |
| I7 | Accepted | Remove demo-specific and unmeasurable approval claims. |
| I8 | Accepted | Store plain summary and limitations in canonical receipt schema v2. |
| I9 | Accepted | Run a complete bundled verification in the container CI job and add on-demand corpus evaluation. |
| I10 | Accepted | Send `Cache-Control: no-store` for mutable run status and bypass browser caches. |
| M1-M4 | Accepted | Apply while touching the dashboard and finding presentation code. |
| M5 | Deferred | Reader-thread/trailing-separator behaviour is documented for post-submission hardening; it does not alter current verdict evidence. |
| M6-M11 | Accepted | Apply exact failure matching, selector validation, CSP, documentation corrections, and clearer scope presentation. |

Positive findings P1-P11 are retained as independently reviewed strengths; no
implementation change is required for them.

## Milestone 3 independent re-review

Source: `reviews/claude/milestone-3.md`

Claude independently replayed the three Milestone 2 critical exploits and
confirmed C1-C3 are closed. The re-review found one new Critical
false-rejection and four Important reliability/evidence findings. Codex
accepted the Critical and every actionable Important/Minor finding.

| Finding | Disposition | Decision |
| --- | --- | --- |
| CR-1 | Accepted | Remove exact textual-versus-filesystem line-count equality. Observed filesystem counts remain authoritative, path-set mismatches remain hard violations, and renames are rejected honestly at preflight as unsupported. |
| IM-1 | Accepted | Require at least two viable changed-line mutants for `VERIFIED`; display the exact count in the stage, receipt, JSON, and dashboard. The one-mutant alternate patch is now `PARTIALLY_VERIFIED`. |
| IM-2 | Accepted | Raise the production whole-run budget from 60 to 90 seconds and record all three packaged public candidate durations. Container evidence remains a deployment gate. |
| IM-3 | Accepted | Timeout, missing-suite, reported failure, and inconsistent-process blocking reasons now use distinct factual wording. |
| IM-4 | Accepted | Increase the process-tree test budget and stop waiting early if the probe finishes before creating its child PID. |
| MI-1 | Accepted | Describe deletion-only or otherwise unmutated files as lacking viable changed-line mutation evidence instead of claiming mutants should have existed. |
| MI-2 | Accepted | Add a defensive unhealthy-process branch to the mutation-stage summary even though the current engine exits earlier. |
| MI-3 | Accepted | Any changed Java source outside the recognised standard root is retained in the no-evidence set and withholds `VERIFIED`. Multi-module mapping remains outside the MVP. |
| MI-4 | Accepted | Preserve the unrounded mutation score for verdict comparison and round only in renderers. |
| MI-5 | Accepted | Catch initial dashboard POST/network failures and restore an actionable retry state. |
| MI-6 | Accepted | Start polling only while a nonterminal run ID remains and clear polling state centrally on error. |
| MI-7 | Accepted | Log worker failures server-side while keeping the public failure message generic and sanitised. |
| Unadjudicated 1 | No change | Full-tree capture remains bounded by the local 1,000-file/8 MiB intake cap and the tiny hash-allowlisted hosted fixture; no public arbitrary repository is accepted. |
| Unadjudicated 2 | Accepted defensively | Reject `.git` in any path segment at preflight, including nested repository metadata. |

Post-remediation evidence:

- The full Java 21 build discovered 76 tests, passed 74, skipped only two
  explicitly gated evaluation tests, and completed the live vertical slice.
- The six-patch corpus matched all expected verdicts with zero unsafe
  `VERIFIED` outcomes.
- Five robust runs produced identical normalised evidence.
- All three packaged public candidates completed under the 90-second budget
  with the expected verdicts.

The later final adversarial audit independently confirmed CR-1 is closed.
Public deployment remains blocked only on production-container/CI proof.

## Milestone 3 final adversarial audit

Source: `reviews/claude/milestone-3-final-audit.md`

Claude independently reran the original exploits, the Java 21 full build, the
six-patch corpus, determinism, and receipt path scans. Its verdict was
`READY FOR CONTAINER GATE`, with no Critical finding.

| Finding | Disposition | Decision |
| --- | --- | --- |
| FA-1 | Accepted | Rename the crossed robust patch files to content- and ID-aligned names, update the manifest paths, and preserve each candidate’s exact patch bytes. Because the manifest text is hash-relevant, regenerate receipt evidence rather than claiming every receipt hash is unchanged. |
| FA-2 | Accepted | Remove the unsupported 51/51 subset claim. Use the reproducible Java 21 `./mvnw -B -ntp -o verify` result; after the FM-2 regression test was added, the current result is 77 discovered, 75 passed, 2 explicitly gated, 0 failures/errors. |
| FM-1 | Accepted | State the two-viable-mutant minimum in README’s `VERIFIED` definition. |
| FM-2 | Accepted | Render conclusive mutation evidence below the configured count as `Insufficient`; reserve `Inconclusive` for unhealthy, incomplete, or unmapped evidence. |
| FM-3 | Deferred | Mixed/no-line-ending restoration remains a theoretical false-rejection risk. `core.autocrlf=false` and the uniform bundled fixture keep it outside the submission-critical path. |
| FM-4 | Accepted limitation | Non-standard Java source roots remain fail-safe `PARTIALLY_VERIFIED`; multi-module/source-root mapping is outside the MVP and already disclosed. |
| FM-5 | Accepted limitation | Deletion-only production changes remain unable to obtain changed-new-line mutation evidence and therefore cannot reach `VERIFIED`; the receipt wording accurately discloses the limitation. |

FA-1 and FA-2 are evidence-hygiene corrections, not verdict-policy changes.
No additional full Claude review is required. Public deployment remains
blocked only on a successful production-container run.

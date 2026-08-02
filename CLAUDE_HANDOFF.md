# Claude Code review handoff

## Role

You are an independent reviewer for PatchReceipt. The user created and directs the project, while Codex assists with engineering work. Review the current repository and produce findings; do not become the implementation owner.

## Product proposition

> AI writes the patch. PatchReceipt proves whether it deserves to ship.

PatchReceipt is an offline Java verification engine for AI-generated patches. It proves a baseline bug, applies a patch, reconciles the observed filesystem against the declared patch, reruns the proof, executes unchanged regressions, injects a sealed independent verifier pack, runs targeted PIT mutation testing, and emits evidence receipts.

## Hard boundaries

- Hosted execution accepts only bundled case and patch IDs.
- No arbitrary hosted uploads, source, commands, paths, URLs, or test content.
- Local execution requires `--allow-local-execution`.
- No runtime OpenAI or Anthropic API.
- No database, authentication, Gradle, GitHub App, or multi-language support in the MVP.
- A failed mandatory correctness stage must always mean `REJECTED`.
- Claude does not directly edit main product code during normal review.

## Read first

1. `AGENTS.md`
2. `PROJECT_PLAN.md`
3. `CODEX_JOURNAL.md`
4. `README.md`
5. `docs/ARCHITECTURE.md` and `docs/SECURITY.md` when present

## Build

On Windows:

```powershell
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.cache\maven'
.\mvnw.cmd verify
```

On Unix:

```sh
MAVEN_USER_HOME="$PWD/.cache/maven" ./mvnw verify
```

## Milestone 1 review request

At Milestone 1, the robust live run was `VERIFIED` in 27,036 ms with
six regressions, nine edge cases, and four of four changed-line mutants killed.
The three hosted patches now produce `REJECTED`, `PARTIALLY_VERIFIED`, and
`VERIFIED` through the browser.

Independently check:

- Does the baseline failure prove the intended bug rather than a compile/configuration error?
- Can a patch bypass tests or alter the verifier pack?
- Can any failed/missing stage become `PARTIALLY_VERIFIED` or `VERIFIED`?
- Is changed-line mutation scoring conservative and explainable?
- Are input hashes and evidence provenance sufficient?
- Are safety claims narrower than the actual controls?

Write unedited findings to `reviews/claude/milestone-1.md`, ordered by severity with concrete reproduction steps. If suggesting code, put a unified diff under `reviews/claude/proposals/`; do not apply it.

## Final audit context

Codex has now also completed:

- the six-patch ground-truthed corpus with all expected verdicts and zero
  unsafe `VERIFIED` outcomes;
- five repeated robust runs with identical normalised evidence;
- a final Java 21 release build with 77 discovered tests, 75 executed passes,
  two intentionally gated skips, zero failures/errors, and a live PIT-backed
  vertical slice completed in 35.58 seconds;
- Docker, Railway, and GitHub Actions preparation;
- architecture, threat-model, evaluation, submission, presentation, and demo
  assets; and
- a final local browser proof of the three hosted verdicts.

Please extend the same review with these final checks:

- Can `compile-breaking`, `build-bypass`, or a missing Surefire/PIT report be
  misrepresented as passing evidence?
- Can a timed-out or truncated PIT report ever bypass correctness failures,
  or produce anything stronger than `PARTIALLY_VERIFIED`?
- Does Windows cleanup reliably remove JGit read-only object files without
  broadening the deletion boundary?
- Do Docker/runtime assumptions preserve the allowlisted-only claim?
- Does CI actually exercise the vertical slice and container health path?
- Are the architecture, security, evaluation, README, and presentation claims
  narrower than the implementation?
- Is any submission claim unsupported because deployment, testers, or the
  public repository are still pending?

Do not rewrite submission copy merely for style. Prioritise exploitable
correctness gaps, verdict loopholes, evidence provenance, unsafe claims, and
demo-breaking reliability issues.

## Milestone 2 - judge-facing evidence iteration

Status: **implemented in the working tree, deliberately not finalised pending
Claude and user review**.

Codex reopened development after the user challenged the premature
"complete" framing. A browser audit of the actual product found:

- the hidden idle state remained visibly overlaid after a verdict;
- completed runs exposed stage summaries but buried the decisive metrics;
- failed dynamic tests appeared only as generated JUnit indices;
- a user could not compare another candidate without reloading;
- the bug report leaked its Markdown heading marker; and
- cached static assets could survive a new deployment.

The working tree now:

- enforces `[hidden]` presentation;
- adds compact regression, edge-case, mutation, and scope metrics;
- surfaces exact counterexamples, blocking reasons, and warnings;
- includes human-readable assertion context for sealed dynamic tests;
- supports `Compare another candidate`;
- renders the bug title separately from its Markdown body;
- adds versioned CSS and JavaScript URLs; and
- adds `RunRegistryEvidenceSummaryTests`.

Primary files:

- `src/main/java/dev/patchreceipt/web/RunRegistry.java`
- `src/main/java/dev/patchreceipt/web/RunSnapshot.java`
- `src/main/resources/templates/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/app.css`
- `src/main/resources/demo-cases/checkout-coupons/verifier/**`
- `src/test/java/dev/patchreceipt/web/RunRegistryEvidenceSummaryTests.java`

Please review the current working-tree diff, not only commit `4ae1a0c`.

Specific questions:

1. Can receipt-derived failure messages or warnings create an HTML injection
   path in the dashboard?
2. Can a generic engine failure be misleadingly summarized by the four
   compact metrics?
3. Does reset/comparison state permit duplicate polling, queue abuse, or stale
   receipt links?
4. Is suppressing `generatedContractCases()[N]` safe and narrowly scoped?
5. Which one improvement would most increase judge confidence without
   expanding beyond the six-day MVP?

Write unedited findings to `reviews/claude/milestone-2.md`. Do not modify the
main source directly. If code is necessary, place a unified diff under
`reviews/claude/proposals/`.

## Milestone 3 — focused security and evidence re-review

Claude’s milestone-2 review is preserved unedited at
`reviews/claude/milestone-2.md`, and every disposition is recorded in
`REVIEW_DECISIONS.md`. Codex accepted and implemented C1-C3, I1-I10, M1-M4,
and M6-M11. M5 remains explicitly deferred.

The current implementation adds:

- strict fail-closed preflight parsing;
- post-apply observed-filesystem scope reconciliation before patched execution;
- observed changed lines as the only PIT line filter and final scope evidence;
- fail-closed mandatory test and mutation-process health rules;
- changed-file mutation-gap disclosure;
- canonical receipt schema v2 summary and limitations;
- centralised nested evidence sanitisation;
- terminal failed jobs, state-race protection, stale expiry, no-store polling,
  and a browser polling ceiling; and
- CI that runs and asserts a complete robust verification inside the production
  image, plus an on-demand six-patch corpus job.

The exact focused re-review request is
`reviews/claude/milestone-3-prompt.md`. Run that prompt against the current
working tree and preserve the answer as `reviews/claude/milestone-3.md`.

Public deployment remains blocked until the production-container job proves
`minimal-robust` reaches `VERIFIED`. Claude’s final adversarial audit has
already reported no remaining Critical finding.

The local corpus, determinism, full Java 21 build, final packaged-JAR browser
run, receipt path scan, and all three dashboard verdict checks now pass.

## Milestone 3 remediation follow-up

Claude’s complete re-review is preserved unedited at
`reviews/claude/milestone-3.md`. Codex accepted and fixed CR-1, IM-1 through
IM-4, MI-1 through MI-7, and the nested `.git` defence-in-depth item. Every
disposition is recorded in `REVIEW_DECISIONS.md`.

Key behaviour changes:

- Observed filesystem counts are authoritative; harmless non-minimal diff text
  no longer creates a false hard violation.
- Path-set mismatches remain hard failures, and renames are rejected explicitly
  at preflight as outside the MVP.
- `VERIFIED` requires at least two viable changed-line mutants as well as the
  configured score. The one-mutant alternate patch is now
  `PARTIALLY_VERIFIED`.
- Timeout explanations no longer claim tests failed when they did not execute.
- Production whole-run budget is 90 seconds; stage budget remains 45 seconds.
- Web polling/network failures recover cleanly, and worker failures are logged
  without exposing details publicly.

Fresh evidence:

- reproducible full Java 21 build: 77 discovered, 75 passed, 2 gated skips,
  0 failures/errors;
- live vertical slice: 35.58 seconds;
- corpus: 6/6 expected verdicts, zero unsafe `VERIFIED`;
- determinism: 5/5 normalised receipts identical;
- packaged public runs: `REJECTED` in 23,381 ms,
  `PARTIALLY_VERIFIED` in 30,125 ms, and `VERIFIED` in 25,943 ms.

Claude’s final adversarial audit is preserved at
`reviews/claude/milestone-3-final-audit.md`. Its verdict is
`READY FOR CONTAINER GATE`, with no Critical finding. FA-1, FA-2, and FM-1
through FM-5 are dispositioned in `REVIEW_DECISIONS.md`.

No further full Claude review is required. Production deployment still
requires the prepared container job to pass.

# Claude Code — milestone 3 independent re-review

Reviewer role: independent senior Java / application-security reviewer.
Scope: full working tree (54 modified, 14 untracked) against HEAD `4ae1a0c`, which
predates the remediation.
Date: 27 July 2026.

## Method and honesty statement

Read-only. No main product file was modified, created, or deleted; `git status`
is byte-identical to the state at the start of this review. Only this review
file was written.

Evidence comes from three sources, labelled throughout:

1. **Executable probes.** Five throwaway programs compiled against the freshly
   built `target/classes` and run entirely inside the system temp directory.
   They exercise the real `ScopeAnalyzer`, `ObservedScopeAnalyzer`, and
   `PatchApplier` — not reimplementations. Probe output is quoted verbatim.
2. **A build I ran.** `./mvnw -B -ntp -o "-Dtest=!VerticalSliceIntegrationTests" test`.
3. **Code reading**, explicitly marked where a claim is not backed by execution.

A parallel multi-agent sweep was also launched; 20 of its 22 agents died on a
session limit, so **it contributed almost nothing to this review** and its
partial output is not relied on except where I independently re-derived the
same conclusion. Two items it raised that I could not adjudicate are listed in
the *Unadjudicated* section rather than silently promoted or dropped.

I did not run the live PIT vertical slice, the six-patch corpus, the
determinism protocol, the Docker build, or the CI workflow. Claims about those
are assessed from their recorded artifacts and from code, and are labelled as
such.

---

## Verified fixed — C1, C2, C3

All three milestone-2 criticals are genuinely closed. I replayed the original
exploits rather than trusting the journal.

### C1 — closed at preflight, with independent defense in depth

The exact milestone-2 exploit (a second file hidden behind a bare `---`/`+++`
block separated by a line reading `diff`) is now rejected before anything is
materialized or executed. Probe output, bundled policy:

```
A. Milestone-2 C1 exploit replay (hidden pom.xml)
  [preflight] files=1 +2/-0 provenance=PATCH_PREFLIGHT
  [preflight] hard = [Unparseable line in hunk for .../CheckoutCalculator.java,
                      Old file header does not match diff header: pom.xml,
                      New file header does not match diff header: pom.xml,
                      Hunk appears before complete file headers: .../CheckoutCalculator.java,
                      Diff file must contain exactly one --- and one +++ header: .../CheckoutCalculator.java]
  => REJECTED AT PREFLIGHT (never materialized, never executed)

B. Same shape against forbidden src/test/**
  => REJECTED AT PREFLIGHT (5 independent hard violations)
```

Five independent checks fire, so no single check is load-bearing. I then
confirmed the second layer works on its own: given a preflight that names only
the declared file and an on-disk tree where `pom.xml` was also modified,
[`ObservedScopeAnalyzer`](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java)
catches it twice —

```
hard violations:
   - Forbidden path changed: pom.xml
   - Applied patch changed a path absent from preflight: pom.xml
```

— via the forbidden-glob re-application at
[ObservedScopeAnalyzer.java:77-83](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L77)
and the path-set reconciliation at
[:102-105](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L102).
Ordering is correct: the snapshot is taken at
[VerificationEngine.java:149-150](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L149)
while `patched/` is still pristine, reconciliation runs at
[:249](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L249),
and the hard-violation early return at
[:273-283](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L273)
fires **before** `injectVerifier(patched)` and before any patched Maven run. A
hidden edit therefore cannot reach test execution.

Regression-guarded by
[`ScopeAnalyzerTests.rejectsTraditionalFileHeaderHiddenAfterAnOpenHunk`](../../src/test/java/dev/patchreceipt/scope/ScopeAnalyzerTests.java#L48),
which uses the literal exploit string including the `diff` separator.

### C2 — closed, with the strong form of the assertion

The blank-context-line patch that silently dropped later additions now records
both:

```
C. blank context line with stripped leading space
  preflight additions    : 2  (2 '+' lines present)
  preflight changedLines : [11, 14]
  hard : []   warn : []
```

`[11, 14]` exactly matches the real on-disk line numbers I measured in
milestone 2, so the PIT changed-line denominator is now complete. The fix is
[ScopeAnalyzer.java:138](../../src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java#L138)
(`line.startsWith(" ") || line.isEmpty()`), and — importantly — the former
silent `inHunk = false` fall-through is now a hard violation at
[:144-147](../../src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java#L144).
Fail-closed, not fail-quiet.

[`acceptsEmptyContextAndKeepsCountingLaterAdditions`](../../src/test/java/dev/patchreceipt/scope/ScopeAnalyzerTests.java#L74)
asserts `changedLines` **contains both entries**, not merely that a violation
fired. That is the assertion that actually guards the bug.

### C3 — closed; no stage/verdict divergence remains

[`VerdictPolicy.decide`](../../src/main/java/dev/patchreceipt/engine/VerdictPolicy.java#L26)
now enforces the identical four conditions the stage uses at
[VerificationEngine.java:411-414](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L411):

| Condition | Stage | VerdictPolicy |
|---|---|---|
| `processHealthy()` | :411 | :26 |
| `conclusive()` | :412 | :28 |
| `filesWithoutMutants().isEmpty()` | :413 | :30 |
| `score >= requiredScore` | :414 | :32 |

An unhealthy PIT process no longer reaches the parser at all
([:379-390](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L379)),
and a missing/corrupt report now throws rather than degrading silently
([PitestReportParser.java:37-44](../../src/main/java/dev/patchreceipt/parsers/PitestReportParser.java#L37)).
The false summary string is fixed by the branch chain at
[:417-432](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L417).

The guarding tests are strong, not tautological.
[`timedOutPitProcessIgnoresParseablePerfectReport`](../../src/test/java/dev/patchreceipt/engine/VerificationEngineFailClosedTests.java#L83)
writes a real `mutations.xml`, asserts with the **real** `PitestReportParser`
that it scores 100%, then proves the engine still returns `PARTIALLY_VERIFIED`
and that the parser is never invoked. `VerdictPolicyTests` covers
`processHealthy=false` ([:67](../../src/test/java/dev/patchreceipt/engine/VerdictPolicyTests.java#L67))
and non-empty `filesWithoutMutants` ([:91](../../src/test/java/dev/patchreceipt/engine/VerdictPolicyTests.java#L91))
independently, each with a reported perfect score.

---

## Critical

### CR-1. Legitimate patches are falsely `REJECTED` because preflight and observed scope are compared for exact equality

**Component:** [ObservedScopeAnalyzer.java:102-118](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L102)
(path-set equality and `additions`/`deletions` equality).

Preflight counts raw `+`/`-` lines from the diff *text*
([ScopeAnalyzer.java:130-137](../../src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java#L130)).
Observed recomputes a **histogram diff of the resulting file bytes**
([ObservedScopeAnalyzer.java:161-167](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L161)).
These two numbers disagree for several perfectly valid patch shapes, and any
disagreement is a hard violation.

**Case A — non-minimal patch (proven).** A patch that restates a byte-identical
line as delete+add, which is a very common shape in AI-authored diffs:

```
D. non-minimal patch (author deletes+adds a byte-identical line)
  [preflight] files=1 +3/-3    hard = []
  [apply] SUCCEEDED
  [observed]  files=1 +2/-2
  [observed]  hard = [Applied line counts differ from preflight: expected +3/-3 but observed +2/-2]
  => REJECTED at observed-scope stage
```

The patch is well-formed, passes strict preflight, and applies cleanly. It is
rejected purely because the histogram algorithm minimizes what the author did
not. Note this also bites `git diff` output: git defaults to Myers,
`ObservedScopeAnalyzer` uses `HISTOGRAM`
([:28-29](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L28)),
and two correct algorithms can emit different-but-equivalent edit scripts with
different `+`/`-` totals for the same before/after pair.

**Case B — rename (proven).** Preflight registers only the new path
(`files.computeIfAbsent(newPath, …)`,
[ScopeAnalyzer.java:67-69](../../src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java#L67));
the old path's deletion is never counted. On disk a rename is a delete plus an
add:

```
[preflight] files=1 +1/-1   →  src/main/java/B.java only, hard = []
[apply] SUCCEEDED           →  A exists=false  B exists=true
[observed] files=2 +2/-2
   src/main/java/A.java +0/-2      src/main/java/B.java +2/-0
[observed] hard:
   - Applied patch changed a path absent from preflight: src/main/java/A.java
   - Applied line counts differ from preflight: expected +1/-1 but observed +2/-2
=> LEGITIMATE RENAME REJECTED at observed-scope stage
```

Both old and new paths were inside `expectedPaths`. The rename is rejected
anyway.

**Why it matters.** These are *wrong verdicts on valid input*, in the primary
code path, newly introduced by the milestone-3 fix. The user-facing blocking
reason — "Applied patch changed a path absent from preflight" — reads as an
accusation of tampering, and it flows into the dashboard's *Decisive findings*
and the plain-language summary. For a product whose thesis is a trustworthy
verdict, confidently rejecting a clean patch with a security-flavoured message
is a correctness defect, not a conservative default. The stated input domain is
AI-authored diffs, which is exactly where case A is common.

This does **not** affect the demo: the three bundled patches are minimal and
rename nothing, and the recorded corpus is 6/6. It affects the product claim
and anyone using the CLI.

**Recommended fix (verified safe).** Drop the exact `additions`/`deletions`
equality at [:110-118](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L110),
or downgrade it to a warning. It carries no security weight: the observed
counts are already the authoritative ones used for PIT filtering and for the
`maximumChangedLines` cap at
[:123-126](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L123).
I verified with the probe quoted under C1 that the forbidden-glob and path-set
checks fire independently, so removing the count check **does not reopen C1**.

For renames, additionally register the old path in preflight when
`oldPath != newPath` so the observed path set can match. If renames are instead
meant to be out of scope for the MVP, reject them explicitly at preflight with
an honest message ("renames are not supported") rather than letting them pass
preflight and fail later as an apparent scope violation.

---

## Important

### IM-1. A single surviving-or-killed mutant certifies an arbitrarily large change

**Component:** [PitestReportParser.java:121](../../src/main/java/dev/patchreceipt/parsers/PitestReportParser.java#L121)
— `conclusive = changed > 0 && timedOutOrErrored == 0 && reportComplete`.

Coverage is enforced **per file**, never per line. One viable changed-line
mutant anywhere in a changed file makes the evidence conclusive, and if it is
killed the score is 100%.

This is not hypothetical — it is in the shipped corpus.
[`docs/evidence/evaluation-summary.json`](../../docs/evidence/evaluation-summary.json)
records `alternate-robust` as `VERIFIED` with `"changedLineMutants": 1,
"killed": 1`, versus `minimal-robust` at 5/5. Both render as "100%" and both
reach the top verdict, but they rest on very different amounts of evidence, and
the receipt does not distinguish them beyond a count a reader must notice.

**Why it matters.** Same denominator-inflation family as C2, one layer up. The
receipt's headline "mutation threshold met" is materially weaker for a 1-mutant
change than a reader would assume.

**Fix (cheap).** Require a minimum viable-mutant count for `VERIFIED` (even 2–3
would separate the two corpus cases), or add the count to the plain summary and
the dashboard tile so "100% (1 mutant)" is visible without opening the JSON.
The `limitations` block at
[ReceiptLanguage.java:45-51](../../src/main/java/dev/patchreceipt/receipt/ReceiptLanguage.java#L45)
already states the count — promoting it to the verdict line is a small change.

### IM-2. The 60-second production budget has thinner headroom than the recorded evidence implies

**Component:** [application.properties:13](../../src/main/resources/application.properties#L13)
(`total-timeout-seconds=60`), consumed by
[VerificationEngine.boundedTimeout:694-699](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L694).

Recorded durations against that 60 s ceiling:

| Run | Duration | Harness budget |
|---|---:|---|
| `correct-with-drift` (corpus) | **63,116 ms** | 180 s |
| `alternate-robust` (corpus) | 53,734 ms | 180 s |
| `minimal-robust` (corpus) | 49,002 ms | 180 s |
| determinism worst run | **50,915 ms** | 60 s |
| packaged robust dashboard run | 42,306 ms | 60 s |

`docs/EVALUATION.md:41-44` **honestly discloses** the 180 s / 120 s correctness
harness versus the 60 s / 45 s production configuration, which I credit. What
it does not reconcile is that `correct-with-drift` — one of the three hosted
demo candidates — has a single recorded duration that *exceeds* the production
total budget. `releaseProof.productionConfigDashboard` claims
`publicVerdictsMatched: 3` but records a duration only for the robust run, so
there is no recorded evidence that `correct-with-drift` completes inside 60 s.

Separately, the worst determinism run used 85% of the production budget on the
development machine, while the CI container job runs with `--memory=1g
--cpus=2` and the `Dockerfile` sets no timeout override.

**Failure path.** When the budget is exhausted, `boundedTimeout` returns
`Math.max(1, …)` = 1 ms, so the next child is killed instantly. If that lands
on the patched-test stage, `combinedTestProcessHealthy` returns false and all
three correctness gates fail — see IM-3 for what the receipt then says.

**Fix.** Record a per-candidate duration for all three hosted patches under the
production configuration, and raise the container's total budget via an
environment override (the property is already externalized) until there is a
measured p95 on the target hardware. A CI red on the new container step would
be a submission-day surprise.

### IM-3. A total-run timeout produces blocking reasons that falsely assert tests failed

**Component:** [VerificationEngine.java:329-371](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L329).

The stage *summaries* correctly say "The shared patched-test run timed out
before X could be verified" ([:322](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L322),
[:341](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L341),
[:361](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L361)).
But the `blockingReasons` pushed alongside them are unconditional:

```java
if (!fixed)            blockingReasons.add("Reproduction test still fails after patching");
if (!regressionsPass)  blockingReasons.add("Original regression suite fails after patching");
if (!edgesPass)        blockingReasons.add("Independent edge-case suite fails");
```

On a timeout none of those statements is true — nothing failed, the run ran out
of time. Those strings are the ones surfaced in the dashboard's *Decisive
findings* ([RunRegistry.java:167](../../src/main/java/dev/patchreceipt/web/RunRegistry.java#L167))
and consumed by `ReceiptLanguage.rejectedSummary`
([:70-73](../../src/main/java/dev/patchreceipt/receipt/ReceiptLanguage.java#L70)),
so the plain-language summary tells a non-expert the patch broke the tests.

Given IM-2 makes a timeout plausible on constrained hardware, this is the most
likely way a judge sees a confidently wrong explanation. Same
false-evidence family as C3, which is why I rate it Important rather than Minor.

**Fix.** Branch the blocking reason on `patchedTestRun.timedOut()` exactly as
the summaries already do — e.g. "Patched test run exceeded the time budget
before correctness could be established".

### IM-4. The process-tree termination test is timing-dependent and fails under load

**Component:** [BoundedProcessRunnerTests.java:53-56](../../src/test/java/dev/patchreceipt/runner/BoundedProcessRunnerTests.java#L53).

My focused run:

```
[ERROR] BoundedProcessRunnerTests.timeoutTerminatesTheParentAndItsChildProcess:56
Expecting path: ...junit-.../child.pid to exist
[ERROR] Tests run: 69, Failures: 1, Errors: 0, Skipped: 2
```

Re-run in isolation: `Tests run: 2, Failures: 0` — **BUILD SUCCESS**. So this is
load-induced flakiness, not a milestone-3 regression. The machine was busy with
a concurrent agent sweep.

The cause is structural: the test waits `80 × 50ms = 4s` for the child PID file
while the runner's own timeout is 5 s. Those two numbers are coupled, so JVM
startup slower than 4 s fails the assertion. `CODEX_JOURNAL.md` records fixing
this same test once before for the same reason; the fix narrowed the window
without removing it.

**Why it matters.** It sits in the *safety* suite, and CI runners are exactly
the loaded environment that triggers it. A red CI on submission day over a
non-bug is an avoidable risk.

**Fix.** Raise the runner timeout in this test (e.g. 15 s) and the wait loop
proportionally (e.g. 10 s), preserving the ratio; or await
`pidFile.exists() || future.isDone()` and fail only if the future completed
without ever writing the PID.

---

## Minor

| # | Component | Finding |
|---|---|---|
| MI-1 | [ObservedScopeAnalyzer.java:164](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L164) | Deletion-only edits produce empty `changedLines` (`getBeginB() == getEndB()`), confirmed by probe (`deleted file … changedLines=[]`). The file then always lands in `filesWithoutMutants`, so a pure-deletion production change can **never** reach `VERIFIED`, and the receipt says "produced no viable mutants", which is misleading — none were possible. Fails safe; the message should distinguish "no mutable changed lines" from "mutants expected but absent". |
| MI-2 | [VerificationEngine.java:417-432](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L417) | The summary chain has no `!processHealthy` branch. Unreachable today only because `PitestReportParser` hardcodes `true` at [:111](../../src/main/java/dev/patchreceipt/parsers/PitestReportParser.java#L111) and the unhealthy path returns earlier at [:379](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L379). Structurally identical to the bug just fixed; add the branch defensively. |
| MI-3 | [PitestReportParser.java:70-71](../../src/main/java/dev/patchreceipt/parsers/PitestReportParser.java#L70), [:126-129](../../src/main/java/dev/patchreceipt/parsers/PitestReportParser.java#L126) | Source-root mapping is hardcoded to `src/main/java/`. A changed Java file under a non-standard root (multi-module, `core/src/main/java`) is invisible to both mutant mapping *and* the `filesWithoutMutants` check, so it silently contributes nothing while another file carries the score. Bundled case unaffected; CLI-only. Derive the root from the manifest, or warn when a changed `.java` file falls outside the recognized root. |
| MI-4 | [PitestReportParser.java:119](../../src/main/java/dev/patchreceipt/parsers/PitestReportParser.java#L119) | Score is rounded to one decimal *before* the gate comparison at [VerdictPolicy.java:32](../../src/main/java/dev/patchreceipt/engine/VerdictPolicy.java#L32), so a true 79.96% presents as 80.0% and passes. Not reachable at realistic mutant counts (needs ~1000+); compare on the unrounded value and round only for display. |
| MI-5 | [app.js:61-73](../../src/main/resources/static/app.js#L61) | `startRun()` has no rejection handler on the initial `fetch`. A network-level failure leaves `run-button` disabled with no error rendered — a dead end requiring reload. Wrap in try/catch calling `renderError`. |
| MI-6 | [app.js:78](../../src/main/resources/static/app.js#L78) | The poll interval is armed unconditionally after the POST, even if `renderRun` already hit a terminal branch and nulled `state.runId`, producing a `GET /api/v1/runs/null` (404 → "could not be refreshed"). Guard on `state.runId` before `setInterval`. |
| MI-7 | [RunRegistry.java:99-106](../../src/main/java/dev/patchreceipt/web/RunRegistry.java#L99) | `catch (Throwable)` swallows the failure with no logging. Correct for the UI (fixed generic message, no leak), but an `Error` disappears silently, which will make production diagnosis hard. Log at error level before setting `FAILED`. |

---

## Confirmed fixed from milestone 2

Beyond C1–C3, I verified each accepted disposition in `REVIEW_DECISIONS.md`:

- **I1** — `TestEvidence.successful()` now excludes `skipped`; the edge-case
  summary uses `passed()` ([VerificationEngine.java:360](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L360)).
  `combinedTestProcessHealthy` additionally cross-checks the Maven exit code
  against reported suite results, and its test asserts the skipped case returns
  false.
- **I2** — [`EvidenceSanitizer`](../../src/main/java/dev/patchreceipt/receipt/EvidenceSanitizer.java)
  covers reasons, warnings, stage summaries and logs, **nested metric values**
  (recursively, [:182-202](../../src/main/java/dev/patchreceipt/receipt/EvidenceSanitizer.java#L182)),
  test failures, mutation findings, and scope evidence. Masks are applied
  longest-path-first ([:38](../../src/main/java/dev/patchreceipt/receipt/EvidenceSanitizer.java#L38),
  [:51](../../src/main/java/dev/patchreceipt/receipt/EvidenceSanitizer.java#L51))
  so a nested workspace cannot be corrupted by a shorter mask, matching is
  case-insensitive, and `pathVariants` covers backslash, forward-slash, `%20`,
  and `file:` URI forms. It is applied **before** the digest and before
  `VerdictPolicy.decide` ([VerificationEngine.java:519-541](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L519)).
  The null-workspace path (early preflight rejection, where `workspace` is still
  null) is handled at [:48](../../src/main/java/dev/patchreceipt/receipt/EvidenceSanitizer.java#L48) — no NPE.
- **I3** — `snapshot()` reads state first
  ([RunRegistry.java:119-120](../../src/main/java/dev/patchreceipt/web/RunRegistry.java#L119)) and
  `stableState` ([:261-265](../../src/main/java/dev/patchreceipt/web/RunRegistry.java#L261))
  downgrades `COMPLETED`-with-null-receipt to `RUNNING`. `app.js` also guards
  independently ([:106](../../src/main/resources/static/app.js#L106)). Fixed on both sides.
- **I4** — terminal `FAILED` state, `catch (Throwable)`, `purgeExpired` no
  longer requires `COMPLETED` and cancels a live `Future`
  ([:267-281](../../src/main/java/dev/patchreceipt/web/RunRegistry.java#L267)), plus a
  180 s client polling deadline ([app.js:83-88](../../src/main/resources/static/app.js#L83)).
- **I5** — manifest PIT targets are forwarded and the plugin version is pinned:
  `org.pitest:pitest-maven:1.25.4:mutationCoverage`
  ([VerificationEngine.java:625-636](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L625)).
- **I6** — `filesWithoutMutants` is derived from the **observed** scope, which is
  the correct source set, and it withholds `VERIFIED`. Working in the real
  corpus: `correct-with-drift` carries the warning "Some changed production
  files produced no viable mutants".
- **I7** — "It is safe to approve" and "the coupon bug" are both gone.
  `ReceiptLanguage.limitations()` now states plainly what was not checked.
- **I8** — `plainSummary` and `limitations` are stored record components
  ([VerificationReceipt.java:19-20](../../src/main/java/dev/patchreceipt/domain/VerificationReceipt.java#L19)),
  so JSON carries them, the digest covers them
  ([ReceiptDigestService.java:26-28](../../src/main/java/dev/patchreceipt/receipt/ReceiptDigestService.java#L26)
  strips only `receiptDigest`), and HTML/Markdown/dashboard all *read* the
  stored value rather than recomputing — I checked specifically for
  recomputation and found none.
- **I9** — the CI container job now POSTs a real run and asserts
  `verdict=VERIFIED`, `schemaVersion=2`, `scope.provenance=OBSERVED_FILESYSTEM`,
  and `mutation.processHealthy=true`
  ([ci.yml:91-160](../../.github/workflows/ci.yml#L91)), with a `FAILED`
  short-circuit. A `workflow_dispatch` job runs the gated corpus.
- **I10** — `Cache-Control: no-store` on both mutable endpoints
  ([RunApiController.java:55,62](../../src/main/java/dev/patchreceipt/web/RunApiController.java#L55));
  `app.js` polls with `{cache: "no-store"}`.
- **M1/M2** — the previously unescaped `href` and `class` interpolations are now
  `escapeHtml`-wrapped ([app.js:100](../../src/main/resources/static/app.js#L100),
  [:177](../../src/main/resources/static/app.js#L177)).
- **M6** — reproduction requires an exact fully-qualified type match
  ([VerificationEngine.java:598](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L598)),
  guarded by a test using `dev.attacker.AssertionFailedError`.
- **M8** — the standalone HTML receipt now ships a restrictive CSP meta tag.
- **M10** — README and SECURITY now say plainly "Maven offline mode is not a
  network-egress firewall."

**Injection re-audit (clean).** I re-checked every sink. All new fields —
`plainSummary`, `limitations`, `metric.description`, `filesWithoutMutants`,
scope `provenance` — pass through `escapeHtml` in `app.js` and `html()` in
`HtmlReceiptRenderer`. `renderBugReport` and `renderError` use `textContent`.
I found **no reachable HTML or JavaScript injection path.**

**Preflight robustness (probed).** Real `git diff` output is not broken by the
stricter parser: `index`/`old mode`/`new mode`/`similarity index`/`rename
from|to` metadata lines are accepted; new files (`--- /dev/null`) and deletions
(`+++ /dev/null`) parse correctly; CRLF bodies parse correctly; duplicate
`diff --git` blocks for one path and mode-only changes with no hunks are
rejected. Only the rename case has a downstream problem (CR-1 case B).

**Evidence artifacts are current, not stale.** Both
`docs/evidence/*.json` are `schemaVersion: 2`, `recordedAt: 2026-07-27`, and
every corpus entry carries `scopeProvenance: OBSERVED_FILESYSTEM` — i.e.
regenerated after milestone 3, not carried over from `4ae1a0c`. I independently
counted **70** `@Test`/`@TestFactory`/`@ParameterizedTest` methods, matching the
documented "70 discovered".

---

## Unadjudicated

My parallel sweep raised two items its refuter agents never reached before the
session limit. I list them without endorsement:

1. Whether `ObservedScopeAnalyzer.capture` reading every file's full bytes into
   a map is a memory concern. My own reading says no for the hosted case — the
   snapshot is taken before any build, so `target/` does not exist, and the
   materialized fixture is small — but I did not measure it against the 8 MiB
   local-intake cap for the CLI path.
2. Whether `isVerifierMetadata` (`.git` and `.git/` prefix only,
   [:183-185](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L183))
   should also skip nested `.git` directories in subdirectories. Preflight
   independently rejects any `.git`-prefixed path
   ([ScopeAnalyzer.java:172-174](../../src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java#L172)),
   so I believe this is not exploitable, but I did not probe it.

---

## Required answers

**C1 — closed.** Verified by replaying the exact exploit, in both the `pom.xml`
and `src/test/**` forms. Rejected at preflight by five independent checks
before materialization or execution, and independently caught a second time by
the observed forbidden-glob and path-set checks. Regression-guarded by a test
using the literal exploit string.

**C2 — closed.** Verified by probe: `changedLines=[11, 14]`, matching the real
on-disk line numbers. The former silent drop is now a hard violation, and the
guarding test asserts the changed-line content rather than merely the presence
of an error.

**C3 — closed.** `VerdictPolicy` enforces all four mutation conditions,
identical to the stage's `mutationPass`. Unhealthy PIT processes never reach the
parser; missing/corrupt reports throw. Proven by a test that constructs a real
100%-scoring report and confirms the verdict is still `PARTIALLY_VERIFIED`. No
new stage/verdict divergence exists anywhere in the codebase — `VerdictPolicy`
is the sole verdict producer.

**Can any unsafe patch still receive `VERIFIED`?** No unsafe patch, in the sense
of milestone 2 — I could not construct one, and the two structural layers are
independent. The residual weakness is IM-1: a *safe but thinly-evidenced* patch
can be `VERIFIED` on a single viable mutant, as `alternate-robust` is in the
shipped corpus. That overstates confidence; it does not admit a malicious or
incorrect patch. The errors this build makes now point the other way — CR-1
rejects valid patches.

**Ready for production-container verification?** Not yet, on evidence rather
than on correctness. The CI container job is now genuinely well-constructed and
asserts the right fields. But no run has yet been observed completing inside the
production 60-second budget on constrained hardware, the worst recorded run
used 85% of that budget on a faster machine, and one hosted candidate
(`correct-with-drift`) has a recorded duration of 63.1 s that exceeds it. Run
the container job once and record per-candidate durations for all three hosted
patches under production configuration; if it goes green, this answer becomes
yes.

**Does any Critical finding remain?** Yes — one: **CR-1**, false rejection of
legitimate non-minimal and rename patches, introduced by the milestone-3
remediation itself. It is not demo-blocking, the fix is roughly three lines, and
I verified the fix does not reopen C1.

**Overall.** This is a substantial and largely well-executed remediation. The
two-layer design — strict fail-closed preflight plus post-apply filesystem
reconciliation — is the right architecture, and it is the reason C1 is closed
rather than merely patched. The tests guard the actual bugs rather than the
happy path, the documentation is narrower than the implementation throughout,
and the evidence artifacts were properly regenerated. Fix CR-1, branch the
timeout blocking reasons (IM-3), get one green container run recorded (IM-2),
and this is ready for public deployment.

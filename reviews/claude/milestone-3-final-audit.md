# PatchReceipt — Milestone 3 Final Adversarial Audit

Independent senior Java security and verification reviewer.
Date: 27 July 2026.
Scope: full working tree (57 tracked-modified, 17 untracked) against HEAD `4ae1a0c`.

---

## 1. Executive verdict

**READY FOR CONTAINER GATE**

No Critical finding remains. All three Milestone 2 criticals (C1, C2, C3) and the
Milestone 3 critical (CR-1) are closed, and I confirmed each by re-running the
original exploits against freshly compiled code rather than by reading the
journal. I independently reproduced the six-patch corpus (6/6, exactly one
`VERIFIED`), the full Java 21 build (76/74/2/0/0), and a ten-pattern
absolute-path scan over receipts my own run generated.

The single remaining gate is the one the prompt anticipated: **Docker is not
installed on this machine, so production-container behavior is unproven.** That
is a deployment blocker, not a code defect. The CI job that would close it is
correctly constructed.

Two Important and five Minor findings are recorded below. None affects verdict
correctness; the most substantive is a patch-file/ID naming crossover that
harms evidence traceability.

---

## 2. Commands and environment

Default `JAVA_HOME` on this machine is Corretto **25.0.3**. The documented target
is Java 21, and Codex's release proof used 21.0.7, so I explicitly used the
installed JDK 21 rather than silently substituting 25.

```
JDK:    C:\Program Files\Java\jdk-21  →  java 21.0.7 2025-04-15 LTS (build 21.0.7+8-LTS-245)
Maven:  3.9.16 via checksum-verified wrapper, MAVEN_USER_HOME=<repo>/.cache/maven, offline (-o)
Docker: not installed  ("docker: command not found")
```

| # | Command | Result |
|---|---|---|
| 1 | `./mvnw -B -ntp -o verify` | **BUILD SUCCESS**, 1:09. Tests run **76**, Failures **0**, Errors **0**, Skipped **2** |
| 2 | `./mvnw -B -ntp -o -Dpatchreceipt.evaluation=true -Dpatchreceipt.determinism=true -Dtest=EvaluationCorpusIntegrationTests test` | **BUILD SUCCESS**, 3:52. Tests run **2**, Failures **0**, Errors **0**, Skipped **0** (227.2 s) |
| 3 | Five throwaway probe programs compiled against `target/classes`, executed in the system temp directory | See §5 |
| 4 | Ten-pattern absolute-path scan over `target/evaluation-receipts/*.json` | 0 hits (see §4/§6) |

Repository writes: **this report only.** `git status` is unchanged from the
state at audit start (57 tracked-modified, 17 untracked — identical set). The
only filesystem change is `target/`, which is gitignored build output. No
product file was edited, no patch generated, no code reformatted.

Environmental limitations: no Docker/Podman; no public deployment; no external
testers. Nothing in this report claims container or deployed behavior.

---

## 3. Previous finding verification

All probes below ran against `target/classes` freshly compiled by command 1.

### C1 — hidden scope changes → **CLOSED**

Replayed the literal Milestone 2 exploit (a second file hidden behind a bare
`---`/`+++` block separated by a line reading `diff`), in both the `pom.xml` and
`src/test/**` variants:

```
A. hidden pom.xml
  [preflight] files=1 +2/-0 provenance=PATCH_PREFLIGHT
  [preflight] hard = [Unparseable line in hunk for .../CheckoutCalculator.java,
                      Old file header does not match diff header: pom.xml,
                      New file header does not match diff header: pom.xml,
                      Hunk appears before complete file headers: .../CheckoutCalculator.java,
                      Diff file must contain exactly one --- and one +++ header: .../CheckoutCalculator.java]
  => REJECTED AT PREFLIGHT (never materialized, never executed)

B. hidden src/test/** edit
  => REJECTED AT PREFLIGHT (5 independent hard violations)
```

Five independent checks fire; none is load-bearing alone.

**Can a crafted patch reach patched test execution before hard observed-scope
violations are rejected?** No. Tracing
[VerificationEngine.java](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java):
the pristine snapshot is taken at `:149-150` before anything touches `patched/`,
`patchApplier.apply` runs at `:220`, `observedScopeAnalyzer.reconcile` at `:249`,
and the hard-violation early return is at `:273-283` — which precedes both
`injectVerifier(patched)` (`:285`) and the patched Maven invocation (`:291`).
Nothing executes patched code between apply and reconciliation.

Observed scope is authoritative: `scope` is reassigned at `:249`, and that same
object supplies the final receipt and the PIT changed-line filter at `:396`
(`scope.changedLinesByPath()`).

I also verified the second layer holds **independently** of preflight. Given a
preflight naming only the declared file and an on-disk tree where `pom.xml` was
additionally modified:

```
hard violations:
   - Forbidden path changed: pom.xml
   - Applied patch changed a path absent from preflight: pom.xml
```

Both the forbidden-glob re-application
([ObservedScopeAnalyzer.java:77-83](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L77))
and the path-set mismatch check (`:102-105`) fire. This matters because CR-1's
fix removed the line-count check — see CR-1 below.

Additional hardening verified: `.git` is rejected in **any** path segment
(`containsVerifierMetadata` → `List.of(normalize(path).split("/")).contains(".git")`),
covering the nested-metadata case I left unadjudicated at Milestone 3.
Symbolic-link and binary changes are hard violations in the observed comparator
(`:134-145`).

### C2 — observed changed-line accounting → **CLOSED**

```
C. blank context line with stripped leading space
  preflight additions    : 2   (2 '+' lines present)
  preflight changedLines : [11, 14]
  hard : []   warn : []
```

`[11, 14]` matches the real on-disk line numbers I measured in Milestone 2. The
fix is [ScopeAnalyzer.java:138](../../src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java#L138)
(`line.startsWith(" ") || line.isEmpty()`), and the former silent
`inHunk = false` fall-through is now a hard violation (`:144-147`) — fail-closed
rather than fail-quiet. Hunk counts are validated at all three boundaries
(next `@@`, next `diff --git`, end of patch).

### C3 — failed mutation process → **CLOSED**

[VerdictPolicy.decide](../../src/main/java/dev/patchreceipt/engine/VerdictPolicy.java#L26)
now enforces the identical five conditions the stage uses at
[VerificationEngine.java:423-429](../../src/main/java/dev/patchreceipt/engine/VerificationEngine.java#L423):

| Condition | Stage | VerdictPolicy |
|---|---|---|
| `processHealthy()` | :423 | :26 |
| `conclusive()` | :424 | :28 |
| `changedLineMutants() >= requiredChangedLineMutants()` | :425-427 | :30 |
| `filesWithoutMutants().isEmpty()` | :428 | :32 |
| `changedLineScore() >= requiredScore()` | :429 | :34 |

No divergence. `VerdictPolicy` is the sole verdict producer — nothing in
`web/`, `RunRegistry`, or `app.js` recomputes it.

Unhealthy PIT never reaches the parser (`:379-390` returns `failedMutation`
before parsing). A missing, wrong-root, partial, or corrupt report now **throws**
rather than degrading silently
([PitestReportParser.java:37-44](../../src/main/java/dev/patchreceipt/parsers/PitestReportParser.java#L37),
plus `requiredAttribute`/`requiredText`/`positiveInteger`). An unsupported
mutation status sets `reportComplete = false` → inconclusive. `MI-2` is also
addressed: the summary chain now has an explicit `!processHealthy` branch, so
the Milestone 2 false string cannot return even if the earlier guard were
removed.

`MI-4` verified: `round()` is deleted from the parser; the raw `score` is stored
and compared, and rounding happens only in renderers.

### CR-1 — legitimate non-minimal diffs → **CLOSED, both halves**

**Non-minimal patch (was falsely REJECTED, now passes):**

```
D. non-minimal patch (author restates a byte-identical line as delete+add)
  [preflight] files=1 +3/-3   hard = []
  [apply] SUCCEEDED
  [observed]  files=1 +2/-2   changedLines=[10, 12]
  [observed]  hard = []   warn = []
  => clean scope; eligible to continue toward VERIFIED
```

The exact-equality check is gone from
[ObservedScopeAnalyzer.java:110-117](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L110),
which now applies only the `maximumFiles` and `maximumChangedLines` caps against
observed counts. Note the result is *better* than a workaround: observed
`changedLines=[10, 12]` correctly excludes the unchanged middle line, so PIT
scoring is more accurate than the raw patch text would have given.

**Rename (was falsely rejected with a tampering-flavoured message):**

```
[preflight] hard = [File renames are not supported: src/main/java/A.java -> src/main/java/B.java]
=> rejected at preflight
```

Renames are now refused **at preflight with an honest message**
([ScopeAnalyzer.java](../../src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java),
`!oldPath.equals(newPath)` guard), instead of passing preflight and failing later
as an apparent scope violation. That is the documented decision and it is the
correct one for the MVP.

**Did removing the line-count check weaken C1?** No — proven above: the
forbidden-glob and path-set checks both still fire on an undeclared file edit.

### Minimum mutation-evidence count → **IMPLEMENTED AND EFFECTIVE**

`minimumChangedLineMutants: 2`
([patchreceipt.yml](../../src/main/resources/demo-cases/checkout-coupons/patchreceipt.yml)),
threaded through `CaseManifest.Mutation` → `PitestReportParser.parse` →
`MutationEvidence.requiredChangedLineMutants` → `VerdictPolicy` and the stage.

It has real teeth, verified in my own corpus run: the one-mutant patch that was
`VERIFIED` at Milestone 3 is now `PARTIALLY_VERIFIED`, and `correct-with-drift`
carries the same warning. Full four-surface parity: HTML (`:305`), Markdown
(`:209` "Required viable mutants"), JSON (record component), dashboard
(`RunRegistry.mutationScore` now renders `"100% · 5 mutants"` and returns
`"Inconclusive"` if any of the four conditions fail), stage metrics (`:700`),
and the sanitizer passes it through (`:143`).

---

## 4. New findings

### Critical

**None.**

I could not construct any path to an unsafe, incorrect, or unevidenced
`VERIFIED`, nor any hosted-input escape, nor any evidence fabrication. The
errors this build can still make point toward over-rejection, not
over-acceptance.

### Important

#### FA-1. Patch files and patch IDs are crossed over, breaking evidence traceability

**Component:** [patchreceipt.yml](../../src/main/resources/demo-cases/checkout-coupons/patchreceipt.yml), `patches:` block.

```yaml
  - patchId: minimal-robust        # → file: patches/alternate-robust.patch
  - patchId: alternate-robust      # → file: patches/minimal-robust.patch
```

Every receipt, corpus row, README table, and `evaluation-summary.json` entry
labelled `minimal-robust` was in fact produced by `patches/alternate-robust.patch`
(the `HashSet`-based implementation), and vice versa. An auditor who opens
`patches/minimal-robust.patch` to see what the headline `VERIFIED` receipt
actually verified reads the wrong diff.

I confirmed the crossover independently rather than assuming it: EVALUATION.md
states the public robust patch "reconciled to **11 additions and 1 deletion**",
and counting `alternate-robust.patch` gives exactly 11 `+` and 1 `-`, while
`minimal-robust.patch` does not.

**Impact.** No claim is false — Codex correctly updated the titles and
descriptions to describe the newly-assigned content ("Alternate robust
**map-based** fix", "too little viable mutation evidence"), the manifest is the
single source of truth, and input hashes cover the real patch bytes. So this is
a traceability and reproducibility defect, not dishonesty. But it is exactly the
kind of thing that makes an external reviewer distrust an otherwise clean
evidence trail, and the crossover is load-bearing for the demo (pointing each ID
at its like-named file would leave no hosted `VERIFIED` candidate).

**Smallest defensible remediation.** Rename the two files to match their content
and IDs — e.g. `robust-set-based.patch` and `robust-map-based.patch`, or simply
swap the two files' names on disk — and update the two `file:` keys. No verdict,
hash-relevant, or behavioral change; purely a rename so that names, IDs, titles,
and content all agree.

#### FA-2. The "51/51 focused tests" claim is not independently reproducible

**Component:** [docs/EVALUATION.md](../../docs/EVALUATION.md) "Hardened safety suite" section; repeated in `REVIEW_DECISIONS.md`.

EVALUATION.md reports "**51 tests with 0 failures, 0 errors, and 0 skips**" for
the post-review remediation suite, but no command or `-Dtest` filter is recorded
for that subset. I verified the full-build figure directly (76 discovered / 74
passed / 2 gated skips) and it matches
`evaluation-summary.json → releaseProof.fullVerify` exactly. I could not
reproduce 51 because I do not know which classes it covers.

**Impact.** An unsupported-as-stated submission claim sitting beside figures
that *are* fully supported. It reads as precise but cannot be re-derived by a
judge or reviewer, which weakens the surrounding evidence by association.

**Remediation.** Either record the exact command next to the number, or drop the
51 figure and cite only the reproducible 76/74/2 full-verify result.

### Minor

| # | Component | Finding |
|---|---|---|
| FM-1 | [README.md:100](../../README.md#L100) | The `VERIFIED` definition lists reproduction, regressions, edge cases, observed scope, process health, file-level coverage, and the score threshold — but **omits the two-mutant minimum**, which EVALUATION.md does include. The implementation is stricter than README describes, so this under-claims (the safe direction); still, the two documents should agree. |
| FM-2 | [RunRegistry.mutationScore](../../src/main/java/dev/patchreceipt/web/RunRegistry.java#L216) | The dashboard tile renders `"Inconclusive"` when the mutant count is below the minimum. The evidence is actually *conclusive but insufficient*. The Decisive Findings list carries the accurate warning ("Too few viable changed-line mutants…"), so nothing is misleading in aggregate, but the tile word is imprecise. Consider `"Insufficient"`. |
| FM-3 | [PatchApplier.detectLineEndings](../../src/main/java/dev/patchreceipt/runner/PatchApplier.java#L86) | Files whose original endings are `MIXED` or `NONE` skip line-ending restoration. If JGit ever normalized such a file, observed reconciliation would see whole-file churn and could exceed `maximumChangedLines` → false rejection. `core.autocrlf=false` is set explicitly, so I believe this is unreachable — **I did not probe it**, and the bundled fixture is uniformly LF. Flagged for completeness only. |
| FM-4 | [PitestReportParser.java:70-71](../../src/main/java/dev/patchreceipt/parsers/PitestReportParser.java#L70) | Mutant→source mapping still hardcodes `src/main/java/`, while `isJavaSourcePath` (`:126-129`) now accepts any non-test `.java`. A changed Java file under a non-standard root is therefore seeded into `filesWithoutMutants` but can never be credited with mutants → permanent `PARTIALLY_VERIFIED`. This is fail-safe **and disclosed**, and matches the accepted MI-3 disposition ("multi-module mapping remains outside the MVP"). Recorded as a known limit, not a defect. |
| FM-5 | [ObservedScopeAnalyzer.java:164](../../src/main/java/dev/patchreceipt/scope/ObservedScopeAnalyzer.java#L164) | Deletion-only production edits still yield empty `changedLines` (probe: `deleted file … changedLines=[]`), so such a file can never reach `VERIFIED`. MI-1's accepted remediation reworded the message honestly ("lack viable changed-line mutation evidence" rather than claiming mutants should have existed), which resolves the misleading-message half. The structural limit remains. |

---

## 5. Adversarial scenarios attempted

| Scenario | Method | Outcome |
|---|---|---|
| Hidden `pom.xml` behind bare `---`/`+++` after a `diff` line | Probe, real `ScopeAnalyzer` | Rejected at preflight (5 violations) |
| Same shape against `src/test/**` | Probe | Rejected at preflight |
| Undeclared file modified on disk while preflight names one file | Probe, real `ObservedScopeAnalyzer.reconcile` | Rejected twice (forbidden-glob + path-set) |
| Blank context line with stripped leading space | Probe | Both additions counted, `changedLines=[11,14]` |
| Non-minimal diff (identical line restated as `-`/`+`) | Probe, full preflight→apply→reconcile | **Now accepted** (CR-1 fixed) |
| File rename with edit | Probe, full pipeline | Rejected at preflight, honest message |
| Real `git diff` metadata (`index`, `old/new mode`, `similarity index`) | Probe | Accepted — strict parser does not break real git output |
| New file (`--- /dev/null`) / deleted file (`+++ /dev/null`) | Probe | Both parse correctly |
| CRLF patch body | Probe | Accepted |
| Duplicate `diff --git` for one path | Probe | Rejected (header-count check) |
| Mode-only change with no hunks | Probe | Rejected |
| Nested `.git` path segment | Code + test `rejectsNestedVerifierGitMetadata` | Rejected |
| PIT timeout / non-zero exit with a parseable 100% report | Test `timedOutPitProcessIgnoresParseablePerfectReport` uses a **real** report proven by the **real** parser to score 100% | `PARTIALLY_VERIFIED`; parser never invoked |
| Worker throws `Error` (not `Exception`) with a secret path in the message | Test `workerFailureAlwaysBecomesATerminalSafeState`; observed in build log | Terminal `FAILED`; public message generic; detail only in server log |
| Path leaks in generated receipts | 10-pattern scan over my own corpus receipts | 0 hits; `<workspace>` mask present 8× |
| Similarly-named failure class (`dev.attacker.AssertionFailedError`) | Test | Reproduction rejected |
| Blank / wildcard test selectors | Code: `BundledCaseRepository.validate`, `LocalCaseLoader`, `SurefireReportParser.matches` | Rejected at manifest load on both hosted and CLI paths |

Regression guards confirmed present for every fix, including
`rejectsRenamesExplicitlyAtPreflight`,
`nonMinimalUnifiedDiffCanStillProduceCleanObservedScope`,
`usesObservedCountsWhenEquivalentPatchTextIsNonMinimal`,
`onePerfectMutantIsStillOnlyPartialEvidence`,
`detectsForbiddenFileThatWasAbsentFromPreflight`, and
`observedDiffSuppliesCanonicalChangedLines`.

---

## 6. Test and corpus results

**Command 1 — full `verify`, Java 21.0.7, offline:**

```
Tests run: 76, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS   Total time: 01:09 min
```

Matches the documented 76 discovered / 74 passed / 2 gated skips exactly. The
two skips are `EvaluationCorpusIntegrationTests`'
`@EnabledIfSystemProperty` tests — **gated, not passing**, in this run.

`BoundedProcessRunnerTests.timeoutTerminatesTheParentAndItsChildProcess`, which
flaked under load during my Milestone 3 review, passed here (IM-4 remediation:
larger budget plus early exit when the probe finishes first).

**Command 2 — gated corpus + determinism (the two skips above, explicitly enabled):**

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0   (227.2 s)
BUILD SUCCESS   Total time: 03:52 min
```

**Per-patch outcomes read from receipts my own run generated**
(`target/evaluation-receipts/`), not from Codex's recorded JSON:

| Patch ID | Verdict | Expected | Mutants | Required | Killed | Healthy | Scope provenance |
|---|---|---|---:|---:|---:|---|---|
| `plausible-distinct` | `REJECTED` | `REJECTED` | 0 | 2 | 0 | false | `OBSERVED_FILESYSTEM` |
| `correct-with-drift` | `PARTIALLY_VERIFIED` | `PARTIALLY_VERIFIED` | 1 | 2 | 1 | true | `OBSERVED_FILESYSTEM` |
| `minimal-robust` | `VERIFIED` | `VERIFIED` | 5 | 2 | 5 | true | `OBSERVED_FILESYSTEM` |
| `alternate-robust` | `PARTIALLY_VERIFIED` | `PARTIALLY_VERIFIED` | 1 | 2 | 1 | true | `OBSERVED_FILESYSTEM` |
| `build-bypass` | `REJECTED` | `REJECTED` | 0 | 2 | 0 | false | `PATCH_PREFLIGHT` |
| `compile-breaking` | `REJECTED` | `REJECTED` | 0 | 2 | 0 | false | `OBSERVED_FILESYSTEM` |

- **6 of 6 expected verdicts matched**, independently reproduced.
- **Exactly one `VERIFIED`**, and it carries 5 viable changed-line mutants
  against a minimum of 2. **Zero unsafe `VERIFIED`.**
- The three required public outcomes hold:
  `plausible-distinct` → `REJECTED`, `correct-with-drift` → `PARTIALLY_VERIFIED`,
  `minimal-robust` → `VERIFIED`.
- All six receipts are `schemaVersion: 2` with a non-empty `plainSummary` and
  3–4 `limitations` entries.

**My own absolute-path scan** over those six receipts — patterns `C:\Users`,
`C:/Users`, `Mac 48-70`, `Mac%2048-70`, `.patchreceipt-work`, `.cache/maven`,
`.cache\maven`, `file:/`, `chatgpt-projects`, `g-p-6a64f13` — returned **0 hits**,
with the `<workspace>` mask present 8 times (so sanitization demonstrably fired
rather than there being nothing to mask).

Receipt digests from my run differ from Codex's recorded values, which is
expected and correct: the digest covers `receiptId`, timestamps, and durations.
The determinism protocol compares *normalized* evidence, and it passed.

---

## 7. Evidence and documentation consistency

**Regenerated, not stale.** Both `docs/evidence/*.json` are `schemaVersion: 2`,
`recordedAt: 2026-07-27`, carry `requiredChangedLineMutants: 2`, and every corpus
row reports `scopeProvenance`. All six digests differ from the Milestone 3
values, so the corpus was genuinely re-run after remediation.

**Cross-checks that hold.** The claimed "11 additions and 1 deletion" for the
public robust patch matches `alternate-robust.patch` exactly. The claimed
26,069 ms packaged robust run, the 90-second budget
([application.properties](../../src/main/resources/application.properties):
`total-timeout-seconds=90`), and the two-mutant minimum all match the code and
the JSON. My independently measured corpus verdicts and mutant counts match
Codex's recorded values row for row.

**IM-2 properly discharged.** All three public candidates now have recorded
production-config durations — 14,780 / 22,393 / 26,069 ms against a 90-second
budget. My Milestone 3 concern (one candidate recorded at 63,116 ms against a
60-second budget) is resolved: worst case is now ~29% of budget, and my own
corpus runs were faster still.

**Honest where it should be.** EVALUATION.md's status table marks
`Production container` as **BLOCKED LOCALLY**, `Deployed p95 latency` and
`Three-person usability test` as **PENDING**, and the Claude re-review as
**REMEDIATED; SIGN-OFF PENDING**. The "Honest interpretation" section explicitly
disclaims deployed latency, provider isolation, arbitrary-repository safety,
design quality, security, performance, and concurrency. The harness-versus-
production timeout split (180 s / 120 s vs 90 s / 45 s) is stated plainly. I
found no overclaim.

**Inconsistencies found:** FA-1 (patch file/ID crossover), FA-2 (unreproducible
51/51), FM-1 (README omits the two-mutant minimum). Nothing else.

---

## 8. Container-readiness assessment

**Not proven — and I am not claiming it.** `docker: command not found` on this
machine, so I ran no container. The following is workflow and Dockerfile
*inspection only*.

The CI job in [.github/workflows/ci.yml](../../.github/workflows/ci.yml) is
correctly constructed for the gate. It POSTs a real run, polls to a terminal
state, short-circuits on `FAILED` with `docker logs`, and asserts four
substantive fields rather than grepping for a string:

```
.verdict            == VERIFIED
.schemaVersion      == 2
.scope.provenance   == OBSERVED_FILESYSTEM
.mutation.processHealthy == true
```

Supporting posture: non-root `USER 10001:10001`; `PATCHRECEIPT_RUNNER_OFFLINE=true`;
build stage warms `/app/.cache/maven` by running the real vertical slice, so PIT
and JUnit artifacts should resolve offline; health polled via `/actuator/health`;
runtime constrained to `--memory=1g --cpus=2`; a `workflow_dispatch` job runs the
gated corpus.

Residual risks I could not retire without Docker:

1. **Offline repository relocation.** The cache is warmed at
   `/workspace/.cache/maven` and used from `/app/.cache/maven` under a different
   UID. Maven `-o` with relocated `_remote.repositories` is a known failure mode.
   Only a real container run settles it.
2. **Timing on constrained hardware.** The Dockerfile sets no timeout override,
   so the container inherits the 90-second default. My local runs finished in
   ~22 s and the packaged run in 26 s, leaving good headroom — but a 2-CPU,
   1 GB runner is materially slower than this machine.
3. **No `HEALTHCHECK` instruction** in the Dockerfile; CI polls the endpoint
   externally instead, which is sufficient for CI but not for a platform that
   relies on container health.

Adding `changedLineMutants >= 2` to the CI assertions would be a cheap extra
guard, though `VERIFIED` already implies it.

---

## 9. Deployment blockers

1. **Production-container proof.** No container has completed a live
   `minimal-robust` run. This is the only blocker standing between the current
   state and public deployment. Discharged by running the existing CI
   `container` job (or an equivalent local Docker run) once and retaining the
   output.

Not blockers, but open before broader public claims: deployed p95 latency and
the three-person usability sessions, both already marked `PENDING` by Codex.

FA-1 and FA-2 should be fixed before submission for evidence hygiene, but
neither blocks the container gate.

---

## 10. Final recommendation

**Codex may proceed to the production-container gate.**

Separating what I actually established:

**Locally verified by execution (highest confidence).** C1, C2, C3, and CR-1 all
closed — each confirmed by re-running the original exploit against freshly
compiled code, not by reading documentation. The six-patch corpus at 6/6 with
exactly one `VERIFIED` and zero unsafe `VERIFIED`, reproduced from receipts my
own run generated on Java 21.0.7. Full `verify` at 76 run / 0 failures / 0 errors
/ 2 gated skips. Five-run determinism passing. Zero absolute-path leaks across
ten patterns. The two-mutant minimum demonstrably downgrading a previously
`VERIFIED` candidate. Renames rejected honestly at preflight; non-minimal diffs
accepted.

**Inferred from code inspection only (high but not executed confidence).** The
absence of any remaining stage/verdict divergence; that `VerdictPolicy` is the
sole verdict producer; the receipt/renderer/dashboard four-way parity for the new
fields; the polling, caching, and terminal-state behavior in `app.js` and
`RunRegistry` (verified by reading plus the passing unit tests, not by driving a
browser).

**Gated or not run.** The corpus and determinism tests are skipped by default and
only ran because I explicitly set their properties — treat the default build's
"2 skipped" as *not run*, never as *passed*. I did not drive the dashboard in a
browser, did not measure deployed latency, and did not run usability sessions.

**Not proven at all.** Container behavior. Docker is unavailable here; §8 is
inspection, not runtime evidence. Do not describe the container path as verified
in any submission material until the CI job has actually gone green and its
output is retained.

**No Critical finding remains.** Fix FA-1 (a two-file rename) and FA-2 (record
or drop the 51/51 figure) for evidence hygiene, run the container job, and this
is ready for public deployment.

One note on my own process, for the record: during the Milestone 3 review I
reported `BoundedProcessRunnerTests` as flaky after it failed in my run. That
failure was caused by load from a concurrent agent sweep I had launched myself;
the test passed in isolation then and passed cleanly here. Codex's IM-4
remediation is still a genuine improvement, but I want the record to show I
created the conditions that exposed it.

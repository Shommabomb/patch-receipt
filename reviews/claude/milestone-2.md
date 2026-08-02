I'll start by reading the documentation files and getting oriented in the repo.

Large maven cache in the tree. Let me get the actual source layout and work around the git ownership issue.

Now let me read the required documentation files.

Documentation is unusually thorough and self-critical. Now the core verdict logic.

Now the scope analyzer, parsers, and runner — the core safety and evidence path.

XmlSupport is correctly hardened against XXE. Let me read the runner and workspace layer.

Now the working-tree diff — the primary review target.

Let me empirically test a scope-parser hypothesis using the already-built classes (writing only to the scratchpad).

Confirmed the parser mis-attributes. Now let me verify whether JGit actually applies that hidden hunk.

Classpath broke on the spaces in the path. Let me stage the jars locally.

JGit swallowed the bare header into the open hunk. Let me test whether a hunk-terminating separator changes that.

Confirmed a real bypass. Let me pin down its two most damaging consequences.

Both confirmed end-to-end. Let me check existing test coverage and run the focused suite.

Build is green. Let me confirm two last details before writing up.

I did not create `reviews/claude/milestone-2.md` — you asked me not to create files, so the full review is below. The project tree is unchanged (`git status` matches the session start exactly); I ran the test suite and compiled throwaway probes into the scratchpad only.

---

# PatchReceipt — independent review (working tree, milestone 2)

**Reviewed:** commit `4ae1a0c` plus all uncommitted changes (12 modified, 4 untracked).
**Build check I ran:** `./mvnw -B -ntp -o "-Dtest=!VerticalSliceIntegrationTests" test` → **BUILD SUCCESS, 41 tests run, 0 failures, 0 errors, 2 skipped** (the two `@EnabledIfSystemProperty` corpus/determinism tests), 25.1 s. I did not run the live PIT vertical slice or a browser session.

Three findings below are backed by executable probes I compiled against `target/classes`; probe output is quoted verbatim. Everything else is code reading and is labelled as such.

---

## Critical — must fix before submission

### C1. The scope analyzer ignores any file whose hunks lack a `diff --git` header; the patch still applies in full

**Component:** [ScopeAnalyzer.java:42-82](src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java:42), specifically the `@@` branch at [:55-64](src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java:55) which reuses the stale `current` file, and the fall-through at [:79-81](src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java:79).

Only `^diff --git a/(.+) b/(.+)$` registers a file. A `@@` header is accepted whenever `current != null` — it never re-checks that the hunk belongs to the file `current` names. JGit's `ApplyCommand`, meanwhile, parses traditional `---`/`+++` entries.

**Evidence.** Two patches, identical second file, bundled policy (`forbiddenGlobs: [pom.xml, …, src/test/**]`):

```
=== CONTROL (two diff --git headers) ===
  filesChanged   : 2
  file           : .../CheckoutCalculator.java  expected=true  forbidden=false
  file           : pom.xml                      expected=false forbidden=true
  hardViolations : [Forbidden path changed: pom.xml]

=== PROBE (second file via bare ---/+++, separated by a line reading "diff") ===
  filesChanged   : 1
  file           : .../CheckoutCalculator.java  expected=true  forbidden=false changedLines=[2, 11]
  hardViolations : []
  warnings       : []
  apply: SUCCEEDED
  calc patched     = true
  pom.xml POISONED = true
  *** BYPASS: forbidden file modified with clean scope ***
```

The same shape against `src/test/**`:

```
CASE A: hidden edit to forbidden src/test/**
  scope filesChanged : 1
  scope sees src/test/.../CheckoutCalculatorTest.java ? false
  hard=[] warn=[]
  apply : SUCCEEDED
  forbidden file modified on disk = true
```

And the file cap:

```
CASE C: real files in patch : 6 (maximumFiles policy = 2)
        filesChanged report : 1     hard=[] warn=[]
```

Note the separator matters: a bare blank line or `Index:` line makes JGit throw `PatchFormatException`, so this is deliberate crafting, not accidental. A line containing exactly `diff` terminates JGit's hunk body cleanly while `ScopeAnalyzer` treats it as `inHunk = false`.

**Why it matters.** This is the one control the product is built on. `pom.xml` and `src/test/**` are the top two forbidden globs, and the `build-bypass` demo candidate exists specifically to prove PatchReceipt catches pom edits. A patch can rewrite the project's own regression tests while the receipt attests *"All original tests pass unchanged"* and *"Patch stays within the declared production scope"* with `filesChanged: 1`. The sealed verifier pack is safe (it is re-injected after apply, at [VerificationEngine.java:221](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:221)) — but the project's own tests are not. Not reachable through the hosted app (only three bundled patch IDs), so this is a CLI-path and product-claim defect, not a hosted RCE.

**Fix.** Do not try to make the hand-written parser match JGit — that race is unwinnable, and `docs/SECURITY.md:89` already concedes the parser is incomplete. Instead add **post-apply scope reconciliation** in the engine: both `baseline/` and `patched/` are already materialized side by side at [VerificationEngine.java:117-122](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:117), so after `patchApplier.apply` walk both trees, compute the actual changed/added/removed file set, and re-run `policy.forbiddenGlobs`/`expectedPaths`/`maximumFiles` against *observed* changes. Any file changed on disk but absent from the preflight evidence is a hard violation. That is ~40 lines, closes this entire bug class permanently, and gives the receipt a provenance upgrade worth talking about. As an immediate cheap guard, also hard-fail when the count of `^\+\+\+ ` headers differs from the count of `diff --git` headers.

---

### C2. A blank context line silently drops later additions from the changed-line set, weakening mutation scoring

**Component:** [ScopeAnalyzer.java:75-81](src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java:75) → consumed at [VerificationEngine.java:318](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:318) → [PitestReportParser.java:48](src/main/java/dev/patchreceipt/parsers/PitestReportParser.java:48).

A context line for an empty source line is `" "`. Many editors, mail paths, and — critically — LLMs emit `""` instead. That hits the `else` branch, sets `inHunk = false`, and every remaining `+` line in the hunk is dropped. JGit accepts the same line as context and applies the hunk.

**Evidence.** Same patch, applied and analyzed:

```
scope additions recorded : 1  (2 '+' lines are present)
scope changedLines       : [11]
apply                    : SUCCEEDED
addedBeforeBlank on disk : true
addedAfterBlank  on disk : true

Actual new-file line numbers of the two added lines:
  line 11 : addedBeforeBlank
  line 14 : addedAfterBlank
```

**Why it matters.** `changedLinesByPath` is the mutation denominator. A line missing from that set means PIT mutants on that line are filtered out at `PitestReportParser.java:48` and can never enter `changed`. A **surviving mutant on a dropped line cannot lower the score** — so a patch that should be `PARTIALLY_VERIFIED` (score below 80%) reports 100% and becomes `VERIFIED`. This requires no adversary, and it happens in exactly the input domain PatchReceipt targets: AI-authored unified diffs. The receipt's `additions` count is also simply wrong. Existing [ScopeAnalyzerTests.java](src/test/java/dev/patchreceipt/scope/ScopeAnalyzerTests.java) never exercises a hunk body containing a non-`␣+-` line.

**Fix.** Treat `line.isEmpty()` as a context line (`newLine++`). Then make the remaining `else` a **hard violation** (`"Unparseable line in hunk"`) rather than a silent `inHunk = false` — a scope analyzer must fail closed. Additionally, capture the hunk header's declared `+count` at [:61](src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java:61) and hard-fail when the observed body length disagrees; that single check would have caught both C1 and C2.

---

### C3. A failed or timed-out PIT run with a parseable passing report yields `VERIFIED`, and the stage prints a false summary

**Component:** [VerificationEngine.java:334-355](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:334) and [VerdictPolicy.java:26-30](src/main/java/dev/patchreceipt/engine/VerdictPolicy.java:26).

The engine computes `mutationPass = mutationRun.successful() && mutation.conclusive() && score >= required` — but that boolean only sets the **stage status**. The verdict is decided by `verdictPolicy.decide(blockingReasons, warnings, mutation)`, which never sees the process result. `ProcessResult.successful()` is `!timedOut && exitCode == 0`.

So when PIT finishes writing `mutations.xml` but the Maven process then times out or exits non-zero:

- `mutation.conclusive() == true`, `changedLineScore() == 100.0 >= 80.0` → `VerdictPolicy` adds no warning → **`VERIFIED`**.
- `mutationPass == false` → stage recorded as **`WARN`**.
- The summary branch at [:347-352](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:347) falls to `"Mutation score %.1f%% is below the %.1f%% gate."` → the receipt literally prints **"Mutation score 100.0% is below the 80.0% gate."**

[VerdictPolicyTests.java:57-65](src/test/java/dev/patchreceipt/engine/VerdictPolicyTests.java:57) confirms the policy half directly: `decide(List.of(), List.of(), mutation(true, 100, 80))` → `VERIFIED`. (Code-reading finding — I did not induce a PIT timeout.)

**Why it matters.** This answers `CLAUDE_HANDOFF.md`'s explicit question — *"Can a timed-out or truncated PIT report ever produce anything stronger than `PARTIALLY_VERIFIED`?"* — with **yes**. `CODEX_JOURNAL.md` records this exact scenario happening ("PIT to time out after starting its XML report"); the narrow fix landed on the *parse* path but not the *process-result* path. Worse than the verdict is the artifact: a downloadable receipt that shows `mutation: WARN` next to `verdict: VERIFIED` and states a numeric falsehood. For a product whose thesis is trustworthy evidence, that is a submission blocker.

**Fix.** Pass mutation-process health into the decision. Either add a `processHealthy` flag to `MutationEvidence` and require it in `VerdictPolicy` alongside `conclusive()`, or set `mutation = unavailableMutation(manifest)` whenever `!mutationRun.successful()`, so an unhealthy run is inconclusive by construction. Separately, fix the summary branch so it only claims "below the gate" when the score is actually below the gate; add a third branch for "the mutation process did not complete successfully".

---

## Important — should fix if time allows

### I1. Skipped tests count as passing, and the edge-case summary reports the wrong number

**Component:** [TestEvidence.java:18-20](src/main/java/dev/patchreceipt/domain/TestEvidence.java:18); summary at [VerificationEngine.java:293-295](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:293).

`successful()` is `tests > 0 && failures == 0 && errors == 0` — `skipped` is not consulted. A suite of 9 skipped tests satisfies both `tests() > 0` and `successful()`, so `edgesPass == true`. The stage then formats `"%d sealed dynamic edge cases pass".formatted(edgeCases.tests())` using the **total**, printing *"9 sealed dynamic edge cases pass"* when zero executed — while the new dashboard metric renders `"0 / 9"` from `ratio()` at [RunRegistry.java:180](src/main/java/dev/patchreceipt/web/RunRegistry.java:180).

**Why it matters.** A mandatory correctness gate that passes on zero executed assertions, plus a visible self-contradiction between the receipt text and the dashboard tile. Not reachable via the three bundled patches, but it is a real hole in the gate and a demo-credibility risk if it ever fires.

**Fix.** `successful()` → `tests > 0 && failures == 0 && errors == 0 && skipped == 0`. Format the stage summary with `evidence.passed()`, not `evidence.tests()`.

### I2. Blocking reasons and warnings are never sanitized — absolute host paths reach user-visible evidence

**Component:** [VerificationEngine.java:393](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:393). `workspaceManager.sanitize` is called at only four sites ([:177](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:177), [:263](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:263), [:331](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:331), [:354](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:354)) — all stage logs.

`blockingReasons.add("Verification engine error: " + safeMessage(exception))` passes a raw exception message straight into the receipt. An `IOException` from `Files.createDirectory` or `materializeProject` carries the absolute path. That string then flows to the dashboard "Decisive findings" via [RunRegistry.java:167](src/main/java/dev/patchreceipt/web/RunRegistry.java:167), into the HTML/Markdown receipts, and into the plain-language summary via [ReceiptLanguage.java:36](src/main/java/dev/patchreceipt/receipt/ReceiptLanguage.java:36). `sanitize` also masks only the workspace — not `patchreceipt.maven-user-home`, which `MavenRunner` puts on every command line as `-Dmaven.repo.local=<absolute path>`.

**Why it matters.** Violates the `AGENTS.md` invariant "Remove absolute workspace paths from user-visible evidence." On a laptop demo the leaked path contains the operator's username.

**Fix.** Sanitize on write, not on read: route every `blockingReasons.add` / `warnings.add` through a helper that applies `sanitize`, and extend `sanitize` to mask the Maven user home and `System.getProperty("user.home")`.

### I3. `snapshot()` reads the receipt before the state, so a poll at the completion instant returns `COMPLETED` with nulls

**Component:** [RunRegistry.java:107](src/main/java/dev/patchreceipt/web/RunRegistry.java:107) reads `job.receipt`; [:129](src/main/java/dev/patchreceipt/web/RunRegistry.java:129) reads `job.state`. The worker at [:92-95](src/main/java/dev/patchreceipt/web/RunRegistry.java:92) writes receipt → completedAt → currentStage → state.

Interleaving: snapshot reads `receipt == null`; worker completes; snapshot reads `state == COMPLETED`. Result: `verdict`, `summary`, `evidence`, `receipts` all null. [app.js:102](src/main/resources/static/app.js:102) then evaluates `run.verdict.toLowerCase()` and throws a `TypeError`.

**Why it matters.** Narrow window, but it opens at precisely the moment a 900 ms poll is most likely to land — the end of the demo run. The interval isn't cleared, so the next tick recovers, but you get a console error and a visible flicker in front of judges.

**Fix.** Read `RunState state = job.state;` **first**, then the receipt. The inverse skew (`RUNNING` with a receipt present) is harmless. Or guard: `if (state == COMPLETED && receipt == null) state = RUNNING;`.

### I4. A worker that throws leaves a job polling forever and never purged

**Component:** [RunRegistry.java:83-96](src/main/java/dev/patchreceipt/web/RunRegistry.java:83) has no try/catch; [:219-225](src/main/java/dev/patchreceipt/web/RunRegistry.java:219) purges only when `state == RunState.COMPLETED`.

`engine.verify` catches `Exception`, but not `Error`, and `receipt(...)`/`digestService.attachDigest` execute *inside* that catch block — a failure there escapes. The job then stays `RUNNING` forever: never purged (unbounded map growth), and [app.js:77](src/main/resources/static/app.js:77) polls at 900 ms with no cap or deadline. `docs/SECURITY.md:135` correctly discloses there is no whole-run timeout; the UI has no client-side one either.

**Fix.** Wrap the `execute` body in `try/catch (Throwable)` that sets a terminal state and a synthetic error receipt. Purge non-`COMPLETED` jobs older than `RETENTION` too. Add a client-side poll ceiling (~3 min) that renders a clear timeout message.

### I5. `mutation.targetClasses` / `targetTests` are parsed but never used

**Component:** [CaseManifest.java:45-54](src/main/java/dev/patchreceipt/casepack/CaseManifest.java:45); the PIT invocation at [VerificationEngine.java:309-311](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:309) is `List.of("-q", "org.pitest:pitest-maven:mutationCoverage")` with no targeting flags. A grep confirms the only consumer of the `mutation` block is `minimumChangedLineScore`. All real targeting lives in the fixture's own [project/pom.xml:51-57](src/main/resources/demo-cases/checkout-coupons/project/pom.xml:51).

**Why it matters.** [PatchReceiptCli.java:216-221](src/main/java/dev/patchreceipt/cli/PatchReceiptCli.java:216) scaffolds these keys for local users, implying configuration that has no effect. If a local project's pom lacks the pitest plugin, the goal resolves an unpinned plugin version — which fails outright under `-o`. The `init` README says nothing about the pom requirement.

**Fix.** Either forward them (`-DtargetClasses=…`, `-DtargetTests=…`) on the PIT invocation, or delete them from the schema and the scaffold and document the pitest-plugin prerequisite in the generated README.

### I6. Mutation evidence does not disclose changed files that produced zero mutants

**Component:** [PitestReportParser.java:40-79](src/main/java/dev/patchreceipt/parsers/PitestReportParser.java:40); source mapping hardcodes `"src/main/java/"` at [:45-47](src/main/java/dev/patchreceipt/parsers/PitestReportParser.java:45).

The score is `killed / changed` where `changed` counts only mutants PIT actually generated on changed lines. A changed production file that PIT didn't target — or that lives outside `src/main/java` — contributes nothing and is not reported as a gap. `correct-with-drift` changes `AuditBanner.java`, which `targetClasses` excludes; that file is mutation-untested, and the receipt says "100% changed-line score" without qualification. Drift already forces `PARTIALLY_VERIFIED` there, so the demo is honest by accident, not by design.

**Fix.** Compute per-file changed-line mutant counts. Emit a warning (which forces `PARTIALLY_VERIFIED`) when any changed production file yields zero viable mutants, and add a `filesWithoutMutants` field to `MutationEvidence` so the receipt shows the gap explicitly.

### I7. The plain-language summary hardcodes the demo domain and overstates the guarantee

**Component:** [ReceiptLanguage.java:18-19](src/main/java/dev/patchreceipt/receipt/ReceiptLanguage.java:18) and [:31](src/main/java/dev/patchreceipt/receipt/ReceiptLanguage.java:31), locked in by [ReceiptLanguageTests.java:34-35](src/test/java/dev/patchreceipt/receipt/ReceiptLanguageTests.java:34).

Two problems in one string:

1. **"fixed the coupon bug"** is baked into a renderer shared by the CLI. Run `patchreceipt verify` on any local project and the receipt says the patch fixed the coupon bug. A test asserts this wording, so it will not drift accidentally.
2. **"It is safe to approve."** is an unmeasurable safety claim. The gates prove: the named reproduction flipped, six regressions and nine sealed edge cases pass, one file changed as declared, and ≥80% of changed-line mutants died. None of that establishes safety. `AGENTS.md:35` requires public claims to stay measurable, and this is the single most prominent sentence in the entire product — it is the first line of the dashboard and the receipt header.

**Fix.** Derive the noun from `receipt.caseTitle()` rather than hardcoding it. Replace the VERIFIED sentence with something the evidence supports, e.g. *"Every check PatchReceipt runs passed: the bug is fixed, the existing tests still pass, and the changed lines survived tampering. PatchReceipt did not review design, security, or performance."* That last clause is the honesty differentiator, and it costs one line.

### I8. The plain summary reaches HTML and Markdown but not JSON

**Component:** [HtmlReceiptRenderer.java:88,92](src/main/java/dev/patchreceipt/receipt/HtmlReceiptRenderer.java:88) and [MarkdownReceiptRenderer.java:31-32](src/main/java/dev/patchreceipt/receipt/MarkdownReceiptRenderer.java:31) both call `ReceiptLanguage.plainSummary`. [JsonReceiptRenderer.java:19](src/main/java/dev/patchreceipt/receipt/JsonReceiptRenderer.java:19) serializes the `VerificationReceipt` record, which has no such field.

**Why it matters.** The three formats no longer carry the same content, and because the text is derived rather than stored, `ReceiptDigestService` does not cover it — the human-facing verdict sentence is outside the integrity digest. `AGENTS.md:19` asks all three to originate from one canonical model.

**Fix.** Add `plainSummary` as a real field on `VerificationReceipt`, populated once in `VerificationEngine.receipt(...)`. All three renderers then read it and the digest covers it.

### I9. CI never completes a verification inside the container, and the corpus protocols are gated off

**Component:** [ci.yml:80-99](.github/workflows/ci.yml:80) — the container job checks `/actuator/health` and greps `/api/v1/cases` for `"minimal-robust"`, then stops. [EvaluationCorpusIntegrationTests.java:38,71](src/test/java/dev/patchreceipt/EvaluationCorpusIntegrationTests.java:38) require `-Dpatchreceipt.evaluation=true` / `-Dpatchreceipt.determinism=true`, which no workflow sets.

Two consequences. First, **no run has ever been proven to complete inside the offline container.** The Dockerfile warms the cache at `/workspace/.cache/maven` and the runtime uses `/app/.cache/maven` under UID 10001 with `-o`; a relocated local repository under Maven offline mode is a well-known failure mode, and the first person to click "Verify" on the deployment would be the one to discover it. Second, the strongest claim in `docs/EVALUATION.md` — 6/6 corpus verdicts, zero unsafe `VERIFIED` — is a one-time local measurement CI does not defend, and `EVALUATION.md:5-10` already admits those digests are stale after milestone 2 changed the verifier-pack hash.

**Fix.** Add ~6 lines to the container job: `POST /api/v1/runs {caseId, patchId: "minimal-robust"}`, poll `/api/v1/runs/{id}` until `COMPLETED`, assert `"verdict":"VERIFIED"`. Add a `workflow_dispatch` job that sets `-Dpatchreceipt.evaluation=true` so the corpus is re-provable on demand.

### I10. No `Cache-Control` on the run-status endpoint

**Component:** [RunApiController.java:58-61](src/main/java/dev/patchreceipt/web/RunApiController.java:58); [app.js:81](src/main/resources/static/app.js:81) polls without a cache-buster.

Spring sets no cache headers here, and `fetch()` defaults to `cache: "default"`. Behind Railway's proxy, a heuristically cached in-progress snapshot freezes the dashboard mid-run. Receipt endpoints are immutable per-UUID so they are fine.

**Fix.** `Cache-Control: no-store` on `/api/v1/runs/**`.

---

## Minor — polish only

| # | Component | Finding |
|---|---|---|
| M1 | [app.js:117](src/main/resources/static/app.js:117) | `href` interpolated into `<a>` unescaped. Values are server-built UUID paths today, so not exploitable — but it is the one unescaped sink in an otherwise clean file. Wrap in `escapeHtml`. |
| M2 | [app.js:142](src/main/resources/static/app.js:142) | `stage.status.toLowerCase()` interpolated into `class=` unescaped. Enum-derived, so safe; escape for consistency. |
| M3 | [ReceiptLanguage.java:36](src/main/java/dev/patchreceipt/receipt/ReceiptLanguage.java:36) | Locale-sensitive `toLowerCase()`. Use `Locale.ROOT`. |
| M4 | [RunRegistry.java:201-217](src/main/java/dev/patchreceipt/web/RunRegistry.java:201) | Answering handoff Q4: suppressing `generatedContractCases()[N]` is narrowly scoped and safe **as long as the message carries the case name** — which the milestone-2 verifier change now guarantees for assertions. But if a generated case fails by exception, `message` is blank, the code falls back to `failure.type()`, and the finding degrades to a bare `java.lang.NullPointerException` with no identifier at all. Also: `findings` is a `LinkedHashSet`, so two distinct cases failing with identical text silently collapse, and `.limit(3)` truncates with no "and N more". |
| M5 | [BoundedProcessRunner.java:39-46](src/main/java/dev/patchreceipt/runner/BoundedProcessRunner.java:39) | `truncated` is not set when only the line separator is clipped (`line.length() < remaining < value.length()`). The reader virtual thread can also outlive `reader.join(3s)`. |
| M6 | [VerificationEngine.java:476-480](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:476) | Reproduction accepts any failure type whose *simple* name matches, so an `AssertionFailedError` from any package qualifies. |
| M7 | [SurefireReportParser.java:77-84](src/main/java/dev/patchreceipt/parsers/SurefireReportParser.java:77) | `matches()` returns true for every suite when `requestedClass` is blank or `*`, silently aggregating unrelated suites for a malformed local manifest. Reject a blank selector instead. |
| M8 | [HtmlReceiptRenderer.java:28-34](src/main/java/dev/patchreceipt/receipt/HtmlReceiptRenderer.java:28) | The standalone receipt has no CSP meta tag. Escaping is complete so nothing is exploitable, but `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">` is a free defence-in-depth line on a file judges will open locally. |
| M9 | [docs/EVALUATION.md:32-33](docs/EVALUATION.md:32) | Stale counts: says 37 focused / 38 milestone-2. The working tree now runs **41 discovered, 39 passed, 2 gated skips**. |
| M10 | [PROJECT_PLAN.md:44](PROJECT_PLAN.md:44), [README.md:48](README.md:48) | Both say execution is "offline in the production container". Only Maven's `-o` flag is set; there is no network egress restriction. `docs/SECURITY.md:102` states this precisely — align the other two to it. |
| M11 | [RunRegistry.java:193-199](src/main/java/dev/patchreceipt/web/RunRegistry.java:193) | The Scope tile renders a bare count ("1 file"). A non-expert cannot tell whether that is good. Render "Clean" / "1 unexpected file" instead. |

---

## Positive findings

These are real strengths, verified rather than assumed:

- **P1 — The hosted allowlist genuinely holds.** [BundledCaseRepository.java:47,123](src/main/java/dev/patchreceipt/casepack/BundledCaseRepository.java:47) uses exact set membership and exact string equality, evaluated *before* `caseRoot()` builds any resource path, so `caseId` cannot traverse. `POST /api/v1/runs` accepts a two-field `@Valid` record and nothing else. Answering question 2: **no, the hosted app cannot execute anything outside the three bundled patches.**
- **P2 — XML parsing is correctly hardened.** [XmlSupport.java:19-25](src/main/java/dev/patchreceipt/parsers/XmlSupport.java:19) disables DOCTYPE, both entity classes, external DTD/schema access, XInclude, and entity expansion. This is the complete set, and it is easy to get wrong.
- **P3 — Escaping is complete.** I traced every sink in [app.js](src/main/resources/static/app.js) and [HtmlReceiptRenderer.java](src/main/java/dev/patchreceipt/receipt/HtmlReceiptRenderer.java). Every receipt-derived string passes through `escapeHtml` / `html()`, both of which cover `& < > " '`. The bug report uses `textContent`. **I found no reachable HTML or JavaScript injection path** — answering question 4.
- **P4 — Verdict precedence is correct and well tested.** `blockingReasons` short-circuits before any mutation logic, and [VerdictPolicyTests](src/test/java/dev/patchreceipt/engine/VerdictPolicyTests.java) covers the downgrade-resistance case explicitly.
- **P5 — Mutation interpretation is conservative in every direction that matters.** `NO_COVERAGE` counts in the denominator but not as killed; `TIMED_OUT`/`RUN_ERROR`/`MEMORY_ERROR` force `conclusive == false`; `changed == 0` forces inconclusive. All three fail toward `PARTIALLY_VERIFIED`. This is the honest choice and it is not the obvious one.
- **P6 — Reproduction validity is strict.** [VerificationEngine.java:471-481](src/main/java/dev/patchreceipt/engine/VerificationEngine.java:471) requires not-timed-out, non-zero exit, exactly one test, exactly one failure, zero errors, and a matching failure type. Compile errors and timeouts cannot masquerade as reproduction — the `compile-breaking` and `build-bypass` candidates cannot be misread as passing evidence.
- **P7 — Process execution is genuinely injection-free.** `ProcessBuilder(List<String>)` throughout; the Windows Plexus Classworlds launcher at [MavenRunner.java:84-114](src/main/java/dev/patchreceipt/runner/MavenRunner.java:84) really does eliminate shell parsing rather than merely quoting around it.
- **P8 — Workspace boundaries are enforced on both create and delete.** [WorkspaceManager.java:27,53](src/main/java/dev/patchreceipt/engine/WorkspaceManager.java:27) both check `startsWith(root)` and reject the root itself; the DOS read-only clearing is correctly scoped to files already inside that boundary, so the Windows JGit cleanup fix did not widen the deletion surface.
- **P9 — The documentation is unusually honest.** `docs/SECURITY.md`'s residual-risk column, the explicit "hashes are not signatures" paragraph, and `docs/EVALUATION.md`'s `PENDING` markers are more candid than most shipped products. The security model is stated *narrower* than the implementation, which is the correct direction.
- **P10 — The working tree builds clean.** 41 tests, 0 failures, 0 errors, 2 intentionally gated skips, 25.1 s.
- **P11 — The milestone-2 dashboard does clear the 20-second bar** (question 9). Verdict badge → one-sentence plain summary → four metric tiles → decisive findings is a genuinely good information hierarchy, and collapsing the technical summary behind `<details>` was the right call. Two things still block a true non-expert: "Mutation / 100%" is unexplained jargon at the tile level, and the Scope tile is a bare count (M11). Adding a six-word caption under each tile ("did we break anything?", "did we try to break the fix?") would finish the job.

---

## Direct answers to your ten questions

1. **Can an unsafe patch get `VERIFIED`?** Yes — three ways: C1 (hidden forbidden-file edit, scope reports clean), C2 (dropped changed lines shrink the mutation denominator so a surviving mutant is invisible), C3 (unhealthy PIT process with a passing report). None are reachable through the three bundled hosted patches.
2. **Can the hosted app execute anything outside the allowlist?** No. P1.
3. **Timeout / process / workspace / path / diff / log handling.** Process, workspace, and path handling are sound (P7, P8). Diff handling is the weak point (C1, C2). Log handling is good for stage logs but blocking reasons and warnings bypass sanitization entirely (I2).
4. **Receipt-driven HTML/JS injection?** None found. P3. Two unescaped-but-uncontrollable interpolations noted as M1/M2.
5. **Are the verdict rules logically correct?** The rules themselves, yes (P4). The *inputs* to them are not always: C3 feeds mutation evidence that the stage itself flagged as WARN, and I1 feeds a "successful" suite that executed nothing.
6. **Is mutation evidence interpreted honestly?** The scoring is conservative and correct (P5). The *scope* of what was mutated is not disclosed — C2 and I6 both let a receipt claim a clean changed-line score over an incomplete line or file set.
7. **Does scope drift reliably prevent `VERIFIED`?** When the analyzer sees the file, yes — [ScopeAnalyzer.java:99-101](src/main/java/dev/patchreceipt/scope/ScopeAnalyzer.java:99) → `warnings` → `PARTIALLY_VERIFIED`, cleanly. The failure is upstream: C1 means a drifted file can be invisible to the analyzer entirely.
8. **Cached or stale results?** Receipts are immutable per-UUID, so no. The run-status endpoint lacks `no-store` (I10), and a wedged job polls forever (I4). Static assets are correctly cache-busted with `?v=0.2`, though that requires a manual bump every deploy.
9. **20 seconds for a non-expert?** Close to yes. P11.
10. **Biggest confidence win without expanding scope?** See below.

---

## Recommended next milestone

Ordered by value per hour, all inside the existing six-day envelope:

1. **Post-apply scope reconciliation** (~40 lines, half a day). Both trees are already on disk. Diffing them and re-running the policy against *observed* changes closes C1 permanently, makes C2 impossible to exploit, and upgrades the receipt from "we parsed the diff you gave us" to "we verified what actually changed on disk." That is a stronger product claim than anything currently in the deck, and it is the single highest-value change here.
2. **Fix C2 and C3 directly** (~1 hour). Treat `""` as context, make unparseable hunk lines a hard violation, and gate the verdict on PIT process health.
3. **Add a "what this run did not prove" block to the receipt and dashboard** (~1 hour) — *my answer to question 10.* Your documentation is already more honest than the artifact judges will actually look at. `docs/SECURITY.md` and `docs/EVALUATION.md` are candid; the receipt says "It is safe to approve." Move that candour into the receipt: one short list stating that PatchReceipt did not review design, security, performance, or concurrency; that mutation covered N of M changed lines; and that a clean scope means "no unexpected file changed", not "the change is correct." Judges reward a team that names its own limits, and this converts an existing strength into the thing being demoed. Fold I7's wording fix into the same change.
4. **Prove one full run inside the container in CI** (~6 lines of YAML). This is the only evidence gap that could turn into a live failure in front of an audience (I9).
5. **Then regenerate the corpus and determinism evidence** and refresh the stale numbers in `docs/EVALUATION.md` (I9, M9) — after 1–3 land, since they change verdict-relevant behaviour and the digests would need regenerating again otherwise.

I would leave I5, I6, I8 and everything in the Minor table until after the submission freeze.

---

## Verdict

**Ready for demo. Not ready for public deployment.**

The demo path is solid: the hosted allowlist genuinely holds, the three bundled candidates exercise all three verdicts through code paths I traced end to end, the working tree builds clean at 41 tests, and the milestone-2 dashboard is a real improvement that tells a coherent 20-second story. Nothing in C1–C3 is reachable through the three hosted patch IDs, so none of them can surface during a scripted demo.

It is not ready for public deployment for two separate reasons. First, correctness: the scope analyzer has a demonstrated bypass (C1) and a demonstrated accidental-corruption path (C2) that together undercut the product's central claim, and the mutation path can emit a self-contradicting receipt (C3). Second, evidence: no verification run has ever been proven to complete inside the production container, and the corpus digests backing the strongest claims in `docs/EVALUATION.md` are stale by the project's own admission.

One framing note for the submission, since `CLAUDE_HANDOFF.md` asks whether any claim is unsupported. The README states the safety boundary in terms of controls that exist, which is fair — but "Reject forbidden edits" (index.html) and "Preflight forbidden globs" (`docs/SECURITY.md` threat table) are stronger than what C1 shows the implementation currently delivers. Until item 1 above lands, the honest phrasing is that the preflight rejects forbidden edits *in well-formed `git diff` output*, which is exactly the caveat `docs/SECURITY.md:89` already makes about the parser — it just needs to be carried into the threat table and the dashboard copy.


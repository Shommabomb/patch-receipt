# Codex journal

This journal records the primary Codex implementation, testing, debugging, review, and delivery work. Times use Europe/London.

## 2026-07-25 - Planning and repository foundation

### Intent

Turn the approved PatchReceipt plan into a reliable six-day hackathon build, starting with a proof of the hardest technical loop before investing in presentation polish.

### Environment findings

- The fresh project directory was empty.
- Amazon Corretto JDK 25.0.3 and Git 2.50 are available.
- Maven, Gradle, Docker, and Railway CLI are not installed globally.
- The application targets Java 21 for portable deployment.

### Decisions

- Bootstrapped Spring Boot 4.1.0 from the official Spring Initializr.
- Pinned Maven Wrapper 3.3.4 to Maven 3.9.16 with distribution checksum validation.
- Selected one Maven module with logical package boundaries.
- Kept hosted execution restricted to bundled, hash-allowlisted cases.
- Defined Claude as a read-only independent reviewer using tracked handoff files.

### Next proof

Create the checkout fixture, reproduce its duplicate-coupon bug, apply the robust patch, pass regressions and independent tests, run PIT, and emit a JSON receipt before beginning the dashboard.

### Supply-chain correction

The first wrapper launch rejected a SHA-256 value obtained from a secondary package listing. Codex did not disable checksum validation. It downloaded the Maven Central binary separately, verified that binary's SHA-512 against Apache's official published SHA-512, calculated the matching SHA-256, and pinned that verified value in `maven-wrapper.properties`.

## 2026-07-25 - Vertical-slice implementation, first debug loop

### Implemented

- Bundled `checkout-core` baseline project and sealed verifier pack.
- Three judge-facing unified diffs.
- Canonical domain evidence types, case loader, scope analyser, bounded process runner, Surefire and PIT parsers, verification state machine, and JSON receipt renderer.

### First failed check

`VerticalSliceIntegrationTests` failed while loading Spring, before executing the fixture. Spring Boot 4 configures Jackson 3 for the web stack; the YAML dependency and initial receipt layer used Jackson 2, so no Jackson 2 `ObjectMapper` bean existed.

### Correction

Keep Jackson 2 private to manifest and receipt serialization rather than replacing Spring Boot 4's web mapper. Receipt timestamps are canonical ISO-8601 strings, so the private mapper needs no Java-time module. Added `.mvn/maven.config` because `MAVEN_USER_HOME` pins the wrapper distribution but does not by itself relocate Maven's dependency repository.

### Second failed check

The engine reached the real baseline stage, but Windows `cmd.exe /c` split the absolute wrapper path at the space in the user profile. The receipt correctly returned `REJECTED` with the process log rather than misclassifying the missing test execution as reproduction.

### Correction

On Windows the bounded runner now launches Maven's Plexus Classworlds entry point directly from the checksum-verified wrapper distribution. This avoids shell parsing entirely and preserves argument boundaries for paths containing spaces.

## 2026-07-25 - Vertical slice proved and optimised

### First complete receipt

The first complete end-to-end run produced `VERIFIED` with:

- The baseline regression suite passing.
- The named reproduction failing by `AssertionFailedError` before the patch.
- The same reproduction passing after the patch.
- Six original regressions and nine sealed dynamic edge cases passing.
- Four of four viable changed-line mutants killed for a 100% score.
- One expected production path and no scope warnings.

The first cold engine run took 70,972 ms. A warmed run took 53,804 ms because the engine paid for six separate Maven launches.

### Runtime optimisation

Codex combined the independently selected baseline regression and reproduction tests into one Maven invocation, then combined patched reproduction, unchanged regressions, and edge cases into a second invocation. Surefire XML remains parsed separately for each mandatory gate. PIT remains a third isolated invocation.

A judge-facing live browser run of the robust patch completed in **27,036 ms**, below the 45-second milestone target and the 60-second public target. It retained 100% changed-line mutation evidence.

### Windows sandbox diagnosis

Restricted local execution caused Java 25 `Path.toRealPath()` to throw `AccessDeniedException` for readable project files. `javac` calls that method while closing classpath ZIP files. This is specific to the development sandbox rather than PatchReceipt; the same offline command succeeds outside that restriction. The project-local cache and workspace boundaries remained unchanged.

### Evidence-model determinism correction

The expanded scope tests showed changed-line sets could iterate as `7, 6, 5`. The canonical model now stores an unmodifiable sorted set, making receipt serialization deterministic.

### Judge-facing browser proof

The live web flow produced:

- `plausible-distinct` → `REJECTED`, with three independent edge-case failures and mutation skipped.
- `correct-with-drift` → `PARTIALLY_VERIFIED`, with all correctness checks and mutation passing but an unexpected production path.
- `minimal-robust` → `VERIFIED`, with every gate passing.

JSON, Markdown, and standalone HTML receipt endpoints all returned HTTP 200, matching verdicts, and the same digest-bearing canonical evidence.

## 2026-07-25 - Parallel Codex sidecars

Two bounded Codex subagents worked on disjoint files while the primary Codex agent implemented the engine, CLI, API, and dashboard:

- Receipt sidecar: deterministic Markdown and escaped standalone HTML renderers plus three parity tests.
- Adversarial-test sidecar: fifteen scope, Surefire, and PIT parser tests.

The primary agent reviewed and integrated their shared-workspace changes. Claude Code has not yet reviewed the milestone.

## 2026-07-25 - Hardening, evaluation, and submission assets

### Product hardening

Codex expanded the focused suite to 33 passing non-end-to-end tests covering
verdict precedence, hosted allowlisting, manifest hashes, CLI consent,
malformed and hostile diffs, corrupt Surefire/PIT reports, process timeout,
child-tree termination, log truncation, receipt parity and escaping, queue
saturation, unknown API IDs, and receipt downloads.

The browser then exercised all three hosted candidates through the real
asynchronous API. JSON, Markdown, and standalone HTML exports returned the
same verdict-bearing canonical receipt.

### Six-patch corpus debug loops

The first corpus run rejected `alternate-robust` because its unified-diff hunk
header understated the new body by one line. Codex corrected the fixture
rather than weakening JGit application checks.

The next run produced every expected verdict, but the safety assertion used
`endsWith("VERIFIED")`, which also matches `PARTIALLY_VERIFIED`. The assertion
now compares the parsed verdict token exactly.

The `compile-breaking` candidate initially had the same kind of malformed hunk
count and therefore failed at patch application. Codex corrected that fixture
so it now applies and reaches the intended Java compilation failure. Engine
summaries were tightened so a missing Surefire report says the test did not
execute instead of incorrectly saying “0 edge cases fail.”

### Determinism audit

Five robust runs produced identical verdicts, input hashes, test evidence,
mutation evidence, scope evidence, reasons, and warnings. The first automated
comparison still failed because its normalization removed top-level
`durationMs` but retained the same volatile subprocess duration under
`sharedInvocationDurationMs`.

Codex added that explicitly volatile metric to normalization and verified that
all five preserved receipts reduce to the same SHA-256 normalised evidence
hash. The corrected five-run automated protocol then passed.

### Submission production

Codex produced:

- architecture and security documentation plus Mermaid, SVG, and PNG diagrams;
- a measured evaluation report and tester protocol;
- Docker, Railway, and GitHub Actions deployment preparation;
- a public README and submission document;
- a seven-slide editable PowerPoint deck and PDF export; and
- a precisely timed 2:58 demo script.

The presentation follows the Codex Grid layout system. Every slide was
rendered and visually inspected. A template connector extending beyond the
canvas was corrected, after which all seven layout exports stayed within the
1280×720 bounds.

Public GitHub, Railway, tester, Claude-review, and final video actions remain
external inputs owned by the user; no completion claim is made for them.

## 2026-07-25 - Release-candidate audit

A heavily repeated local run caused PIT to time out after starting its XML
report. The parser correctly rejected the truncated XML, but the engine's
outer failure handler initially turned that missing confidence evidence into a
generic rejection. Codex narrowed the handling at the mutation boundary:
timed-out, missing, or malformed mutation evidence is now recorded as an
explicit warning and yields `PARTIALLY_VERIFIED` only when every mandatory
correctness and scope gate passed. Mandatory failures still take precedence
through the independently tested verdict policy.

The final Windows audit also found 43 small scratch directories left from
prior runs. JGit marks object files read-only on Windows, and cleanup stopped
at the first such file. `WorkspaceManager` now clears the DOS read-only
attribute before deletion and continues past isolated cleanup failures. A
regression test creates the same read-only shape. The old disposable
workspaces were removed after their exact project-local path was checked.

The first release run then exposed a timing race in the child-process
termination test: under load, its one-second deadline could expire before the
probe wrote the child PID. The test now launches asynchronously, waits for the
PID evidence, and then observes the bounded runner's five-second timeout. It
still proves that the parent and its spawned child are terminated.

The final exact local command completed successfully in 46.838 seconds:

- 38 tests discovered;
- 36 tests executed with zero failures or errors;
- two long corpus/determinism tests intentionally gated and skipped;
- the live vertical slice produced a mutation-backed `VERIFIED` receipt; and
- zero Java processes and zero scratch workspaces remained afterward.

This is local release evidence, not a substitute for the still-pending
GitHub Actions, container, public deployment, latency, tester, and Claude
audits.

## 2026-07-27 - Product reopened for iteration

The user correctly challenged the framing that the project was already
finished before they had inspected it and before Claude Code had participated.
Codex therefore treated commit `4ae1a0c` as the MVP baseline rather than a
submission freeze.

A fresh browser audit ran both a robust and a rejected candidate. It found a
real presentation defect: the CSS class for the idle receipt state overrode
the HTML `hidden` attribute, leaving "No verdict without evidence" visible
beside a completed verdict. The same audit showed that exact counterexamples
were available in the canonical receipt but not surfaced in the dashboard,
that raw Markdown leaked into the bug report, and that comparing candidates
required a reload.

Milestone 2 adds:

- explicit hidden-state behaviour;
- verdict-level regression, edge-case, mutation, and scope metrics;
- exact failure, blocking, and warning findings;
- human-readable dynamic-test assertion context;
- a compare-another-candidate reset flow;
- clean bug-title rendering; and
- cache-busted v0.2 static assets.

The focused application/API suites passed. The subsequent complete build
discovered 40 tests, passed 38, skipped only the two intentionally gated
corpus/determinism tests, and completed the live robust PIT vertical slice in
58.130 seconds. Desktop and 390-pixel mobile browser reviews confirmed the
responsive layout, the rejected counterexample flow, and the reset
interaction.

Adding assertion context intentionally changed the verifier-pack hash. The
evaluation report now labels preserved corpus receipt digests as MVP
`4ae1a0c` evidence pending regeneration after review. Claude Code was not
installed or discoverable on this machine, so no Claude finding is claimed.
The updated handoff makes Claude review an explicit gate before this iteration
is committed as final.

## 2026-07-27 — Plain-language receipt iteration

The user inspected real rejected and verified receipts and reported that they
could not tell what was confusing, boring, impressive, or missing. This is a
product finding: the evidence was technically present, but the result assumed
engineering vocabulary that a first-time user did not have.

Codex added a shared `ReceiptLanguage` summary used by the dashboard, HTML
receipt, and Markdown receipt. The dashboard now leads with a one-sentence
explanation and keeps the original technical summary collapsible. Focused
tests passed: 10 tests, 0 failures. The updated application was packaged and
the local preview restarted.

## 2026-07-27 — Milestone 3 security and evidence hardening

Claude Code’s complete milestone-2 review is preserved unedited at
`reviews/claude/milestone-2.md`. Codex accepted all three critical findings,
all important findings, and the low-risk minor findings selected in the
approved plan. `REVIEW_DECISIONS.md` records every disposition; only M5’s
reader-thread/trailing-separator edge remains deferred.

### Scope is now based on the applied filesystem

The preflight parser now requires corresponding Git, old-file, and new-file
headers; validates hunk counts; accepts blank context explicitly; and rejects
unknown hunk content. More importantly, preflight is no longer authoritative.
The engine snapshots the pristine project, applies the patch, and uses JGit
line diffs to compare the actual trees before patched code executes. Binary or
symlink changes, forbidden files, hard size limits, and any disagreement
between claimed and observed changes reject the patch. Observed paths and
changed lines feed both the receipt and PIT.

Regression tests reproduce Claude’s hidden `pom.xml`, hidden `src/test/**`,
six-files-as-one, blank-context, malformed-count, and claim-mismatch attacks.
All are now fail-closed.

The first real observed-scope run exposed a Windows line-ending problem:
JGit’s temporary repository converted the otherwise minimal LF patch into a
whole-file CRLF rewrite, so the verifier correctly reported `+30/-18` rather
than the patch’s claimed `+13/-1`. Codex did not weaken reconciliation.
`PatchApplier` now disables JGit automatic line-ending conversion and restores
the source file’s original LF or CRLF convention. Dedicated LF and CRLF tests
now protect that behaviour; the real robust patch reconciles exactly at
`+13/-1`.

### Mandatory evidence fails closed

Skipped mandatory tests no longer pass. Surefire selection is exact, the
reproduction must expose the configured fully qualified failure class, and
reported pass counts are the tests that actually passed.

Mutation evidence now records process health and changed production files
without viable mutants. A PIT timeout, non-zero exit, missing/partial report,
parse failure, or no-mutant file cannot produce `VERIFIED`, even if a stale
parseable report says 100%. The engine invokes the pinned PIT 1.25.4 goal and
forwards manifest class and test targets. Focused tests cover timeout and
non-zero exit with a deliberately perfect report.

While exercising the plausible patch, Codex found a separate accounting bug
created by the optimised shared Maven invocation: an edge-case assertion makes
the combined process exit non-zero, but separately parsed reproduction and
regression reports can still be valid passes. Process health now checks that
the exit code agrees with the combined reports instead of treating every
non-zero shared exit as every suite failing. The plausible patch therefore
returns the intended edge-case `REJECTED` evidence rather than inventing
additional failures.

### Receipt and web hardening

Receipt schema v2 stores one canonical plain summary and limitations list,
observed-scope provenance, mutation-process health, and files without mutation
evidence. One central sanitiser masks workspace, Maven-cache,
application-root, and user-home paths throughout nested evidence. JSON,
Markdown, HTML, the dashboard, and the digest consume the same wording.

The web registry now has a terminal `FAILED` state, catches worker failures,
avoids the completed/null-receipt race, expires stale jobs, and returns
`Cache-Control: no-store` for mutable state. Polling bypasses caches and stops
after three minutes. The dashboard explains mutation evidence, renders scope
as `Clean` or `N unexpected files`, escapes remaining attributes, and shows
what the run did not prove. Standalone receipts include a restrictive CSP.

A direct scan of generated JSON then found a path shape the original tests had
missed: Jansi emits Maven JAR locations as a percent-encoded local file URI.
Normal slash and backslash forms were masked, but the percent-encoded space
prevented a match. The sanitiser now covers native, slash-normalised,
percent-encoded, and raw file-URI path forms, with a regression test matching
the real warning. Final receipts are scanned for both ordinary and encoded
local paths.

Observed-line reconciliation also corrected an overstated historical demo
metric. The former 4-of-4 count came from preflight line numbers that included
unchanged lines; the map-based patch has one genuinely changed-line mutant.
The existing set-based robust candidate is smaller and produces five genuine
changed-line mutants, all killed by the same sealed pack. Codex therefore
swapped only the two robust candidate file mappings: the public
`minimal-robust` label now names the stronger 5-of-5 set-based patch, while the
map-based implementation remains the second correct corpus candidate.

### Evaluation status

The hardened six-patch corpus passed on Java 25 with the Java 21 release
target:

- `plausible-distinct` → `REJECTED`;
- `correct-with-drift` → `PARTIALLY_VERIFIED`;
- `minimal-robust` → `VERIFIED`;
- `alternate-robust` → `VERIFIED`;
- `build-bypass` → `REJECTED`; and
- `compile-breaking` → `REJECTED`.

There were zero unsafe `VERIFIED` outcomes. The serial correctness run used a
180-second test-only total allowance because this Windows host was heavily
loaded; production configuration remains a 60-second whole-run deadline.
Those local durations are correctness evidence, not deployed latency.

Docker, Podman, and the Railway CLI are not installed on this machine.
Accordingly, the exact production-container verification remains a deployment
blocker and must run through the prepared GitHub Actions container job or a
remote Railway build. Public deployment remains blocked until that proof and
Claude’s focused milestone-3 re-review pass.

## 2026-07-27 — Milestone 3 frozen local release evidence

Codex completed the remaining local release gates after the verdict-relevant
behaviour was frozen.

- The focused Java 21 hardening suite passed 55/55 tests.
- The six-patch corpus matched 6/6 expected verdicts with zero unsafe
  `VERIFIED` outcomes.
- Five robust runs produced identical normalised evidence and passed the local
  path scan.
- The final Java 21 `verify` discovered 70 tests, passed 68, skipped only the
  two explicitly gated corpus tests, and reported no failures or errors.
- The live PIT-backed vertical slice completed in 42.08 seconds.
- All three public candidates were exercised through the production-config
  dashboard and produced `REJECTED`, `PARTIALLY_VERIFIED`, and `VERIFIED`.
- The exact final packaged Java 21 JAR produced `VERIFIED` in 42,306 ms with
  6/6 regressions, 9/9 edge cases, 5/5 observed changed-line mutants killed,
  healthy mutation execution, and clean observed scope.
- Mutable status responses returned `Cache-Control: no-store`; the final
  receipt exposed no tested native, slash-normalised, file-URI, or
  percent-encoded local path form.

The browser capture set, architecture SVG/PNG, seven-slide PowerPoint, and
seven-page PDF were refreshed against the hardened evidence. Every slide and
PDF page was rendered and inspected. The template fidelity check passed with
zero findings, the PPTX contained no empty structural placeholders, and the
PDF contained the new 5/5 mutation, observed-scope, and 42.3-second figures
without the stale values.

The local Codex sandbox cannot launch Maven child builds from the application;
that produced a misleading baseline failure during one direct preview.
Launching the same packaged JAR in the normal desktop environment succeeded.
This was recorded as an execution-environment limitation, not worked around by
weakening a verification gate.

Public deployment is still deliberately blocked. Docker/Podman is unavailable
locally, so the prepared CI container job must prove a complete offline
`minimal-robust` run, and Claude must complete the focused read-only Milestone
3 re-review before deployment resumes.

## 2026-07-27 — Claude Milestone 3 remediation

Claude completed the focused read-only re-review and independently proved the
original C1-C3 exploits are closed. It found one new Critical
false-rejection: exact equality between textual diff counts and a canonical
filesystem diff rejects valid non-minimal patches because different diff
algorithms can describe the same resulting bytes with different edit counts.

Codex accepted CR-1 and all actionable reliability/evidence findings:

- removed the non-security line-count equality while retaining hard observed
  path-set reconciliation and authoritative observed counts;
- rejected renames explicitly at preflight instead of misreporting them as
  hidden scope drift;
- required at least two viable changed-line mutants for `VERIFIED`;
- changed the one-mutant alternate patch ground truth to
  `PARTIALLY_VERIFIED`;
- preserved unrounded mutation scores for verdict comparison;
- made non-standard Java source roots fail closed when mutation mapping is
  unavailable;
- corrected timeout and missing-test blocking reasons;
- widened the process-tree regression-test timing margin;
- recovered cleanly from dashboard POST and polling failures;
- logged worker failures while preserving a generic public message; and
- rejected nested `.git` path segments defensively.

One focused run initially failed because a traversal fixture also triggered the
new rename message. Codex changed rename detection to run only after both diff
paths pass root validation. The corrected focused subset passed, but its exact
one-off selector was not retained; the subset count is therefore not used as a
release claim. The reproducible full `verify` result below is authoritative.

Fresh release evidence:

- full Java 21 build: 76 discovered, 74 passed, 2 explicitly gated, 0
  failures/errors;
- live vertical slice: 28.77 seconds;
- six-patch corpus: 6/6 expected verdicts, zero unsafe `VERIFIED`;
- five-run determinism: identical normalised evidence, SHA-256
  `46a308ad6db812df89d1305ad98738c764f37f83e9c5136489f4d3284bb19555`;
- packaged public candidates: `REJECTED` in 14,780 ms,
  `PARTIALLY_VERIFIED` in 22,393 ms, and `VERIFIED` in 26,069 ms;
- robust mutation evidence: 5/5 killed with a configured minimum of 2;
- all evaluated receipts passed the local absolute-path scan.

The production whole-run budget is now 90 seconds, while the per-process stage
budget remains 45 seconds. This creates measured local headroom without
claiming deployed p95 latency. The final dashboard and receipt screenshots were
refreshed to display the viable-mutant count and evidence-count threshold.

Public deployment remains blocked on the production container job. Claude’s
long review is complete; only the narrow sign-off prompt in
`reviews/claude/milestone-3-signoff-prompt.md` remains when the user’s Claude
allowance resets.

## 2026-07-27 — Final presentation evidence refresh

Codex updated the seven-slide deck through the editable presentation workflow,
then refreshed it again after the final-audit evidence regeneration. Slide 6
now rounds the measured 24.971-second packaged run to 25.0 seconds, and the
speaker note preserves the exact measurement. The source layout was otherwise
preserved.

- All seven final slide renders were inspected.
- Template fidelity passed with zero findings.
- The overflow test passed on all slides.
- PowerPoint regenerated the seven-page PDF.
- The rendered PDF contact sheet and evidence page were inspected and contain
  the current 25.0-second figure without clipping or layout drift.

## 2026-07-27 — Final adversarial-audit disposition

Claude’s final audit reported `READY FOR CONTAINER GATE`, with no Critical
finding. Codex dispositioned FA-1, FA-2, and FM-1 through FM-5 in
`REVIEW_DECISIONS.md`.

- FA-1: renamed the crossed robust fixture files to
  `minimal-robust-set-based.patch` and `alternate-robust-map-based.patch`, then
  updated the manifest paths. Candidate patch bytes and SHA-256 values remained
  unchanged: `minimal-robust` retains `9a305b…be1`, while
  `alternate-robust` retains `4b5b03…c88`.
- The manifest SHA-256 necessarily changed because its path strings changed.
  Codex therefore regenerated the corpus, determinism, and production-config
  evidence rather than claiming receipt hashes were unchanged.
- FA-2: removed the unsupported 51/51 subset claim and retained only a
  reproducible full Java 21 command and result.
- FM-1: documented the minimum of two viable changed-line mutants in README.
- FM-2: the dashboard now calls conclusive evidence below that minimum
  `Insufficient`, with a dedicated regression test.
- FM-3 remains deferred; FM-4 and FM-5 remain explicit fail-safe MVP
  limitations.

Current post-audit evidence:

- full Java 21 `verify`: 77 discovered, 75 passed, 2 gated, 0
  failures/errors;
- explicitly enabled corpus and determinism tests: 2/2 passed, 0 skipped;
- six-patch corpus: 6/6 expected verdicts, zero unsafe `VERIFIED`;
- five-run determinism: identical normalised evidence, SHA-256
  `3920e51823e639dc45795efd7390982360eaa6da9dc9b92e8dadbb14f2a7366c`;
- packaged public candidates: `REJECTED` in 15,553 ms,
  `PARTIALLY_VERIFIED` in 22,047 ms, and `VERIFIED` in 24,971 ms;
- all regenerated corpus and determinism receipts passed the ten-pattern
  absolute-path scan.

The production-container CI assertion now also requires the changed-line
mutant count to meet the receipt's configured minimum, in addition to checking
the verdict, schema, observed scope, and mutation-process health.

The only remaining deployment gate is a successful production-container run.

## 2026-07-27 — Model-agnostic positioning and UK-English pass

Codex replaced the Codex-specific product claim with the model-agnostic
positioning:

> AI writes the patch. PatchReceipt proves whether it deserves to ship.

Codex remains the documented primary builder and hackathon provenance, while
the product accepts unified diffs from Codex, Claude Code, Cursor, Copilot, or
any other coding agent. `docs/AGENTIC_AI_WORKFLOW.md` now documents the real
agent loop: let an agent create a patch, export its unified diff, run the local
trusted-project verifier, feed receipt findings back to the agent, and repeat
until the evidence is sufficient for human or CI approval.

All current user-facing project copy was converted to UK English. Technical
identifiers, official tool output, third-party product names, source-code
keywords, CSS properties, and preserved external reviews were deliberately
left unchanged.

The copy change altered the bundled manifest hash and canonical receipt text,
so Codex regenerated rather than relabelled the evidence:

- full Java 21 `verify`: 77 discovered, 75 passed, 2 gated, 0
  failures/errors; live vertical slice 35.58 seconds;
- explicitly enabled corpus and determinism tests: 2/2 passed in 5 minutes
  26 seconds;
- six-patch corpus: 6/6 expected verdicts, zero unsafe `VERIFIED`;
- five-run determinism: identical normalised evidence, SHA-256
  `8dbe2994a373729832adde418712191547940a61100477e7a3ed7f2836c442a6`;
- packaged public candidates: `REJECTED` in 23,381 ms,
  `PARTIALLY_VERIFIED` in 30,125 ms, and `VERIFIED` in 25,943 ms;
- the refreshed packaged receipt reports 6/6 regressions, 9/9 edge cases,
  5/5 observed changed-line mutants killed, healthy mutation execution, clean
  observed scope, and `Cache-Control: no-store` on mutable status;
- the dashboard, completed receipt, presentation, PDF, architecture image, and
  screenshot assets were refreshed and visually inspected.

The production-container and public-deployment gates remain separate and
pending.

## 2026-08-02 — Railway deployment and public smoke test

Codex diagnosed three successive clean-container failures from Railway logs:

- `drainingSeconds` was encoded as a JSON string instead of a number;
- the Temurin build image lacked `unzip`, causing Maven Wrapper to download a
  tar archive and compare it with the correctly pinned ZIP checksum; and
- offline nested verification needed the fixture's dynamically selected
  Surefire and PIT runtimes warmed during the networked image-build stage.

After those corrections, Railway built and started the production image. The
service is public at
`https://patch-receipt-production.up.railway.app/`. A read-only and allowlisted
production smoke test established:

- dashboard HTTP 200 and health status `UP`;
- one bundled case with exactly three public patch candidates;
- `plausible-distinct` returned `REJECTED`;
- `correct-with-drift` returned `PARTIALLY_VERIFIED`;
- `minimal-robust` returned `VERIFIED` in 6,652 ms with 6/6 regressions,
  9/9 edge cases, clean observed scope, and 5/5 changed-line mutants killed;
- JSON, Markdown, and HTML receipts returned HTTP 200 with matching verdicts;
  and
- the tested responses contained no local absolute path.

This is a production smoke-test sample, not a p95 measurement. The external
three-person usability study was not completed, so no tester result is claimed.

Codex also inspected the public GitHub Actions result. Windows passed, while
Ubuntu stopped with exit code 126 because `mvnw` lacked its Git executable bit.
The wrapper mode and workflow were corrected, and Ubuntu now explicitly warms
the fixture's test and mutation runtimes before offline verification. A green
run on the resulting submission commit remains pending until the user pushes
this change.

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

## 2026-07-25 - Vertical slice proved and optimized

### First complete receipt

The first complete end-to-end run produced `VERIFIED` with:

- The baseline regression suite passing.
- The named reproduction failing by `AssertionFailedError` before the patch.
- The same reproduction passing after the patch.
- Six original regressions and nine sealed dynamic edge cases passing.
- Four of four viable changed-line mutants killed for a 100% score.
- One expected production path and no scope warnings.

The first cold engine run took 70,972 ms. A warmed run took 53,804 ms because the engine paid for six separate Maven launches.

### Runtime optimization

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
all five preserved receipts reduce to the same SHA-256 normalized evidence
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

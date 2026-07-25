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

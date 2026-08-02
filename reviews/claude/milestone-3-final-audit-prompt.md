# PatchReceipt — Milestone 3 Final Adversarial Audit

Act as an independent senior Java security and verification reviewer. This is
the final audit before PatchReceipt is allowed to enter container CI and public
deployment.

Use **read-only mode for the product**. Do not edit product files, generate
patches, reformat code, or change implementation behavior. The only authorized
repository write is the final audit report requested below. You may run tests
and other non-mutating diagnostic commands. If a command would modify tracked
source files, do not run it.

## Repository

The repository root is the folder containing this prompt:

`patch-receipt`

## Required context

Read these files before forming conclusions:

1. `AGENTS.md`
2. `PROJECT_PLAN.md`
3. `CLAUDE_HANDOFF.md`
4. `REVIEW_DECISIONS.md`
5. `reviews/claude/milestone-2.md`
6. `reviews/claude/milestone-3.md`
7. `docs/ARCHITECTURE.md`
8. `docs/SECURITY.md`
9. `docs/EVALUATION.md`
10. `docs/evidence/evaluation-summary.json`
11. `docs/evidence/determinism-summary.json`

Treat previous reviews as hypotheses to verify, not conclusions to repeat.

## Audit objective

Determine whether the current implementation genuinely closes the Milestone 3
scope, mutation, test, receipt, and job-handling failures without introducing
new false acceptances or serious false rejections.

The required public-candidate outcomes are:

- `plausible-distinct` → `REJECTED`
- `correct-with-drift` → `PARTIALLY_VERIFIED`
- `minimal-robust` → `VERIFIED`

The review is not complete merely because existing tests pass. Trace the
relevant production paths and independently reason about bypasses.

## Audit procedure

### 1. Confirm the previous Critical findings

Independently inspect the current implementation for:

- hidden edits to `pom.xml`, `.mvn/**`, `mvnw*`, `.github/**`,
  `src/test/**`, verifier files, nested `.git` paths, binaries, and symbolic
  links;
- malformed or inconsistent diff headers and hunk counts;
- additions following blank context lines;
- patches claiming one changed file while modifying multiple real files;
- discrepancies between preflight paths and the resulting filesystem tree;
- non-minimal but legitimate unified diffs;
- file renames and the documented decision to reject them explicitly.

Confirm that observed filesystem scope is authoritative for final evidence and
PIT changed-line filtering. Explain whether a crafted patch can reach patched
test execution before hard observed-scope violations are rejected.

### 2. Challenge mutation evidence

Trace PIT invocation, process execution, report parsing, and verdict policy.
Verify that `VERIFIED` is impossible when:

- PIT times out or exits non-zero;
- a stale or parseable 100% report remains after a failed process;
- the report is missing, partial, corrupt, or unrelated;
- changed production files have no viable changed-line evidence;
- fewer than the configured minimum viable changed-line mutants exist;
- the changed-line score is below the configured threshold;
- mutation targets or source roots cannot be mapped conclusively.

Check that the score is compared without premature rounding and that the
receipt accurately discloses process health, provenance, required mutant count,
files lacking evidence, and inconclusive states.

### 3. Challenge mandatory test gates

Confirm that:

- skipped mandatory tests cannot pass;
- passed counts are actual passed counts;
- the baseline reproduction must fail through the exact configured failure
  class and selector;
- blank or wildcard reproduction selectors are rejected;
- compilation errors, unrelated failures, crashes, and timeouts cannot count as
  a reproduced bug;
- correctness failures always produce `REJECTED`, never
  `PARTIALLY_VERIFIED`.

### 4. Challenge receipts and public job handling

Inspect central sanitization and every public receipt/API failure path. Look for
Windows, Unix, slash-normalized, file-URI, percent-encoded, Maven-cache,
application-root, workspace, and user-home path leaks.

Confirm that:

- schema v2 uses one canonical summary and limitations model;
- HTML, Markdown, JSON, dashboard summaries, and the digest cannot materially
  disagree;
- limitations do not overclaim design, security, performance, concurrency, or
  requirements outside the verifier pack;
- worker exceptions always reach a terminal `FAILED` state with a safe public
  message;
- `COMPLETED` cannot expose a null verdict or receipt;
- mutable status responses use `Cache-Control: no-store`;
- polling terminates on completion, failure, timeout, and initial-request
  failure;
- stale jobs expire safely.

### 5. Run verification

Run the repository’s documented Java 21 commands where the environment permits.
At minimum:

1. The focused hardening/security tests.
2. The complete Maven `verify` build.
3. The six-patch evaluation corpus if it is not already part of the complete
   run or can be invoked separately without changing source files.

Do not silently substitute a different Java version. Record the exact Java and
Maven versions used, every command, exit status, discovered/passed/failed/skipped
counts, and any environmental limitation.

If a test is gated by an explicit property, distinguish “not run” from “pass.”
Do not claim container verification unless an actual production container was
built and completed a live `minimal-robust` run.

### 6. Validate evidence and submission claims

Compare current source behavior with:

- `docs/EVALUATION.md`;
- the two evidence-summary JSON files;
- the current public-candidate manifests;
- README claims;
- presentation-facing metrics where inspectable.

Flag stale, contradictory, unsupported, or overly broad claims. In particular,
verify the reported 6/6 corpus outcome, zero unsafe `VERIFIED` results, the
two-mutant minimum, five-run determinism claim, 90-second whole-run budget, and
26.1-second packaged robust run.

### 7. Inspect container readiness without overclaiming

Review the Dockerfile and CI workflow for:

- Java/runtime compatibility;
- offline Maven/PIT dependency availability;
- non-root execution;
- health checks;
- resource and timeout settings;
- a real API run that is polled to a terminal state and asserted `VERIFIED`.

If Docker is available, run the documented production-container verification.
If it is unavailable, state that container proof remains an explicit deployment
blocker. Do not treat workflow inspection as runtime proof.

## Severity rules

- **Critical:** an unsafe, incorrect, or unevidenced patch can reach
  `VERIFIED`; a hosted-input escape; or material evidence fabrication.
- **Important:** a realistic false rejection, reliability failure, path leak,
  misleading receipt, non-terminal job, or unsupported submission claim.
- **Minor:** bounded maintainability, wording, or usability weakness that does
  not affect verdict correctness.

Do not promote hypothetical style preferences into security findings. For each
finding, include an exact file and line reference, a concrete reproduction or
reasoning chain, impact, and the smallest defensible remediation.

## Required output

Write the final report to:

`reviews/claude/milestone-3-final-audit.md`

Use this structure:

1. **Executive verdict**
   - `READY FOR CONTAINER GATE`
   - `NOT READY`
2. **Commands and environment**
3. **Previous finding verification**
   - C1 hidden scope changes
   - C2 observed changed-line accounting
   - C3 failed mutation process
   - CR-1 legitimate non-minimal diffs
   - minimum mutation-evidence count
4. **New findings**
   - Critical
   - Important
   - Minor
5. **Adversarial scenarios attempted**
6. **Test and corpus results**
7. **Evidence/documentation consistency**
8. **Container-readiness assessment**
9. **Deployment blockers**
10. **Final recommendation**

If there are no findings in a severity group, explicitly write `None`.

The final recommendation must clearly distinguish:

- locally verified behavior;
- behavior inferred only from code inspection;
- tests that were gated or not run;
- container behavior not yet proven; and
- whether Codex may proceed to the production-container gate.

Do not implement fixes. Finish the complete report even if you find a Critical
issue.

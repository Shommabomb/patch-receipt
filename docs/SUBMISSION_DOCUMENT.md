# PatchReceipt

**Primary track:** Agentic Coding
**Secondary angle:** Building Evals

> AI writes the patch. PatchReceipt proves whether it deserves to ship.

## Submission links

Current public links:

- **Live application:** [patch-receipt-production.up.railway.app](https://patch-receipt-production.up.railway.app/)
- **Source repository:** [github.com/Shommabomb/patch-receipt](https://github.com/Shommabomb/patch-receipt)
- **Demo video:** `[ADD PUBLIC VIDEO URL]`
- **Presentation:** [GitHub PDF](https://github.com/Shommabomb/patch-receipt/blob/main/docs/PRESENTATION.pdf)

## How judges can try it

The public application runs three bundled, hash-allowlisted candidates and
requires no account. It demonstrates the complete `REJECTED`,
`PARTIALLY_VERIFIED`, and `VERIFIED` paths. Arbitrary code is intentionally not
accepted by the hosted service because Maven projects can execute code during
their build.

For a trusted Java 21 Maven project, judges can clone the repository, build the
executable JAR, scaffold an independent verifier pack with the `init` command,
and run the `verify` command with a clean baseline, bug report, unified diff,
and `--allow-local-execution`. The full command is documented in `README.md`
and `docs/AGENTIC_AI_WORKFLOW.md`.

## Problem

AI coding agents can generate a convincing patch in seconds. The difficult part is deciding whether that patch should be merged.

An ordinary green test suite is incomplete evidence. It may never prove that the reported bug existed, may omit edge cases suggested by the contract, may be too weak to detect plausible wrong implementations, and may ignore unrelated production changes. A reviewer is left reconstructing those questions manually from logs and intuition.

PatchReceipt turns that decision into a deterministic verification receipt.

## Solution

PatchReceipt is a model-agnostic Java 21 verification system for small AI-generated Java patches. The candidate may come from Codex, Claude Code, Cursor, Copilot, or any other tool that can produce a unified diff. For each candidate it:

1. checks the unified diff and declared change scope before execution;
2. runs the pristine project's original regressions;
3. injects a sealed reproduction test and proves the intended assertion fails before the patch;
4. applies the patch to a fresh copy with JGit;
5. compares the pristine and patched filesystems and rejects hidden or mismatched changes before patched code runs;
6. proves the same reproduction passes afterward;
7. reruns the unchanged original regressions;
8. injects a sealed JUnit dynamic-test pack with independent edge cases;
9. runs a version-pinned PIT process against manifest targets and scores viable mutants on observed changed lines;
10. applies fixed verdict rules; and
11. renders receipt schema v2 as HTML, Markdown, and JSON with canonical summary, limitations, input hashes, and a SHA-256 digest.

The result is not a weighted trust score:

- **REJECTED** means a mandatory correctness, execution, or hard-scope gate failed.
- **PARTIALLY_VERIFIED** means correctness passed, but observed scope drift or unhealthy/incomplete mutation confidence prevents a full claim.
- **VERIFIED** requires every mandatory gate, clean observed scope, a healthy complete mutation run for every changed production file, and the mutation threshold.

## Judge-facing case

The bundled `checkout-core` project has a retry bug:

> Retried checkout requests can submit the same coupon more than once. Coupon codes are case-insensitive and each coupon may affect an order only once.

PatchReceipt exposes three deliberately plausible outcomes:

| Candidate | What happens | Verdict |
| --- | --- | --- |
| Object-level `distinct()` | Fixes the obvious duplicate but fails three sealed edge cases involving code identity | `REJECTED` |
| Correct canonicalization plus unrelated edit | Passes correctness and mutation checks but changes an unexpected production file | `PARTIALLY_VERIFIED` |
| Minimal robust canonicalization | Passes six regressions, nine sealed edge cases, mutation pressure, and clean observed scope | `VERIFIED` |

The demonstration is designed so a judge can understand the failed counterexample and the robust evidence within three minutes.

## Implementation

PatchReceipt is one Spring Boot 4.1 Maven application with strong logical package boundaries:

- Spring MVC and Thymeleaf provide the public one-screen dashboard.
- Picocli provides a trusted-project local CLI.
- JGit inspects and applies unified diffs.
- Maven Wrapper 3.3.4 pins Maven 3.9.16 with checksum validation.
- JUnit and Surefire provide reproduction, regression, and edge-case evidence.
- PIT 1.25.4 provides targeted changed-line mutation evidence.
- Jackson and deterministic renderers produce receipt parity across three formats.

A successful run uses three bounded Maven child processes: one shared baseline test run, one shared patched test run, and one targeted PIT run. Sharing selectors reduces startup overhead without merging the evidence gates.

## Safety and deployment boundary

The public application accepts only known `caseId` and `patchId` values. It does not accept uploaded repositories, raw patches, commands, URLs, paths, Maven goals, or verifier source.

Safety comes primarily from executing only bundled, reviewed, allowlisted fixtures—not from claiming that a container is a secure arbitrary-code sandbox. Runs use fresh scratch workspaces, argument-array process launches, one worker, a queue cap, process timeouts, child-tree termination, bounded logs, workspace cleanup, path validation, and a non-root container user.

The local CLI is intentionally different: it can verify a trusted small Maven project, but refuses to execute until the user passes `--allow-local-execution`.

## No runtime API requirement

PatchReceipt uses no OpenAI or Anthropic API at runtime and needs no API credits:

- JGit applies and analyses patches.
- Maven compiles and runs the target.
- JUnit expands deterministic independent cases.
- PIT challenges the strength of the test evidence.
- Fixed rules calculate the verdict.

The natural-language bug report is displayed and hashed, not silently interpreted by an LLM. This keeps the MVP reproducible and honest.

## Evaluation

The six-patch ground-truthed corpus contains:

- two correct minimal implementations;
- one correct implementation with soft scope drift;
- one plausible implementation defeated by independent edge cases;
- one forbidden build/test bypass; and
- one compile-breaking patch.

The hardened corpus produces all six expected verdicts with zero unsafe patches marked `VERIFIED`. The primary robust corpus run completed in 38,944 ms in the extended correctness harness with:

- 6 of 6 original regressions passing;
- 9 of 9 sealed edge cases passing;
- 5 of 5 viable observed changed-line mutants killed; and
- no hard or soft scope violation.

Five repeated hardened runs produced identical normalised evidence. The final
packaged Java 21 JAR also completed the public robust flow through the
production-config dashboard in 25.943 seconds. Production-container proof,
deployed warm latency, and the three-person usability study must still be
reported only after their recorded protocols complete. The live evaluation
status and machine-readable summaries are documented in
`docs/EVALUATION.md`.

## Impact

PatchReceipt targets a growing bottleneck in agentic software development: code generation is becoming cheaper, while high-quality review evidence remains expensive.

The MVP demonstrates a practical pattern:

> Coding agent → unified diff → PatchReceipt → evidence receipt → human or CI decision.

- require proof that the bug exists before claiming it is fixed;
- keep independent tests outside the patchable project;
- challenge those tests with mutation analysis;
- treat scope as a verification gate;
- make every verdict inspectable by humans and machines; and
- keep safety claims narrower than the actual execution boundary.

The immediate product is intentionally small and reliable. The same evidence model could later back pull-request checks, CI actions, signed verifier packs, and stronger isolated execution.

## Effective use of Codex

The user created and directed PatchReceipt and made the final product decisions. Codex helped to:

- converted the hackathon brief into a decision-complete plan;
- established durable repository rules in `AGENTS.md`;
- implemented the engine, CLI, API, dashboard, fixtures, tests, receipts, and deployment preparation;
- diagnosed real Jackson, Windows path, sandbox, patch-format, determinism, and performance failures;
- reduced the robust flow from six Maven launches to three;
- built and ran an adversarial six-patch corpus;
- tested all three judge-facing outcomes in the browser;
- produced the architecture, threat model, evaluation protocol, presentation, and demo script; and
- recorded its work in `CODEX_JOURNAL.md` and milestone commits.

Claude Code is reserved for an auditable independent review. Its exact request is in `CLAUDE_HANDOFF.md`; unedited findings belong under `reviews/claude/`, and Codex records each disposition in `REVIEW_DECISIONS.md`. PatchReceipt does not claim hidden model-to-model communication or runtime AI that did not occur.

## Deliberate limitations

- Hosted execution supports only bundled cases.
- The local adapter supports trusted Java 21 Maven projects only.
- Edge tests come from a prewritten sealed verifier pack.
- Scope policy is declared path and size enforcement, not semantic intent detection.
- Receipt hashes provide traceability, not signatures.
- The public demo has no accounts, database, or durable history.

These limits are part of the product's credibility: PatchReceipt proves a narrow claim well instead of disguising a broad unsafe prototype.

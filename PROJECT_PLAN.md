# PatchReceipt implementation plan

## Goal

Build a Java 21 verification system for AI-generated patches. It must prove a bug before a patch, apply the patch, verify the fix, run unchanged regressions, inject an independent sealed verifier pack, run targeted PIT mutation testing, detect declared scope drift, and emit one evidence receipt as JSON, Markdown, and HTML.

The primary hackathon track is Agentic Coding and the secondary angle is Building Evals.

## MVP

- One Spring Boot web application with a safe bundled `checkout-core` case.
- Three judge-facing patch candidates:
  - plausible object-level deduplication: `REJECTED`;
  - correct code plus unrelated production change: `PARTIALLY_VERIFIED`;
  - minimal robust fix: `VERIFIED`.
- One trusted-project Maven CLI requiring `--allow-local-execution`.
- No runtime AI, API key, database, authentication, arbitrary hosted upload, Gradle support, or GitHub App.

## Verification order

1. Resolve and hash the case, bug report, patch, manifest, and verifier pack.
2. Parse the patch and enforce hard scope policy before execution.
3. Run original baseline regressions.
4. Inject the sealed reproduction test and prove its expected assertion fails.
5. Create a fresh workspace and apply the patch.
6. Compare the pristine and patched trees, reject hidden or mismatched changes, and make observed filesystem evidence authoritative.
7. Run original regressions unchanged.
8. Inject the same reproduction and edge-case tests.
9. Prove the reproduction passes and run the generated edge-case matrix.
10. Run a version-pinned PIT process against manifest targets and score viable mutations on observed changed lines.
11. Apply the fixed verdict policy and render receipt schema v2 in all formats.

## Verdict policy

- `REJECTED`: invalid reproduction, apply/compile failure, post-patch reproduction failure, regression or edge-case failure, hard scope violation, or required-stage error/timeout.
- `PARTIALLY_VERIFIED`: all correctness gates pass but soft drift exists, a changed production file has no viable mutants, or mutation evidence is unhealthy, unavailable, inconclusive, or below 80%.
- `VERIFIED`: all correctness gates pass, observed scope is clean, the mutation process and report are healthy, every changed production file has viable evidence, and the changed-line score is at least 80%.

## Architecture

A single Maven module contains logical packages for domain types, case packs, scope analysis, bounded execution, report parsing, orchestration, receipts, CLI, and web. Bundled case files are copied from classpath resources into unique ignored workspaces. JGit applies patches. Maven/JUnit/PIT produce evidence. Spring MVC and Thymeleaf expose an allowlisted asynchronous run API and one-page dashboard.

## Safety

The public app accepts only bundled case and patch IDs. It does not accept source, diffs, commands, paths, URLs, or credentials. Child processes are bounded, Maven runs in offline mode, logs are capped and sanitised, workspaces are deleted, and only one verification job runs at a time. The container does not yet enforce a network-egress boundary, so the project does not claim that all child code is network-isolated.

## Delivery order

1. Prove the baseline-to-patch-to-PIT vertical slice and JSON receipt.
2. Generalize the engine, scope policy, CLI, and receipt renderers.
3. Build and browser-test the dashboard.
4. Add Docker, CI, evaluation corpus, security and architecture documentation.
5. Produce the submission document, seven-slide presentation, and three-minute demo assets.

## Acceptance

The build passes on Windows and Ubuntu; all three public candidates receive their expected verdicts; no unsafe evaluation patch receives `VERIFIED`; receipts agree across formats; the CLI refuses execution without its safety flag; the public API is allowlisted; and the deployed principal flow needs no credentials.

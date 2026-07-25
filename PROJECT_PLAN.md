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
5. Create a fresh workspace, apply and compile the patch.
6. Run original regressions unchanged.
7. Inject the same reproduction and edge-case tests.
8. Prove the reproduction passes and run the generated edge-case matrix.
9. Run PIT against changed production classes and score viable mutations on changed lines.
10. Apply the fixed verdict policy and render all receipt formats.

## Verdict policy

- `REJECTED`: invalid reproduction, apply/compile failure, post-patch reproduction failure, regression or edge-case failure, hard scope violation, or required-stage error/timeout.
- `PARTIALLY_VERIFIED`: all correctness gates pass but soft drift exists or mutation evidence is unavailable, inconclusive, or below 80%.
- `VERIFIED`: all correctness gates pass, scope is clean, mutation ran successfully with at least one viable changed-line mutant, and the changed-line score is at least 80%.

## Architecture

A single Maven module contains logical packages for domain types, case packs, scope analysis, bounded execution, report parsing, orchestration, receipts, CLI, and web. Bundled case files are copied from classpath resources into unique ignored workspaces. JGit applies patches. Maven/JUnit/PIT produce evidence. Spring MVC and Thymeleaf expose an allowlisted asynchronous run API and one-page dashboard.

## Safety

The public app accepts only bundled case and patch IDs. It does not accept source, diffs, commands, paths, URLs, or credentials. Child processes are bounded, logs are capped and sanitized, workspaces are deleted, execution is offline in the production container, and only one verification job runs at a time.

## Delivery order

1. Prove the baseline-to-patch-to-PIT vertical slice and JSON receipt.
2. Generalize the engine, scope policy, CLI, and receipt renderers.
3. Build and browser-test the dashboard.
4. Add Docker, CI, evaluation corpus, security and architecture documentation.
5. Produce the submission document, seven-slide presentation, and three-minute demo assets.

## Acceptance

The build passes on Windows and Ubuntu; all three public candidates receive their expected verdicts; no unsafe evaluation patch receives `VERIFIED`; receipts agree across formats; the CLI refuses execution without its safety flag; the public API is allowlisted; and the deployed principal flow needs no credentials.

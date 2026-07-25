# PatchReceipt repository guidance

## Mission

PatchReceipt is a deterministic verifier for AI-generated Java patches. Preserve the central claim:

> Codex writes the patch. PatchReceipt proves whether it deserves to ship.

The hosted application may execute only bundled, hash-allowlisted Java cases. Never add public repository uploads, raw patch submission, arbitrary commands, runtime LLM calls, authentication, a database, Gradle support, or GitHub integration to the MVP.

## Build and verification

- Target Java 21 and build only through `mvnw` or `mvnw.cmd`.
- Keep Maven caches in `.cache/maven` and verification workspaces in `.patchreceipt-work`.
- Run `mvnw.cmd verify` on Windows or `./mvnw verify` on Unix after code changes.
- Run the bundled end-to-end verification after changes to the engine, runner, manifest, fixture, verdict policy, or receipt schema.
- Treat a baseline assertion failure as reproduction only when the named test compiled and executed. Compilation errors and timeouts are not reproduction.
- A mandatory correctness failure must always produce `REJECTED`.
- HTML, Markdown, and JSON receipts must originate from the same canonical model.

## Safety invariants

- Use `ProcessBuilder` argument lists; never concatenate user values into a shell command.
- The web API accepts only known case and patch IDs.
- Local project execution requires the explicit `--allow-local-execution` flag.
- Cap process time, output, workspace size, concurrency, and retained run lifetime.
- Remove absolute workspace paths from user-visible evidence.
- Do not call the Docker container an arbitrary-code sandbox.

## Working agreements

- Codex is the primary builder and owns implementation and final decisions.
- Record each meaningful milestone, failed check, correction, and self-review in `CODEX_JOURNAL.md`.
- Claude is an independent reviewer. Put its unedited findings under `reviews/claude/` and disposition every finding in `REVIEW_DECISIONS.md`.
- Keep public claims measurable and avoid claiming natural-language test generation, semantic intent detection, direct model-to-model communication, or runtime AI.
- Update README and architecture/evaluation documentation whenever externally visible behavior changes.

## Code review rules

- Flag any path that lets hosted callers control source code, commands, Maven goals, filesystem paths, URLs, or test content.
- Flag any verdict path that can return `VERIFIED` after a failed or missing mandatory stage.
- Flag receipts that omit input hashes, stage evidence, scope evidence, or mutation provenance.
- Prefer small, testable Java types over framework-heavy abstractions.

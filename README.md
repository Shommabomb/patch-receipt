# PatchReceipt

> Codex writes the patch. PatchReceipt proves whether it deserves to ship.

PatchReceipt is a deterministic verification layer for AI-generated Java patches. It reproduces the reported bug on a pristine baseline, applies the patch, reruns the proof, executes unchanged regressions and a sealed edge-case pack, challenges the evidence with changed-line mutation testing, detects scope drift, and emits one evidence receipt as JSON, Markdown, and standalone HTML.

It uses no runtime LLM or API key.

![PatchReceipt verification architecture](docs/architecture.png)

## What the demo proves

The bundled `checkout-core` case asks whether retried, case-insensitive coupon codes can be applied more than once.

| Candidate | Correctness evidence | Scope | Mutation | Verdict |
| --- | --- | --- | --- | --- |
| Plausible object `distinct()` fix | Reproduction fixed; 3 sealed edge cases fail | Clean | Skipped after correctness failure | `REJECTED` |
| Correct fix with unrelated edit | 6 regressions and 9 edge cases pass | Unexpected production path | 100% changed-line score | `PARTIALLY_VERIFIED` |
| Minimal robust fix | 6 regressions and 9 edge cases pass | Clean | 4/4 changed-line mutants killed | `VERIFIED` |

The measured robust browser run completed in **27.036 seconds** on the development machine.
The six-patch ground-truthed corpus matched all six expected verdicts with
zero unsafe `VERIFIED` outcomes, and five robust runs produced identical
normalized evidence.

## Run locally

Prerequisite: Java 21 or newer. Maven is bootstrapped by the checksum-verified wrapper and its dependencies stay under `.cache/maven`.

Windows:

```powershell
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.cache\maven'
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Unix:

```sh
export MAVEN_USER_HOME="$PWD/.cache/maven"
./mvnw verify
./mvnw spring-boot:run
```

Open `http://localhost:8080`.

The first run downloads the pinned Maven distribution and dependencies. Hosted/container execution is offline after its build cache has been prepared.

## Trusted-project CLI

Build the executable JAR:

```powershell
.\mvnw.cmd package
```

Scaffold a sealed verifier pack:

```text
java -jar target/patch-receipt-0.0.1-SNAPSHOT.jar init
  --output verifier-pack
```

Verify a small trusted Java 21 Maven project:

```text
java -jar target/patch-receipt-0.0.1-SNAPSHOT.jar verify
  --project <directory>
  --bug-report <markdown-file>
  --patch <unified-diff>
  --verifier-pack <directory>
  --output <directory>
  --allow-local-execution
```

Without `--allow-local-execution`, the CLI exits without running the project. Java builds and tests can execute arbitrary code; review local inputs first.

## Hosted API

The hosted surface accepts identifiers only:

- `GET /api/v1/cases`
- `POST /api/v1/runs` with `{ "caseId": "...", "patchId": "..." }`
- `GET /api/v1/runs/{runId}`
- `GET /api/v1/runs/{runId}/receipt.json`
- `GET /api/v1/runs/{runId}/receipt.md`
- `GET /api/v1/runs/{runId}/receipt.html`
- `GET /actuator/health`

There is no upload, URL, repository, source, command, path, or test-content field in the public API.

## Verdict policy

- `REJECTED`: a mandatory correctness or safety gate fails.
- `PARTIALLY_VERIFIED`: correctness passes, but mutation evidence is incomplete/below threshold or declared scope drifts.
- `VERIFIED`: reproduction, regressions, edge cases, clean scope, and the mutation threshold all pass.

A weighted trust score never overrides these rules.

## Safety boundary

The public service executes only bundled, hash-allowlisted project, patch, manifest, bug-report, and verifier-pack inputs. It uses a single-worker bounded queue, stage and run limits, capped logs/workspaces, argument-array process execution, process-tree termination, path validation, offline Maven, scratch workspaces, sanitized evidence, and a non-root container user.

The container is not presented as a secure arbitrary-code sandbox. Arbitrary hosted repository execution is intentionally unsupported. See [docs/SECURITY.md](docs/SECURITY.md).

## Architecture and evidence

- [Approved product plan](PROJECT_PLAN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Security model](docs/SECURITY.md)
- [Deployment guide](docs/DEPLOYMENT.md)
- [Evaluation](docs/EVALUATION.md)
- [Demo script](docs/DEMO_SCRIPT.md)
- [Submission document](docs/SUBMISSION_DOCUMENT.md)
- [Presentation](docs/PRESENTATION.pptx) and [PDF export](docs/PRESENTATION.pdf)
- [Tester protocol](docs/TESTER_SCRIPT.md)
- [Final submission checklist](docs/SUBMISSION_CHECKLIST.md)
- [Codex implementation journal](CODEX_JOURNAL.md)
- [Claude review handoff](CLAUDE_HANDOFF.md)
- [Review decisions](REVIEW_DECISIONS.md)

## Build integrity

- Java release target: 21
- Spring Boot: 4.1.0
- Maven Wrapper: 3.3.4
- Maven: 3.9.16 with SHA-256 distribution verification
- PIT: 1.25.4
- JGit: 7.7.0

CI runs the focused safety suite on Windows, the full vertical slice and live PIT evidence on Ubuntu, then builds and smoke-tests the production container.

## AI use

Codex is the primary builder and owns planning, architecture, implementation, tests, debugging, integration, deployment preparation, evaluation, and submission assets. Its milestone decisions and failing-to-passing loops are recorded in `CODEX_JOURNAL.md` and Git history.

Claude Code is an independent reviewer through `CLAUDE_HANDOFF.md`, `reviews/claude/`, and `REVIEW_DECISIONS.md`; it is not the primary implementation agent. PatchReceipt itself does not call OpenAI or Anthropic at runtime.

## License

Apache License 2.0.

# PatchReceipt

> Codex writes the patch. PatchReceipt proves whether it deserves to ship.

PatchReceipt is a deterministic Java verification layer for AI-generated code changes. It proves that the reported bug existed before a patch, verifies the same behavior afterward, runs unchanged regressions, injects independent edge-case tests, challenges the tests with mutation testing, checks declared scope, and produces an inspectable evidence receipt.

## Status

Implementation is in progress. The current hard gate is the complete baseline-to-patch-to-mutation vertical slice described in [PROJECT_PLAN.md](PROJECT_PLAN.md).

## Intended commands

Windows:

```powershell
$env:MAVEN_USER_HOME = (Resolve-Path '.cache/maven').Path
.\mvnw.cmd verify
```

Unix:

```sh
MAVEN_USER_HOME="$PWD/.cache/maven" ./mvnw verify
```

Run the web application:

```powershell
.\mvnw.cmd spring-boot:run
```

Trusted local verification will use:

```text
java -jar patch-receipt.jar verify
  --project <directory>
  --bug-report <markdown-file>
  --patch <unified-diff>
  --verifier-pack <directory>
  --output <directory>
  --allow-local-execution
```

## Safety boundary

The public application will execute only bundled, hash-allowlisted cases and patches. It will not accept uploaded repositories or raw diffs. The local CLI executes developer-trusted Maven projects only after an explicit opt-in flag.

## AI use

Codex is the primary builder for planning, architecture, implementation, tests, debugging, deployment preparation, evaluation, documentation, and final review. Claude Code is used as a tracked independent reviewer through `CLAUDE_HANDOFF.md`, `reviews/claude/`, and `REVIEW_DECISIONS.md`.

PatchReceipt itself does not call an LLM at runtime.

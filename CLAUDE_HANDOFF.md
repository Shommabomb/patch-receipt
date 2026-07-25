# Claude Code review handoff

## Role

You are the independent reviewer for PatchReceipt. Codex is the primary builder. Review the current repository and produce findings; do not silently become the implementation owner.

## Product proposition

> Codex writes the patch. PatchReceipt proves whether it deserves to ship.

PatchReceipt is an offline Java verification engine for AI-generated patches. It proves a baseline bug, applies a patch, reruns the proof, executes unchanged regressions, injects a sealed independent verifier pack, runs targeted PIT mutation testing, enforces declared scope, and emits evidence receipts.

## Hard boundaries

- Hosted execution accepts only bundled case and patch IDs.
- No arbitrary hosted uploads, source, commands, paths, URLs, or test content.
- Local execution requires `--allow-local-execution`.
- No runtime OpenAI or Anthropic API.
- No database, authentication, Gradle, GitHub App, or multi-language support in the MVP.
- A failed mandatory correctness stage must always mean `REJECTED`.
- Claude does not directly edit main product code during normal review.

## Read first

1. `AGENTS.md`
2. `PROJECT_PLAN.md`
3. `CODEX_JOURNAL.md`
4. `README.md`
5. `docs/ARCHITECTURE.md` and `docs/SECURITY.md` when present

## Build

On Windows:

```powershell
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.cache\maven'
.\mvnw.cmd verify
```

On Unix:

```sh
MAVEN_USER_HOME="$PWD/.cache/maven" ./mvnw verify
```

## Milestone 1 review request

Milestone 1 is complete. The current robust live run is `VERIFIED` in 27,036 ms with
six regressions, nine edge cases, and four of four changed-line mutants killed.
The three hosted patches now produce `REJECTED`, `PARTIALLY_VERIFIED`, and
`VERIFIED` through the browser.

Independently check:

- Does the baseline failure prove the intended bug rather than a compile/configuration error?
- Can a patch bypass tests or alter the verifier pack?
- Can any failed/missing stage become `PARTIALLY_VERIFIED` or `VERIFIED`?
- Is changed-line mutation scoring conservative and explainable?
- Are input hashes and evidence provenance sufficient?
- Are safety claims narrower than the actual controls?

Write unedited findings to `reviews/claude/milestone-1.md`, ordered by severity with concrete reproduction steps. If suggesting code, put a unified diff under `reviews/claude/proposals/`; do not apply it.

## Final audit context

Codex has now also completed:

- the six-patch ground-truthed corpus with all expected verdicts and zero
  unsafe `VERIFIED` outcomes;
- five repeated robust runs with identical normalized evidence;
- a final local release build with 38 discovered tests, 36 executed passes,
  two intentionally gated skips, a live PIT-backed vertical slice, and no
  leaked Java processes or scratch workspaces;
- Docker, Railway, and GitHub Actions preparation;
- architecture, threat-model, evaluation, submission, presentation, and demo
  assets; and
- a final local browser proof of the three hosted verdicts.

Please extend the same review with these final checks:

- Can `compile-breaking`, `build-bypass`, or a missing Surefire/PIT report be
  misrepresented as passing evidence?
- Can a timed-out or truncated PIT report ever bypass correctness failures,
  or produce anything stronger than `PARTIALLY_VERIFIED`?
- Does Windows cleanup reliably remove JGit read-only object files without
  broadening the deletion boundary?
- Do Docker/runtime assumptions preserve the allowlisted-only claim?
- Does CI actually exercise the vertical slice and container health path?
- Are the architecture, security, evaluation, README, and presentation claims
  narrower than the implementation?
- Is any submission claim unsupported because deployment, testers, or the
  public repository are still pending?

Do not rewrite submission copy merely for style. Prioritize exploitable
correctness gaps, verdict loopholes, evidence provenance, unsafe claims, and
demo-breaking reliability issues.

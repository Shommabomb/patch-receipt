# Claude Code milestone 3 focused re-review prompt

Act as PatchReceipt’s independent senior Java and application-security reviewer.
Codex is the primary builder. This is a focused read-only re-review of the
milestone-3 remediation; do not edit the main source.

Repository:

Use the currently opened `patch-receipt` repository root: the folder containing
`AGENTS.md`, `pom.xml`, and this prompt.

Read these first:

1. `AGENTS.md`
2. `reviews/claude/milestone-2.md`
3. `REVIEW_DECISIONS.md`
4. `CODEX_JOURNAL.md`
5. `docs/SECURITY.md`
6. `docs/ARCHITECTURE.md`

Then inspect the complete working-tree diff. Focus on whether the implementation
actually closes C1, C2, and C3 rather than merely changing receipt wording.

Codex’s frozen local evidence is recorded in `docs/EVALUATION.md`:

- 55/55 focused Java 21 hardening tests passed;
- the six-patch corpus matched 6/6 expected verdicts with zero unsafe
  `VERIFIED` outcomes;
- five robust runs produced identical normalized evidence;
- the full Java 21 build discovered 70 tests, passed 68, skipped only two
  explicitly gated corpus tests, and had no failures/errors; and
- the exact packaged JAR produced `VERIFIED` in 42,306 ms with 6/6
  regressions, 9/9 edge cases, 5/5 observed changed-line mutants killed, and
  clean observed scope.

Do not trust those numbers merely because they are documented. Check the code,
test reports, machine-readable summaries, and commands as part of the review.

Verify, with code reading and focused probes/tests where useful:

1. Hidden `pom.xml`, `src/test/**`, and extra-file edits cannot reach patched
   test execution or `VERIFIED`.
2. Strict preflight accepts blank context correctly, validates headers and hunk
   counts, and fails closed on unsupported content.
3. Post-apply observed scope discovers the actual added, modified, and removed
   files; rejects binary/symlink and preflight mismatches; reapplies every scope
   policy; and supplies the final receipt and PIT changed-line set.
4. PIT timeout, non-zero exit, missing/partial/unreadable report, or a changed
   production file with no viable mutants can never produce `VERIFIED`, even
   when a parseable report says 100%.
5. Skipped mandatory tests cannot pass, selectors are exact, and reproduction
   requires the configured fully qualified failure type.
6. Central sanitization covers reasons, warnings, nested stage metrics, test
   failures, mutation/scope evidence, and Windows/Unix forms of workspace,
   Maven-cache, application-root, and user-home paths.
7. Receipt schema v2 gives JSON, Markdown, HTML, the dashboard, and the digest
   identical summary, limitations, verdict, and evidence.
8. Worker exceptions and stale jobs become terminal; no poll can expose
   `COMPLETED` with a null verdict; mutable status is not cached; browser polling
   is bounded.
9. The CI container job executes a real `minimal-robust` run and asserts schema
   v2, observed scope, healthy mutation evidence, and `VERIFIED`.
10. Documentation and UI claims remain narrower than what is measured.

Pay special attention to:

- `ScopeAnalyzer`
- `ObservedScopeAnalyzer`
- `PatchApplier`
- `VerificationEngine`
- `VerdictPolicy`
- `SurefireReportParser`
- `PitestReportParser`
- `EvidenceSanitizer`
- receipt renderers and digest
- `RunRegistry`, `RunApiController`, and `app.js`
- the exploit/fail-closed tests
- `.github/workflows/ci.yml`

Do not re-report a milestone-2 item that is demonstrably fixed. Do report any
new regression or loophole, ordered Critical / Important / Minor, with exact
file/line references and a concrete path to failure. End with:

- direct answers for C1, C2, and C3;
- whether any unsafe patch can still receive `VERIFIED`;
- whether the implementation is ready for production-container verification;
- whether any Critical finding remains.

Write your unedited review to:

`reviews/claude/milestone-3.md`

If you cannot write because the session is read-only, return the complete review
in chat so the user can paste it back unchanged. Do not modify main product
files or silently become the implementation owner.

# Claude Code milestone 2 prompt

Review the PatchReceipt working tree as an independent senior Java and
application-security reviewer.

Read these files first:

1. `AGENTS.md`
2. `PROJECT_PLAN.md`
3. `CLAUDE_HANDOFF.md`
4. `CODEX_JOURNAL.md`
5. `docs/SECURITY.md`

Then inspect `git diff` and the affected tests.

Do not act as the primary builder and do not modify the main source. Run
read-only inspections and tests where useful. Write your unedited,
severity-ordered findings to:

`reviews/claude/milestone-2.md`

Prioritize:

- verdict or evidence misrepresentation;
- HTML/script injection through receipt-derived content;
- stale or duplicate asynchronous run state;
- hosted allowlist and execution-boundary regressions;
- misleading compact metrics;
- demo-breaking usability defects; and
- unsupported submission claims.

For every finding include the file, relevant line or symbol, impact, and a
concrete reproduction. If proposing code, write a unified diff under
`reviews/claude/proposals/` rather than applying it.

Do not spend time rewriting prose merely for style. End with:

- ship / do-not-ship recommendation;
- the single highest-value remaining improvement; and
- the exact commands you ran.

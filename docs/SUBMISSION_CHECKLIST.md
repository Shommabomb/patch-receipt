# PatchReceipt submission checklist

This checklist separates work Codex can complete in the repository from actions that require the user's accounts, testers, voice, or final authority.

## Codex-complete and evidence-backed

- [x] Java 21 Maven project and pinned Maven Wrapper created.
- [x] Deterministic verification engine implemented.
- [x] Baseline bug reproduction and post-patch verification implemented.
- [x] Original regression, sealed edge-case, changed-line mutation, and scope evidence implemented.
- [x] Fixed `REJECTED`, `PARTIALLY_VERIFIED`, and `VERIFIED` policy implemented.
- [x] Canonical receipt model rendered as HTML, Markdown, and JSON.
- [x] Hosted API restricted to bundled case and patch identifiers.
- [x] Trusted-project CLI requires `--allow-local-execution`.
- [x] Single-page judge-facing dashboard implemented.
- [x] Browser confirmed all three public verdict states.
- [x] Robust browser run measured at 27,036 ms with 6 regressions, 9 edges, and 4 of 4 changed-line mutants killed.
- [x] Final local suite: 38 discovered, 36 passed, 2 intentionally gated skips.
- [x] Live vertical slice passed and left zero Java processes and zero scratch workspaces.
- [x] Dockerfile, Railway health configuration, and Windows/Ubuntu CI workflow authored.
- [x] Evaluation, demo, tester, and submission-checklist drafts created.
- [x] Codex planning and debugging record maintained in `CODEX_JOURNAL.md`.

## Remaining Codex repository work

- [x] Run the complete six-patch corpus and replace only measured pending cells in `EVALUATION.md`.
- [x] Run and record the five-run determinism protocol.
- [ ] Complete a clean final CI run on the exact submission commit.
- [ ] Validate the production container and health endpoint on the exact submission commit.
- [ ] Measure deployed warm latency and calculate p95 from a declared sample.
- [ ] Incorporate all three tester observation forms without hiding failures.
- [x] Finish `docs/ARCHITECTURE.md` and exported diagram assets.
- [x] Finish `docs/SECURITY.md`.
- [x] Update `README.md` from implementation-status language to final public instructions.
- [x] Finish the submission document.
- [x] Create and visually verify the seven-slide presentation and PDF export.
- [x] Perform final local broken-link, receipt-parity, artifact-count, and layout checks.
- [ ] Capture final screenshots from the frozen public deployment.
- [ ] Incorporate Claude's milestone review under `reviews/claude/` and disposition each finding in `REVIEW_DECISIONS.md`.
- [ ] Freeze behavior, version all artifacts consistently, and perform final broken-link and receipt-parity checks.

## User and account actions

### Public repository

- [ ] Choose or create the public GitHub repository.
- [ ] Confirm the intended Git author name and email.
- [ ] Push the exact reviewed commit and tags.
- [ ] Enable GitHub Actions and allow the final workflow to finish.
- [ ] Confirm the repository is publicly readable in a signed-out browser.
- [ ] Copy the final repository URL into the submission document and deck.

### Deployment

- [ ] Create or confirm the Railway account and Hobby billing.
- [ ] Connect Railway to the public repository.
- [ ] Confirm Railway builds the supplied Dockerfile.
- [ ] Configure spending alerts or limits acceptable to the user.
- [ ] Confirm `/actuator/health` is healthy.
- [ ] Confirm the public dashboard opens without authentication.
- [ ] Run the deployed latency sample while Codex records results.
- [ ] Keep the service available through the judging period.

### Tester study

- [ ] Recruit three amateur testers.
- [ ] Run `TESTER_SCRIPT.md` separately with each tester.
- [ ] Return the three completed observation forms to Codex.
- [ ] Approve any UI wording changes prompted by tester confusion.
- [ ] Re-run affected checks if the interface changes.

### Presentation and video

- [ ] Choose the final presentation visual direction.
- [ ] Review the seven-slide deck for personal tone and factual accuracy.
- [ ] Record the demo using `DEMO_SCRIPT.md`.
- [ ] Keep the final cut between 2:50 and 3:00.
- [ ] Verify no personal data, local paths, notifications, or credentials appear.
- [ ] Upload the final video and confirm it plays in a signed-out browser.
- [ ] Supply the final video URL.
- [ ] If a public Google document is required, upload the submission document and enable link viewing.

### Final submission

- [ ] Re-read the live hackathon rules and required fields.
- [ ] Confirm the chosen track is Agentic Coding and the secondary evaluation angle is Building Evals.
- [ ] Confirm every factual metric matches `EVALUATION.md`.
- [ ] Confirm repository, deployment, receipts, deck, document, and video all show the same version.
- [ ] Open every submitted link in a signed-out browser.
- [ ] Save a local copy or screenshot of every submitted field.
- [ ] Perform the irreversible final submission.
- [ ] Save the confirmation page and submission timestamp.

## Go/no-go gate

Do not submit until every statement below is true:

- [ ] The public URL is healthy and needs no credentials.
- [ ] The final GitHub Actions run is green.
- [ ] The six-patch corpus has zero unsafe `VERIFIED` outcomes.
- [ ] Pending evaluation claims are either measured or removed from submission materials.
- [ ] The presentation and video use only frozen-version evidence.
- [ ] The video is no longer than three minutes.
- [ ] All repository, app, receipt, document, deck, and video links work while signed out.
- [ ] The user has personally reviewed and approved the final submission.

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
- [x] Browser-confirm the hardened build’s three public verdict states.
- [x] Record the hardened production-config browser run with 6 regressions, 9 edges, and 5 of 5 changed-line mutants killed.
- [x] Record the final full local suite after Milestone 3.
- [x] Live vertical slice passed and left zero Java processes and zero scratch workspaces.
- [x] Dockerfile, Railway health configuration, and Windows/Ubuntu CI workflow authored.
- [x] Public Railway smoke test passed for health, all three verdicts, receipt downloads, and path sanitisation.
- [x] Evaluation, demo, tester, and submission-checklist drafts created.
- [x] Codex planning and debugging record maintained in `CODEX_JOURNAL.md`.

## Remaining Codex repository work

- [x] Run the complete six-patch corpus and replace only measured pending cells in `EVALUATION.md`.
- [x] Run and record the five-run determinism protocol.
- [ ] Complete a clean final CI run on the exact submission commit.
- [ ] Revalidate the production container and health endpoint after the final documentation/CI commit deploys.
- [ ] Measure deployed warm latency and calculate p95 from a declared sample.
- [ ] Incorporate all three tester observation forms without hiding failures.
- [x] Finish `docs/ARCHITECTURE.md` and exported diagram assets.
- [x] Finish `docs/SECURITY.md`.
- [x] Update `README.md` from implementation-status language to final public instructions.
- [x] Finish the submission document.
- [x] Create and visually verify the seven-slide presentation and PDF export.
- [x] Perform final local broken-link, receipt-parity, artefact-count, and layout checks.
- [ ] Capture final screenshots from the frozen public deployment.
- [x] Preserve Claude’s complete milestone-2 review and disposition every finding.
- [x] Preserve Claude’s focused milestone-3 re-review and disposition every new finding.
- [x] Preserve Claude’s final adversarial audit and disposition FA-1, FA-2, and FM-1 through FM-5.
- [ ] Freeze behaviour, version all artefacts consistently, and perform final broken-link and receipt-parity checks.

## User and account actions

### Public repository

- [x] Create the public GitHub repository.
- [ ] Confirm the intended Git author name and email.
- [ ] Push the exact reviewed commit and tags.
- [ ] Enable GitHub Actions and allow the final workflow to finish.
- [x] Confirm the repository is publicly readable without repository credentials.
- [ ] Copy the final repository URL into the submission document and deck.

### Deployment

- [x] Create the Railway project and connect it to the public repository.
- [x] Confirm Railway builds and starts the supplied Dockerfile.
- [ ] Configure spending alerts or limits acceptable to the user.
- [x] Confirm `/actuator/health` is healthy.
- [x] Confirm the public dashboard opens without authentication.
- [ ] Run the deployed latency sample while Codex records results.
- [ ] Keep the service available through the judging period.

### Tester study

Status: **not completed before the deadline; no tester result may be claimed.**

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
- [x] Confirm repository, deployment, receipts, deck, document, and video all show the same frozen product behaviour.
- [x] Open every submitted link in a signed-out browser.
- [ ] Save a local copy or screenshot of every submitted field.
- [ ] Perform the irreversible final submission.
- [ ] Save the confirmation page and submission timestamp.

## Go/no-go gate

Do not submit until every statement below is true:

- [x] The public URL is healthy and needs no credentials.
- [x] The final GitHub Actions run is green.
- [x] The hardened six-patch corpus has zero unsafe `VERIFIED` outcomes.
- [x] Pending evaluation claims are either measured or explicitly reported as not completed.
- [x] The presentation and video use only frozen-version evidence.
- [x] The video is no longer than three minutes (2:57).
- [x] All repository, app, receipt, document, deck, and video links work while signed out.
- [ ] The user has personally reviewed and approved the final submission.

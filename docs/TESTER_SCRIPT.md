# PatchReceipt amateur tester script

Status: **PENDING — no tester results have been measured yet**

This script is for three people who do not need programming knowledge. Each session should be run separately and should take no more than five minutes, including the short follow-up.

## Facilitator preparation

1. Give the tester only the public PatchReceipt URL.
2. Use a laptop or desktop browser at normal zoom.
3. Do not explain what the verdicts mean before the test.
4. Assign candidates by position:
   - Tester 1: first candidate.
   - Tester 2: second candidate.
   - Tester 3: third candidate.
5. Start timing when the tester receives the task below.
6. Do not coach. If the tester asks for help, say: “Please do what seems natural.” Record the request.
7. Stop timing when the tester has opened the receipt download and answered whether the patch should ship.

## Instructions to read to each tester

“You are reviewing a software change suggested by AI. You do not need to understand the code.

Please use this page to:

1. choose the **[first / second / third]** candidate;
2. check whether PatchReceipt thinks it should ship;
3. tell me the most important reason for that decision; and
4. download either the Markdown or JSON receipt.

Work as you normally would. Please talk aloud if something is confusing.”

Start the timer immediately after reading the instructions.

## Follow-up questions

Ask these questions in order without suggesting an answer:

1. “Would you ship this patch: yes, no, or only after a person reviews it?”
2. “What evidence on the page mattered most to you?”
3. “In your own words, what does this verdict mean?”
4. “What, if anything, was confusing?”
5. “Where would you expect to find more detail?”

## Observation form

Complete one copy per tester.

| Field | Result |
|---|---|
| Tester number | |
| Assigned candidate position | |
| Device and browser | |
| Core-task time | |
| Candidate selected without help | Yes / No |
| Verification started without help | Yes / No |
| Tester waited for completion | Yes / No |
| Tester found the verdict | Yes / No |
| Tester gave a ship decision | Yes / No |
| Tester identified relevant evidence | Yes / No |
| Markdown or JSON receipt downloaded | Yes / No |
| Number of help requests | |
| Tester’s exact verdict explanation | |
| Tester’s exact confusion or suggestion | |

## Facilitator scoring key

Do not show this section to testers before the session.

| Assigned candidate | Expected decision | Sufficient plain-language explanation |
|---|---|---|
| First: plausible object-level deduplication | Do not ship | The independent correctness checks found behavior the patch did not fix. |
| Second: correct fix with unrelated drift | Require human review before shipping | The fix passed correctness checks, but it changed an unexpected production file. |
| Third: minimal robust fix | Reasonable to ship on the evidence shown | The bug check, original tests, independent edge cases, scope check, and mutation check passed. |

Accept equivalent wording. Do not require the tester to use terms such as “mutation testing,” “scope drift,” or “regression.”

## Planned acceptance threshold

The usability result passes only if:

- at least two of the three testers complete all four tasks without help in under three minutes; and
- all three correctly explain their verdict after viewing the receipt.

Record the result as `PENDING` until all three observation forms are complete. Report failed attempts and requests for help; do not discard them.

## Results summary

Complete this section only after all three tests.

| Measure | Result |
|---|---|
| Testers completing all tasks unaided in under three minutes | Pending |
| Testers correctly explaining the verdict | Pending |
| Testers downloading a receipt | Pending |
| Median core-task time | Pending |
| Most common confusion | Pending |
| Product changes made from feedback | Pending |

Final usability status: **PENDING**

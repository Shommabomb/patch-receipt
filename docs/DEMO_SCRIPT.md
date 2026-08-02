# PatchReceipt demo script

Target duration: **2:58**
Hard maximum: **3:00**

## Recording preparation

Complete these steps within 30 minutes of recording because completed web runs expire:

1. Open the deployed PatchReceipt dashboard in **Tab 1** and leave it fresh.
2. In **Tab 2**, run **Minimal robust fix** to completion and leave the completed `VERIFIED` dashboard visible.
3. Open the public repository's `CODEX_JOURNAL.md` in **Tab 3**.
4. Open a terminal in **Window 2** with this command already entered, but do not run a verification:

   ```text
   java -jar target/patch-receipt-0.0.1-SNAPSHOT.jar verify --help
   ```

5. Set browser zoom to 100%, hide bookmarks and personal account details, disable notifications, and use a 1080p recording canvas.
6. Keep the pointer still unless the script calls for a click.
7. Record one real plausible-patch run. A brief edit may remove dead waiting time, but retain the same run ID and add a small on-screen caption: `Brief cut — same verification run`.

## Timed script

| Time | Exact screen action | Exact narration |
|---|---|---|
| `0:00–0:13` | Start on Tab 1 at the hero. Do not scroll for the first five seconds. Move the pointer across the sentence “AI writes the patch.” | “Any coding agent can write a patch in seconds. The hard question is whether that patch deserves to ship. PatchReceipt turns that decision into deterministic, inspectable evidence.” |
| `0:13–0:27` | Scroll once to **Retry-safe checkout coupons**. Point at the bug report and then the **Hash allowlisted** chip. | “This bundled Java checkout has a retry bug: the same case-insensitive coupon can be applied more than once. The hosted demo runs only hash-allowlisted code and accepts no uploads or API keys.” |
| `0:27–0:40` | Click **Plausible object-level deduplication**. Pause on the diff. | “Here is the dangerous kind of AI patch: small, readable, and plausible. It removes duplicate objects, but that is not the same as proving the coupon contract.” |
| `0:40–0:45` | Click **Verify this patch** exactly once. | “I’ll verify it against a sealed pack the patch cannot edit.” |
| `0:45–1:04` | Keep the running timeline centred. Point to each stage only as it appears. If needed, make the labelled brief cut here. | “PatchReceipt first checks scope, then proves the bug exists on the pristine baseline. It applies the patch to a fresh copy, reruns the original tests, generates independent edge cases, and only runs mutation analysis when correctness still holds.” |
| `1:04–1:18` | When the badge changes to `REJECTED`, point first at the badge, then the failed edge-case stage. Click **HTML**. | “The original green tests were not enough. The independent contract challenge fails, so this cannot be softened into ‘partially verified’. Mandatory correctness failure means rejected.” |
| `1:18–1:31` | In the HTML receipt tab, scroll to **Independent edge cases** and pause on the named failures. Then close this tab with `Ctrl+W`. | “The receipt keeps the counterexamples, stage evidence, input hashes, and a canonical digest. A reviewer can see why the patch failed instead of trusting a score.” |
| `1:31–1:43` | Switch to Tab 2. Point to the `VERIFIED` badge and the completed evidence stages. | “Now compare the minimal robust patch. Every required evidence gate completed and the result is verified.” |
| `1:43–2:01` | Click **HTML**. In the receipt, scroll first to **Patched regression**, then **Independent edge cases**, then **Mutation evidence**. | “It preserved all 6 original regressions, passed 9 sealed edge cases, and killed all 5 viable mutants on genuinely changed lines. The observed file scope stayed clean.” |
| `2:01–2:16` | Close the HTML tab with `Ctrl+W`. On the completed dashboard, click **Markdown**, then click **JSON**. Let both downloads finish. | “The same canonical evidence model exports as human-readable HTML and Markdown, plus machine-readable JSON. The verdict is rule-based and consistent across all three.” |
| `2:16–2:30` | Switch to Window 2. Run the prepared `verify --help` command and point at `--allow-local-execution`. | “For trusted local Maven projects, the same engine is available as a CLI. It refuses to execute project code until the developer explicitly acknowledges that build tests can run arbitrary code.” |
| `2:30–2:47` | Return to the browser and switch to Tab 3. Scroll through the journal headings **Vertical-slice implementation, first debug loop**, **Vertical slice proved and optimised**, and **Judge-facing browser proof**. | “Codex was the primary builder: planning the system, implementing the engine and interface, diagnosing real failures, optimising six Maven launches down to three, and recording the evidence. Claude is reserved as a tracked independent reviewer, not a hidden primary builder.” |
| `2:47–2:58` | Return to Tab 2. Place the pointer between the `VERIFIED` badge and the receipt links. End on the product proposition. | “AI writes the patch. PatchReceipt proves whether it deserves to ship: before, after, and beyond the green test.” |

## Editing rules

- Final runtime must be between `2:50` and `3:00`; target `2:58`.
- Do not replace live output with a mock receipt.
- Do not imply the robust run is a deployed p95 measurement; it is one measured browser run.
- Do not claim that PatchReceipt uses an LLM at runtime or invents tests from natural language.
- Do not call the hosted container an arbitrary-code sandbox.
- Keep the `PENDING` evaluation items out of voiceover unless they have been measured before recording.
- If any click or metric differs on the frozen submission commit, stop and update this script before recording.

## Capture checklist

- [ ] No account names, tokens, local absolute paths, or notifications are visible.
- [ ] The rejected run retains one run ID across any edit.
- [ ] The robust dashboard shows `VERIFIED`.
- [ ] The robust receipt visibly shows 6 regressions, 9 edge cases, and 5 of 5 mutants killed.
- [ ] Markdown and JSON downloads visibly start.
- [ ] The safety boundary and local CLI consent flag are visible.
- [ ] The Codex journal is readable for at least ten seconds.
- [ ] Final exported video is at most three minutes.

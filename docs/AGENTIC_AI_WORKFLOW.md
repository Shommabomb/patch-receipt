# Using PatchReceipt with agentic AI

PatchReceipt is model-agnostic. Codex, Claude Code, Cursor, Copilot, or another coding agent may produce the candidate change; PatchReceipt only needs a normal unified diff.

## Where it fits

```text
Bug report
    |
    v
Coding agent writes a candidate patch
    |
    v
PatchReceipt verifies the patch independently
    |
    +--> REJECTED: give the counterexample to the agent and try again
    |
    +--> PARTIALLY_VERIFIED: review incomplete mutation evidence or scope drift
    |
    +--> VERIFIED: evidence gates passed; a human or CI may continue the merge process
```

PatchReceipt is not another coding agent. It is the independent evidence step between generation and merge.

## One practical agent loop

1. Start from a clean, trusted Java 21 Maven project.
2. Write the bug report and verifier pack before evaluating candidate patches. The verifier pack must remain outside the agent's patchable project.
3. Let the coding agent implement its fix on a branch or in a separate working tree.
4. Export the candidate change:

   ```text
   git diff --binary <baseline-commit>...<agent-branch> > candidate.patch
   ```

5. Run PatchReceipt:

   ```text
   java -jar target/patch-receipt-0.0.1-SNAPSHOT.jar verify
     --project <clean-baseline-directory>
     --bug-report <bug-report.md>
     --patch <candidate.patch>
     --verifier-pack <verifier-pack-directory>
     --output <receipt-directory>
     --allow-local-execution
   ```

6. Use the result:

   - `REJECTED`: pass the failing edge case, regression, compilation error, or scope violation back to the agent.
   - `PARTIALLY_VERIFIED`: inspect the warning. The code may be correct, but the available evidence is not strong enough for `VERIFIED`.
   - `VERIFIED`: every configured correctness, scope, and mutation gate passed. This is evidence for review, not proof of design quality or security outside the verifier pack.

7. If the agent revises its patch, export a new diff and run PatchReceipt again. Each run creates a new hash-bound receipt.

## Example prompt for a coding agent

> Fix the bug described in `bug-report.md`. Keep the change minimal and do not edit tests, build files, CI configuration, or the verifier pack. When finished, explain the change and leave the working tree ready for a unified diff. Do not claim the patch is verified; PatchReceipt will evaluate it independently.

## What makes this genuinely agentic

An orchestrator can repeat the loop automatically:

1. Ask the agent for a patch.
2. Run PatchReceipt locally.
3. Read the machine-readable JSON receipt.
4. If the verdict is `REJECTED`, feed only the decisive findings back to the agent.
5. Stop on `VERIFIED`, a retry limit, or a human-review condition.

No OpenAI, Anthropic, or other model API is required by PatchReceipt itself. The agent and verifier remain separate, which prevents the patch author from silently weakening the evidence used to judge its own work.

## Current MVP boundary

The public dashboard demonstrates this loop with bundled, hash-allowlisted candidates. It does not accept arbitrary repositories or patches. Real trusted-project use is through the local CLI with the explicit `--allow-local-execution` acknowledgement.

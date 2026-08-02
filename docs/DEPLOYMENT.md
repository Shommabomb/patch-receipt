# Deploy PatchReceipt

PatchReceipt is deployed through `Dockerfile` and `railway.json` at:

[`https://patch-receipt-production.up.railway.app/`](https://patch-receipt-production.up.railway.app/)

The deployment uses the repository owner's GitHub and Railway accounts; no
credentials are stored in this project.

## Before connecting Railway

1. Run the final local verification:

   ```powershell
   $env:MAVEN_USER_HOME = Join-Path (Get-Location) '.cache\maven'
   .\mvnw.cmd verify
   ```

2. Push the exact reviewed commit to a public GitHub repository.
3. Wait for all three GitHub Actions jobs to pass:
   - Windows unit and safety suite;
   - Ubuntu vertical slice with live PIT evidence; and
   - production container build and health smoke test.

## Railway setup

1. Create a Railway project from the public GitHub repository.
2. Select the repository root. Railway should detect the supplied
   `Dockerfile` through `railway.json`.
3. Do not add an application secret or database.
4. Generate a public Railway domain.
5. Confirm the deployment health check is `/actuator/health`.
6. Set a spending alert or limit acceptable to the repository owner.
7. Keep the service at one replica for the demo; the application itself
   serializes verification through one worker.

The application reads Railway's injected `PORT` value and defaults to `8080`
locally.

## Public smoke test

Open the public domain in a signed-out browser, then check:

```text
GET /
GET /actuator/health
GET /api/v1/cases
```

Expected properties:

- no authentication prompt;
- health status `UP`;
- exactly one bundled case;
- exactly three hosted patch candidates;
- no endpoint accepting repository, URL, path, command, raw patch, or source
  content.

Run all three candidates and confirm:

| Patch ID | Expected verdict |
| --- | --- |
| `plausible-distinct` | `REJECTED` |
| `correct-with-drift` | `PARTIALLY_VERIFIED` |
| `minimal-robust` | `VERIFIED` |

Download the robust receipt in HTML, Markdown, and JSON and check that all
three show the same verdict and receipt digest.

## Latency sample

Do not call one successful run “p95.”

1. Complete one unrecorded warm-up run.
2. Run the robust candidate at least 20 times.
3. Record every receipt duration and every failed run.
4. Report sample size, minimum, median, p95, maximum, and failures in
   `docs/EVALUATION.md`.

## Rollback and fallback

- Railway deploys a saved Git commit. Roll back to the last known green commit
  if a new build fails.
- If live PIT repeatedly exceeds the product target, implement only the
  documented hash-bound `CACHED_CI_EVIDENCE` fallback; do not silently reuse
  stale mutation output.
- If Railway itself is unavailable, deploy the same Dockerfile to a paid
  Render web service. Do not redesign the application during submission
  week.

## Verified production status

The 2 August 2026 smoke test established:

- dashboard HTTP 200 and health status `UP`;
- exactly one case and three allowlisted candidates;
- `plausible-distinct` returned `REJECTED`;
- `correct-with-drift` returned `PARTIALLY_VERIFIED`;
- `minimal-robust` returned `VERIFIED` in 6,652 ms; and
- JSON, Markdown, and HTML receipts returned HTTP 200 with matching verdicts
  and no tested local absolute-path disclosure.

This single production run is not a latency distribution or p95. Provider-level
isolation and arbitrary-code safety remain outside the supported claims.

# PatchReceipt security model

## Security statement

PatchReceipt’s public safety model is **bundled allowlisting, not sandboxing**.

The web application is designed to execute only source, patches, manifests, and verifier tests packaged with PatchReceipt and selected by known IDs. A container reduces blast radius and improves reproducibility, but it is not presented as a secure boundary for arbitrary hostile Java, Maven plugins, repositories, patches, or tests.

Do not add public source uploads, raw patch bodies, remote repository URLs, custom verifier code, Maven goals, shell commands, or filesystem paths without replacing this model with a purpose-built isolation design.

The repository contains Docker and Railway configuration, but no deployment has yet been performed or security-tested in a public environment.

## Assets and trust boundaries

PatchReceipt protects:

- the host and container running the verifier;
- the application’s Maven cache and scratch workspace;
- the integrity of verdict rules and sealed verifier tests;
- the availability of the single verification worker;
- the confidentiality of host filesystem paths and process logs; and
- the traceability of evidence receipts.

The trust boundaries are:

1. **Anonymous web caller:** untrusted, but able to submit only `caseId` and `patchId`.
2. **Bundled case pack:** trusted application content reviewed and packaged with the JAR.
3. **Verification child processes:** capable of executing Java and Maven plugin code; constrained operationally but not safely sandboxed.
4. **Trusted local CLI user:** explicitly chooses to execute their local project and verifier pack.
5. **Container/runtime provider:** expected to enforce its own process, filesystem, and memory isolation.

## Hosted admission controls

`BundledCaseRepository` admits only:

- case `checkout-coupons`; and
- patch IDs `plausible-distinct`, `correct-with-drift`, and `minimal-robust`.

`POST /api/v1/runs` accepts only those two identifiers. It does not accept code, patch text, URLs, commands, paths, Maven options, test selectors, or verifier source from the request. Unknown IDs are rejected before a workspace or child process is created.

The project, verifier pack, manifest, bug report, and diffs are read from packaged classpath resources. SHA-256 values are computed for all five input categories and included in the receipt. These hashes are traceability data:

- they are not digital signatures;
- they are not compared with an external signed registry during admission; and
- they do not by themselves prove provenance.

The effective admission guarantee is the in-code ID allowlist plus packaged application resources.

## Local CLI boundary

The CLI refuses `verify` unless the caller supplies `--allow-local-execution`. Its warning states that Java builds and tests can run arbitrary code.

For local inputs, `LocalCaseLoader`:

- requires a `pom.xml`;
- accepts only a schema-v1 Java 21 Maven manifest;
- rejects symbolic links in accepted project and verifier paths;
- rejects absolute or escaping verifier index entries;
- excludes `.git`, `.cache`, `.patchreceipt-work`, `target`, `build`, and `.idea`;
- caps intake at 1,000 files and 8 MiB; and
- constrains stage timeout configuration to 1–120 seconds.

These checks prevent common accidental path and size problems. They do not make an untrusted local repository safe. A local POM, Maven plugin, test, annotation processor, or verifier class may execute arbitrary code with the CLI user’s permissions.

## Patch and path controls

Before materialization or execution, `ScopeAnalyzer` inspects the unified diff and:

- requires matching `diff --git`, `---`, and `+++` headers;
- validates header correspondence and every hunk’s declared line counts;
- accepts an empty context line explicitly and rejects unknown hunk content;
- rejects empty diffs and diffs without complete file headers;
- rejects NUL content and Git binary-patch markers;
- rejects absolute, drive-qualified, and `..` traversal paths;
- records changed files, additions, deletions, and changed new-line numbers;
- enforces expected production paths;
- rejects forbidden globs;
- limits the bundled case to two changed files and 50 changed lines; and
- reports an unexpected production path as soft drift.

The bundled manifest forbids changes to:

- `pom.xml`;
- `.mvn/**`;
- `mvnw` and `mvnw.cmd`;
- `.github/**`;
- `Dockerfile`; and
- `src/test/**`.

JGit applies the already-inspected unified diff to a fresh patched copy. `VerificationCase` normalises every materialised path and refuses a destination outside its workspace.

Immediately after application, before patched code runs, `ObservedScopeAnalyzer` compares the pristine and patched trees. It independently discovers added, modified, and removed files, computes actual line changes, rejects changed binaries and symbolic links, and reapplies all path and size policies. Any mismatch between preflight claims and observed changes is a hard violation. Final receipts and PIT filtering use this observed evidence.

The preflight parser remains intentionally limited to the strict diff form used by the MVP. Safety does not depend on accepting every Git patch form: unsupported forms fail closed, and accepted forms must reconcile with what changed on disk.

## Process controls

All child processes are created with `ProcessBuilder(List<String>)`. PatchReceipt never builds a command by concatenating request values into a shell string.

Maven goals used by the engine are fixed:

1. one shared baseline JUnit run;
2. one shared patched JUnit run; and
3. one targeted PIT run, only after correctness gates pass.

On Windows, the runner invokes Maven’s Plexus Classworlds Java entry point directly from the checksum-verified wrapper distribution. On Unix, it invokes the fixed wrapper path through `sh`; request data is still passed as separate arguments rather than interpreted shell text.

The production container sets the runner to Maven offline mode and includes the warmed project-local Maven repository. Maven receives:

- batch and no-transfer-progress flags;
- disabled colour;
- an explicit project-local repository;
- `MAVEN_OPTS=-Xmx256m -XX:MaxMetaspaceSize=160m`; and
- a headless JVM.

Maven offline mode prevents dependency resolution from the network; it is not a
network-egress firewall for arbitrary Java code. The hosted allowlist remains
the primary safety boundary until the deployment platform enforces egress
controls.

The bundled PIT worker is single-threaded and has `-Xmx192m`. The application container starts with `-XX:MaxRAMPercentage=35.0` and `-XX:ActiveProcessorCount=2`. A Surefire fork does not currently have a separate explicit heap cap; the container or hosting platform remains the outer memory boundary.

## Time, output, workspace, queue, and retention limits

The bundled manifest currently configures:

| Resource | Implemented limit |
| --- | --- |
| Maven process duration | 45 seconds per invocation |
| Whole production verification | 90 seconds |
| Captured output | 24,000 characters per invocation |
| Run workspace | 67,108,864 bytes (64 MiB), checked after execution |
| Concurrent verification | 1 active job |
| Waiting queue | 3 jobs |
| Completed receipt retention | 30 minutes in memory |
| Local input | 1,000 files and 8 MiB |

When a process exceeds its timeout, `BoundedProcessRunner` destroys descendants, then destroys and forcibly destroys the parent if needed. A timeout cannot count as valid reproduction or passing correctness evidence.

Output is read concurrently, capped in memory, and marked as truncated in the internal `ProcessResult`. Before a receipt is finalised, one central sanitiser masks workspace, Maven-cache, application-root, and user-home paths across stages, logs, reasons, warnings, test failures, mutation evidence, scope evidence, and nested metrics. The receipt records log truncation where exposed by stage evidence.

Workspaces are unique per receipt and restricted beneath `.patchreceipt-work`. Cleanup runs in `finally` and recursively deletes only a normalised descendant of the configured workspace root. The 64 MiB check occurs after child execution; it is not a live disk quota.

`RunRegistry` uses one worker and an `ArrayBlockingQueue` of three. A full queue returns HTTP 429. Jobs and receipts are process-local, contain no user account data, and terminal completed/failed entries are purged after 30 minutes. Worker failures cannot remain indefinitely `RUNNING`.

The engine enforces a 90-second production whole-run deadline and bounds each new child process by the smaller of its stage limit and remaining run time. In-process cleanup and final rendering may add slight wall-clock overhead after a child process exits. There is no per-IP rate limiter.

## Evidence and output controls

The engine uses typed domain records and a fixed `VerdictPolicy`. Any blocking correctness, execution, workspace, or hard-scope failure produces `REJECTED`; it cannot be downgraded to `PARTIALLY_VERIFIED`. Skipped mandatory tests do not pass. PIT timeout, non-zero exit, unreadable/partial output, or missing file-level mutation evidence is explicitly unhealthy or inconclusive and cannot produce `VERIFIED`.

Receipt schema v2 stores one canonical summary and limitations block alongside all evidence. JSON, Markdown, and HTML render that same content. The standalone HTML renderer escapes evidence text and includes a restrictive document CSP. Receipt download endpoints set explicit content types, content disposition, and `X-Content-Type-Options: nosniff`; mutable status responses send `Cache-Control: no-store`.

`ReceiptDigestService` removes the digest field, sorts canonical JSON properties and map keys, and computes SHA-256 over the remaining receipt. This detects changes when receipts are compared, but the digest is not keyed or signed and does not prevent a party from fabricating a new receipt and digest.

The current application does not configure a global Content Security Policy, HSTS, cross-origin policy, or authentication. Those omissions are acceptable only for the bundled, non-sensitive demonstration and must be revisited before broader use.

## Credentials, external services, and persistence

PatchReceipt uses:

- no OpenAI or Claude runtime API;
- no API key;
- no application credentials;
- no authentication or user accounts;
- no database;
- no persistent volume; and
- no remote repository or URL fetch at runtime.

The web registry and receipts are in memory. Scratch workspaces and the Maven cache are local filesystem data. The cache is application tooling, not user history.

The Docker runtime uses non-root UID/GID `10001` and a non-login user. This limits container permissions but does not turn Java execution into a hostile-code sandbox.

## Threats and mitigations

| Threat | Current mitigation | Residual risk |
| --- | --- | --- |
| Caller submits malicious Java or tests | Web API accepts only allowlisted IDs backed by packaged resources | A compromised or malicious bundled resource still executes |
| Command injection | Fixed goals and `ProcessBuilder` argument arrays; no shell-string concatenation | Trusted local manifests still influence Maven test selectors as arguments |
| Diff changes tests or build controls | Strict fail-closed preflight plus post-apply filesystem reconciliation before patched execution | Semantic intent is not inferred; arbitrary hostile repositories remain unsupported |
| Path traversal or symlink escape | Normalization, root-prefix checks, traversal rejection, and local symlink rejection | Filesystem and platform edge cases require continued testing |
| CPU or process exhaustion | Three-process design, 90-second run deadline, per-process timeout, process-tree termination, one worker, queue of three | No per-client rate limit |
| Memory exhaustion | Maven heap/metaspace bounds, PIT heap bound, application RAM percentage, one active run | Surefire has no explicit child heap cap; provider limits are required |
| Disk exhaustion | Unique scratch roots, cleanup, 64 MiB post-run check, 8 MiB local intake cap | Workspace size is checked after execution, so temporary growth can exceed the limit |
| Log or path disclosure | Output cap and centralised masking of known application, cache, workspace, and home paths | Arbitrary secrets printed by a trusted local build cannot be recognised generically |
| Receipt tampering | Canonical SHA-256 digest and matching renderers | Digest is unsigned and can be recomputed by an attacker |
| Queue abuse | Bounded queue and HTTP 429 | Anonymous callers can repeatedly occupy available slots |
| Dependency or image compromise | Maven Wrapper checksum and offline runtime cache | Container base images are tag-pinned, not digest-pinned; no signed SBOM is enforced |

## Residual risks and required future work

Before PatchReceipt accepts any untrusted repository, patch, or verifier pack, it needs a materially different execution architecture. At minimum:

- disposable VM or microVM isolation with no host mounts;
- no network egress;
- read-only base filesystem and ephemeral bounded writable storage;
- kernel-level CPU, memory, process, and disk quotas;
- provider-enforced deadline and reliable process-group termination independent of the application process;
- per-client rate limiting and abuse monitoring;
- a complete, battle-tested patch parser and stricter manifest validation;
- signed case-pack and receipt provenance;
- explicit Surefire child-JVM limits;
- cleanup auditing and startup garbage collection for abandoned workspaces;
- security response headers and production TLS validation; and
- dependency, container, and SBOM scanning with digest-pinned images.

Until those controls exist, the safe claim is narrow: the public application runs only reviewed, bundled demonstration cases selected by allowlisted IDs. The local CLI runs only projects the user has decided to trust.

## Reporting security issues

This hackathon repository does not yet publish a dedicated security contact. Do not include secrets or sensitive projects in bug reports. Record reproducible findings privately with the repository owner until a public disclosure process is established.

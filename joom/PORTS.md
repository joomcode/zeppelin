# Joom changes on Apache Zeppelin 0.12.1

Base: upstream `v0.12.1`, commit `ae9cb72ff`. Fork version: `0.12.1-joom-1`.
The reference fork is `joom-0.9` at `be1fe0d4b`.
Changes are adapted by behavior; obsolete implementation details are not copied blindly.

| Original change | Behavior retained / implementation | Regression coverage |
|---|---|---|
| `24f3ce8f6`, `e869e7376` | Global `default` and forced `new` ACL; independent permission sets and persisted overrides | AuthorizationPolicyTest, NoteAuthTest; audit outsider/reader/runner/writer scenarios |
| `f520524a6` | Paragraph edits emit indexing events | NotebookServiceTest: edit becomes searchable without restart |
| `503528c7c`, `2879ba1a6` | Code search splits dots and preserves underscores; digits retained to avoid breaking numbered names | LuceneSearchTest |
| `2ba64160e` | Last executor survives reload; transient authentication resets independently | NoteTest |
| `c4f66d7d6`, `89b835c03`, `51cb9c458` | BigQuery Cloud client, NULL, zero rows, project, region and cancellation per interpreter | BigQueryResultTest and real GCP constants/metadata controls |
| `7218b8ca0` | Configured Joom Scala precode runs once during inner-session initialization, including SQL-first | SparkInterpreterTest and real SQL-first -> Scala control |
| `27b84cf75` | Foreign/Delta job groups and optional job-link exceptions do not escape the Spark listener | SparkUtilsTest: foreign event followed by valid Zeppelin event |
| `fc821eea5`, `44a311a8a` | Failed launches/close discard state without losing the original error | ManagedInterpreterGroupTest, RemoteInterpreterCloseTest; K8s fault/restart series |
| `7ce169808`, `c8246ce34` RPC part | Preserve the transport exception after exhausted retries, including null messages | PooledRemoteFailureTest |
| `c8246ce34` DNS part | Bounded DNS readiness retry and interruption handling | DnsReadyTest; delayed-DNS behavior is simulated deterministically, not proven by ordinary pod starts |
| `7533f7f5c` | Preserve argv through SSH/sudo/bash wrappers, including spaces in classpath | test_impersonation.py executes the actual launcher with stub executables |
| `91a6d4eeb`, `69be09346`, `474ad4561`, `3f3696b2d`, `83099ac5e` | Retain unacknowledged local text, including empty text, across NOTE and PARAGRAPH broadcasts | Playwright migration regressions; validated failing upstream control |
| `391506f3d`, `c525f5552`, `e7ff8d928`, `1de931c0c`, `9e79ab222` | Re-associate the note while preserving editor instances and drafts; refuse offline execution | Actual websocket break/reconnect and delayed NOTE delivery |
| `63cbcde6d`, `fd9a35155`, `1bdeae362`, `250c5c0ea`, `6da3df498` | Renew ticket before reconnect; inline login or SSO in another tab preserves the current page | Playwright re-login and persistence control; real restart series recorded separately |
| Conflict diagnostics series (`b935490c7`, `980de75a6`, `a8c0fe763`, `ffdeec4ab`, `ea0e707c7`) | Warn on concurrent text changes without dumping notebook contents | Two-tab audit; no claim of automatic conflict resolution. The old server checker only logged conflicts. |
| `7f91e4a2f`, `8b388c0d6`, `8967ef57f`, `a1436ac66`, `2675eeb95`, `11941c112` | Full-path titles, filtering, renaming and clone defaults in active Angular UI | Path/clone audit and deployment smoke |
| New observed UI defect | Copy inherited websocket listener registries; published-paragraph NOTE handler must not mutate normal editor state | Same delayed NOTE scenarios failed before registry isolation and passed after it |
| Existing Yahoo remote-user extension | Relocate only javax.servlet to jakarta.servlet; keep deployed filter/realm behavior | Artifact namespace assertion and GCP header-based authentication smoke |

## Changes omitted deliberately

- `4aa8882cc` HADOOP_USER_NAME workaround addressed HBase, which is no longer used.
  Current GCS write/read and Spark proxy-user controls passed without it. Spark still
  contains Hadoop client APIs; this is not a claim that every Hadoop library is absent.
- `dcf034b73` token rotation branch is not copied. Two actual TTL600s token changes
  and real API operations passed on pristine 0.12.1/Fabric8 6.13.4. This finite control
  does not rule out every possible failure.
- `e88036cf3` comparator and `52750c4a3` null dynamic-form mechanisms are already
  present upstream. Trino driver property handling from `1ae6438bc` is present in
  the 0.12.1 JDBC interpreter. Do not overlay an old 0.9 JDBC interpreter JAR.
- `870683d99` collaborative-status workaround is not copied: upstream guards this
  path when collaborative mode is disabled, matching the deployed setting. A full
  collaborative-mode stress test remains separate.
- `91c9a40c6` search shutdown and additional socket diagnostics are not copied
  mechanically after the upstream refactor. Ordinary restart/reconnect tests do
  not establish absence of their original races.
- Old version bumps, packaging workarounds, dependency upgrades and AngularJS
  placement/base-href changes are replaced by 0.12.1 packaging and its active UI.
  This port targets the new Angular UI; classic AngularJS is not its regression target.

## Evidence discipline

A passing finite series proves only the conditions exercised. Retain image digests,
source revision, timestamps, real fault-injection counts and failed positive controls.
Discard invalid injections, setup failures and concurrent deployment interference.
The initial audit and raw evidence remain on branch `codex/zeppelin-012-audit`.
The deployment report records which scenarios ran against the actual built image.

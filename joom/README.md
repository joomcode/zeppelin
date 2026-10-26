# Zeppelin 0.12.1 Joom fork

This branch maintains the Joom behaviors documented in [PORTS.md](PORTS.md) on the
unchanged Apache 0.12.1 API shape. Each functional change has its original commit
references in the Git message and its own regression coverage.

## Build

Use JDK11 (as pinned in pom.xml), the Maven wrapper and the upstream-pinned Node18.
The server uses Java11. Interpreter images use the current matched Java17/Spark3.5.3
runtime from GCP. Server and interpreter JARs both come from this same source tree.

```sh
export JAVA_HOME=/path/to/jdk-11
bash joom/build.sh
```

The script verifies the exact Spark3.5.3 source archive, rebuilds the shaded RPC
chain and Scala2.12 module, runs selected regression tests, checks changed Java
lines, adapts the pinned remote-user extension and builds the active Angular WAR.
The upstream baseline contains existing Checkstyle violations. They are counted
separately; new errors on added lines fail `check_changed_style.py`.

Review `joom/build-context/overlay/joom-build.json` (source revision and every
artifact SHA256), then submit the two amd64 image targets:

```sh
gcloud builds submit joom/build-context --project=gcp-joomanalytics-platform \
  --config=joom/build-context/cloudbuild.json
```

The explicit build context contains JAR/WAR outputs and metadata only. It excludes
notebooks, live configuration, cloud credentials, browser state and test traces.
Image bases are pinned by digest; Java17/Spark runtime initially matches GCP test
build 1.1.3518. Use the resulting image digest when deploying.

## Browser regressions

Against an isolated Shiro fixture, provide the private credentials-file path:

```sh
cd zeppelin-web-angular
ZEPPELIN_REGRESSION_URL=http://127.0.0.1:18081/ \
ZEPPELIN_REGRESSION_CREDENTIALS=/private/path/credentials.json \
  ./node/node ./node_modules/@playwright/test/cli.js test --config=playwright.migration.config.js
```

For GCP balloon, use its local port-forward with the /balloon-gcp/ context and
`ZEPPELIN_REGRESSION_REMOTE_USER=aleksandrov`. This header-mode check validates the
backend remote-user integration; it does not replace a real browser SSO-session check.
The inline password-login case is specific to Shiro fixtures and skips in header mode.

Playwright traces can contain session data. Keep them private and outside Git.
The tests create and remove only their own synthetic notebooks. They do not run
Spark jobs or write data tables. Their held NOTE precondition must be observed
before an injection can count as a valid result.

## Balloon deployment

GCP balloon is namespace `zeppelin-test` in context `analytics-gke`, served through
https://analytics.joom.ai/balloon-gcp/. The old test service shares the 0.9 notebook
bucket/prefix. The 0.12 pilot must use separate notebook, search and local-repo PVCs;
retain the old PVC resources and Helm revision for rollback. Copy selected notes
without executing their production statements.

Use existing per-team Workload Identity, not copied long-lived keys. Cloud test
operations are restricted to `joom-analytics.junk_aleksandrov` and its approved GCS
location under `gs://joom-analytics-users/aleksandrov/junk_aleksandrov`.
Deployment details, image digests and final regression results are recorded separately.

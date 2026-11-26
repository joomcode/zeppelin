# Zeppelin 0.12.1 Joom fork

The maintained release branch is [`joom-0.12`](https://github.com/joomcode/zeppelin/tree/joom-0.12),
following the existing `joom-0.9` branch convention. It contains the migration fixes
and the standard Zeppelin UI. The experimental AI adapter remains in a separate draft PR.

This branch maintains the Joom behaviors documented in [PORTS.md](PORTS.md) on the
unchanged Apache 0.12.1 API shape. Each functional change has its original commit
references in the Git message and its own regression coverage.

## Build

Product images are built explicitly from this branch. As with `joom-0.9` and its
`dev/build_joom_release.sh`, compiling/publishing the product is a separate step
from deploying it. For 0.12, `joom/build.sh` prepares and tests the product artifacts;
the Docker commands below publish the server and interpreter images. Datasci CI
updates the analytics/Spark runtime and deploys the reviewed chart image pins.

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
artifact SHA256), then build and push the two amd64 targets. Local Docker is the
verified path for this pilot; the default Cloud Build compute account cannot read
uploaded sources, and no additional IAM grant was added.

```sh
revision=$(git rev-parse HEAD)
image=europe-west4-docker.pkg.dev/gcp-joomanalytics-platform/images/zeppelin:0.12.1-joom-1
docker buildx build --platform linux/amd64 --target server \
  --build-arg SOURCE_REVISION="$revision" --tag "$image" --push joom/build-context
docker buildx build --platform linux/amd64 --target interpreter \
  --build-arg SOURCE_REVISION="$revision" --tag "$image-interpreter" --push joom/build-context
```

A Cloud Build configuration is also generated for an environment with an already
configured build identity and source/log/registry access.

The explicit build context contains JAR/WAR outputs and metadata only. It excludes
notebooks, live configuration, cloud credentials, browser state and test traces.
Image bases are pinned by digest; Java17/Spark runtime initially matches GCP test
build 1.1.3518. Use the resulting image digest when deploying.

## Browser regressions

Against an isolated Shiro fixture, provide the private credentials-file path:

```sh
cd zeppelin-web-angular
ZEPPELIN_REGRESSION_URL=http://127.0.0.1:8080/ \
ZEPPELIN_REGRESSION_CREDENTIALS=/private/path/credentials.json \
  ./node/node ./node_modules/@playwright/test/cli.js test --config=playwright.migration.config.js
```

Run these controls against your own isolated instance built from this branch.
The temporary comparison instance and its localhost UI proxy have been retired;
they are not dependencies of the balloon deployment.

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
https://analytics.joom.ai/balloon-gcp/. Both versions use the shared S3 notebook
repository `s3://joom-analytics-notebooks/zeppelin-09/notebook/`. The 0.12 release
keeps its own search index and mutable ACL/interpreter state on PVCs; its earlier
local notebook volume is retained as a backup. Preserve the old release for an
explicit rollback. Notebook edits affect the shared repository; reverting the
server does not undo saved edits. Datasci deployment and CI instructions are in
[README-012.md](https://github.com/joomcode/datasci/blob/codex/zeppelin-012-balloon/analytics/joom/deploy/kubernetes-gcp/zeppelin/README-012.md).

Use existing per-team Workload Identity, not copied long-lived keys. Cloud test
operations are restricted to `joom-analytics.junk_aleksandrov` and its approved GCS
location under `gs://joom-analytics-users/aleksandrov/junk_aleksandrov`.
Deployment details, image digests and final regression results are recorded separately.

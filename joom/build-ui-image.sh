#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${IMAGE:?Set IMAGE to the target server image tag}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK installation}"

# Dependencies/SDK are the same as the regular joom/build.sh build. Keep the
# Docker context to one WAR; no notebook data or local configuration is sent.
(
  cd zeppelin-web-angular
  ./node/node ./node/node_modules/npm/bin/npm-cli.js run build
)
build_context="$(mktemp -d "${TMPDIR:-/tmp}/zeppelin-ui.XXXXXX")"
trap 'rm -rf "$build_context"' EXIT
"$JAVA_HOME/bin/jar" cf "$build_context/zeppelin-web-angular.war" -C zeppelin-web-angular/dist/zeppelin .
cp joom/Dockerfile.ui "$build_context/Dockerfile"
docker build --platform linux/amd64 --build-arg "SOURCE_REVISION=$(git rev-parse HEAD)" \
  --tag "$IMAGE" "$build_context"
docker push "$IMAGE"

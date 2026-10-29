#!/usr/bin/env bash
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#     http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail
cd "$(dirname "$0")/.."
: "${IMAGE:?Set IMAGE to the target server image tag}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK installation}"

# Dependencies/SDK are the same as the regular joom/build.sh build. Keep the
# Docker context to one WAR; no notebook data or local configuration is sent.
if [[ "${SKIP_UI_BUILD:-0}" != "1" ]]; then
  (
    cd zeppelin-web-angular
    ./node/node ./node/node_modules/npm/bin/npm-cli.js run build
  )
fi
[[ -f zeppelin-web-angular/dist/zeppelin/index.html ]]
[[ -f zeppelin-web-angular/dist/zeppelin/WEB-INF/web.xml ]]
build_context="$(mktemp -d "${TMPDIR:-/tmp}/zeppelin-ui.XXXXXX")"
trap 'rm -rf "$build_context"' EXIT
"$JAVA_HOME/bin/jar" cf "$build_context/zeppelin-web-angular.war" -C zeppelin-web-angular/dist/zeppelin .
cp joom/Dockerfile.ui "$build_context/Dockerfile"
docker build --platform linux/amd64 --build-arg "SOURCE_REVISION=$(git rev-parse HEAD)" \
  --tag "$IMAGE" "$build_context"
docker push "$IMAGE"

#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more contributor
# license agreements. See the NOTICE file distributed with this work for
# additional information regarding copyright ownership. The ASF licenses this
# file to you under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.

set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 11 installation}"
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx3g}"
mkdir -p joom/cache spark/interpreter/target
archive=joom/cache/spark-3.5.3.tgz
if [[ ! -f "$archive" ]]; then
  curl -fL --retry 3 https://archive.apache.org/dist/spark/spark-3.5.3/spark-3.5.3.tgz -o "$archive"
fi
python3 - "$archive" <<'CHECK'
import hashlib,sys
assert hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest() == '550b63c794afbb82d5d54c1dc4b9e17002dc2a7e272bcee06554674c0d5cf24a'
CHECK
tar -xzf "$archive" -C spark/interpreter/target spark-3.5.3/python
modules=zeppelin-server,markdown,shell,bigquery,spark/scala-2.12,zeppelin-plugins/launcher/k8s-standard,zeppelin-plugins/notebookrepo/gcs,zeppelin-plugins/notebookrepo/s3
common=(-B -ntp -pl "$modules" -am -Dmaven.gitcommitid.nativegit=true -Ddownload.plugin.skip=true -Dcheckstyle.skip=true -Pspark-3.5 -Pspark-scala-2.12)
./mvnw "${common[@]}" install -DskipTests
./mvnw "${common[@]}" test -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=AuthorizationPolicyTest,NoteAuthTest,NoteTest,LuceneSearchTest,NotebookServiceTest,NotebookRestApiTest#testRestParagraphEditsUpdateSearch,RemoteInterpreterCloseTest,ManagedInterpreterGroupTest,PooledRemoteFailureTest,BigQueryResultTest,DnsReadyTest,SparkUtilsTest,SparkInterpreterTest#testSparkInterpreter'
python3 joom/test_impersonation.py
python3 joom/check_changed_style.py
./mvnw -B -ntp org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file   -Dfile=joom/vendor/shiro-remote-user-0.0.1-SNAPSHOT.jar -DgroupId=com.joom.vendor   -DartifactId=shiro-remote-user -Dversion=0.0.1 -Dpackaging=jar -DgeneratePom=true
./mvnw -B -ntp -f joom/shiro-jakarta/pom.xml package
./mvnw -B -ntp -pl zeppelin-web-angular 'frontend:install-node-and-npm@install node'
(
  cd zeppelin-web-angular
  export PATH="$PWD/node:$PATH"
  HUSKY=0 ./node/node ./node/node_modules/npm/bin/npm-cli.js ci --ignore-scripts
  ./node/node ./node/node_modules/npm/bin/npm-cli.js run build:projects
  ./node/node ./node/node_modules/npm/bin/npm-cli.js run build
)
./mvnw -B -ntp -pl zeppelin-web-angular war:war
python3 joom/prepare_context.py
# Submit explicitly after reviewing joom/build-context/overlay/joom-build.json and Dockerfile:
# gcloud builds submit joom/build-context --project=gcp-joomanalytics-platform --config=joom/build-context/cloudbuild.json

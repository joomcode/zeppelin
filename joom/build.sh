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
mkdir -p joom/cache
modules=zeppelin-server,markdown,shell,bigquery,spark/scala-2.12,zeppelin-plugins/launcher/k8s-standard,zeppelin-plugins/notebookrepo/gcs,zeppelin-plugins/notebookrepo/s3
# Runtime Spark/PySpark comes from datasci's Spark image, not an Apache archive.
common=(-B -ntp -pl "$modules" -am -Dmaven.gitcommitid.nativegit=true -Ddownload.plugin.skip=true -DskipBundledPySpark=true -Dcheckstyle.skip=true -Pspark-3.5 -Pspark-scala-2.12)
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

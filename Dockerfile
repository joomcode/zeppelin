#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
FROM openjdk:8 as builder
ADD . /workspace/zeppelin
WORKDIR /workspace/zeppelin
ENV MAVEN_OPTS="-Xms1024M -Xmx2048M -XX:MaxMetaspaceSize=1024m -XX:-UseGCOverheadLimit -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
# Allow npm and bower to run with root privileges
RUN --mount=type=cache,target=/root/.m2 echo "unsafe-perm=true" > ~/.npmrc && \
    echo '{ "allow_root": true }' > ~/.bowerrc && \
    ./mvnw package -e -Pbuild-distr -DargLine="-Xmx8g" -DskipTests -Phadoop-2.6 -Pscala-2.11 -pl '!beam,!hbase,!pig,!file,!flink,!flink/flink1.10-shims,!flink/flink1.11-shims,!flink/flink1.12-shims,!flink/flink-shims,!flink/interpreter,!ignite,!kylin,!lens,!cassandra,!elasticsearch,!alluxio,!scio,!groovy,!sap,!java,!geode,!neo4j,!hazelcastjet,!submarine,!sparql,!mongodb,!ksql,!scalding,!flink-cmd,!influxdb' -am && \
    # Example with doesn't compile all interpreters
    # ./mvnw -B package -DskipTests -Pbuild-distr -Pspark-3.2 -Pinclude-hadoop -Phadoop3 -Pspark-scala-2.12 -Pweb-angular -Pweb-dist -pl '!groovy,!submarine,!livy,!hbase,!file,!flink' && \
    mkdir -p /opt/zeppelin && \
    mv /workspace/zeppelin/zeppelin-distribution/target/zeppelin-*/zeppelin-*/* /opt/zeppelin/

FROM ubuntu:20.04
COPY --from=builder /opt/zeppelin /opt/zeppelin

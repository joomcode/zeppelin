#!/bin/bash

set -eo pipefail

./dev/change_scala_version.sh 2.11
mvn clean package -Pbuild-distr -DargLine="-Xmx8g" -DskipTests -Phadoop-2.6 -Pscala-2.11 -Pweb-angular -pl '!beam,!hbase,!pig,!file,!flink,!flink/flink1.10-shims,!flink/flink1.11-shims,!flink/flink1.12-shims,!flink/flink-shims,!flink/interpreter,!ignite,!kylin,!lens,!cassandra,!elasticsearch,!alluxio,!scio,!groovy,!sap,!java,!geode,!neo4j,!hazelcastjet,!submarine,!sparql,!mongodb,!ksql,!scalding,!flink-cmd,!influxdb' -am

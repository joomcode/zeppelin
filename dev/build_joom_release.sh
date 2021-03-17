#!/bin/bash

set -eo pipefail

./dev/change_scala_version.sh 2.11
mvn package -Pbuild-distr -DskipTests -Phadoop-2.6 -Pscala-2.11 -pl '!beam,!hbase,!pig,!jdbc,!file,!flink,!ignite,!kylin,!lens,!cassandra,!elasticsearch,!bigquery,!alluxio,!scio,!livy,!groovy,!sap,!java,!geode,!neo4j,!hazelcastjet,!submarine,!sparql,!mongodb,!ksql,!scalding' -am

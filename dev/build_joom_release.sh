#!/bin/bash

set -eo pipefail

./dev/change_scala_version.sh 2.11
mvn package -Pbuild-distr -DskipTests -P-Phadoop-2.6 -pl '!beam,!hbase,!pig,!ignite,!kylin,!lens,!cassandra,!elasticsearch,!alluxio,!scio,!groovy,!sap,!java,!geode,!neo4j,!hazelcastjet,!submarine,!sparql,!mongodb,!ksql,!scalding,!flink' -am

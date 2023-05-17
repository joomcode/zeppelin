#!/bin/bash

set -eo pipefail

version="$1"
docker build -t jfrog.joom.it/datasci/analytics/zeppelin-base:$version .
docker push jfrog.joom.it/datasci/analytics/zeppelin-base:$version

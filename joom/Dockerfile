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

ARG SERVER_BASE=apache/zeppelin@sha256:ebb76b9b98f1b5457cd10c118fc6624269918a3602711a08d9f237673c0c0abb

FROM ${SERVER_BASE} AS server
USER 0
ARG SOURCE_REVISION
LABEL org.opencontainers.image.source="https://github.com/joomcode/zeppelin"       org.opencontainers.image.revision="${SOURCE_REVISION}"       org.opencontainers.image.version="0.12.1-joom-1"
# Replace complete affected artifact sets so an old JAR cannot win class loading.
RUN rm -f /opt/zeppelin/lib/zeppelin-*.jar     /opt/zeppelin/interpreter/zeppelin-interpreter-shaded-*.jar     /opt/zeppelin/zeppelin-web-angular-*.war &&     rm -rf /opt/zeppelin/interpreter/spark /opt/zeppelin/interpreter/bigquery     /opt/zeppelin/plugins/Launcher/K8sStandardInterpreterLauncher     /opt/zeppelin/plugins/NotebookRepo/GCSNotebookRepo     /opt/zeppelin/plugins/NotebookRepo/S3NotebookRepo
COPY overlay/ /opt/zeppelin/
# Spark and its Python bridge are supplied together by the datasci runtime image.
RUN rm -rf /opt/zeppelin/interpreter/spark/pyspark
RUN ln -s /opt/zeppelin /zeppelin && \
    mkdir -p /opt/zeppelin/logs /opt/zeppelin/run /opt/zeppelin/webapps && \
    chgrp 0 /opt/zeppelin /opt/zeppelin/logs /opt/zeppelin/run /opt/zeppelin/webapps && \
    chmod 775 /opt/zeppelin /opt/zeppelin/logs /opt/zeppelin/run /opt/zeppelin/webapps && \
    chmod 755 /opt/zeppelin/bin/interpreter.sh
ENV Z_HOME=/zeppelin ZEPPELIN_HOME=/zeppelin HOME=/zeppelin
USER 1000:0
WORKDIR /zeppelin

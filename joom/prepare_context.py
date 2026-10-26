#!/usr/bin/env python3
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

"""Package only explicit build outputs, never local configuration, notebooks or credentials."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'joom/build-context'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
VERSION = ET.parse(ROOT / 'pom.xml').findtext('m:version', namespaces=NS)
REVISION = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip()


def copy(source, target):
    if not source.is_file():
        raise FileNotFoundError(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def tree(source, target):
    for path in source.rglob('*'):
        if path.is_file():
            # A previous upstream build can remain in target/interpreter after versioning.
            if path.name.endswith('-0.12.1.jar'):
                continue
            copy(path, target / path.relative_to(source))


if DEST.exists():
    shutil.rmtree(DEST)
DEST.mkdir(parents=True)
overlay = DEST / 'overlay'
copy(ROOT / 'bin/interpreter.sh', overlay / 'bin/interpreter.sh')
for module in ['zeppelin-common', 'zeppelin-interpreter', 'zeppelin-zengine',
               'zeppelin-server', 'zeppelin-jupyter-interpreter']:
    artifact = ET.parse(ROOT / module / 'pom.xml').findtext('m:artifactId', namespaces=NS)
    jar = f'{artifact}-{VERSION}.jar'
    copy(ROOT / module / 'target' / jar, overlay / 'lib' / jar)

shaded = f'zeppelin-interpreter-shaded-{VERSION}.jar'
copy(ROOT / 'zeppelin-interpreter-shaded/target' / shaded, overlay / 'interpreter' / shaded)
for name in ['spark', 'bigquery']:
    tree(ROOT / 'interpreter' / name, overlay / 'interpreter' / name)
for kind, name in [('Launcher', 'K8sStandardInterpreterLauncher'),
                   ('NotebookRepo', 'GCSNotebookRepo'), ('NotebookRepo', 'S3NotebookRepo')]:
    tree(ROOT / 'plugins' / kind / name, overlay / 'plugins' / kind / name)
war = f'zeppelin-web-angular-{VERSION}.war'
copy(ROOT / 'zeppelin-web-angular/target' / war, overlay / war)
shiro = ROOT / 'joom/shiro-jakarta/target/shiro-remote-user-jakarta-0.12.1-joom-1.jar'
with zipfile.ZipFile(shiro) as archive:
    classes = [archive.read(name) for name in archive.namelist() if name.endswith('.class')]
    assert not any(b'javax/servlet' in data for data in classes)
    assert any(b'jakarta/servlet' in data for data in classes)
copy(shiro, overlay / 'lib' / shiro.name)
files = {str(p.relative_to(overlay)): hashlib.sha256(p.read_bytes()).hexdigest()
         for p in sorted(overlay.rglob('*')) if p.is_file()}
metadata = {'version': VERSION, 'source_revision': REVISION, 'upstream': 'ae9cb72ff',
            'spark_runtime_tag': '1.1.3518',
            'spark_runtime_digest': 'sha256:f6982e5c61f0c426630f8087c5ce8226bedb4d9bf020c4e4491abe3790a45020',
            'files_sha256': files}
(overlay / 'joom-build.json').write_text(json.dumps(metadata, indent=2) + '\n')
copy(ROOT / 'joom/Dockerfile', DEST / 'Dockerfile')
(DEST / '.gcloudignore').write_text('.gcloudignore\n')
image = f'europe-west4-docker.pkg.dev/gcp-joomanalytics-platform/images/zeppelin:{VERSION}'
steps = []
for target, tag in [('server', image), ('interpreter', image + '-interpreter')]:
    steps.append({'name': 'gcr.io/cloud-builders/docker', 'args': [
        'build', '--target', target, '--build-arg', 'SOURCE_REVISION=' + REVISION,
        '--tag', tag, '.']})
config = {'steps': steps, 'images': [image, image + '-interpreter'], 'timeout': '2400s',
          'options': {'machineType': 'E2_HIGHCPU_8'}}
(DEST / 'cloudbuild.json').write_text(json.dumps(config, indent=2) + '\n')
print(json.dumps({'context': str(DEST), 'version': VERSION, 'revision': REVISION,
                  'files': len(files), 'bytes': sum(p.stat().st_size for p in overlay.rglob('*') if p.is_file())}))

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

"""Run upstream Checkstyle and reject violations on added Java lines only.

The release baseline contains existing violations, including untouched generated
sources. Keep them visible without rewriting unrelated upstream code in a port.
"""
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = 'ae9cb72ff'
added = {}
name = None
patch = subprocess.check_output(['git', '-C', str(ROOT), 'diff', '--unified=0', BASE,
                                 '--', '*.java'], text=True)
for line in patch.splitlines():
    if line.startswith('+++ b/'):
        name = line[6:]
    elif line.startswith('@@'):
        match = re.search(r'\+(\d+)(?:,(\d+))?', line)
        start = int(match.group(1))
        count = int(match.group(2) or 1)
        added.setdefault(name, set()).update(range(start, start + count))
modules = set()
for name in added:
    directory = (ROOT / name).parent
    while directory != ROOT and not (directory / 'pom.xml').exists():
        directory = directory.parent
    modules.add(str(directory.relative_to(ROOT)))
logs = ROOT / 'joom/cache/checkstyle'
logs.mkdir(parents=True, exist_ok=True)
violations = []
legacy = 0
for module in sorted(modules):
    report = ROOT / module / 'target/checkstyle-result.xml'
    report.unlink(missing_ok=True)
    with (logs / (module.replace('/', '-') + '.log')).open('w') as output:
        subprocess.run([str(ROOT / 'mvnw'), '-B', '-ntp',
            'checkstyle:check@checkstyle-fail-build', '-pl', module], cwd=ROOT,
            stdout=output, stderr=subprocess.STDOUT)
    if not report.exists():
        raise RuntimeError('Checkstyle did not produce a report for ' + module)
    for file in ET.parse(report).getroot().findall('file'):
        relative = str(Path(file.attrib['name']).relative_to(ROOT))
        for error in file.findall('error'):
            if int(error.attrib.get('line', '0')) in added.get(relative, set()):
                violations.append({'file': relative, **error.attrib})
            else:
                legacy += 1
result = {'base': BASE, 'violations_on_added_lines': violations,
          'untouched_baseline_violations': legacy}
(logs / 'summary.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
raise SystemExit(any(item['severity'] == 'error' for item in violations))

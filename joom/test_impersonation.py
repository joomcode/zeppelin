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

"""Run the actual launcher with stub executables; never invokes sudo or SSH."""
from pathlib import Path
import json
import os
import shlex
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='zeppelin-argv-') as directory:
    root = Path(directory)
    home = root / 'zeppelin with spaces'
    for path in [home / 'bin', home / 'conf', home / 'logs', home / 'interpreter/test', root / 'commands', root / 'jdk/bin']:
        path.mkdir(parents=True)
    for name in ['common.sh', 'interpreter.sh']:
        shutil.copyfile(ROOT / 'bin' / name, home / 'bin' / name)
    (home / 'conf/zeppelin-env.sh').write_text('')
    (home / 'interpreter/test/test.jar').touch()
    (root / 'jdk/bin/java').write_text('#!/usr/bin/env bash\nif [[ "$1" == "-version" ]]; then\n  echo \'openjdk version "11.0.27"\' >&2\nelse\n  printf \'%s\\0\' "$@" > "$CAPTURE"\nfi\n')
    (root / 'jdk/bin/java').chmod(0o755)
    for name in ['ssh', 'sudo']:
        p = root / 'commands' / name
        p.write_text('#!/usr/bin/env bash\nprintf \'%s\\0\' "$@" > "$CAPTURE"\n')
        p.chmod(0o755)
    cases = ['', 'sudo -H -u ${ZEPPELIN_IMPERSONATE_USER} -E',
             'sudo -H -u ${ZEPPELIN_IMPERSONATE_USER} bash -c']
    for command in cases:
        capture = root / 'argv'
        env = dict(os.environ, ZEPPELIN_HOME=str(home), ZEPPELIN_CONF_DIR=str(home / 'conf'),
                   JAVA_HOME=str(root / 'jdk'), CAPTURE=str(capture),
                   PATH=str(root / 'commands') + ':' + os.environ['PATH'])
        env.pop('ZEPPELIN_IMPERSONATE_CMD', None)
        if command:
            env['ZEPPELIN_IMPERSONATE_CMD'] = command
        result = subprocess.run(['bash', str(home / 'bin/interpreter.sh'), '-d',
            str(home / 'interpreter/test'), '-c', 'localhost', '-p', '12320',
            '-r', '12321:12321', '-i', 'test-id', '-l', str(root / 'local-repo'),
            '-g', 'test', '-u', 'audit_user'], env=env, capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        argv = capture.read_bytes().decode().rstrip('\0').split('\0')
        if not command or command.endswith('-c'):
            payload = shlex.split(argv[-1])
        else:
            payload = argv
        assert 'zeppelin with spaces' in payload[payload.index('-cp') + 1], payload
        assert any('audit_user' in arg for arg in argv)
        print(json.dumps({'case': command or 'default SSH', 'passed': True}))

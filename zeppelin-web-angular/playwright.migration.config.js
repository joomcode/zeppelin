/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e/migration',
  workers: 1,
  retries: 0,
  timeout: 60000,
  expect: { timeout: 15000 },
  reporter: [['list'], ['html', { outputFolder: 'target/migration-report', open: 'never' }]],
  outputDir: 'target/migration-results',
  use: {
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    extraHTTPHeaders: process.env.ZEPPELIN_REGRESSION_REMOTE_USER
      ? { 'X-Remote-User': process.env.ZEPPELIN_REGRESSION_REMOTE_USER, 'X-Remote-Roles': 'domain-joom' }
      : undefined
  }
});

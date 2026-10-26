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

import * as fs from 'fs';
import { expect, test, Page, WebSocketRoute } from '@playwright/test';

const base = (process.env.ZEPPELIN_REGRESSION_URL || 'http://127.0.0.1:18081/').replace(/\/?$/, '/');
const api = (path: string) => new URL(`api/${path}`, base).toString();
const credentials = process.env.ZEPPELIN_REGRESSION_CREDENTIALS
  ? JSON.parse(fs.readFileSync(process.env.ZEPPELIN_REGRESSION_CREDENTIALS, 'utf8')).audit_owner
  : undefined;
let angularErrors: string[];
let noteId: string;
let paragraphId: string;
let blockCommits: boolean;
let holdNotes: boolean;
let held: Array<{ client: WebSocketRoute; message: string | Buffer }>;
let sockets: Array<{ client: WebSocketRoute; server: WebSocketRoute }>;

async function editorText(page: Page) {
  return page.locator('.view-lines').first().innerText();
}

async function edit(page: Page, text: string) {
  const editor = page.getByRole('textbox', { name: 'Editor content;Press Alt+F1 for Accessibility Options.' }).first();
  await editor.click();
  await editor.press('ControlOrMeta+A');
  await editor.press('Backspace');
  if (text) {
    await page.keyboard.insertText(text);
  }
  await expect.poll(() => editorText(page)).toBe(text);
}

async function reconnect() {
  const previous = sockets.length;
  const current = sockets[previous - 1];
  current.client.close({ code: 1011, reason: 'migration regression' });
  current.server.close({ code: 1011, reason: 'migration regression' });
  await expect.poll(() => sockets.length).toBeGreaterThan(previous);
}

test.beforeEach(async ({ page }) => {
  angularErrors = [];
  page.on('console', message => {
    if (message.type() === 'error' && message.text().startsWith('ERROR ')) {
      angularErrors.push(message.text());
    }
  });
  blockCommits = true;
  holdNotes = false;
  held = [];
  sockets = [];
  if (credentials) {
    const login = await page.request.post(api('login'), {
      form: { userName: 'audit_owner', password: credentials.password }
    });
    expect(login.ok()).toBeTruthy();
  }
  const created = await page.request.post(api('notebook'), {
    data: { notePath: '/migration-regression/Local-edits', defaultInterpreterGroup: 'md', addingEmptyParagraph: false }
  });
  expect(created.ok()).toBeTruthy();
  noteId = (await created.json()).body;
  const paragraph = await page.request.post(api(`notebook/${noteId}/paragraph`), {
    data: { title: 'Regression', text: '%md\nSERVER_BASELINE' }
  });
  paragraphId = (await paragraph.json()).body;
  await page.routeWebSocket('**/ws', client => {
    const server = client.connectToServer();
    sockets.push({ client, server });
    client.onMessage(message => {
      const data = JSON.parse(message.toString());
      if (blockCommits && data.op === 'COMMIT_PARAGRAPH') {
        return;
      }
      server.send(message);
    });
    server.onMessage(message => {
      const data = JSON.parse(message.toString());
      if (holdNotes && data.op === 'NOTE') {
        held.push({ client, message });
      } else {
        client.send(message);
      }
    });
  });
  await page.goto(new URL(`#/notebook/${noteId}`, base).toString());
  await page.getByRole('textbox', { name: 'Editor content;Press Alt+F1 for Accessibility Options.' }).first().waitFor();
});

test.afterEach(async ({ page }) => {
  await page.goto('about:blank');
  if (noteId) {
    await page.request.delete(api(`notebook/${noteId}`));
  }
  expect(angularErrors).toEqual([]);
});

for (const local of ['', '%md\nPENDING_LOCAL_EDIT']) {
  test(`delayed NOTE preserves ${local ? 'nonempty' : 'empty'} local edit`, async ({ page }) => {
    holdNotes = true;
    await reconnect();
    await expect.poll(() => held.length).toBeGreaterThan(0);
    await edit(page, local);
    holdNotes = false;
    const frames = held.splice(0);
    frames.forEach(frame => frame.client.send(frame.message));
    // Allow the released real NOTE to reach Angular/Monaco before checking stability.
    await page.waitForTimeout(500);
    expect(await editorText(page)).toBe(local);
  });
}

test('unchanged server broadcast preserves a local edit', async ({ page }) => {
  await edit(page, '%md\nBROADCAST_LOCAL_EDIT');
  const response = await page.request.put(api(`notebook/${noteId}/paragraph/${paragraphId}`), {
    data: { text: '%md\nSERVER_BASELINE' }
  });
  expect(response.ok()).toBeTruthy();
  await page.waitForTimeout(500);
  expect(await editorText(page)).toBe('%md\nBROADCAST_LOCAL_EDIT');
});

test('inline login preserves and subsequently saves a draft', async ({ page }) => {
  test.skip(!credentials, 'This control needs a local Shiro fixture; SSO is tested separately.');
  await edit(page, '%md\nSESSION_LOCAL_EDIT');
  let expire = true;
  await page.route('**/api/security/ticket', async route => {
    if (expire) {
      expire = false;
      await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
    } else {
      await route.continue();
    }
  });
  // The authentication failure is injected; the websocket break and subsequent login are real.
  const current = sockets[sockets.length - 1];
  current.client.close({ code: 1011 });
  current.server.close({ code: 1011 });
  await expect(page.getByText('Session expired', { exact: true })).toBeVisible();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Username').fill('audit_owner');
  await dialog.getByLabel('Password').fill(credentials.password);
  blockCommits = false;
  await dialog.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(dialog).toBeHidden();
  await expect
    .poll(async () => {
      const note = (await (await page.request.get(api(`notebook/${noteId}`))).json()).body;
      return note.paragraphs.find((p: { id: string }) => p.id === paragraphId).text;
    })
    .toBe('%md\nSESSION_LOCAL_EDIT');
  expect(await editorText(page)).toBe('%md\nSESSION_LOCAL_EDIT');
});

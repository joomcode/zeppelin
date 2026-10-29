// Exercise the real host adapter without a browser or Zeppelin server.
const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const crypto = require('node:crypto');
const ts = require('../zeppelin-web-angular/node_modules/typescript');
const source = fs.readFileSync(require('node:path').join(__dirname,
  '../zeppelin-web-angular/src/app/pages/workspace/notebook/ai-panel/ai-panel.component.ts'), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: {
  target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, experimentalDecorators: true
}}).outputText;
const CHANNEL = { channel: 'joom-sql-draft', version: 1 };
const binding = { name: 'prod', selected: true, interpreters: [
  { name: 'sql', class: 'org.apache.zeppelin.spark.SparkSqlInterpreter' }
] };

async function host(options = {}) {
  const writes = [], replies = [];
  const frame = { postMessage: (payload, origin) => replies.push({ payload, origin }) };
  const module = { exports: {} };
  const context = {
    exports: module.exports, module, crypto: crypto.webcrypto, URL, AbortController,
    window: { location: { origin: 'https://analytics.joom.ai' }, addEventListener() {}, removeEventListener() {},
      setTimeout, clearTimeout },
    fetch: async (url, request) => {
      if (!request?.method) return { ok: options.config !== false,
        json: async () => ({ enabled: true, panelUrl: 'https://iron-head-stage.joom.ai/embed/sql' }) };
      writes.push({ url, request });
      if (options.networkError) throw new Error('connection lost');
      return { ok: !options.denied, status: options.denied ? 403 : 200,
        json: async () => ({ status: 'OK', body: 'new-paragraph' }) };
    },
    require: name => {
      if (name === '@angular/core') return { Component: () => target => target,
        Input: () => () => {}, ViewChild: () => () => {} };
      throw new Error(`Unexpected runtime dependency: ${name}`);
    }
  };
  vm.runInNewContext(compiled, context);
  const panel = new module.exports.NotebookAiPanelComponent({
    getBase: () => 'https://analytics.joom.ai/balloon-gcp/',
    skipTrailingSlash: value => value.replace(/\/$/, ''),
    getRestApiBase: () => 'https://analytics.joom.ai/balloon-gcp/api'
  }, { bypassSecurityTrustResourceUrl: value => value });
  panel.noteId = 'note-a'; panel.connected = true; panel.bindings = [binding];
  panel.frame = { nativeElement: { contentWindow: frame } }; panel.open = true;
  await panel.ngOnInit();
  const insert = (extra = {}) => panel.insert({ requestId: 'request-a', noteId: panel.noteId,
    contextId: panel.contextId, dialect: 'spark', sql: 'SELECT 1', ...extra });
  return { panel, writes, replies, frame, insert };
}

test('missing optional configuration never interrupts a notebook', async () => {
  const { panel, writes } = await host({ config: false });
  assert.equal(panel.enabled, false); assert.equal(writes.length, 0);
});
test('unique bound Spark SQL magic is required', async () => {
  const { panel } = await host();
  assert.equal(panel.sqlMagic, '%prod.sql');
  panel.bindings = [binding, { ...binding, name: 'other' }];
  assert.equal(panel.canInsert, false);
});
test('untrusted origins and source windows cannot insert', async () => {
  const { panel, frame, writes } = await host();
  const data = { ...CHANNEL, type: 'insert', requestId: 'bad', noteId: panel.noteId,
    contextId: panel.contextId, dialect: 'spark', sql: 'SELECT 1' };
  panel.receive({ origin: 'https://evil.test', source: frame, data });
  panel.receive({ origin: 'https://iron-head-stage.joom.ai', source: {}, data });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(writes.length, 0);
});
test('valid insertion writes a new paragraph exactly once', async () => {
  const { insert, writes, replies } = await host();
  await Promise.all([insert(), insert()]);
  assert.equal(writes.length, 1);
  assert.match(writes[0].url, /notebook\/note-a\/paragraph$/);
  assert.deepEqual(JSON.parse(writes[0].request.body), { title: 'AI SQL draft', text: '%prod.sql\nSELECT 1' });
  assert.equal(replies.at(-1).payload.ok, true);
});
test('a late answer cannot enter another notebook', async () => {
  const { panel, insert, writes } = await host();
  const contextId = panel.contextId;
  panel.noteId = 'note-b'; panel.ngOnChanges({ noteId: {} });
  await insert({ noteId: 'note-a', contextId });
  assert.equal(writes.length, 0);
});
test('offline and read-only hosts reject writes', async () => {
  for (const field of ['connected', 'readOnly']) {
    const { panel, insert, writes } = await host();
    panel[field] = field === 'readOnly';
    await insert(); assert.equal(writes.length, 0);
  }
});
test('server ACL denial is explained without a retry', async () => {
  const { insert, writes, replies } = await host({ denied: true });
  await insert();
  assert.equal(writes.length, 1);
  assert.equal(replies.at(-1).payload.ok, false);
  assert.match(replies.at(-1).payload.error, /permission/);
});
test('unknown network outcome is never automatically replayed', async () => {
  const { insert, writes, replies } = await host({ networkError: true });
  await insert(); await insert();
  assert.equal(writes.length, 1);
  assert.match(replies.at(-1).payload.error, /Check the notebook/);
});

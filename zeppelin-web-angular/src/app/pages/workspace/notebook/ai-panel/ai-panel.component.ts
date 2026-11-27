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

import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  ViewChild
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { InterpreterBindingItem } from '@zeppelin/sdk';
import { BaseUrlService } from '@zeppelin/services/base-url.service';

const CHANNEL = 'joom-sql-draft';

/** The host adapter knows editor state only; AI and credentials stay in Iron Head. */
@Component({
  selector: 'zeppelin-notebook-ai-panel',
  templateUrl: './ai-panel.component.html',
  styleUrls: ['./ai-panel.component.less']
})
export class NotebookAiPanelComponent implements OnInit, OnChanges, OnDestroy {
  @Input() noteId = '';
  @Input() readOnly = false;
  @Input() connected = false;
  @Input() bindings: InterpreterBindingItem[] = [];
  @Input() defaultInterpreterGroup = '';
  @ViewChild('frame') frame?: ElementRef<HTMLIFrameElement>;
  enabled = false;
  open = false;
  frameReady = false;
  frameError = false;
  private readyTimer?: number;
  frameUrl?: SafeResourceUrl;
  panelPageUrl = '';
  private panelOrigin = '';
  private contextId = this.newContextId();
  private insertionIds = new Set<string>();
  private destroyed = false;
  private receive = (event: MessageEvent) => {
    if (!this.open || event.origin !== this.panelOrigin || event.source !== this.frame?.nativeElement.contentWindow) {
      return;
    }
    const data = event.data;
    if (!data || data.channel !== CHANNEL || data.version !== 1) {
      return;
    }
    if (data.type === 'ready') {
      this.frameReady = true;
      this.frameError = false;
      window.clearTimeout(this.readyTimer);
      this.cdr.markForCheck();
      this.sendContext();
    } else if (data.type === 'insert') {
      void this.insert(data);
    }
  };

  get sqlMagic(): string | null {
    const candidates: { magic: string; binding: InterpreterBindingItem }[] = [];
    for (const binding of this.bindings) {
      if (binding.selected) {
        for (const item of binding.interpreters) {
          if (item.class === 'org.apache.zeppelin.spark.SparkSqlInterpreter') {
            candidates.push({ magic: `%${binding.name}.${item.name}`, binding });
          }
        }
      }
    }
    // Zeppelin 0.12 reports all available settings as bound. An explicit
    // notebook default can disambiguate Spark vs. Comet without guessing from
    // list order or reading paragraph contents.
    const resolved =
      candidates.length > 1 && this.defaultInterpreterGroup
        ? candidates.filter(
            ({ binding }) =>
              binding.id === this.defaultInterpreterGroup || binding.name === this.defaultInterpreterGroup
          )
        : candidates;
    return resolved.length === 1 ? resolved[0].magic : null;
  }

  get canInsert(): boolean {
    return this.frameReady && !!this.noteId && this.connected && !this.readOnly && !!this.sqlMagic;
  }

  toggle() {
    this.open = !this.open;
    this.frameReady = false;
    this.frameError = false;
    window.clearTimeout(this.readyTimer);
    if (this.open) {
      this.readyTimer = window.setTimeout(() => {
        this.frameError = !this.frameReady;
        this.cdr.markForCheck();
      }, 15000);
    }
  }

  private newContextId(): string {
    return Array.from(crypto.getRandomValues(new Uint32Array(4))).join('-');
  }

  async ngOnInit() {
    window.addEventListener('message', this.receive);
    try {
      const base = this.baseUrl.skipTrailingSlash(this.baseUrl.getBase());
      const response = await fetch(`${base}/ai-config.json`, { credentials: 'same-origin', cache: 'no-store' });
      if (!response.ok) {
        return;
      }
      const config = await response.json();
      if (this.destroyed || config.enabled !== true || typeof config.panelUrl !== 'string') {
        return;
      }
      const url = new URL(config.panelUrl);
      if (url.protocol !== 'https:' && !(url.protocol === 'http:' && url.hostname === 'localhost')) {
        return;
      }
      url.searchParams.set('parentOrigin', window.location.origin);
      this.panelOrigin = url.origin;
      this.panelPageUrl = url.toString();
      this.frameUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url.toString());
      this.enabled = true;
      this.cdr.markForCheck();
    } catch {
      // Optional configuration/AI failures must never interrupt the notebook.
    }
  }

  ngOnChanges(changes: { [key: string]: unknown }) {
    if (changes.noteId) {
      this.contextId = this.newContextId();
      this.insertionIds.clear();
    }
    this.sendContext();
  }

  sendContext() {
    this.post({ type: 'host-context', noteId: this.noteId, contextId: this.contextId, canInsert: this.canInsert });
  }

  private post(payload: object) {
    if (!this.destroyed && this.open && this.panelOrigin) {
      this.frame?.nativeElement.contentWindow?.postMessage(
        { channel: CHANNEL, version: 1, ...payload },
        this.panelOrigin
      );
    }
  }

  // Messages carry IDs rather than editor text. Only an explicit Insert action writes a new paragraph.
  private async insert(data: {
    requestId?: string;
    noteId?: string;
    contextId?: string;
    sql?: string;
    dialect?: string;
  }) {
    const requestId = data.requestId;
    if (typeof requestId !== 'string' || requestId.length > 100 || this.insertionIds.has(requestId)) {
      return;
    }
    this.insertionIds.add(requestId);
    const contextId = this.contextId;
    const reply = (payload: object) => this.post({ type: 'insert-result', requestId, contextId, ...payload });
    if (
      !this.canInsert ||
      data.noteId !== this.noteId ||
      data.contextId !== contextId ||
      data.dialect !== 'spark' ||
      typeof data.sql !== 'string' ||
      !data.sql.trim() ||
      data.sql.length > 200000
    ) {
      reply({ ok: false, error: 'Notebook, connection or Spark SQL binding changed. Copy SQL instead.' });
      return;
    }
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(
        `${this.baseUrl.getRestApiBase()}/notebook/${encodeURIComponent(this.noteId)}/paragraph`,
        {
          method: 'POST',
          credentials: 'same-origin',
          signal: controller.signal,
          headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
          body: JSON.stringify({ title: 'AI SQL draft', text: `${this.sqlMagic}\n${data.sql}` })
        }
      );
      if (response.status === 401 || response.status === 403) {
        reply({ ok: false, error: 'You do not have permission to add a paragraph. Sign in or copy SQL instead.' });
        return;
      }
      if (!response.ok) {
        throw new Error('Insertion failed.');
      }
      const result = await response.json();
      if (result.status !== 'OK') {
        throw new Error('Insertion was not confirmed.');
      }
      reply({ ok: true, paragraphId: result.body });
    } catch {
      reply({
        ok: false,
        error: 'Insertion was not confirmed. Check the notebook before retrying; it may have been saved.'
      });
    } finally {
      window.clearTimeout(timeout);
    }
  }

  ngOnDestroy() {
    this.destroyed = true;
    window.clearTimeout(this.readyTimer);
    window.removeEventListener('message', this.receive);
  }

  constructor(
    private baseUrl: BaseUrlService,
    private sanitizer: DomSanitizer,
    private cdr: ChangeDetectorRef
  ) {}
}

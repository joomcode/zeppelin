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

import { Component, Input } from '@angular/core';
import { NzModalRef } from 'ng-zorro-antd/modal';
import { TicketService } from '../../services/ticket.service';

@Component({
  selector: 'zeppelin-session-login',
  template: `
    <p>Your edits are kept on this page. Sign in to reconnect and save them.</p>
    <div *ngIf="authUrl; else passwordForm">
      <p><a [href]="authUrl" target="_blank" rel="noopener">Sign in using SSO in another tab</a></p>
      <button nz-button nzType="primary" (click)="retry()" [disabled]="loading">Retry connection</button>
    </div>
    <ng-template #passwordForm>
      <form (ngSubmit)="login()">
        <p>
          <label>
            Username
            <input nz-input name="username" [(ngModel)]="username" autocomplete="username" />
          </label>
        </p>
        <p>
          <label>
            Password
            <input nz-input name="password" type="password" [(ngModel)]="password" autocomplete="current-password" />
          </label>
        </p>
        <button nz-button nzType="primary" type="submit" [disabled]="loading">Sign in</button>
      </form>
    </ng-template>
    <p *ngIf="error" role="alert">{{ error }}</p>
  `
})
export class SessionLoginComponent {
  @Input() authUrl = '';
  username = this.ticketService.ticket.principal;
  password = '';
  loading = false;
  error = '';

  constructor(
    private ticketService: TicketService,
    private modal: NzModalRef
  ) {}

  login(): void {
    this.loading = true;
    this.ticketService.login(this.username, this.password).subscribe({
      next: () => {
        this.password = '';
        this.modal.close(true);
      },
      error: () => {
        this.loading = false;
        this.error = 'Sign in failed. Your edits are still on this page.';
      }
    });
  }

  retry(): void {
    this.loading = true;
    this.ticketService.getTicket(true).subscribe({
      next: () => this.modal.close(true),
      error: () => {
        this.loading = false;
        this.error = 'The session is not available yet. Complete sign in, then retry.';
      }
    });
  }
}

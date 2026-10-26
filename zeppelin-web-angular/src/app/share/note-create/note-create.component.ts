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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { MessageService, NoteListService } from '@zeppelin/services';

import { NzModalRef } from 'ng-zorro-antd/modal';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { InterpreterItem, MessageReceiveDataTypeMap, Note, OP } from '@zeppelin/sdk';

@Component({
  selector: 'zeppelin-note-create',
  templateUrl: './note-create.component.html',
  styleUrls: ['./note-create.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NoteCreateComponent extends MessageListenersManager implements OnInit {
  @Input() path?: string;
  @Input() cloneNote?: Exclude<Note['note'], undefined>;
  noteName = '';
  defaultInterpreter?: string;
  listOfInterpreter: InterpreterItem[] = [];

  @MessageListener(OP.INTERPRETER_SETTINGS)
  getInterpreterSettings(data: MessageReceiveDataTypeMap[OP.INTERPRETER_SETTINGS]) {
    this.listOfInterpreter = data.interpreterSettings;
    this.defaultInterpreter = data.interpreterSettings[0].name;
    this.cdr.markForCheck();
  }

  @MessageListener(OP.NEW_NOTE)
  newNoteCreated(_: MessageReceiveDataTypeMap[OP.NEW_NOTE]) {
    this.nzModalRef.destroy();
  }

  newNoteName(path: string) {
    let newCount = 1;
    this.noteListService.notes.flatList.forEach(note => {
      const noteName = note.path;
      if (noteName.match(/^\/Untitled Note [0-9]*$/)) {
        const lastCount = +noteName.substr(15);
        if (newCount <= lastCount) {
          newCount = lastCount + 1;
        }
      }
    });
    return `${path ? `${path}/` : ''}Untitled Note ${newCount}`;
  }

  cloneNoteName(cloneNote: Exclude<Note['note'], undefined>) {
    const fullPath = cloneNote.path || cloneNote.name;
    const prefix = fullPath.replace(/ \d+$/, '');
    let copyCount = 1;
    this.noteListService.notes.flatList.forEach(note => {
      const suffix = note.path.startsWith(`${prefix} `) ? note.path.slice(prefix.length + 1) : '';
      if (/^\d+$/.test(suffix)) {
        copyCount = Math.max(copyCount, Number(suffix) + 1);
      }
    });
    return `${prefix} ${copyCount}`;
  }

  createNote() {
    if (this.cloneNote) {
      this.messageService.cloneNote(this.cloneNote.id, this.noteName);
    } else {
      this.messageService.newNote(this.noteName, this.defaultInterpreter);
    }
  }

  constructor(
    public messageService: MessageService,
    private cdr: ChangeDetectorRef,
    private noteListService: NoteListService,
    private nzModalRef: NzModalRef
  ) {
    super(messageService);
  }

  ngOnInit() {
    this.messageService.getInterpreterSettings();
    // Component should have either defined 'cloneNote' or defined 'path' property.
    this.noteName = this.cloneNote ? this.cloneNoteName(this.cloneNote) : this.newNoteName(this.path!);
  }
}

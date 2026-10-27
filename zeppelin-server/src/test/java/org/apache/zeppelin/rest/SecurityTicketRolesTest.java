/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.socket.ConnectionManager;
import org.apache.zeppelin.socket.NotebookSocket;
import org.apache.zeppelin.storage.ConfigStorage;

class SecurityTicketRolesTest {
  @Test
  void ticketKeepsBroadcastPermissionsInSyncWithoutPasswordLogin() throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.load();
    AuthorizationService authorization = new AuthorizationService(
        mock(NoteManager.class), conf, mock(ConfigStorage.class));
    authorization.setOwners("shared-note", Set.of("another-owner"));
    authorization.setReaders("shared-note", Set.of("domain-read"));
    authorization.setWriters("shared-note", Set.of("another-owner"));
    authorization.setRunners("shared-note", Set.of("another-owner"));
    AuthenticationService authentication = mock(AuthenticationService.class);
    when(authentication.getPrincipal()).thenReturn("remote-user");
    when(authentication.getAssociatedRoles()).thenReturn(Set.of("domain-read"));
    SecurityRestApi api = new SecurityRestApi(authentication, authorization);
    ConnectionManager connections = new ConnectionManager(authorization, conf);
    connections.addUserConnection("remote-user", mock(NotebookSocket.class));
    AtomicBoolean visible = new AtomicBoolean();

    // Remote-user authentication bypasses LoginRestApi: only ticket() is called.
    api.ticket();
    connections.forAllUsers((user, entities) ->
        visible.set(authorization.isReader("shared-note", entities)));
    assertTrue(visible.get(), "A broadcast must retain notebooks readable through the SSO role");

    // Refresh must also remove a revoked role, rather than retaining the old grant.
    when(authentication.getAssociatedRoles()).thenReturn(Collections.emptySet());
    api.ticket();
    connections.forAllUsers((user, entities) ->
        visible.set(authorization.isReader("shared-note", entities)));
    assertFalse(visible.get(), "A refreshed ticket must not retain a revoked SSO role");
  }
}

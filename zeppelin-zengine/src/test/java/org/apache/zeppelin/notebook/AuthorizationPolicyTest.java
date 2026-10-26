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

package org.apache.zeppelin.notebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Set;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;

class AuthorizationPolicyTest {
  private final ConfigStorage storage = mock(ConfigStorage.class);
  private final ZeppelinConfiguration conf = mock(ZeppelinConfiguration.class);

  private AuthorizationService service(String policies) throws Exception {
    when(storage.loadNotebookAuthorization())
        .thenReturn(NotebookAuthorizationInfoSaving.fromJson(policies));
    return new AuthorizationService(mock(NoteManager.class), conf, storage);
  }

  @Test
  void missingNoteUsesDefaultAndRemainsImplicitWhenSaving() throws Exception {
    AuthorizationService auth = service("{\"authInfo\":{\"default\":{"
        + "\"owners\":[\"team\"],\"readers\":[\"readers\"],"
        + "\"writers\":[\"writers\"],\"runners\":[\"runners\"]}}}");
    assertEquals(Set.of("team"), auth.getOwners("existing-note"));
    assertEquals(Set.of("readers"), auth.getReaders("existing-note"));
    assertFalse(auth.isReader("existing-note", Set.of("outsider")));
    auth.saveNoteAuth();
    ArgumentCaptor<NotebookAuthorizationInfoSaving> saved =
        ArgumentCaptor.forClass(NotebookAuthorizationInfoSaving.class);
    verify(storage).save(saved.capture());
    assertEquals(Set.of("default"), saved.getValue().getAuthInfo().keySet());
  }

  @Test
  void newPolicyIsIndependentForEveryNote() throws Exception {
    AuthorizationService auth = service("{\"authInfo\":{\"new\":{"
        + "\"owners\":[\"team\"],\"readers\":[\"readers\"],"
        + "\"writers\":[\"writers\"],\"runners\":[\"runners\"]}}}");
    auth.createNoteAuth("a", new AuthenticationInfo("creator"));
    auth.createNoteAuth("b", new AuthenticationInfo("creator"));
    auth.getOwners("a").add("another-owner");
    assertEquals(Set.of("team"), auth.getOwners("b"));
    assertEquals(Set.of("team"), auth.getOwners("new"));
    assertFalse(auth.isReader("a", Set.of("outsider")));
  }

  @Test
  void editingInheritedPolicyPreservesOtherPermissionsAndPersistsOverride() throws Exception {
    AuthorizationService auth = service("{\"authInfo\":{\"default\":{"
        + "\"owners\":[\"team\"],\"readers\":[\"readers\"],"
        + "\"writers\":[\"writers\"],\"runners\":[\"runners\"]}}}");
    auth.setWriters("a", Set.of("writer"));
    assertEquals(Set.of("writer"), auth.getWriters("a"));
    assertEquals(Set.of("readers"), auth.getReaders("a"));
    assertEquals(Set.of("writers"), auth.getWriters("b"));
    auth.saveNoteAuth();
    ArgumentCaptor<NotebookAuthorizationInfoSaving> saved =
        ArgumentCaptor.forClass(NotebookAuthorizationInfoSaving.class);
    verify(storage).save(saved.capture());
    assertEquals(Set.of("writer"), saved.getValue().getAuthInfo().get("a").get("writers"));
  }

  @Test
  void explicitAclOverridesGlobalPolicy() throws Exception {
    AuthorizationService auth = service("{\"authInfo\":{"
        + "\"default\":{\"readers\":[\"global\"],\"owners\":[\"global\"],"
        + "\"writers\":[\"global\"],\"runners\":[\"global\"]},"
        + "\"a\":{\"readers\":[\"private\"],\"owners\":[\"private\"],"
        + "\"writers\":[\"private\"],\"runners\":[\"private\"]}}}");
    assertEquals(Set.of("private"), auth.getReaders("a"));
    assertFalse(auth.isReader("a", Set.of("global")));
  }

  @Test
  void absentPolicyKeepsUpstreamNewNotebookPermissions() throws Exception {
    AuthorizationService auth = service("{\"authInfo\":{}}");
    auth.createNoteAuth("a", new AuthenticationInfo("creator"));
    assertTrue(auth.isOwner("a", Set.of("creator")));
    assertTrue(auth.isReader("a", Set.of("creator")));
  }
}

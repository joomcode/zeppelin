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

package org.apache.zeppelin.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.eclipse.aether.RepositoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;


class ManagedInterpreterGroupTest {

  private InterpreterSetting interpreterSetting;
  private ZeppelinConfiguration zConf;

  @BeforeEach
  public void setUp() throws IOException, RepositoryException {
    zConf = ZeppelinConfiguration.load();
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setConf(zConf)
        .create();
  }

  @Test
  void failedLaunchIsDiscardedBeforeRetry() throws Exception {
    InterpreterSetting setting = org.mockito.Mockito.mock(InterpreterSetting.class);
    RecoveryStorage recovery =
        org.mockito.Mockito.mock(RecoveryStorage.class);
    RemoteInterpreterProcess first =
        org.mockito.Mockito.mock(RemoteInterpreterProcess.class);
    RemoteInterpreterProcess second =
        org.mockito.Mockito.mock(RemoteInterpreterProcess.class);
    org.mockito.Mockito.when(setting.getRecoveryStorage()).thenReturn(recovery);
    org.mockito.Mockito.when(setting.createInterpreterProcess(
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any())).thenReturn(first, second);
    org.mockito.Mockito.doThrow(new IOException("launch timeout")).when(first).start("user");
    ManagedInterpreterGroup group = new ManagedInterpreterGroup("group", setting, zConf);
    org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
        () -> group.getOrCreateInterpreterProcess("user", new java.util.Properties()));
    org.mockito.Mockito.verify(first).stop();
    org.junit.jupiter.api.Assertions.assertSame(second,
        group.getOrCreateInterpreterProcess("user", new java.util.Properties()));
  }

  @Test
  void testInterpreterGroup() {
    ManagedInterpreterGroup interpreterGroup =
        new ManagedInterpreterGroup("group_1", interpreterSetting, zConf);
    assertEquals(0, interpreterGroup.getSessionNum());

    // create session_1
    List<Interpreter> interpreters = interpreterGroup.getOrCreateSession("user1", "session_1");
    assertEquals(3, interpreters.size());
    assertEquals(EchoInterpreter.class.getName(), interpreters.get(0).getClassName());
    assertEquals(DoubleEchoInterpreter.class.getName(), interpreters.get(1).getClassName());
    assertEquals(1, interpreterGroup.getSessionNum());

    // get the same interpreters when interpreterGroup.getOrCreateSession is invoked again
    assertEquals(interpreters, interpreterGroup.getOrCreateSession("user1", "session_1"));
    assertEquals(1, interpreterGroup.getSessionNum());

    // create session_2
    List<Interpreter> interpreters2 = interpreterGroup.getOrCreateSession("user1", "session_2");
    assertEquals(3, interpreters2.size());
    assertEquals(EchoInterpreter.class.getName(), interpreters2.get(0).getClassName());
    assertEquals(DoubleEchoInterpreter.class.getName(), interpreters2.get(1).getClassName());
    assertEquals(2, interpreterGroup.getSessionNum());

    // close session_1
    interpreterGroup.close("session_1");
    assertEquals(1, interpreterGroup.getSessionNum());

    // close InterpreterGroup
    interpreterGroup.close();
    assertEquals(0, interpreterGroup.getSessionNum());
  }
}

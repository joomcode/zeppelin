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


package org.apache.zeppelin.interpreter.remote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;

class RemoteInterpreterCloseTest {
  @Test
  void failedOpenCanCloseWithoutStartingProcessAndForgetItsCachedProcess() throws Exception {
    ManagedInterpreterGroup group = mock(ManagedInterpreterGroup.class);
    RemoteInterpreterProcess failed = mock(RemoteInterpreterProcess.class);
    RemoteInterpreterProcess replacement = mock(RemoteInterpreterProcess.class);
    when(group.getOrCreateInterpreterProcess(any(), any())).thenReturn(failed, replacement);
    RemoteInterpreter interpreter = new RemoteInterpreter(new Properties(), "session", "class",
        "user", mock(ZeppelinConfiguration.class));
    interpreter.setInterpreterGroup(group);
    assertSame(failed, interpreter.getOrCreateInterpreterProcess());
    interpreter.close();
    assertFalse(interpreter.isOpened());
    verify(group, times(1)).getOrCreateInterpreterProcess(any(), any());
    assertSame(replacement, interpreter.getOrCreateInterpreterProcess());
    verifyNoInteractions(failed);
  }

  @Test
  void deadOpenedProcessCanCloseWithoutRpc() throws Exception {
    ManagedInterpreterGroup group = mock(ManagedInterpreterGroup.class);
    RemoteInterpreterProcess failed = mock(RemoteInterpreterProcess.class);
    when(group.getOrCreateInterpreterProcess(any(), any())).thenReturn(failed);
    RemoteInterpreter interpreter = new RemoteInterpreter(new Properties(), "session", "class",
        "user", mock(ZeppelinConfiguration.class));
    interpreter.setInterpreterGroup(group);
    interpreter.getOrCreateInterpreterProcess();
    interpreter.setOpened(true);
    interpreter.close();
    assertFalse(interpreter.isOpened());
    verify(failed).isRunning();
    verifyNoMoreInteractions(failed);
  }
}

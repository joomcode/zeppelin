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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;

class PooledRemoteFailureTest {
  @Test
  void exhaustedConnectionRetriesPreserveOriginalCauseEvenWithoutMessage() {
    IOException failure = new IOException();
    AtomicInteger attempts = new AtomicInteger();
    try (PooledRemoteClient<RemoteInterpreterService.Client> pool =
        new PooledRemoteClient<RemoteInterpreterService.Client>(() -> null) {
          @Override
          public RemoteInterpreterService.Client getClient() throws Exception {
            attempts.incrementAndGet();
            throw failure;
          }
        }) {
      RuntimeException error = assertThrows(RuntimeException.class,
          () -> pool.callRemoteFunction(client -> null));
      assertSame(failure, error.getCause());
      assertEquals(3, attempts.get());
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class DnsReadyTest {
  @Test
  void waitsForRecordPublishedAfterPodRegistration() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    List<Long> waits = new ArrayList<>();
    DnsReady.await("interpreter", host -> {
      if (calls.incrementAndGet() < 3) {
        throw new UnknownHostException(host);
      }
      return InetAddress.getLoopbackAddress();
    }, waits::add);
    assertEquals(3, calls.get());
    assertEquals(List.of(1000L, 2000L), waits);
  }

  @Test
  void reportsExhaustionAndDoesNotSwallowInterruption() {
    UnknownHostException missing = new UnknownHostException("interpreter");
    List<Long> waits = new ArrayList<>();
    assertSame(missing, assertThrows(UnknownHostException.class,
        () -> DnsReady.await("interpreter", host -> {
          throw missing;
        }, waits::add)));
    assertEquals(List.of(1000L, 2000L, 4000L, 8000L), waits);
    assertThrows(InterruptedException.class,
        () -> DnsReady.await("interpreter", host -> {
          throw missing;
        },
            delay -> {
              throw new InterruptedException();
            }));
  }
}

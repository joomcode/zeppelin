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

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Pod registration can precede publication of its headless-service DNS record. */
final class DnsReady {
  interface Lookup {
    InetAddress resolve(String name) throws UnknownHostException;
  }

  interface Sleep {
    void pause(long milliseconds) throws InterruptedException;
  }

  static void await(String name, Lookup lookup, Sleep sleep)
      throws UnknownHostException, InterruptedException {
    UnknownHostException last = null;
    for (int attempt = 0; attempt < 5; attempt++) {
      if (attempt > 0) {
        sleep.pause(1000L << (attempt - 1));
      }
      try {
        lookup.resolve(name);
        return;
      } catch (UnknownHostException e) {
        last = e;
      }
    }
    throw last;
  }

  private DnsReady() { }
}

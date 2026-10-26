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

package org.apache.zeppelin.bigquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Properties;
import org.apache.zeppelin.interpreter.InterpreterResult;

class BigQueryResultTest {
  private TableResult result(FieldValue... values) {
    TableResult result = mock(TableResult.class);
    Field field = Field.of("value", LegacySQLTypeName.STRING);
    when(result.getSchema()).thenReturn(Schema.of(field));
    when(result.iterateAll()).thenReturn(values.length == 0 ? List.of()
        : List.of(FieldValueList.of(List.of(values), field)));
    return result;
  }

  @Test
  void nullValueAndEmptyResultsRemainSuccessfulTables() {
    InterpreterResult nil = BigQueryInterpreter.formatResult(
        result(FieldValue.of(FieldValue.Attribute.PRIMITIVE, null)), 10);
    assertEquals(InterpreterResult.Code.SUCCESS, nil.code());
    assertEquals("value\nnull\n", nil.message().get(0).getData());
    InterpreterResult empty = BigQueryInterpreter.formatResult(result(), 10);
    assertEquals(InterpreterResult.Code.SUCCESS, empty.code());
    assertEquals("value\n", empty.message().get(0).getData());
  }

  @Test
  void rowLimitCapsResultsAcrossAllPages() {
    TableResult table = result();
    FieldValueList row = FieldValueList.of(
        List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "x")),
        Field.of("value", LegacySQLTypeName.STRING));
    when(table.iterateAll()).thenReturn(List.of(row, row, row));
    assertEquals("value\nx\n", BigQueryInterpreter.formatResult(table, 1)
        .message().get(0).getData());
  }

  @Test
  void jobUsesConfiguredProjectAndRegionAndCancelsThisInterpreterOnly() throws Exception {
    BigQuery service = mock(BigQuery.class);
    Job job = mock(Job.class);
    when(service.create(any(JobInfo.class))).thenReturn(job);
    Properties properties = new Properties();
    properties.setProperty(BigQueryInterpreter.PROJECT_ID, "selected-project");
    properties.setProperty(BigQueryInterpreter.REGION, "europe-west3");
    BigQueryInterpreter interpreter = new BigQueryInterpreter(properties, service);
    when(job.getQueryResults()).thenAnswer(invocation -> {
      interpreter.cancel(null);
      return result();
    });
    assertEquals(InterpreterResult.Code.SUCCESS, interpreter.interpret("SELECT 1", null).code());
    verify(job).cancel();
    ArgumentCaptor<JobInfo> info = ArgumentCaptor.forClass(JobInfo.class);
    verify(service).create(info.capture());
    assertEquals("selected-project", info.getValue().getJobId().getProject());
    assertEquals("europe-west3", info.getValue().getJobId().getLocation());
    interpreter.cancel(null);
    verify(job, times(1)).cancel();
  }

  @Test
  void queryErrorPreservesItsMessage() {
    BigQuery service = mock(BigQuery.class);
    when(service.create(any(JobInfo.class))).thenThrow(new BigQueryException(400, "bad query"));
    BigQueryInterpreter interpreter = new BigQueryInterpreter(new Properties(), service);
    InterpreterResult result = interpreter.interpret("invalid", null);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertEquals("bad query", result.message().get(0).getData());
  }
}

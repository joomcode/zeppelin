/*
* Copyright 2016 Google Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0

* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.zeppelin.bigquery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

/** BigQuery jobs using application default credentials and the configured billing project. */
public class BigQueryInterpreter extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(BigQueryInterpreter.class);
  static final String PROJECT_ID = "zeppelin.bigquery.project_id";
  static final String WAIT_TIME = "zeppelin.bigquery.wait_time";
  static final String MAX_ROWS = "zeppelin.bigquery.max_no_of_rows";
  static final String SQL_DIALECT = "zeppelin.bigquery.sql_dialect";
  static final String REGION = "zeppelin.bigquery.region";

  // A service and running job belong to this interpreter, not another user or project.
  private BigQuery service;
  private volatile Job currentJob;

  public BigQueryInterpreter(Properties properties) {
    super(properties);
  }

  BigQueryInterpreter(Properties properties, BigQuery service) {
    this(properties);
    this.service = service;
  }

  @Override
  public void open() {
    if (service == null) {
      service = BigQueryOptions.newBuilder()
          .setProjectId(getProperty(PROJECT_ID)).build().getService();
    }
  }

  @Override
  public InterpreterResult interpret(String sql, InterpreterContext context) {
    Job job = null;
    try {
      open();
      String dialect = getProperty(SQL_DIALECT, "").toLowerCase(java.util.Locale.ROOT);
      Boolean legacy = null;
      if ("legacysql".equals(dialect)) {
        legacy = Boolean.TRUE;
      } else if ("standardsql".equals(dialect)) {
        legacy = Boolean.FALSE;
      }
      QueryJobConfiguration configuration = QueryJobConfiguration.newBuilder(sql)
          .setUseLegacySql(legacy)
          .setJobTimeoutMs(Long.parseLong(getProperty(WAIT_TIME, "60000")))
          .build();
      JobId.Builder id = JobId.newBuilder().setProject(getProperty(PROJECT_ID)).setRandomJob();
      String region = getProperty(REGION, "");
      if (!region.isEmpty()) {
        id.setLocation(region);
      }
      job = service.create(JobInfo.newBuilder(configuration).setJobId(id.build()).build());
      currentJob = job;
      TableResult result = job.getQueryResults();
      return formatResult(result, Long.parseLong(getProperty(MAX_ROWS, "1000")));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new InterpreterResult(InterpreterResult.Code.ERROR, "BigQuery query interrupted");
    } catch (Exception e) {
      LOGGER.error("BigQuery query failed", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          e.getMessage() == null ? e.toString() : e.getMessage());
    } finally {
      if (currentJob == job) {
        currentJob = null;
      }
    }
  }

  static InterpreterResult formatResult(TableResult result, long maxRows) {
    if (result.getSchema() == null) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "Query completed.");
    }
    StringBuilder output = new StringBuilder("%table ");
    output.append(result.getSchema().getFields().stream()
        .map(field -> field.getName()).collect(Collectors.joining("\t"))).append('\n');
    long rows = 0;
    for (FieldValueList row : result.iterateAll()) {
      if (rows++ >= maxRows) {
        break;
      }
      for (int i = 0; i < row.size(); i++) {
        if (i > 0) {
          output.append('\t');
        }
        FieldValue value = row.get(i);
        output.append(value.isNull() ? "null" : value.getValue().toString());
      }
      output.append('\n');
    }
    return new InterpreterResult(InterpreterResult.Code.SUCCESS, output.toString());
  }

  @Override
  public void cancel(InterpreterContext context) {
    Job job = currentJob;
    if (job != null) {
      job.cancel();
    }
  }

  @Override
  public void close() {
    cancel(null);
    currentJob = null;
    service = null;
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        BigQueryInterpreter.class.getName() + hashCode());
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext context) {
    return Collections.emptyList();
  }
}

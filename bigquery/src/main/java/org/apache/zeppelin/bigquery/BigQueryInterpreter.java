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

import com.google.api.client.util.Joiner;
import com.google.cloud.bigquery.*;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * BigQuery interpreter for Zeppelin.
 *
 * <ul>
 * <li>{@code zeppelin.bigquery.project_id} - Project ID in GCP</li>
 * <li>{@code zeppelin.bigquery.wait_time} - Query Timeout in ms</li>
 * <li>{@code zeppelin.bigquery.max_no_of_rows} - Max Result size</li>
 * </ul>
 *
 * <p>
 * How to use: <br/>
 * {@code %bigquery.sql<br/>
 * {@code
 *  SELECT departure_airport,count(case when departure_delay>0 then 1 else 0 end) as no_of_delays
 *  FROM [bigquery-samples:airline_ontime_data.flights]
 *  group by departure_airport
 *  order by 2 desc
 *  limit 10
 * }
 * </p>
 */
public class BigQueryInterpreter extends Interpreter {
    private static Logger logger = LoggerFactory.getLogger(BigQueryInterpreter.class);
    private static final char NEWLINE = '\n';
    private static final char TAB = '\t';
    private static BigQuery service = null;
    //Mutex created to create the singleton in thread-safe fashion.
    private static Object serviceLock = new Object();

    static final String PROJECT_ID = "zeppelin.bigquery.project_id";
    static final String WAIT_TIME = "zeppelin.bigquery.wait_time";
    static final String MAX_ROWS = "zeppelin.bigquery.max_no_of_rows";
    static final String SQL_DIALECT = "zeppelin.bigquery.sql_dialect";

    private static Job job = null;

    private static final List NO_COMPLETION = new ArrayList<>();
    private Exception exceptionOnConnect;

    public BigQueryInterpreter(Properties property) {
        super(property);
    }

    @Override
    public void open() {
        if (service == null) {
            synchronized (serviceLock) {
                if (service == null) {
                    try {
                        service = BigQueryOptions.getDefaultInstance().getService();
                        exceptionOnConnect = null;
                        logger.info("Opened BigQuery SQL Connection");
                    } catch (BigQueryException e) {
                        logger.error("Cannot open connection", e);
                        exceptionOnConnect = e;
                        close();
                    }
                }
            }
        }
    }

    //Function to call bigQuery to run SQL and return results to the Interpreter for output
    private InterpreterResult executeSql(String sql) throws InterruptedException {
        final StringBuilder finalmessage = new StringBuilder("%table ");
        String projId = getProperty(PROJECT_ID);
        long wTime = Long.parseLong(getProperty(WAIT_TIME));
        long maxRows = Long.parseLong(getProperty(MAX_ROWS));
        String sqlDialect = getProperty(SQL_DIALECT, "").toLowerCase();
        Boolean useLegacySql;
        switch (sqlDialect) {
            case "standardsql":
                useLegacySql = false;
                break;
            case "legacysql":
                useLegacySql = true;
                break;
            default:
                // Enable query prefix like '#standardSQL' if specified
                useLegacySql = null;
        }
        logger.info("Use legacy sql: {}", useLegacySql);

        JobConfiguration jobConfiguration = QueryJobConfiguration.newBuilder(sql)
                .setUseLegacySql(useLegacySql)
                .setJobTimeoutMs(wTime)
                .setMaxResults(maxRows)
                .build();
        JobInfo jobInfo = JobInfo.of(jobConfiguration);
        job = service.create(jobInfo);
        TableResult result = job.getQueryResults();

        finalmessage.append(Joiner.on(TAB).join(
                result.getSchema().getFields().stream().map(
                        x -> x.getName()).collect(Collectors.toList())));
        finalmessage.append(NEWLINE);
        result.iterateAll().forEach(row -> {
            finalmessage.append(Joiner.on(TAB).join(
                    row.stream().map(x -> x.getValue().toString()).collect(Collectors.toList())
            ));
            finalmessage.append(NEWLINE);
        });

        return new InterpreterResult(Code.SUCCESS, finalmessage.toString());
    }

    @Override
    public void close() {
        logger.info("Close bqsql connection!");
        service = null;
    }

    @Override
    public InterpreterResult interpret(String sql, InterpreterContext contextInterpreter) {
        logger.info("Run SQL command '{}'", sql);
        try {
            return executeSql(sql);
        } catch (Exception e) {
            logger.error("Query failed", e);
            return new InterpreterResult(Code.ERROR, e.getMessage());
        }
    }

    @Override
    public Scheduler getScheduler() {
        return SchedulerFactory.singleton().createOrGetFIFOScheduler(
                BigQueryInterpreter.class.getName() + this.hashCode());
    }

    @Override
    public FormType getFormType() {
        return FormType.SIMPLE;
    }

    @Override
    public int getProgress(InterpreterContext context) {
        return 0;
    }

    @Override
    public void cancel(InterpreterContext context) {
        logger.info("Trying to Cancel current query statement.");

        if (service != null && job != null) {
            job.cancel();
            job = null;
            logger.info("Query Execution cancelled");
        } else {
            logger.info("Query Execution was already cancelled");
        }
    }

    @Override
    public List<InterpreterCompletion> completion(String buf, int cursor,
                                                  InterpreterContext interpreterContext) {
        return NO_COMPLETION;
    }
}

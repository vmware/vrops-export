package com.vmware.vropsexport.opqsl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.opsql.OpsqlException;
import com.vmware.vropsexport.opsql.Query;
import com.vmware.vropsexport.opsql.QueryCompiler;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class QueryCompilerTest {
  private static final ObjectMapper om =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private void runQuery(final String query, final String resultFileName) throws IOException {
    final QueryCompiler qc = new QueryCompiler();
    final Query q = qc.compile(query);
    final Query expected =
        om.readValue(new File("src/test/resources/opsql/" + resultFileName + ".json"), Query.class);
    Assert.assertNotNull(q);
    Assert.assertEquals(om.writeValueAsString(expected), om.writeValueAsString(q));
  }

  @Test
  public void testSimpleQuery() throws IOException {
    runQuery("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz)", "SimpleQueryResult");
  }

  @Test
  public void testRelativeTimerangeQuery() throws Exception {
    final QueryCompiler qc = new QueryCompiler();
    Query q = qc.compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1s)");
    Assert.assertEquals(System.currentTimeMillis() - 1000, q.getFromTime().getTime(), 1000);
    q = qc.compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1m)");
    Assert.assertEquals(System.currentTimeMillis() - 60000, q.getFromTime().getTime(), 1000);
    q = qc.compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1h)");
    Assert.assertEquals(System.currentTimeMillis() - 60000 * 60, q.getFromTime().getTime(), 1000);
    q = qc.compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1d)");
    Assert.assertEquals(
        System.currentTimeMillis() - 60000 * 60 * 24, q.getFromTime().getTime(), 1000);
    q = qc.compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1w)");
    Assert.assertEquals(
        System.currentTimeMillis() - 60000 * 60 * 24 * 7, q.getFromTime().getTime(), 1000);
  }

  @Test
  public void testAbsoluteTimeRange() {
    final QueryCompiler qc = new QueryCompiler();
    final long fromLocal =
        LocalDateTime.parse("2023-07-04T12:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    final long toLocal =
        LocalDateTime.parse("2023-07-05T12:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    final long fromUTC = Instant.parse("2023-07-04T12:00:00Z").toEpochMilli();
    final long toUTC = Instant.parse("2023-07-05T12:00:00Z").toEpochMilli();
    Query q =
        qc.compile(
            "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).timerange(2023-07-04 12:00)");
    Assert.assertEquals(fromLocal, q.getFromTime().getTime());
    q =
        qc.compile(
            "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).timerange(2023-07-04 12:00, 2023-07-05 12:00)");
    Assert.assertEquals(fromLocal, q.getFromTime().getTime());
    Assert.assertEquals(toLocal, q.getToTime().getTime());
    q =
        qc.compile(
            "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).timerange(2023-07-04 12:00 UTC, 2023-07-05 12:00 UTC)");
    Assert.assertEquals(fromUTC, q.getFromTime().getTime());
    Assert.assertEquals(toUTC, q.getToTime().getTime());
  }

  @Test
  public void testSimpleAliasQuery() throws IOException {
    runQuery(
        "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz cpuDemand)",
        "SimpleAliasedQueryResult");
    runQuery(
        "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz as cpuDemand)",
        "SimpleAliasedQueryResult");
  }

  @Test
  public void testMultiFieldQuery() throws IOException {
    runQuery(
        "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz, cpu|demandpct, @summary|guestFullName)",
        "MultiFieldQueryResult");
  }

  @Test
  public void testSimplFilterQuery() throws IOException {
    runQuery(
        "resource(VMWARE:VirtualMachine).name(\"hello\").fields(cpu|demandmhz)",
        "SimpleFilterQueryResult");
  }

  @Test
  public void testStackedFilterQuery() throws IOException {
    runQuery(
        "resource(VMWARE:VirtualMachine).name(\"hello\", \"goodbye\").regex(\".*foo.*\").whereHealth(\"RED\", \"YELLOW\").whereStatus(\"OK\").whereState(\"RUNNING\").fields(cpu|demandmhz)",
        "StackedFilterQueryResult");
  }

  @Test
  public void testMetricWhereAndQuery() throws IOException {
    runQuery(
        "resource(VMWARE:VirtualMachine).whereMetrics(cpu|demandmhz > 20 and cpu|demandpct > 30).fields(cpu|demandmhz)",
        "WhereMetricAndQueryResult");
  }

  @Test
  public void testPropertyWhereOrQuery() throws IOException {
    runQuery(
        "resource(VMWARE:VirtualMachine).whereProperties(summary|guestFamily = \"linux\" or summary|guestFamily != \"windows\").fields(cpu|demandmhz)",
        "WherePropertyOrQueryResult");
  }

  @Test
  public void testPropertyWhereAndQuery() throws IOException {
    runQuery(
        "resource(VMWARE:VirtualMachine).whereProperties(summary|guestFamily = \"linux\" and @summary|guestFamily != \"windows\").fields(cpu|demandmhz)",
        "WherePropertyAndQueryResult");
  }

  @Test
  public void testMetricWhereOrQuery() throws IOException {
    runQuery(
        "resource(VMWARE:VirtualMachine).whereMetrics(cpu|demandmhz > 20 or cpu|demandpct > 30).fields(cpu|demandmhz)",
        "WhereMetricOrQueryResult");
  }

  @Test
  public void testSyntaxError() throws IOException {
    final QueryCompiler qc = new QueryCompiler();
    boolean exceptionHappened = false;
    try {
      qc.compile("resource(VMWARE:VirtualMachine).name(notALiteral).fields(cpu|demandmhz)");
    } catch (final OpsqlException e) {
      exceptionHappened = true;
    }
    Assert.assertTrue("Should have thrown an exception", exceptionHappened);
  }

  @Test
  public void testMixedFieldQuery() throws IOException {
    runQuery(
        "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz, @summary|guestFullName)",
        "MixedFieldQueryResult");
  }

  @Test
  public void testChildQuery() throws IOException {
    runQuery(
        "resource(VMWARE:HostSystem).children(VMWARE:VirtualMachine h).fields(cpu|demandmhz, stddev(h->cpu|demandmhz))",
        "ChildQueryResult");
  }

  @Test
  public void testDottedName() throws Exception {
    runQuery(
        "resource(VMWARE:HostSystem).children(VMWARE:VirtualMachine h).fields(cpu|demandmhz.dot.dot, stddev(h->cpu|demandmhz.dot.dot))",
        "DottedChildQueryResult");
  }
}

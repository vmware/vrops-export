package com.vmware.vropsexport.opqsl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.opsql.*;
import com.vmware.vropsexport.opsql.Compiler;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;
import org.junit.Assert;
import org.junit.Test;

public class QueryCompilerTest {
  private static final ObjectMapper om =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private void runQuery(final String query, final String resultFileName) throws IOException {
    final Query q = compile(query);
    final Query expected =
        om.readValue(new File("src/test/resources/opsql/" + resultFileName + ".json"), Query.class);
    Assert.assertNotNull(q);
    Assert.assertEquals(om.writeValueAsString(expected), om.writeValueAsString(q));
  }

  private Query compile(final String query) {
    return (Query)
        new Compiler()
            .compile(query, new SessionContext()).stream()
                .filter((s) -> s instanceof Query)
                .findFirst()
                .orElseThrow(RuntimeException::new);
  }

  @Test
  public void testSimpleQuery() throws IOException {
    runQuery("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz)", "SimpleQueryResult");
  }

  @Test
  public void testRelativeTimeRangeQuery() throws Exception {
    Query q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1s)");
    Assert.assertEquals(System.currentTimeMillis() - 1000, q.getFromTime().getTime(), 1000);
    q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1m)");
    Assert.assertEquals(System.currentTimeMillis() - 60000, q.getFromTime().getTime(), 1000);
    q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1h)");
    Assert.assertEquals(System.currentTimeMillis() - 60000 * 60, q.getFromTime().getTime(), 1000);
    q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1d)");
    Assert.assertEquals(
        System.currentTimeMillis() - 60000 * 60 * 24, q.getFromTime().getTime(), 1000);
    q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1w)");
    Assert.assertEquals(
        System.currentTimeMillis() - 60000 * 60 * 24 * 7, q.getFromTime().getTime(), 1000);
  }

  @Test
  public void testAbsoluteTimeRange() {
    final TimeZone tz = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try {
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
      Query q =
          compile(
              "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).timerange(2023-07-04 12:00)");
      Assert.assertEquals(fromLocal, q.getFromTime().getTime());
      q =
          compile(
              "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).timerange(2023-07-04 12:00, 2023-07-05 12:00)");
      Assert.assertEquals(fromLocal, q.getFromTime().getTime());
      Assert.assertEquals(toLocal, q.getToTime().getTime());
    } finally {
      TimeZone.setDefault(tz);
    }
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
  public void testSimpleFilterQuery() throws IOException {
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
    boolean exceptionHappened = false;
    try {
      compile("resource(VMWARE:VirtualMachine).name(notALiteral).fields(cpu|demandmhz)");
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

  private void checkTimezone(final String tz) throws ExporterException {
    final Compiler c = new Compiler();
    final SessionContext ctx = new SessionContext();
    final List<RunnableStatement> stmts = c.compile("timezone(\"" + tz + "\")", ctx);
    for (final RunnableStatement rs : stmts) {
      rs.run(ctx);
    }
    Assert.assertEquals(tz, ctx.getTimezone().getId());
  }

  @Test
  public void testSetTimezone() throws ExporterException {
    checkTimezone("UTC");
    checkTimezone("America/New_York");
    checkTimezone("Europe/Stockholm");
    try {
      checkTimezone("Africa/Wakanda");
      // We shouldn't make it here
      Assert.fail("Should have cause exception");
    } catch (final OpsqlException e) {
      // This is OK
    }
  }
}

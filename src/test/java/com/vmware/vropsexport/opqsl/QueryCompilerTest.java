package com.vmware.vropsexport.opqsl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.opsql.*;
import com.vmware.vropsexport.opsql.Compiler;
import com.vmware.vropsexport.utils.ParseUtils;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
        Compiler.compile(query).stream()
            .filter((s) -> s instanceof Query)
            .findFirst()
            .orElseThrow(RuntimeException::new);
  }

  @Test
  public void testSimpleQuery() throws IOException {
    runQuery("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz)", "SimpleQueryResult");
  }

  @Test
  public void testRelativeTimeRangeQuery() throws ExporterException {
    final SessionContext ctx = new SessionContext();
    Query q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1s)");
    q.resolveDates(ctx);
    Assert.assertEquals(System.currentTimeMillis() - 1000, q.getFromDate().getTime(), 1000);
    q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1m)");
    q.resolveDates(ctx);
    Assert.assertEquals(System.currentTimeMillis() - 60000, q.getFromDate().getTime(), 1000);
    q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1h)");
    q.resolveDates(ctx);
    Assert.assertEquals(System.currentTimeMillis() - 60000 * 60, q.getFromDate().getTime(), 1000);
    q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1d)");
    q.resolveDates(ctx);
    Assert.assertEquals(
        System.currentTimeMillis() - 60000 * 60 * 24, q.getFromDate().getTime(), 1000);
    q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).latest(1w)");
    q.resolveDates(ctx);
    Assert.assertEquals(
        System.currentTimeMillis() - 60000 * 60 * 24 * 7, q.getFromDate().getTime(), 1000);
  }

  @Test
  public void testAbsoluteTimeRange() throws ExporterException {
    final SessionContext ctx = new SessionContext();
    ctx.getConfig().setTimezone("UTC");
    final ZoneId tz = ZoneId.of("UTC");
    final long fromLocal =
        LocalDateTime.parse("2023-07-04T12:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(tz)
            .toInstant()
            .toEpochMilli();
    final long toLocal =
        LocalDateTime.parse("2023-07-05T12:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(tz)
            .toInstant()
            .toEpochMilli();
    Query q =
        compile(
            "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).timerange(2023-07-04 12:00)");
    q.resolveDates(ctx);
    Assert.assertEquals(fromLocal, q.getFromDate().getTime());
    q =
        compile(
            "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).timerange(2023-07-04 12:00, 2023-07-05 12:00)");
    q.resolveDates(ctx);
    Assert.assertEquals(fromLocal, q.getFromDate().getTime());
    Assert.assertEquals(toLocal, q.getToDate().getTime());
  }

  @Test
  public void testShortFormAbsoluteTimeRange() throws ExporterException {
    final SessionContext ctx = new SessionContext();
    ctx.getConfig().setTimezone("UTC");
    final ZoneId tz = ZoneId.of("UTC");
    final long fromLocal = ParseUtils.parseTime("00:00:00", ctx.getConfig().getZoneId()).getTime();
    final long toLocal = ParseUtils.parseTime("13:00:00", ctx.getConfig().getZoneId()).getTime();
    Query q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).timerange(00:00)");
    q.resolveDates(ctx);
    Assert.assertEquals(fromLocal, q.getFromDate().getTime());
    q = compile("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz).timerange(00:00, 13:00)");
    q.resolveDates(ctx);
    Assert.assertEquals(fromLocal, q.getFromDate().getTime());
    Assert.assertEquals(toLocal, q.getToDate().getTime());
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
  public void testSyntaxError() {
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
    final SessionContext ctx = new SessionContext();
    final List<RunnableStatement> stmts = Compiler.compile("set(timezone, \"" + tz + "\")");
    for (final RunnableStatement rs : stmts) {
      rs.run(ctx);
    }
    Assert.assertEquals(tz, ctx.getConfig().getTimezone());
  }

  @Test
  public void testSetTimezone() throws ExporterException {
    checkTimezone("UTC");
    checkTimezone("America/New_York");
    checkTimezone("Europe/Stockholm");
  }

  @Test
  public void testInvalidTimzeone() throws ExporterException {
    try {
      checkTimezone("Africa/Wakanda");
      // We shouldn't make it here
      Assert.fail("Should have caused exception");
    } catch (final OpsqlException e) {
      // This is OK
    }
  }

  private void runStatement(final SessionContext ctx, final String statement)
      throws ExporterException {
    final List<RunnableStatement> stmts = Compiler.compile(statement);
    for (final RunnableStatement rs : stmts) {
      rs.run(ctx);
    }
  }

  @Test
  public void testSimpleSet() throws ExporterException {
    final SessionContext ctx = new SessionContext();
    runStatement(ctx, "set(outputFormat, \"json\")");
    Assert.assertEquals("json", ctx.getConfig().getOutputFormat());
    runStatement(ctx, "set(rollupMinutes, 42)");
    Assert.assertEquals(42, ctx.getConfig().getRollupMinutes());
    runStatement(ctx, "set(allMetrics, true)");
    Assert.assertEquals(true, ctx.getConfig().isAllMetrics());
    runStatement(ctx, "set(allMetrics, false)");
    Assert.assertEquals(false, ctx.getConfig().isAllMetrics());
  }

  @Test
  public void testComplexSet() throws ExporterException {
    final SessionContext ctx = new SessionContext();
    runStatement(ctx, "set(nameSanitizer.forbidden, \"abc1\")");
    Assert.assertEquals("abc1", ctx.getConfig().getNameSanitizer().getForbidden());
    runStatement(ctx, "set(wavefrontConfig.token, \"abc2\")");
    Assert.assertEquals("abc2", ctx.getConfig().getWavefrontConfig().getToken());
    runStatement(ctx, "set(sqlConfig.sql, \"abc3\")");
    Assert.assertEquals("abc3", ctx.getConfig().getSqlConfig().getSql());
    runStatement(ctx, "set(elasticSearchConfig.apiKey, \"abc4\")");
    Assert.assertEquals("abc4", ctx.getConfig().getElasticSearchConfig().getApiKey());
  }
}

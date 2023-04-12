package com.vmware.vropsexport.opqsl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.opsql.Query;
import com.vmware.vropsexport.opsql.QueryCompiler;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

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
    final QueryCompiler qc = new QueryCompiler();
    runQuery("resource(VMWARE:VirtualMachine).fields(cpu|demandmhz)", "SimpleQueryResult");
  }

  @Test
  public void testMultiFieldQuery() throws IOException {
    final QueryCompiler qc = new QueryCompiler();
    runQuery(
        "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz, cpu|demandpct, @summary|guestFullName)",
        "MultiFieldQueryResult");
  }

  @Test
  public void testSimplFilterQuery() throws IOException {
    final QueryCompiler qc = new QueryCompiler();
    runQuery(
        "resource(VMWARE:VirtualMachine).name(\"hello\").fields(cpu|demandmhz)",
        "SimpleFilterQueryResult");
  }

  @Test
  public void testStackedFilterQuery() throws IOException {
    final QueryCompiler qc = new QueryCompiler();
    runQuery(
        "resource(VMWARE:VirtualMachine).name(\"hello\", \"goodbye\").regex(\".*foo.*\").whereHealth(\"RED\", \"YELLOW\").whereStatus(\"OK\").whereState(\"RUNNING\").fields(cpu|demandmhz)",
        "StackedFilterQueryResult");
  }

  @Test
  public void testComplexWhereQuery() {
    final QueryCompiler qc = new QueryCompiler();
    qc.compile(
        "select cpu|demandmhz, @summary|guestFullName from VMWARE:VirtualMachine where not name = \"hello\" and id = \"1\" and cpu|demandmhz > 42 or cpu|demandmhz < 1 and cpu|demandpct > 42");
  }
}

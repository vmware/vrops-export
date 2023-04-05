package com.vmware.vropsexport.opqsl;

import com.vmware.vropsexport.opsql.QueryCompiler;
import org.junit.Test;

public class QueryCompilerTest {
  @Test
  public void testSimpleQuery() {
    QueryCompiler qc = new QueryCompiler();
    qc.compile("select cpu|demandmhz from VMWARE:VirtualMachine");
  }

  @Test
  public void testSimpleWhereQuery() {
    QueryCompiler qc = new QueryCompiler();
    qc.compile(
            "select cpu|demandmhz from VMWARE:VirtualMachine where name = \"hello\"");
  }

  @Test
  public void testComplexWhereQuery() {
    QueryCompiler qc = new QueryCompiler();
    qc.compile(
        "select cpu|demandmhz from VMWARE:VirtualMachine where not name = \"hello\" and id = \"1\" and cpu|demandmhz > 42 or cpu|demandmhz < 1");
  }
}

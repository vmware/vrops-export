package com.vmware.vropsexport.opqsl.console;

import com.vmware.vropsexport.opsql.console.Parser;
import org.jline.reader.ParsedLine;
import org.junit.Assert;
import org.junit.Test;

public class ParserTest {
  private void checkQuery(final String query, final String[] words) {
    for (final String word : words) {
      final int p = query.indexOf(word);
      if (p == -1) {
        throw new RuntimeException("Internal error in test: '" + word + "' not found in query");
      }
      for (int i = 0; i < word.length(); ++i) {
        final ParsedLine pl = new Parser().parse(query, p + i);
        Assert.assertEquals(word, pl.word());
      }
    }
  }

  @Test
  public void testSimpleQuery() {
    checkQuery(
        "resource(VMWARE:VirtualMachine).name(\"hello\").fields(cpu|demandmhz)",
        new String[] {
          "resource", "VMWARE:VirtualMachine", "name", "\"hello\"", "fields", "cpu|demandmhz"
        });
  }

  @Test
  public void testLists() {
    checkQuery(
        "resource(VMWARE:VirtualMachine, VMWARE:HostSystem).name(\"hello\", \"goodbye\").fields(cpu|demandmhz, cpu|demandpct)",
        new String[] {
          "resource",
          "VMWARE:VirtualMachine",
          "VMWARE:HostSystem",
          "name",
          "\"hello\"",
          "\"goodbye\"",
          "fields",
          "cpu|demandmhz",
          "cpu|demandpct"
        });
  }

  @Test
  public void testNotAtWord() {
    final ParsedLine pl =
        new Parser()
            .parse("resource(VMWARE:VirtualMachine).name(\"hello\").fields(cpu|demandmhz)", 8);
    Assert.assertEquals("resource", pl.word());
  }
}

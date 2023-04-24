package com.vmware.vropsexport.opqsl.console;

import com.vmware.vropsexport.opsql.console.Completer;
import com.vmware.vropsexport.opsql.console.Highlighter;
import com.vmware.vropsexport.opsql.console.Parser;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.junit.Assert;
import org.junit.Test;

public class HighlighterTest {
  private void runTest(final String s, final String expected) {
    final Highlighter hl = new Highlighter();
    final LineReader lineReader =
        LineReaderBuilder.builder()
            .highlighter(hl)
            .completer(new Completer())
            .parser(new Parser())
            .build();
    final String ansi = hl.highlight(lineReader, s).toAnsi();
    Assert.assertEquals(expected, ansi);
  }

  @Test
  public void testHighlighter() {
    runTest(
        "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz)",
        "\u001B[1mresource\u001B[0m(VMWARE:VirtualMachine).\u001B[1mfields\u001B[0m(cpu|demandmhz)");
  }

  @Test
  public void testLexerFailure() {
    runTest("resource(;;;", "\u001B[1mresource\u001B[0m(;;;");
  }
}

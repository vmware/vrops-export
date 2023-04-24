package com.vmware.vropsexport.opqsl.console;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.Metadata;
import com.vmware.vropsexport.models.AdapterKind;
import com.vmware.vropsexport.models.AdapterKindResponse;
import com.vmware.vropsexport.opsql.console.Completer;
import com.vmware.vropsexport.opsql.console.Highlighter;
import com.vmware.vropsexport.opsql.console.Parser;
import org.apache.http.HttpException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HighlighterTest {
  private void runTest(final String s, final String expected) throws HttpException, IOException {
    final ObjectMapper om =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    final List<AdapterKind> adapterKinds =
        om.readValue(
                new File("src/test/resources/opsql/AdapterKindResponse.json"),
                AdapterKindResponse.class)
            .getAdapterKind();
    final Metadata backend = mock(Metadata.class);
    when(backend.getAdapterKinds()).thenReturn(adapterKinds);
    final Highlighter hl = new Highlighter();
    final LineReader lineReader =
        LineReaderBuilder.builder()
            .highlighter(hl)
            .completer(new Completer(backend))
            .parser(new Parser())
            .build();
    final String ansi = hl.highlight(lineReader, s).toAnsi();
    Assert.assertEquals(expected, ansi);
  }

  @Test
  public void testHighlighter() throws HttpException, IOException {
    runTest(
        "resource(VMWARE:VirtualMachine).fields(cpu|demandmhz)",
        "\u001B[1mresource\u001B[0m(VMWARE:VirtualMachine).\u001B[1mfields\u001B[0m(cpu|demandmhz)");
  }

  @Test
  public void testLexerFailure() throws HttpException, IOException {
    runTest("resource(;;;", "\u001B[1mresource\u001B[0m(;;;");
  }

  @Test
  public void testLexerFailureAndRecovery() throws HttpException, IOException {
    runTest("resource(;;;)", "\u001B[1mresource\u001B[0m(;;;)");
  }
}

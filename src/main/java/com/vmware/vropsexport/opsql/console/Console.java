/*
 * Copyright 2017-2023 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier:	Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vmware.vropsexport.opsql.console;

import com.vmware.vropsexport.Metadata;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.opsql.OpsqlException;
import com.vmware.vropsexport.opsql.QueryRunner;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

public class Console {

  private final Metadata backend;

  public Console(final Metadata backend) {
    this.backend = backend;
  }

  public void run(final QueryRunner runner) throws ExporterException {
    try {
      try (final Terminal terminal = TerminalBuilder.terminal()) {
        final LineReader lineReader =
            LineReaderBuilder.builder()
                .terminal(terminal)
                .highlighter(new Highlighter())
                .completer(new Completer(backend))
                .parser(new Parser())
                .build();
        for (; ; ) {
          final String query = lineReader.readLine("opsql> ").trim();
          if (query.length() == 0) {
            continue;
          }
          try {
            runner.executeQuery(query, System.out);
          } catch (final OpsqlException e) {
            // Error would already have been printed. Continue!
          } catch (final ExporterException e) {
            System.err.println("Error: " + e.getMessage());
          }
        }
      }
    } catch (final EndOfFileException | UserInterruptException e) {
      // End of input or user hit ^C. Nothing to do
    } catch (final IOException e) {
      throw new ExporterException(e);
    }
  }
}
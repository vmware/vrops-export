/*
 * Copyright 2017-2021 VMware, Inc. All Rights Reserved.
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

import com.vmware.vropsexport.opsql.Constants;
import com.vmware.vropsexport.opsql.OpsqlLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.jline.reader.LineReader;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.List;
import java.util.regex.Pattern;

public class Highlighter extends DefaultHighlighter {
  @Override
  public AttributedString highlight(final LineReader lineReader, final String s) {
    final AttributedStringBuilder asb = new AttributedStringBuilder();
    final OpsqlLexer lexer = new OpsqlLexer(CharStreams.fromString(s));
    lexer.removeErrorListeners(); // We don't want any error messages
    final List<? extends Token> tokens = lexer.getAllTokens();
    int tokenIdx = 0;
    for (int i = 0; i < s.length(); ) {
      // If we ran out of token (probably due to lexer error), just add the rest of the raw input
      // and exit.
      if (tokenIdx >= tokens.size()) {
        asb.append(s.substring(i));
        break;
      }
      final Token token = tokens.get(tokenIdx);
      if (token.getStartIndex() == i) {
        final String word = token.getText();
        asb.append(
            Constants.keywordSet.contains(word)
                ? new AttributedString(word, AttributedStyle.BOLD)
                : word);
        i = token.getStopIndex() + 1;
        tokenIdx++;
      } else {
        ++i;
      }
    }
    return asb.toAttributedString();
  }

  @Override
  public void setErrorPattern(final Pattern pattern) {}

  @Override
  public void setErrorIndex(final int i) {}
}

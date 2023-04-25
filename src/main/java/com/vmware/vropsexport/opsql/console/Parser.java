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

import com.vmware.vropsexport.opsql.OpsqlLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.ParsedLine;
import org.jline.reader.SyntaxError;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Parser implements org.jline.reader.Parser {

  public static class Line implements CompletingParsedLine {
    private final String line;
    private final List<String> words;
    private final int wordCursor;
    private final int cursor;
    private final int wordIndex;

    public Line(
        final String line,
        final List<String> words,
        final int wordCursor,
        final int cursor,
        final int wordIndex) {
      this.line = line;
      this.words = words;
      this.wordCursor = wordCursor;
      this.cursor = cursor;
      this.wordIndex = wordIndex;
    }

    @Override
    public String word() {
      return wordIndex != -1 ? words.get(wordIndex) : "";
    }

    @Override
    public int wordCursor() {
      return wordCursor;
    }

    @Override
    public int wordIndex() {
      return wordIndex;
    }

    @Override
    public List<String> words() {
      return words;
    }

    @Override
    public String line() {
      return line;
    }

    @Override
    public int cursor() {
      return cursor;
    }

    @Override
    public CharSequence escape(final CharSequence charSequence, final boolean b) {
      return charSequence;
    }

    @Override
    public int rawWordCursor() {
      return wordCursor;
    }

    @Override
    public int rawWordLength() {
      return word().length();
    }
  }

  private static final Pattern wordPattern = Pattern.compile("\"?[A-Za-z_|$:][A-Za-z_0-9|$:]*\"?");

  @Override
  public ParsedLine parse(final String s, final int i, final ParseContext parseContext)
      throws SyntaxError {
    // System.err.println("" + i + " " + s + " " + parseContext);
    final OpsqlLexer lexer = new OpsqlLexer(CharStreams.fromString(s));
    lexer.removeErrorListeners(); // We don't want any error messages
    final List<? extends Token> tokens = lexer.getAllTokens();
    final List<String> words = new ArrayList<>(tokens.size());
    int cursorWord = -1;
    int wordCursor = 0;
    for (final Token token : tokens) {
      final String word = token.getText();
      if (wordPattern.matcher(word).matches()) {
        words.add(word);
        if (token.getStartIndex() <= i
            && token.getStopIndex() + 1 >= i) { // +1 since cursor can be after word
          cursorWord = words.size() - 1;
          wordCursor = i - token.getStartIndex();
        }
      }
    }
    return new Line(s, words, wordCursor, i, cursorWord);
  }
}

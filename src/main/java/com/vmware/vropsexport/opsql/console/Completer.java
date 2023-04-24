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
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

public class Completer implements org.jline.reader.Completer {
  @Override
  public void complete(
      final LineReader lineReader, final ParsedLine parsedLine, final List<Candidate> list) {
    final String word = parsedLine.word();
    if (word == null || word.length() == 0) {
      return;
    }
    for (final String keyword : Constants.keywords) {
      if (keyword.startsWith(word)) {
        list.add(new Candidate(keyword, keyword, null, null, "(", null, false, 0));
      }
    }
  }
}

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

import com.vmware.vropsexport.Metadata;
import com.vmware.vropsexport.models.AdapterKind;
import com.vmware.vropsexport.opsql.Constants;
import org.apache.http.HttpException;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.IOException;
import java.util.*;

public class Completer implements org.jline.reader.Completer {
  @FunctionalInterface
  interface Resolver {
    public List<Candidate> apply(Completer completer, String pattern);
  }

  private final Metadata backend;

  private static final Map<String, Resolver> resolvers = new HashMap<>();

  static {
    resolvers.put("resource", Completer::resolveResourceType);
  }

  private final List<Candidate> resourceKinds;

  public Completer(final Metadata backend) {
    this.backend = backend;
    resourceKinds = loadResourceKinds();
  }

  @Override
  public void complete(
      final LineReader lineReader, final ParsedLine parsedLine, final List<Candidate> list) {
    final String word = parsedLine.word();
    if (word == null || word.length() == 0) {
      return;
    }

    // Handle keywords
    for (final String keyword : Constants.keywords) {
      if (keyword.startsWith(word)) {
        list.add(makeCandidate(keyword));
      }
    }

    // Handle context-sensitive completion. Infer context by backing up until we find a keyword we
    // have a resolver for.
    for (int i = parsedLine.wordIndex(); i >= 0; --i) {
      final Resolver resolver = resolvers.get(parsedLine.words().get(i));
      if (resolver != null) {
        list.addAll(resolver.apply(this, word));
        break;
      }
    }
  }

  private static List<Candidate> resolveResourceType(
      final Completer completer, final String pattern) {
    return completer.resourceKinds;
  }

  private List<Candidate> loadResourceKinds() {
    final List<Candidate> result = new ArrayList<>();
    try {
      for (final AdapterKind adapterKind : backend.getAdapterKinds()) {
        for (final String resourceKind : adapterKind.getResourceKinds()) {
          result.add(makeCandidate(adapterKind.getKey() + ":" + resourceKind));
        }
      }
    } catch (final IOException | HttpException e) {
      // Just return an empty list. We don't want any error messages cluttering the console
      return Collections.emptyList();
    }
    return result;
  }

  private static Candidate makeCandidate(final String word) {
    return new Candidate(word, word, null, null, "(", null, false, 0);
  }
}

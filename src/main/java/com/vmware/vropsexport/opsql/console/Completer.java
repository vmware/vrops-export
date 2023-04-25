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
import com.vmware.vropsexport.models.AdapterKind;
import com.vmware.vropsexport.models.StatKeysResponse;
import com.vmware.vropsexport.opsql.Constants;
import org.apache.http.HttpException;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Completer implements org.jline.reader.Completer {
  @FunctionalInterface
  interface Resolver {
    List<Candidate> apply(Completer completer, ParsedLine parsedLine, String pattern);
  }

  private final Metadata backend;

  private static final Map<String, Resolver> resolvers = new HashMap<>();

  static {
    resolvers.put("resource", Completer::resolveResourceType);
    resolvers.put("fields", Completer::resolveStatKey);
    resolvers.put("whereMetrics", Completer::resolveStatKey);
  }

  private final List<Candidate> resourceKinds;

  private final Set<String> resourceKindLookup;

  private final Map<String, List<StatKeysResponse.StatKey>> statCache = new HashMap<>();

  public Completer(final Metadata backend) {
    this.backend = backend;
    resourceKinds = loadResourceKinds();
    resourceKindLookup = resourceKinds.stream().map(Candidate::value).collect(Collectors.toSet());
  }

  @Override
  public void complete(
      final LineReader lineReader, final ParsedLine parsedLine, final List<Candidate> list) {
    final String word = parsedLine.word();
    if (word == null || word.length() == 0) {
      return;
    }

    // Handle keywords
    list.addAll(
        Arrays.stream(Constants.keywords)
            .map(Completer::makeCandidate)
            .collect(Collectors.toList()));

    // Handle context-sensitive completion. Infer context by backing up until we find a keyword we
    // have a resolver for.
    for (int i = parsedLine.wordIndex(); i >= 0; --i) {
      final Resolver resolver = resolvers.get(parsedLine.words().get(i));
      if (resolver != null) {
        list.addAll(resolver.apply(this, parsedLine, word));
        break;
      }
    }
  }

  private static List<Candidate> resolveResourceType(
      final Completer completer, final ParsedLine parsedLine, final String pattern) {
    return completer.resourceKinds;
  }

  private static List<Candidate> resolveStatKey(
      final Completer completer, final ParsedLine parsedLine, final String pattern) {
    final List<Candidate> result = new ArrayList<>();
    final List<String> resourceKinds = inferResourceKind(parsedLine, completer);
    for (final String resourceKind : resourceKinds) {
      final List<StatKeysResponse.StatKey> keys =
          completer.statCache.computeIfAbsent(
              resourceKind, (k) -> loadStatkeys(completer.backend, resourceKind));
      result.addAll(
          keys.stream().map((k) -> makeCandidate(k.getKey())).collect(Collectors.toList()));
    }
    return result;
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

  private static List<StatKeysResponse.StatKey> loadStatkeys(
      final Metadata metadata, final String qualifiedResourceKind) {
    final int p = qualifiedResourceKind.indexOf(":");
    final String adapterKind = p != -1 ? qualifiedResourceKind.substring(0, p) : "VMWARE";
    final String resourceKind =
        p != -1 ? qualifiedResourceKind.substring(p + 1) : qualifiedResourceKind;
    try {
      return metadata.getStatKeysForResourceKind(adapterKind, resourceKind);
    } catch (final IOException | HttpException e) {
      // Just return an empty list. We don't want any error messages cluttering the console
      return Collections.emptyList();
    }
  }

  private static Candidate makeCandidate(final String word) {
    return new Candidate(word, word, null, null, "(", null, false, 0);
  }

  private static Candidate makeCandidate(final String key, final String name) {
    return new Candidate(key, name, null, null, "(", null, false, 0);
  }

  private static List<String> inferResourceKind(final ParsedLine p, final Completer completer) {
    final List<String> result = new ArrayList<>(10);
    final List<String> words = p.words();
    for (int i = 0; i < words.size(); ++i) {
      if (words.get(i).equals("resource")) {
        ++i;
        for (; i < words.size(); ++i) {
          final String word = words.get(i);
          if (completer.resourceKindLookup.contains(word)) {
            result.add(word);
          } else {
            break;
          }
        }
      }
    }
    return result;
  }
}

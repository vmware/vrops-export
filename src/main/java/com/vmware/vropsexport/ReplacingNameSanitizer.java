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
package com.vmware.vropsexport;

public class ReplacingNameSanitizer implements NameSanitizer {
  private final boolean[] flags;

  private final char replacement;

  private final int min;

  private final int max;

  public ReplacingNameSanitizer(final String forbidden, final char replacement) {
    this.replacement = replacement;
    max = forbidden.chars().max().orElse(0);
    min = forbidden.chars().min().orElse(0);
    flags = new boolean[max - min + 1];
    forbidden.chars().forEach(c -> flags[c - min] = true);
  }

  @Override
  public String transform(final String s) {
    StringBuilder sb = null;
    for (int i = 0; i < s.length(); ++i) {
      final char c = s.charAt(i);
      if (c >= min && c <= max && flags[c - min]) {
        if (sb == null) {
          sb = new StringBuilder(s.substring(0, i));
        }
        sb.append(replacement);
      } else {
        if (sb != null) {
          sb.append(c);
        }
      }
    }
    return sb != null ? sb.toString() : s;
  }
}

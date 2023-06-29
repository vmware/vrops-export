/*
 *
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
 *
 */

package com.vmware.vropsexport.utils;

import com.vmware.vropsexport.exceptions.ExporterException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ParseUtils {
  @SuppressFBWarnings("SF_SWITCH_FALLTHROUGH")
  public static long parseLookback(final String lb) throws ExporterException {
    long scale = 1;
    final char unit = lb.charAt(lb.length() - 1);
    switch (unit) {
      case 'w':
        scale *= 7; // fallthru
      case 'd':
        scale *= 24; // fallthru
      case 'h':
        scale *= 60; // fallthru
      case 'm':
        scale *= 60; // fallthru
      case 's':
        scale *= 1000;
        break;
      default:
        throw new ExporterException("Cannot parse time unit");
    }
    try {
      final long t = Long.parseLong(lb.substring(0, lb.length() - 1));
      return t * scale;
    } catch (final NumberFormatException e) {
      throw new ExporterException("Cannot parse time value");
    }
  }
}

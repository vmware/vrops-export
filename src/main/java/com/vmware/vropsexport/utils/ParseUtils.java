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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

public class ParseUtils {
  private static final String dateTimePattern = "yyyy-MM-dd HH:mm[:ss][ zzz]";
  private static final String timePattern = "HH:mm[:ss]";

  private static final DateTimeFormatter dateTimeFormatter =
      new DateTimeFormatterBuilder()
          .append(DateTimeFormatter.ofPattern(dateTimePattern))
          .parseLenient()
          .toFormatter()
          .withZone(ZoneId.systemDefault());

  private static final DateTimeFormatter timeFormatter =
      new DateTimeFormatterBuilder()
          .append(DateTimeFormatter.ofPattern(timePattern))
          .parseLenient()
          .toFormatter()
          .withResolverStyle(ResolverStyle.SMART)
          .withZone(ZoneId.systemDefault());

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

  public static Date parseDateTime(final String s) {
    final TemporalAccessor t = dateTimeFormatter.parse(s);
    final Instant inst = Instant.from(t);
    return new Date(inst.toEpochMilli());
  }

  public static Date parseTime(final String s) {
    final LocalTime t = LocalTime.parse(s, timeFormatter);
    return new Date(
        LocalDate.now().atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
  }
}

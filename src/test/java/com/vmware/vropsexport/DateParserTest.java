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

package com.vmware.vropsexport;

import com.vmware.vropsexport.utils.ParseUtils;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class DateParserTest {
  @Test
  public void testParseDateTime() {
    final long fromLocal =
        LocalDateTime.parse("2023-07-04T12:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    final long fromUTC = Instant.parse("2023-07-04T12:00:00Z").toEpochMilli();
    Assert.assertEquals(fromLocal, ParseUtils.parseDateTime("2023-07-04 12:00").getTime());
    Assert.assertEquals(
        fromLocal + 1000, ParseUtils.parseDateTime("2023-07-04 12:00:01").getTime());
    Assert.assertEquals(fromUTC, ParseUtils.parseDateTime("2023-07-04 12:00 UTC").getTime());
    Assert.assertEquals(
        fromUTC + 1000, ParseUtils.parseDateTime("2023-07-04 12:00:01 UTC").getTime());
    Assert.assertEquals(fromUTC, ParseUtils.parseDateTime("2023-07-04 12:00 UTC").getTime());
    Assert.assertEquals(
        fromUTC + 1000, ParseUtils.parseDateTime("2023-07-04 12:00:01 UTC").getTime());
  }

  public void checkTime(final Date d, final int h, final int m, final int s) {
    final Calendar cal = new GregorianCalendar();
    cal.setTime(d);
    Assert.assertEquals("Hour", h, cal.get(Calendar.HOUR_OF_DAY));
    Assert.assertEquals("Minute", m, cal.get(Calendar.MINUTE));
    Assert.assertEquals("Second", s, cal.get(Calendar.SECOND));
  }

  @Test
  public void testParseTime() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    for (int h = 12; h < 24; ++h) {
      for (int m = 0; m < 60; ++m) {
        checkTime(ParseUtils.parseTime(String.format("%02d:%02d", h, m)), h, m, 0);
      }
    }
    for (int h = 12; h < 24; ++h) {
      for (int m = 0; m < 60; ++m) {
        for (int s = 0; s < 60; ++s) {
          checkTime(ParseUtils.parseTime(String.format("%02d:%02d:%02d", h, m, s)), h, m, s);
        }
      }
    }
  }
}

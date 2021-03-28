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

import org.junit.Assert;
import org.junit.Test;

public class NameSanitizerTest {
  @Test
  public void testReplacingNameSanitizer() {
    final NameSanitizer ns = new ReplacingNameSanitizer("$|^%!?", 'p');
    Assert.assertEquals(
        "peter piper picked a peck of pickled peppers",
        ns.transform("$eter |iper ^icked a %eck of !ickled !e??ers"));
    Assert.assertEquals(
        "peter piper picked a peck of pickled peppers",
        ns.transform("peter piper picked a peck of pickled peppers"));
    Assert.assertTrue( // When no replacements are needed, the same object should be returned.
        "peter piper picked a peck of pickled peppers"
            == ns.transform("peter piper picked a peck of pickled peppers"));
  }
}

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

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

public class InteKeyMapTest {
  @Test
  public void testSimpleSetGet() {
    final Random rnd = new Random();
    final IntKeyMap<Integer> map = new IntKeyMap<>();
    final Integer[] values = new Integer[1000];
    Arrays.setAll(values, (e) -> rnd.nextInt(values.length));
    for (int i = 0; i < values.length; ++i) {
      map.put(values[i], values[i]);
    }
    for (int i = 0; i < values.length; ++i) {
      Assert.assertEquals(values[i], map.get(values[i]));
    }
  }
}

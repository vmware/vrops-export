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

import java.util.Arrays;

/** Simple integer key hash map implemented as a sparse array. */
public class IntKeyMap<V> {
  private static final int slack = 20;

  private V[] array;

  public IntKeyMap() {
    this(slack);
  }

  public IntKeyMap(final int size) {
    array = (V[]) new Object[size];
  }

  public void put(final int key, final V value) {
    if (key >= array.length) {
      array = Arrays.copyOf(array, key + slack);
    }
    array[key] = value;
  }

  public V get(final int key) {
    return key < array.length ? array[key] : null;
  }
}

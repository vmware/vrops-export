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

import java.util.ArrayList;
import java.util.List;

public class ChunkerTest {
  @Test
  public void testAlignedChunker() {
    final List<Integer> list = new ArrayList<>();
    for (int i = 0; i < 64; ++i) {
      list.add(i);
    }
    final List<List<Integer>> chunks = new ArrayList<>();
    Chunker.chunkify(
        list,
        8,
        (i) -> {
          chunks.add(i);
        });
    Assert.assertEquals(8, chunks.size());
    for (int i = 0; i < 8; ++i) {
      Assert.assertEquals(8, chunks.get(i).size());
      Assert.assertEquals(i * 8, chunks.get(i).get(0).longValue());
      Assert.assertEquals(i * 8 + 7, chunks.get(i).get(7).longValue());
    }
  }

  @Test
  public void testUnalignedChunker() {
    final List<Integer> list = new ArrayList<>();
    for (int i = 0; i < 65; ++i) {
      list.add(i);
    }
    final List<List<Integer>> chunks = new ArrayList<>();
    Chunker.chunkify(
        list,
        8,
        (i) -> {
          chunks.add(i);
        });
    Assert.assertEquals(9, chunks.size());
    for (int i = 0; i < 8; ++i) {
      Assert.assertEquals(8, chunks.get(i).size());
      Assert.assertEquals(i * 8, chunks.get(i).get(0).longValue());
      Assert.assertEquals(i * 8 + 7, chunks.get(i).get(7).longValue());
    }
    Assert.assertEquals(1, chunks.get(8).size());
    Assert.assertEquals(64, chunks.get(8).get(0).longValue());
  }
}

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

import java.util.List;
import java.util.function.Consumer;

public class Chunker {
  public static <T> void chunkify(
      final List<T> list, final int chunkSize, final Consumer<List<T>> task) {
    int nChunks = list.size() / chunkSize;
    if (list.size() % nChunks != 0) {
      nChunks++;
    }
    for (int i = 0; i < nChunks; ++i) {
      task.accept(list.subList(i * chunkSize, Math.min(list.size(), i * chunkSize + chunkSize)));
    }
  }
}

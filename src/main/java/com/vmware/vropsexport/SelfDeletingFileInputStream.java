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
package com.vmware.vropsexport;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

public class SelfDeletingFileInputStream extends InputStream {

  private final FileInputStream backer;

  private final File file;

  public SelfDeletingFileInputStream(final String filename) throws FileNotFoundException {
    file = new File(filename);
    backer = new FileInputStream(file);
  }

  public SelfDeletingFileInputStream(final File file) throws FileNotFoundException {
    this.file = file;
    backer = new FileInputStream(this.file);
  }

  @Override
  public int read() throws IOException {
    return backer.read();
  }

  @Override
  public int hashCode() {
    return backer.hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    return obj == this;
  }

  @Override
  public int read(final byte[] b) throws IOException {
    return backer.read(b);
  }

  @Override
  public int read(final byte[] b, final int off, final int len) throws IOException {
    return backer.read(b, off, len);
  }

  @Override
  public String toString() {
    return backer.toString();
  }

  @Override
  public long skip(final long n) throws IOException {
    return backer.skip(n);
  }

  @Override
  public int available() throws IOException {
    return backer.available();
  }

  @Override
  public void mark(final int readlimit) {
    backer.mark(readlimit);
  }

  @Override
  public void close() throws IOException {
    backer.close();
    file.delete();
  }

  public final FileDescriptor getFD() throws IOException {
    return backer.getFD();
  }

  public FileChannel getChannel() {
    return backer.getChannel();
  }

  @Override
  public void reset() throws IOException {
    backer.reset();
  }

  @Override
  public boolean markSupported() {
    return backer.markSupported();
  }
}

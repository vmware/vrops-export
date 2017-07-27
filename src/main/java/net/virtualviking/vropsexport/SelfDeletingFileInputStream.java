/* 
 * Copyright 2017 Pontus Rydin
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
package net.virtualviking.vropsexport;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

public class SelfDeletingFileInputStream extends InputStream {
	
	private FileInputStream backer;
	
	private File file;
	
	public SelfDeletingFileInputStream(String filename) throws FileNotFoundException {
		this.file = new File(filename);
		this.backer = new FileInputStream(this.file);
	}
	
	public SelfDeletingFileInputStream(File file) throws FileNotFoundException {
		this.file = file;
		this.backer = new FileInputStream(this.file);
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
	public boolean equals(Object obj) {
		return backer.equals(obj);
	}

	@Override
	public int read(byte[] b) throws IOException {
		return backer.read(b);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return backer.read(b, off, len);
	}

	@Override
	public String toString() {
		return backer.toString();
	}

	@Override
	public long skip(long n) throws IOException {
		return backer.skip(n);
	}

	@Override
	public int available() throws IOException {
		return backer.available();
	}

	@Override
	public void mark(int readlimit) {
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
	
	@Override
	public void finalize() {
		try {
			this.close();
		} catch(IOException e) {
			// Do nothing. It's probably just already closed.
		}
	}
}

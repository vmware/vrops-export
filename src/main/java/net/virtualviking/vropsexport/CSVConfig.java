package net.virtualviking.vropsexport;

public class CSVConfig {
	private boolean header = true;
	
	private String delimiter = ",";

	public boolean isHeader() {
		return header;
	}

	public void setHeader(boolean header) {
		this.header = header;
	}

	public String getDelimiter() {
		return delimiter;
	}

	public void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}
}

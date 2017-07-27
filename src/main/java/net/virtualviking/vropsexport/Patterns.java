package net.virtualviking.vropsexport;

import java.util.regex.Pattern;

import org.json.JSONObject;

public class Patterns {
	
	public static Pattern parentPattern = Pattern.compile("^\\$parent\\:([_A-Za-z][_A-Za-z0-9]*)\\.(.+)$");
	
	public static Pattern parentSpecPattern = Pattern.compile("^([_\\-A-Za-z][_\\-A-Za-z0-9]*):(.+)$");
	
	public static Pattern adapterAndResourceKindPattern = Pattern.compile("^([_\\-A-Za-z][_\\-A-Za-z0-9]*):(.+)$");
}

package org.djf.util;

public class Utils {
	
	/** return everything strictly before the separator, else all if missing */
	public static String before(String s, String separator) {
		int i = s.indexOf(separator);
		return i < 0 ? s : s.substring(0, i);
	}

	/** return everything strictly after the separator, else "" if missing */
	public static String after(String s, String separator) {
		int i = s.indexOf(separator);
		return i < 0 ? "" : s.substring(i + separator.length());
	}

	/** Integer power function */
	public static int intPow(int a, int b) { //https://stackoverflow.com/a/8071402/13773745
		int result = 1;
		for (int i = 1; i <= b; i++) {
			result *= a;
		}
		return result;
	}
	
}

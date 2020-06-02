package org.djf.scratch;

public class TryJava {

	public static void main(String[] args) {
		byte b = (byte) 255;
		int i = b & 0xff;
		System.out.printf("%s\n", b);
		System.out.printf("%s\n", i);
	}
}

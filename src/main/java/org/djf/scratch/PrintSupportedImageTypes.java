package org.djf.scratch;
import javax.imageio.ImageIO;

public class PrintSupportedImageTypes {
	
	public static void main(String[] args) {
		for (String format : ImageIO.getWriterFormatNames()) {
		    System.out.println("format = " + format);
		}
	}

}

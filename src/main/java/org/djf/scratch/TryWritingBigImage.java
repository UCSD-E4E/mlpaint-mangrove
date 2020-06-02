package org.djf.scratch;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;

import org.djf.util.SwingUtil;

public class TryWritingBigImage {
	
	public static void main(String[] args) throws IOException {
		
		File hugefile = new File("/tmp/bigJunko.png");
		Dimension size = SwingUtil.readImageDimensions(hugefile);
		System.out.printf("%s\n", size);
	}

}

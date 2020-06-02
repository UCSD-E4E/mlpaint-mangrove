package org.djf.scratch;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

public class WriteHugeFile {
	
	public static void main(String[] args) throws IOException {
		int max = 46300;
		BufferedImage img = new BufferedImage(max, max, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g2 = img.createGraphics();
		g2.setPaint(Color.WHITE);
		Random rand = new Random();
		for (int y = 0; y < max; y++) {
			int end = rand.nextInt(max);
			if (end == 0) continue;
			int start = rand.nextInt(end);
			for (int x = start; x < end; x++) {
				g2.drawLine(x, y, x, y);
			}
		}
		g2.dispose();
		
//		boolean rr = ImageIO.write(img, "tiff", new File("/tmp/bigJunko.tiff"));// many times larger
		boolean rr = ImageIO.write(img, "png", new File("/tmp/bigJunko.png"));// lossless compression
		System.out.printf("%s\n", rr);
	}

}

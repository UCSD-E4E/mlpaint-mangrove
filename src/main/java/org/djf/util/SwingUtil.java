package org.djf.util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;

import com.google.common.base.Preconditions;
import org.djf.mlpaint.MyPoint;

/** Swing Utilities.
 * 
 */
public class SwingUtil {
	

	public static final Color TRANSPARENT = new Color(0,0,0,0f);
	public static final Color ALPHARED    = new Color(1,0,0,.2f);
	public static final Color ALPHAGREEN = new Color(0,1,0,.2f);
	public static final Color ALPHABLUE  = new Color(0,1,0,.2f);

	/** new BufferedImage with TYPE_BYTE_BINARY with 1, 2, or 4 bits-per-pixel, depending on # colors offered */
	public static BufferedImage newBinaryImage(int width, int height, Color... colors) {
		Preconditions.checkArgument(colors.length <= 16, "Colors.length must be <= 16");
		int bitsPerPixel = 
				colors.length <= 2 ? 1 :
			    colors.length <= 4 ? 2 :
			    	4;
		int size = 1 << bitsPerPixel;								//GROC
		byte[] r = new byte[size];
		byte[] g = new byte[size];
		byte[] b = new byte[size];
		byte[] a = new byte[size];
		for (int i = 0; i < colors.length; i++) {
			r[i] = (byte) colors[i].getRed();
			g[i] = (byte) colors[i].getGreen();
			b[i] = (byte) colors[i].getBlue();
			a[i] = (byte) colors[i].getAlpha();
		}
		IndexColorModel icm = new IndexColorModel(bitsPerPixel, size, r, g, b, a);
		return new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY, icm);
	}



	public static Dimension readImageDimensions(File imageFile) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(imageFile)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);// list of potential readers
			while (readers.hasNext()) {
				ImageReader reader = readers.next();
				try {
					reader.setInput(in);
					return new Dimension(reader.getWidth(0), reader.getHeight(0));// return the first to succeed
				} catch (Exception ex) {
					ex.printStackTrace();
				} finally {
					reader.dispose();
				}
			}
		}
		throw new IOException("Couldn't parse the file: " + imageFile);
	}

	public static void fillImage(BufferedImage img, int intVal) {
		WritableRaster rawData = img.getRaster();
		for (int x = 0; x < img.getWidth(); x++) {
			for (int y = 0; y < img.getHeight(); y++) {
				rawData.setSample(x, y, 0, intVal);
			}
		}
	}
	
	
	// https://stackoverflow.com/questions/3294388/make-a-bufferedimage-use-less-ram
	public static BufferedImage subsampleImage(ImageInputStream inputStream, int width, int height,
			IIOReadProgressListener progressListener) throws IOException {

		BufferedImage resampledImage = null;

		Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);

		if (!readers.hasNext()) {
			throw new IOException("No reader available for supplied image stream.");
		}

		ImageReader reader = readers.next();

		ImageReadParam imageReaderParams = reader.getDefaultReadParam();
		reader.setInput(inputStream);

		Dimension d1 = new Dimension(reader.getWidth(0), reader.getHeight(0));
		Dimension d2 = new Dimension(width, height);
		int subsampling = (int) scaleSubsamplingMaintainAspectRatio(d1, d2);
		imageReaderParams.setSourceSubsampling(subsampling, subsampling, 0, 0);

		reader.addIIOReadProgressListener(progressListener);
		resampledImage = reader.read(0, imageReaderParams);
		reader.removeAllIIOReadProgressListeners();

		return resampledImage;
	}

	public static long scaleSubsamplingMaintainAspectRatio(Dimension d1, Dimension d2) {
		long subsampling = 1;

		if (d1.getWidth() > d2.getWidth()) {
			subsampling = Math.round(d1.getWidth() / d2.getWidth());
		} else if (d1.getHeight() > d2.getHeight()) {
			subsampling = Math.round(d1.getHeight() / d2.getHeight());
		}

		return subsampling;
	}

}

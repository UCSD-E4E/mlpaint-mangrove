package org.djf.util;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;

/** Utility functions.
 * 
 */
public class Util {


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

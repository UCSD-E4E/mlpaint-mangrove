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

	public static boolean isSameDimensions(Dimension xy, File[] files) throws IOException {
		for (File file: files) { //GROK: hasnext vs for each
			try {
				Dimension imgDim = readImageDimensions(file);
				if ( imgDim.width != xy.width || imgDim.height != xy.height ) {
					return false;
				}
			} catch (IOException ex) { //GROK: ensure
				throw ex;
			}
		}
		return true;
	}

	public static void fillImage(BufferedImage img, int intVal) {
		WritableRaster rawData = img.getRaster();
		for (int x = 0; x < img.getWidth(); x++) {
			for (int y = 0; y < img.getHeight(); y++) {
				rawData.setSample(x, y, 0, intVal);
			}
		}
	}

	public static BufferedImage	subsampleImageFile(File file, int subSamplingEdge) throws IOException {//BufferedImage
		ImageInputStream inputStream = ImageIO.createImageInputStream(file);
		IIOReadProgressListener progressListener = null;
		return subsampleImage(inputStream, subSamplingEdge, progressListener);
	}
	
	// https://stackoverflow.com/questions/3294388/make-a-bufferedimage-use-less-ram, altered mildly
	public static BufferedImage subsampleImage(ImageInputStream inputStream, int subSamplingEdge,
			IIOReadProgressListener progressListener) throws IOException {

		BufferedImage resampledImage = null;

		Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);

		if (!readers.hasNext()) {
			throw new IOException("No reader available for supplied image stream.");
		}

		ImageReader reader = readers.next();

		ImageReadParam imageReaderParams = reader.getDefaultReadParam();
		reader.setInput(inputStream);

		// # of subsampoled pixels in scanline = truncate[(width - subsamplingXOffset + sourceXSubsampling - 1) / sourceXSubsampling].
		imageReaderParams.setSourceSubsampling(subSamplingEdge, subSamplingEdge, 0, 0);

		reader.addIIOReadProgressListener(progressListener);
		resampledImage = reader.read(0, imageReaderParams);
		reader.removeAllIIOReadProgressListeners();

		return resampledImage;
	}

	public static BufferedImage upsampleImage0Channel(BufferedImage smallImg, Dimension goalDimensions, int upSampling) {
		int pixelDifferenceWidth  = smallImg.getWidth()*upSampling  - goalDimensions.width;
		int pixelDifferenceHeight = smallImg.getHeight()*upSampling - goalDimensions.height;
		Preconditions.checkArgument(pixelDifferenceWidth >= 0,
				"For the upsampling in width, our goal is too big.");
		Preconditions.checkArgument(pixelDifferenceHeight>= 0,
				"For the upsampling in height, our goal is too big.");
		Preconditions.checkArgument( pixelDifferenceWidth < upSampling,
				"For the upsampling in width, we overshoot our goal.");
		Preconditions.checkArgument(pixelDifferenceHeight < upSampling,
				"For the upsampling in height, we overshoot our goal.");
		//All those preconditions should work, since our downsampling did this:
		// # of subsampoled pixels in scanline = truncate[(width - subsamplingXOffset + sourceXSubsampling - 1) / sourceXSubsampling].

		BufferedImage bigImg = new BufferedImage(goalDimensions.width, goalDimensions.height, smallImg.getType());
		WritableRaster bigRawData = bigImg.getRaster();
		WritableRaster smallRawData = smallImg.getRaster(); //GROK: Versus writableRaster, what about readableRaster?

		for (int x=0; x < goalDimensions.width; x++) {
			for (int y = 0; y < goalDimensions.height; y++) {
				int pixelVal = smallRawData.getSample(x / upSampling, y / upSampling,0);
				bigRawData.setSample(x, y, 0, pixelVal);
			}
		}
		return bigImg;
	}
}

package org.djf.util;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.stream.ImageInputStream;

import com.google.common.base.Preconditions;
import org.checkerframework.checker.units.qual.C;
import org.djf.mlpaint.ImageResamplingDims;
import org.djf.mlpaint.MyPoint;

/** Swing Utilities.
 * 
 */
public class SwingUtil {

	public static final Color TRANSPARENT = new Color(0,0,0,0f);
	public static final Color ALPHARED    = new Color(1,0,0,.2f);
	public static final Color ALPHAGREEN = new Color(0,1,0,.2f);
	public static final Color ALPHABLUE  = new Color(0,0,1,.2f);
	public static final Color ALPHAYELLOW = new Color(1, 1, 0, .2f);
	public static final Color ALPHAGRAY = new Color(0, 0, 0, .5f);
	public static final Color ALPHABLACK = new Color(0, 0, 0, .7f);
	public static final Color SKYBLUE = new Color(41,204, 255);
	public static final Color SKYRED = new Color(255, 119, 54);
	public static final Color BACKGROUND_GRAY = new Color(238,238,238);

	/** new BufferedImage with TYPE_BYTE_BINARY with 1, 2, or 4 bits-per-pixel, depending on # colors offered */
	public static BufferedImage newBinaryImage(int width, int height, Color... colors) {
		Preconditions.checkArgument(colors.length <= 16, "Colors.length must be <= 16");
		IndexColorModel icm = newBinaryICM(colors);
		return new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY, icm);
	}

	/** get a colormodel for a TYPE_BYTE_BINARY buffered image
	 * see newBinaryImage(), this function's chief use */
	public static IndexColorModel newBinaryICM(Color... colors) {
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
		return icm;
	}

	public static void fillImage(BufferedImage img, int intVal) {
		WritableRaster rawData = img.getRaster();
		for (int x = 0; x < img.getWidth(); x++) {
			for (int y = 0; y < img.getHeight(); y++) {
				rawData.setSample(x, y, 0, intVal);
			}
		}
	}

	public static void fillCodeByCornerColor(BufferedImage image, BufferedImage labels, int labelsCode) {
		WritableRaster img = image.getRaster();
		ColorModel imgCm = image.getColorModel();
		int[][] fourCorners = {	{0,0},
								{0,img.getHeight()-1},
								{img.getWidth()-1, img.getHeight()-1},
								{img.getWidth()-1,0} };
		int imgMainCode = img.getSample(fourCorners[0][0], fourCorners[0][1], 0);
		Color imageColor = new Color(imgCm.getRGB(imgMainCode));
		System.out.print(imageColor);
		for (int[] pair : fourCorners) {
			int imgCode = img.getSample(pair[0], pair[1], 0);
			Color pxlColor = new Color(imgCm.getRGB(imgCode));
			System.out.print(pxlColor);
			if (!pxlColor.equals(imageColor)) {
				System.out.println("The colors at the four corners were not consistent. \n" +
						"So we did not use it for a no_data code in labels.");
				return;
			}
		}
		System.out.println("We have a no_data color supposed: ");
		System.out.print(imageColor);
		System.out.println("We got the same color in the four corners of the image. \n" +
				"So fill it in with NO_DATA for the labels image.");
		fillCodeByColor(image, imageColor, labels, labelsCode);
	}

	public static void fillCodeByColor(BufferedImage image, Color imageColor, BufferedImage labels, int labelsCode) {
		WritableRaster img = image.getRaster();
		WritableRaster lbl = labels.getRaster();
		ColorModel imgCm = image.getColorModel();
		for (int i=0; i < img.getWidth(); i++ ) {
			for (int j=0; j < img.getHeight(); j++) {
				int imgCode = img.getSample(i,j,0);
				Color pxlColor = new Color(imgCm.getRGB(imgCode));
				if (pxlColor.equals(imageColor)) {
					lbl.setSample(i, j, 0, labelsCode);
				}
			}
		}

	}

	public static void addImage(BufferedImage buff1, BufferedImage buff2) {
		Graphics2D g2d = buff1.createGraphics();
		g2d.setComposite(
				AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
		g2d.drawImage(buff2, 0, 0, null);
		g2d.dispose();
	}

	public static void copyImage(BufferedImage empty, BufferedImage full) {
		WritableRaster emptyData = empty.getRaster();
		WritableRaster fullData  = full.getRaster();
		boolean someNotZeroOrOne = false;
		for (int x = 0; x < empty.getWidth(); x++) {
			for (int y = 0; y < empty.getHeight(); y++) {
				int sampleVal = fullData.getSample(x, y,0);
				emptyData.setSample(x, y, 0, sampleVal);
				if (sampleVal != 1 && sampleVal !=0){
					someNotZeroOrOne = true;
				}
			}
		}
		if (someNotZeroOrOne) {
			System.out.println("Some of the labels in the labels image were greater than 1 or 0.");
		}
	}

	public static BufferedImage setRGBNoAlpha(BufferedImage withAlpha) {
		BufferedImage copy = new BufferedImage(withAlpha.getWidth(), withAlpha.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = copy.createGraphics();
		g2d.setColor(Color.WHITE); // Or what ever fill color you want...
		g2d.fillRect(0, 0, copy.getWidth(), copy.getHeight());
		g2d.drawImage(withAlpha, 0, 0, null);
		g2d.dispose();
		return copy;
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
				System.out.println(imgDim);
				if ( imgDim.width != xy.width || imgDim.height != xy.height ) {
					return false;
				}
			} catch (IOException ex) { //GROK: ensure
				throw ex;
			}
		}
		return true;
	}


	public static BufferedImage	subsampleImageFile(File file, ImageResamplingDims xy, IIOMetadata metadata) throws IOException {//BufferedImage
		ImageInputStream inputStream = ImageIO.createImageInputStream(file);
		IIOReadProgressListener progressListener = null;
		return subsampleImage(inputStream, xy, progressListener, metadata);
	}
	
	// https://stackoverflow.com/questions/3294388/make-a-bufferedimage-use-less-ram, altered mildly
	public static BufferedImage subsampleImage(ImageInputStream inputStream, ImageResamplingDims xy,
			IIOReadProgressListener progressListener, IIOMetadata metadata) throws IOException {

		BufferedImage resampledImage = null;

		Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);

		if (!readers.hasNext()) {
			throw new IOException("No reader available for supplied image stream.");
		}

		ImageReader reader = readers.next();

		ImageReadParam imageReaderParams = reader.getDefaultReadParam();
		reader.setInput(inputStream);

		// # of subsampoled pixels in scanline = truncate[(width - subsamplingXOffset + sourceXSubsampling - 1) / sourceXSubsampling].
		imageReaderParams.setSourceSubsampling(xy.samplingEdge, xy.samplingEdge, 0, 0);

		reader.addIIOReadProgressListener(progressListener);

		IIOMetadata m = reader.getImageMetadata(0);
		TIFFDirectory m1 = TIFFDirectory.createFromMetadata(m);
		System.out.printf("We got the image metadata: %s", m1);

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

		WritableRaster smallRawData = smallImg.getRaster(); //GROK: Versus writableRaster, what about readableRaster?
		ColorModel smallCm = smallImg.getColorModel();

		WritableRaster bigRawData = smallCm.createCompatibleWritableRaster(goalDimensions.width, goalDimensions.height);

		for (int x=0; x < goalDimensions.width; x++) {
			for (int y = 0; y < goalDimensions.height; y++) {
				int pixelVal = smallRawData.getSample(x / upSampling, y / upSampling,0);
				bigRawData.setSample(x, y, 0, pixelVal);
			}
		}

		BufferedImage bigImg = new BufferedImage(smallCm, bigRawData, false, null);
		return bigImg;
	}

	public static void save2Bit(BufferedImage in, File outFile) throws IOException {
		//https://stackoverflow.com/a/12672467/13773745
		int w = in.getWidth(), h = in.getHeight();
		byte[] v = new byte[1 << 2];
		for (int i = 0; i < v.length; ++i)
			v[i] = (byte)(i*17);
		ColorModel cm = new IndexColorModel(2, v.length, v, v, v);
		WritableRaster wr = cm.createCompatibleWritableRaster(w, h);
		BufferedImage out = new BufferedImage(cm, wr, false, null);
		Graphics2D g = out.createGraphics();
		g.drawImage(in, 0, 0, null);
		g.dispose();
		ImageIO.write(out, "png", outFile );
	}

	public static BufferedImage deepCopy(BufferedImage bi) {
		ColorModel cm = bi.getColorModel();
		boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		WritableRaster raster = bi.copyData(null);
		return new BufferedImage(cm, raster, isAlphaPremultiplied, null).getSubimage(0, 0, bi.getWidth(), bi.getHeight());
	}

}

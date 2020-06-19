package org.djf.mlpaint;

import org.djf.util.SwingUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;

/** A single object to keep track of the different resampling info
 * 
 * Compares so total fuel is minimized.
 */
public class ImageResamplingDims {
	//Dimensions of big image
	public final int bigx, bigy;
	public final Dimension bigDim;
	//Dimensions of small image
	public final int smallx, smally;
	public final Dimension smallDim;
	//Sampling pixels on a side, thus 2 means 1/4 the size of image
	public final int samplingEdge;

	public ImageResamplingDims(File file, int maxPixels) throws IOException {
		Dimension bigDim = SwingUtil.readImageDimensions(file);
		this.bigx = bigDim.width;
		this.bigy = bigDim.height;
		this.bigDim = bigDim;

		this.samplingEdge = getSamplingEdgeSize(maxPixels);

		//https://docs.oracle.com/javase/7/docs/api/javax/imageio/IIOParam.html
		//truncate[(width  + sourceXSubsampling - 1) / sourceXSubsampling].
		this.smallx =  ( this.bigx + samplingEdge -1 ) / samplingEdge;
		this.smally =  ( this.bigy + samplingEdge -1 ) / samplingEdge;
		this.smallDim = new Dimension(this.smallx, this.smally);

	}

	private int getSamplingEdgeSize(int maxPixels) {
		int imgPixels = this.bigx*this.bigy; //Static vs not static?
		int edge = 1;
		while (imgPixels > maxPixels) {
			edge += 1;
			imgPixels /= (edge*edge);
		}
		return edge;
	}

}

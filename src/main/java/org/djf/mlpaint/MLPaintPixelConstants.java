package org.djf.mlpaint;

import org.djf.util.SwingUtil;

import java.awt.*;

import static org.djf.util.SwingUtil.getTexturePaint;

/** A single point for the priority queue in the Dijkstra algorithm.
 * 
 * Compares so total fuel is minimized.
 */
public class MLPaintPixelConstants {

	public boolean isBigger;

	public static final Color[] FRESH_COLORS = {SwingUtil.TRANSPARENT, Color.blue, Color.RED, SwingUtil.ALPHABLUE};
	public static final Color[] BACKDROP_COLORS = {SwingUtil.TRANSPARENT, SwingUtil.SKYBLUE, SwingUtil.SKYRED, Color.YELLOW};
	public static TexturePaint freshPosTexture;
	public static TexturePaint freshNegTexture;

	public static final Color[] GRAY16 = new Color[16];

	public float tripleFreshBound;
	public float singleFreshBound;

	///Plotting the hash patterns
	public int diagonalSize;
	public int bigDiagSize;

	//Make negatives black diagonals
	public int sRad;
	public int sRadPlus;
	public int bRad;
	public int bRadPlus;

	public int dijkstraStep; // 3 for squares of 9 //This could be optimized so that if we zoom in   SIZE


	public MLPaintPixelConstants() {
		bigger();

		this.GRAY16[0] = SwingUtil.TRANSPARENT;
		for (int i = 1; i < GRAY16.length; i++) {
			GRAY16[i] = SwingUtil.BACKGROUND_GRAY;
		}
	}

	public void resize() {
		if (this.isBigger) {
			smaller();
		} else {
			bigger();
		}
	}

	public int getRepsIncrement(int freshPaintNumPositives, int queueBoundsIdx, int INTERIOR_STEPS) {
		int repsIncrement = (int) (freshPaintNumPositives * Math.pow(1.01, queueBoundsIdx - 1 - INTERIOR_STEPS) * 0.09 / (dijkstraStep*dijkstraStep));
		return repsIncrement;
	}

	public void bigger() {
		this.isBigger = true;
		this.dijkstraStep = 3;
		//Hashing constants for fresh paint
		//Bounds
		this.tripleFreshBound = 9.0f;
		this.singleFreshBound = 5.0f;

		//Hashing constants for labels
		this.diagonalSize = 9;
		this.bigDiagSize = 160;

		this.sRad = 0;
		this.sRadPlus = 2;
		this.bRad = 5;
		this.bRadPlus = 9;

		freshPosTexture = getTexturePaint( FRESH_COLORS[1], BACKDROP_COLORS[1], 100, true); //1 is positive, 2 negative
		freshNegTexture = getTexturePaint( FRESH_COLORS[2], BACKDROP_COLORS[2], 100, true);
	}

	public void smaller() {
		this.isBigger = false;
		this.dijkstraStep = 1;
		//Hashing constants for fresh paint
		//Bounds
		this.tripleFreshBound = 3.0f;
		this.singleFreshBound = 1.0f;

		//Hashing constants for labels
		this.diagonalSize = 9;
		this.bigDiagSize = 50;

		this.sRad = 0;
		this.sRadPlus = 1;
		this.bRad = 3;
		this.bRadPlus = 5;

		freshPosTexture = getTexturePaint( FRESH_COLORS[1], BACKDROP_COLORS[1], 20, false); //1 is positive, 2 negative
		freshNegTexture = getTexturePaint( FRESH_COLORS[2], BACKDROP_COLORS[2], 20, false);
	}

}

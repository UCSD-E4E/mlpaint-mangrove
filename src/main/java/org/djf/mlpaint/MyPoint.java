package org.djf.mlpaint;

/** A single point for the priority queue in the Dijkstra algorithm.
 * 
 * Compares so total fuel is minimized.
 */
public class MyPoint implements Comparable<MyPoint> {

	/** fuel cost required to get here */
	final double fuelCost;
	/** index position */
	final int x, y;
	
	public MyPoint(double fuelCost, int x, int y) {
		super();
		this.fuelCost = fuelCost;
		this.x = x;
		this.y = y;
	}

	@Override
	public int compareTo(MyPoint competitor) {
		return Double.compare(fuelCost, competitor.fuelCost);
	}
}

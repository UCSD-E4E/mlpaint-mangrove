package org.djf.mlpaint;

/** A single point in the Dijkstra algorithm.
 * 
 * Compares so total fuel is minimized.
 */
public class MyPoint implements Comparable<MyPoint> {

	/** fuel required to get here */
	final double fuel;
	/** index position */
	final int x, y;
	
	public MyPoint(double fuel, int x, int y) {
		super();
		this.fuel = fuel;
		this.x = x;
		this.y = y;
	}

	@Override
	public int compareTo(MyPoint competitor) {
		return Double.compare(fuel, competitor.fuel);
	}
}

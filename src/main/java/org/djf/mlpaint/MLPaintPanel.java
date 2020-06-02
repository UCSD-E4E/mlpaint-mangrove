package org.djf.mlpaint;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;

import javax.swing.JPanel;

import smile.classification.Classifier;


/** Magic Label Paint panel.
 * 
 */
public class MLPaintPanel extends JPanel 
	implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
	
	// label pixel value codes
	public static final int UNLABELED = 0;
	public static final int POSITIVE = 1;// mangrove
	public static final int NEGATIVE = 2;// not mangrove
	public static final int FRESH = 11;// fresh paint


	/** current (huge) image */
	final BufferedImage image;

	/** current (huge) image labels: 0=UNLABELED, 1=mangrove, 2=not mangrove, ... 11=FRESH mangrove, 12=FRESH non-mangrove */
	final BufferedImage labels;
	
	/** fresh paint = 1 where the user has freshly painted */
	BufferedImage freshPaint;

	/** input layers:  filename & image.  Does not contain master image or labels layers. */
	final LinkedHashMap<String, BufferedImage> extraLayers;
	
	AffineTransform view = new AffineTransform();
	
	
	Classifier<double[]> classifier;
	

	public MLPaintPanel(BufferedImage masterImage2, BufferedImage labels2,
			LinkedHashMap<String, BufferedImage> extraLayers2) {
		image = masterImage2;
		labels = labels2;
		extraLayers = extraLayers2;
		freshPaint = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		addKeyListener(this);
	}

	
	
	@Override
	public void keyPressed(KeyEvent e) {
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}

	@Override
	public void keyTyped(KeyEvent e) {
		System.out.printf("%s\n", e.toString());
	}

	
	@Override
	public void mouseClicked(MouseEvent e) {
		System.out.printf("%s\n", e.toString());
	}
	
	
	@Override
	public void mouseDragged(MouseEvent e) {
		System.out.printf("%s\n", e.toString());
	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
	}

	@Override
	public void mouseReleased(MouseEvent e) {
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		System.out.printf("%s\n", e.toString());
	}

	

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.drawImage(image, view, null);
		g2.dispose();
	}

}

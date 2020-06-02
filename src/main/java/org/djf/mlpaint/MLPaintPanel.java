package org.djf.mlpaint;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.stream.IntStream;

import javax.swing.JPanel;

import org.djf.util.SwingApp;
import org.djf.util.SwingUtil;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;

import smile.classification.Classifier;
import smile.classification.LDA;
import smile.classification.LogisticRegression;
import smile.classification.SoftClassifier;


/** Magic Label Paint panel.
 * 
 */
public class MLPaintPanel extends JPanel 
	implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
	
	// *label image* pixel index codes
	public static final int UNLABELED = 0;
	public static final int POSITIVE = 1;
	public static final int NEGATIVE = 2;

	// *freshPaint* pixel index codes (it's just a binary image):  
	public static final int FRESHPAINT = 1;

	
	
	/** current RGB image (possibly huge) */
	final BufferedImage image;
	final int width;
	final int height;

	/** matching image labels: 0=UNLABELED, 1=POSITIVE, 2=NEGATIVE, ... */
	final BufferedImage labels;
	
	/** binary image.  pixel index = FRESHPAINT(1) where the user has freshly painted. 
	 * Colors for display are transparent & transparent-green, currently.
	 */
	BufferedImage freshPaint;

	/** pixel size of the brush */
	double brushRadius = 10.0;

	/** Distance to each pixel from fresh paint, initially +infinity.  Not computed for 100% of image (huge?). */
	double[][] distances;
	

	/** input layers:  filename & image.  Does not contain master image or labels layers. */
	final LinkedHashMap<String, BufferedImage> extraLayers;
	
	/** map from screen frame of reference down to image frame of reference, so we can pan & zoom */
	AffineTransform view = new AffineTransform();
	
	/** previous mouse event when drawing/dragging */
	private MouseEvent mousePrev;
	
	SoftClassifier<double[]> classifier;
	
	/** classifier output image, grayscale */
	BufferedImage classifierOutput;
	

	public MLPaintPanel(BufferedImage masterImage, BufferedImage labels2,
			LinkedHashMap<String, BufferedImage> extraLayers2) {
		image = masterImage;
		labels = labels2;
		extraLayers = extraLayers2;
		width = image.getWidth();
		height = image.getHeight();
		freshPaint = SwingUtil.newBinaryImage(width, height, SwingUtil.TRANSPARENT, SwingUtil.ALPHAGREEN);// 1 bit per pixel, 2 colors
		clearFreshPaint();
		distances = new double[width][height];
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		addKeyListener(this);
		setOpaque(true);
		setFocusable(true);// allow key events
	}

	public void resetView() {
		view = new AffineTransform();
		repaint();
	}

	public void clearFreshPaint() {
		WritableRaster rawdata = freshPaint.getRaster();
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				rawdata.setSample(x, y, 0, 0);// 0 or 1
			}
		}
		repaint();
	}
	
	

	///////   Java Swing GUI code / callbacks

	
	@Override
	public void mouseClicked(MouseEvent e) {
		System.out.printf("MouseClick %s\n", e.toString());
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		System.out.printf("MousePress %s\n", e.toString());
		mousePrev = e;
		if (e.isControlDown()) {// add fresh paint
			brushFreshPaint(e);
		}
		e.consume();
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		System.out.printf("MouseDrag %s\n", e.toString());
		if (e.isControlDown()) {
			// pan the image
			double dx = e.getPoint().getX() - mousePrev.getPoint().getX();
			double dy = e.getPoint().getY() - mousePrev.getPoint().getY();
			view.preConcatenate(AffineTransform.getTranslateInstance(dx, dy));
			
		} else if (e.isShiftDown()) {
			// whatever you want
			
		} else {// default
			// put paint down
			brushFreshPaint(e);
		}
		mousePrev = e;
		repaint();
		e.consume();
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		System.out.printf("MouseRelease %s\n", e.toString());
		// if it was painting, then extract the training set
		if (!e.isControlDown() && !e.isShiftDown() && !e.isAltDown()) {
			trainClassifier();
			runDijkstra();
			e.consume();
		}
		mousePrev = null;
	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// System.out.printf("MouseEntered %s\n", e.toString());
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// System.out.printf("MouseExited %s\n", e.toString());
	}

	@Override
	public void keyPressed(KeyEvent e) {
		 // System.out.printf("KeyPressed %s\n", e);// repeats if held, for function keys
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// System.out.printf("KeyReleased %s\n", e);// repeats if held
	}

	@Override
	public void keyTyped(KeyEvent e) {
		System.out.printf("KeyTyped %s\n", e);// repeats if held, for normal typing, not function keys
		char ch = e.getKeyChar();
		if (Character.isDigit(ch)) {
			brushRadius = 5 * (ch - '0' + 1);// somehow tranlate it
		}
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		System.out.printf("%s\n", e.toString());
		Point p = e.getPoint();
		double x = p.getX();
		double y = p.getY();
		double d = e.getPreciseWheelRotation();
		double scale = Math.pow(1.05, d);// scale +/- 5% per step, exponential
		
		if (e.isControlDown()) {
			// zooms at mouse point
			view.preConcatenate(AffineTransform.getTranslateInstance(-x, -y));
			view.preConcatenate(AffineTransform.getScaleInstance(scale, scale));
			view.preConcatenate(AffineTransform.getTranslateInstance(x, y));
			
		} else if (e.isShiftDown()) {// shift adjusts brush radius
			brushRadius *= scale;
			brushRadius = Math.max(0.5, brushRadius);// never < 1 pixel
			System.out.printf("brushRadius := %s\n", brushRadius);
			
		} else if (e.isAltDown()) {// adjusts nose size or adjusts fill agressiveness
			
			
		} else {// default: 
			
		}
		
		repaint();
	}

	
	private void brushFreshPaint(MouseEvent e) {
		Graphics2D g = (Graphics2D) freshPaint.getGraphics();
		Ellipse2D brush = new Ellipse2D.Double(
				e.getX() - brushRadius, 
				e.getY() - brushRadius, 
				2*brushRadius, 2*brushRadius);
		g.setPaint(Color.WHITE);
		g.fill(brush);
		g.dispose();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		System.out.printf("paint component %,d\n", System.currentTimeMillis());
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setColor(getBackground());
		g2.fillRect(0, 0, getWidth(), getHeight());// backgroun
		g2.transform(view);
		g2.drawImage(image, 0, 0, null);
		g2.drawImage(freshPaint, 0, 0, null);// mostly transparent atop
		if (classifierOutput != null) {
			g2.drawImage(classifierOutput, 0, 0, null);
		}
		g2.dispose();
	}
	
	
	
	
	///////   Technology-specific code, not just Java Swing GUI
	
	
	/** Extract training set .  also, initialize the distances[][] matrix */
	public void trainClassifier() {
		WritableRaster rawdata = freshPaint.getRaster();// for direct access to the bitmap index, not its mapped color
		
		// extract positive examples from each fresh paint pixel that is 1
		long t = System.currentTimeMillis();
		List<double[]> positives = Lists.newArrayList();// feature vectors
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				int index = rawdata.getSample(x, y, 0);// returns 0 or 1
				if (index == FRESHPAINT) {// And it's a positive training example
					double[] fv = getFeatureVector(x,y);
					positives.add(fv);
				}
			}
		}
		int npos = positives.size();
		t = SwingApp.reportTime(t, "extracted %s positives from %s x %s", npos, width, height);
		if (npos < 30) {
			return;
		}
		
		// get some random negatives against the positives
		//TODO: smarter testing / picking
		int nneg = 2 * npos;// try 2x or 3x as many negatives
		List<double[]> negatives = Lists.newArrayList();// feature vectors
		Random rand = new Random();
		while (negatives.size() < nneg) {
			int x = rand.nextInt(width);
			int y = rand.nextInt(height);
			int index = rawdata.getSample(x, y, 0);// returns 0 or 1
			if (index != FRESHPAINT) {
				negatives.add(getFeatureVector(x, y));
			}
		}
		Preconditions.checkArgument(nneg == negatives.size());

		// Convert dataset into SMILE format
		int nFeatures = positives.get(0).length;
		int nall = npos + nneg;
		double[][] fvs = Streams.concat(positives.stream(), negatives.stream())
				.toArray(double[][]::new);
		int[] ylabels = IntStream.range(0, nall)
				.map(i -> i < npos ? 1 : 0)// positives first
				.toArray();
		
		// train the SVM or whatever model
		t = SwingApp.reportTime(t, "converted data to train classifier: %d rows x %d features, %.1f%% positive", 
				nall, nFeatures, 100.0 * npos / nall);
		int maxIters = 500;
		double C = 1.0;//TODO
		double lambda = 0.1;// 
		double tolerance = 1e-5;
		classifier = LogisticRegression.fit(fvs, ylabels, lambda , tolerance, maxIters);
		// classifier = SVM.fit(fvs, ylabels, C, tolerance);
		// classifier = LDA.fit(fvs, ylabels, new double[] {0.5, 0.5}, tolerance);
		t = SwingApp.reportTime(t, "trained classifier: %d rows x %d features, %.1f%% positive", 
				nall, nFeatures, 100.0 * npos / nall);
	}
	
	private double[] getFeatureVector(int x, int y) {
		Color clr = new Color(image.getRGB(x, y));
        int red =   clr.getRed();
        int green = clr.getGreen();
        int blue =   clr.getBlue();
        // TODO include HSV & other image layers, possibly also computed
		double[] rr = {red/255.0, green/255.0, blue/255.0};
		return rr;
	}

	private void runDijkstra() {
		PriorityQueue<MyPoint> queue = new PriorityQueue<>(1000);// lowest fuel cost first
		initDistances(queue);
		// TODO Auto-generated method stub
		
	}

	/** initialize Dijkstra distance grid & fill the queue with fresh paint locations @ fuel cost 0 */
	private void initDistances(PriorityQueue<MyPoint> queue) {
		WritableRaster rawdata = freshPaint.getRaster();
		for (int x = 0; x < width; x++) {
			Arrays.fill(distances[x], Double.POSITIVE_INFINITY);
			for (int y = 0; y < height; y++) {
				int index = rawdata.getSample(x, y, 0);// 0 or 1
				if (index == FRESHPAINT) {
					distances[x][y] = 0;
					queue.add(new MyPoint(0.0, x, y));
				}
			}
		}
	}

	private BufferedImage runClassifier() {
		Preconditions.checkNotNull(classifier, "Must put positive paint down first");
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);// grayscale from 0.0 to 1.0 (aka 255)
		WritableRaster raster = out.getRaster();
		IntStream.range(0, width).parallel().forEach(x -> {// run in parallle for speed
			double[] outputs = new double[2];
			for (int y = 0; y < height; y++) {
				double[] fv = getFeatureVector(x, y);
				classifier.predict(fv, outputs);
				double score0 = outputs[0];// probability in [0,1] of class 0, negative
				double score1 = outputs[1];// probability in [0,1] of class 1, positive
				int index = (int) (255 * score0);
				raster.setSample(x, y, 0, index);
			}
		});
		return out;
	}

	public void setShowClassifierOutput(boolean show) {
		if (show && classifier != null) {
			classifierOutput = runClassifier();
		} else {
			classifierOutput = null;
		}
		repaint();
		revalidate();
	}

}

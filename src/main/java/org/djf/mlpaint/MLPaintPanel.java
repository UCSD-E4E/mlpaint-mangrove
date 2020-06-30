package org.djf.mlpaint;

import java.awt.*;
import java.awt.AlphaComposite;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.JComponent;

import com.google.common.math.Stats;
import com.google.common.math.StatsAccumulator;
import javafx.beans.property.SimpleBooleanProperty;
import org.djf.util.SwingUtil;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;

import org.djf.util.Utils;
import smile.classification.LogisticRegression;
import smile.classification.SoftClassifier;

import static org.djf.util.SwingApp.reportTime;


/** Magic Label Paint panel.
 *   This panel rests within the MLPaintApp.
 */
public class MLPaintPanel extends JComponent
	implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

	// *label image* pixel index codes  (future: up to 255 if we need many different label values)
	public static final int UNLABELED = 255;//0;//255; //Question: Should we put an alpha mask on the permanent labels?
	public static final int POSITIVE = 100;//3;//100; // Clear NO_DATA, Clear UNLABELED_, grays for positive and negative.
	public static final int NEGATIVE = 0;//2;// 0;
	public static final int NO_DATA = 254;//1;//254;
	public static final Color[] LABEL_COLORS = {SwingUtil.TRANSPARENT, SwingUtil.TRANSPARENT,SwingUtil.ALPHABLACK, SwingUtil.ALPHAGRAY};
	// possibly up to 16 different labels, as needed, with more colors

	// *freshPaint* pixel index codes (0 to 3 maximum)
	private static final int FRESH_UNLABELED = 0; //Index zero for unlabeled is special: it initializes to zero.
	private static final int FRESH_POS = 1;
	private static final int FRESH_NEG = 2;
	private static final Color[] FRESH_COLORS = {SwingUtil.TRANSPARENT, Color.blue, Color.RED, SwingUtil.ALPHABLUE};
	private static final Color[] BACKDROP_COLORS = {SwingUtil.TRANSPARENT, SwingUtil.SKYBLUE, SwingUtil.SKYRED, Color.YELLOW};

	private static final double EDGE_DISTANCE_FRESH_POS = 0.0001;
	private static final double QUEUE_GROWTH = 0.2/9.0;

	/** current RGB image (possibly huge) in "world coordinates" */
	public BufferedImage image;
	/** width and height of image, extraLayers, labels, freshPaint, etc.  NOT the size of this Swing component on the screen, which may be smaller typically. */
	int width, height;
	int JPanelWidth, JPanelHeight;

	/** extra image layers:  filename & image.  Does not contain master image or labels layers.
	 * Might have computed layers someday.
	 */
	public LinkedHashMap<String, BufferedImage> extraLayers;

	/** matching image labels, like this: 0=UNLABELED, 1=POSITIVE, 2=NEGATIVE, ... */
	public BufferedImage labels;

	/** binary image mask.  pixel index = FRESH_POS where the user has freshly painted positive.
	 * Colors for display are transparent & transparent-green, currently.
	 */
	private BufferedImage freshPaint;
	private Integer freshPaintNumPositives = null;//GROK: run by classifier
	private Area freshPaintArea = new Area();
	private Area antiPaintArea = new Area();
	private List<Point2D> dijkstraPossibleSeeds = Lists.newArrayListWithCapacity(1000);

	/** pixel size of the brush.  */
	public double brushRadius = radiusFromChDigit('1');

	private SoftClassifier<double[]> classifier;
	private final int maxPositives = 4000;
	private final int maxNegatives = 8000;

	/** classifier output image, grayscale */
	public BufferedImage classifierOutput; //GROK: Why was this made private?

	/** Distance to each pixel from fresh paint-derived seed points, initially +infinity.
	 * Allocated for (width x height) of image, but maybe not computed for 100% of image to reduce computation.
	 */
	private float[][] distances;
	private float[][] spareDistances = null; //= new double[width][height];
	public double scorePower = 3.0;
	public ArrayList<PriorityQueue<MyPoint>> listQueues = null;
	public int queueBoundsIdx = -10;
	private int dijkstraStep = 3; // For squares of 9 //This could be optimized so that if we zoom in
	//MAYDO: Optimize this to go even bigger when we're on huge scale and shrink to 1 when we are zoomed in.

	/** suggested area to transfer to labels.  TBD. just a binary mask?  or does it have a few levels?  Or what?? */
	public BufferedImage proposed;


	/** map from screen frame of reference down to image "world coordinates" frame of reference, so we can pan & zoom */
	private AffineTransform view = new AffineTransform();

	/** previous mouse event when drawing/dragging */
	private MouseEvent mousePrev;

	/** clients can toggle this property and we automatically repaint() */
	public final SimpleBooleanProperty showClassifier = new SimpleBooleanProperty();
	private Point2D cursor;


	public MLPaintPanel() {
		super();
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		addKeyListener(this);// or else https://docs.oracle.com/javase/tutorial/uiswing/misc/keybinding.html
		setOpaque(true);
		setFocusable(true);// allow key events
		showClassifier.addListener(event -> repaint());
	}

	public void resetData(BufferedImage masterImage, BufferedImage labels2,
			LinkedHashMap<String, BufferedImage> extraLayers2) {
		image = masterImage;
		width = image.getWidth();
		height = image.getHeight();

//		labels = SwingUtil.newBinaryImage(width, height, LABEL_COLORS);//Nicely initializes to 0.
//		System.out.println("Just created a colormap style binary image for labels.");
//		// if an existing labels loaded, copy it on, profiting from the SwingUtil colormap.
//		if (labels2 != null) {
//			//SwingUtil.addImage(labels, labels2);
//			SwingUtil.copyImage(labels, labels2);
//			System.out.println("We copied in a labels object.");
//		}
		labels = labels2;
		if (labels == null) {
			labels = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
			SwingUtil.fillImage(labels, UNLABELED);
			System.out.println("We created a blank labels object.");
		}

		extraLayers = extraLayers2;
		Preconditions.checkArgument(width  == labels.getWidth() && height == labels.getHeight(),
				"The labels size does not match the image size.");
		extraLayers.values().forEach(im -> {
			Preconditions.checkArgument(width  == im.getWidth() && height == im.getHeight(),
					"The extra layer size does not match the image size.");
		});
		initializeFreshPaint();
		distances = new float[width][height];

		setPreferredSize(new Dimension(width, height));
		resetView();
	}

	public void resetView() {
		view = new AffineTransform();
		double scale =  925.0 / width; //MAYDO: Find the true JPanel size and use that. This is arbitrary, maybe.
		view.preConcatenate(AffineTransform.getScaleInstance(scale, scale));
		showClassifier.set(false);
		repaint();
	}

	public void initializeFreshPaint() {
		long t = System.currentTimeMillis();
		freshPaint = SwingUtil.newBinaryImage(width, height, FRESH_COLORS);// 2 bits per pixel
		t = reportTime(t, "We have made a new freshpaint image.");
		listQueues = null;
		queueBoundsIdx = 19;
		freshPaintArea = new Area();
		antiPaintArea = new Area();
		dijkstraPossibleSeeds = Lists.newArrayListWithCapacity(1000);

		freshPaintNumPositives = null; //MAYDO: Make sure the user can't label with zero while no fresh paint.
		classifier = null;
		classifierOutput = null;

		double areaProportion = (JPanelWidth*JPanelHeight / (double) (width*height));
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
		if (e.isControlDown()) {
			// start dragging to pan the image
		} else {
			// add fresh paint
			brushFreshPaint(e, e.isShiftDown());
		}
		e.consume();
		grabFocus();// keyboard focus, so you can type digits
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		//System.out.printf("MouseDrag %s\n", e.toString());
		if (e.isControlDown()) {
			// pan the image
			System.out.println("Dragging.");
			double dx = e.getPoint().getX() - mousePrev.getPoint().getX();
			double dy = e.getPoint().getY() - mousePrev.getPoint().getY();
			view.preConcatenate(AffineTransform.getTranslateInstance(dx, dy));

		} else {// default
			// put paint down
			brushFreshPaint(e, e.isShiftDown());
		}
		mousePrev = e;
		e.consume();
		repaint();
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		System.out.printf("MouseRelease %s\n", e.toString());
		// if it was painting, then extract the training set
		if (!e.isControlDown() && !e.isAltDown()) {
			//MAYDO: run this in background thread if too slow
			trainClassifier();
			//classifierOutput = runClassifier();
			initDijkstra(); //MAYDO: rename makeSuggestions---dijkstra is just one way to do that
		}
		mousePrev = null;
		repaint();
		e.consume();
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		//cursor = e.gePoint();
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// System.out.printf("MouseEntered %s\n", e.toString());
		setCursorToMarkerAtRightSize();
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// System.out.printf("MouseExited %s\n", e.toString());
		setMarkerToCursor();
	}

	public void setCursorToMarkerAtRightSize() {
		setCursor(
				new Cursor(Cursor.CROSSHAIR_CURSOR));

	}

	public void setMarkerToCursor() {
		setCursor(
				new Cursor(Cursor.DEFAULT_CURSOR));
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
		actOnChar(ch);
	}

	public void actOnChar(char ch) {
		if (Character.isDigit(ch)) {
			brushRadius = radiusFromChDigit(ch);// somehow translate it
			// show the user too
			System.out.printf("paintbrush radius: %s\n", brushRadius);
		} else if (ch == ' '){ //MAYDO: Test if in keys, then send the appropriate digit from a dict.
			// That way we can add labels interactively in the GUI by changing the keys variable.
			writeSuggestionToLabels(NEGATIVE);
		} else if (ch == 'm'){
			writeSuggestionToLabels(POSITIVE);
		}
	}



	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		System.out.printf("%s\n", e.toString());
		Point p = e.getPoint();
		double x = p.getX();
		double y = p.getY();
		double d = e.getPreciseWheelRotation();
		d = -d;
		double scale = Math.pow(1.05, d);// scale +/- 5% per step, exponential
		
		if (e.isControlDown()) {
			// zooms at mouse point
			view.preConcatenate(AffineTransform.getTranslateInstance(-x, -y));
			view.preConcatenate(AffineTransform.getScaleInstance(scale, scale));
			view.preConcatenate(AffineTransform.getTranslateInstance(x, y));
			
		} else if (e.isShiftDown()) {// shift adjusts brush radius
			multiplyBrushRadius(scale);

		} else if (e.isAltDown()) {// adjusts nose size or adjusts fill agressiveness
			
			
		} else {// default: 
			
		}
		
		repaint();
	}


	public void multiplyBrushRadius(double scale) {
		brushRadius *= scale;
		brushRadius = Math.max(0.5, brushRadius);// never < 1 pixel
		brushRadius = Math.min(brushRadius, radiusFromChDigit('9'));
		System.out.printf("brushRadius := %s\n", brushRadius);
	}

	public int radiusFromChDigit(char ch) {
		return 5 * (ch - '0' + 1);
	}

	private void brushFreshPaint(MouseEvent e, boolean isNegative) {
		long t = System.currentTimeMillis();
		int index = isNegative ? FRESH_NEG : FRESH_POS;
		Point2D mousePoint = new Point2D.Double((double) e.getX(), (double) e.getY());
		Ellipse2D brush = new Ellipse2D.Double(
				e.getX() - brushRadius, 
				e.getY() - brushRadius, 
				2*brushRadius, 2*brushRadius);
		IndexColorModel cm = (IndexColorModel) freshPaint.getColorModel();
		Color color = new Color(cm.getRGB(index));
		Graphics2D g = (Graphics2D) freshPaint.getGraphics();
		g.setColor(color);
		try {
			AffineTransform inverse = view.createInverse();
			if (!isNegative) {
				dijkstraPossibleSeeds.add(inverse.transform(mousePoint, null));
			}
			g.transform(inverse);// without this, we're painting WRT screen space, even though the image is zoomed/panned
			g.fill(brush);
			g.dispose();
			t = reportTime(t, "Painted another bit of fresh paint onto the world space."); //0 or 1 ms, even for huge.

			Area brushArea = new Area(brush);
			brushArea = brushArea.createTransformedArea(inverse);
			if (isNegative) {
				antiPaintArea.add(brushArea);
				freshPaintArea.subtract(brushArea);
			} else {
				freshPaintArea.add(brushArea);
				antiPaintArea.subtract(brushArea);
			}
			t = reportTime(t, "Added to the area of fresh or anti paint.");
		} catch (NoninvertibleTransformException e1) {// won't happen
			e1.printStackTrace();
		}


	}

	/**Paints the image, freshpaint, and possibly classifier output, to the screen.
	 *
	 */
	@Override
	protected void paintComponent(Graphics g) {

		long t = System.currentTimeMillis();
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setColor(getBackground());
		System.out.printf("The JPanel is width x height, %d x %d.",getWidth(), getHeight());
		JPanelWidth = getWidth();
		JPanelHeight = getHeight();
		g2.fillRect(0, 0, getWidth(), getHeight());// background may have already been filled in
		if (image == null) {
			g2.dispose();
			return;
		}
		g2.transform(view);
		t = reportTime(t, "Initialized the g2 graphic for repainting.");

		if (showClassifier.get()) {                           // MAYDO: instead have a transparency slider??  That'd be cool.
			g2.drawImage(classifierOutput, 0, 0, null);
			t = reportTime(t, "Classifier output drawn.");
		} else {
			g2.drawImage(image, 0, 0, null);
			t = reportTime(t, "Image drawn.");
		}

		if (listQueues != null && queueBoundsIdx >= 0) {
			g2.setColor(FRESH_COLORS[FRESH_POS] );
			for (MyPoint edgePoint: listQueues.get(queueBoundsIdx)) {
				g2.drawRect(edgePoint.x, edgePoint.y, 3, 3);
			}
			g2.setColor(BACKDROP_COLORS[FRESH_POS]);
			for (MyPoint edgePoint: listQueues.get(queueBoundsIdx)) {
				//g2.drawRect(edgePoint.x,edgePoint.y,2,2);
				g2.fillRect(edgePoint.x, edgePoint.y, 3, 3);
			}
			t = reportTime(t, "Dijkstra suggestion outline drawn from priorityQueue.");
		}

		// add frame to see limit, even if indistinguishable from background
		g2.setColor(Color.BLACK);
		g2.drawRect(0, 0, width, height);
		t = reportTime(t, "Pretty frame drawn.");

		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
		g2.drawImage(labels, 0, 0, null);
		t = reportTime(t, "Labels drawn via affine transform and alpha-painted area..");

		//Draw the fresh paint.
		IndexColorModel fresh_cm = (IndexColorModel) freshPaint.getColorModel();
		boolean asImage = false;
		if (asImage) {
			g2.setColor(new Color(fresh_cm.getRGB(FRESH_POS)));
			g2.fill(freshPaintArea);
			g2.setColor(new Color(fresh_cm.getRGB(FRESH_NEG)));
			g2.fill(antiPaintArea);
			//g2.drawImage(freshPaint, 0, 0, null);// mostly transparent atop
			t = reportTime(t, "Fresh paint drawn.");
		} else {
			crossHatchArea(g2, freshPaintArea,FRESH_COLORS[FRESH_POS], BACKDROP_COLORS[FRESH_POS]);
			crossHatchArea(g2, antiPaintArea, FRESH_COLORS[FRESH_NEG], BACKDROP_COLORS[FRESH_NEG]);
		}

		g2.dispose();
	}

	private void crossHatchArea(Graphics2D g2, Shape thisArea, Color foregroundColor, Color backdropColor) {
		int gsize = 100;
		Composite memComposite = g2.getComposite();
		Stroke memStroke = g2.getStroke();
		Paint memPaint = g2.getPaint();
		Color memColor = g2.getColor();

		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
		//Make a texture
		BufferedImage bufferedImage =
				new BufferedImage(gsize, gsize, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = bufferedImage.createGraphics();
		//Paint the edges of lines on the repeating patterns of the texture
		g2d.setColor(SwingUtil.TRANSPARENT);
		g2d.fillRect(0, 0, gsize, gsize);
		g2d.setColor(backdropColor);
		g2d.drawLine(0, 0+2, gsize-2, gsize); // \
		g2d.drawLine(0+2, 0, gsize, gsize-2); // \
		g2d.drawLine(-1, gsize+2, gsize, 1); // /
		g2d.drawLine(-1, gsize-2, gsize, -3); // /
		g2d.drawLine(0, 0+3, gsize-3, gsize); // \
		g2d.drawLine(0+3, 0, gsize, gsize-3); // \
		g2d.drawLine(-1, gsize+3, gsize, 2); // /
		g2d.drawLine(-1, gsize-3, gsize, -4); // /
		//Paint the center lines on the repeating pattern
		g2d.setColor(foregroundColor);
		g2d.drawLine(0, 0, gsize, gsize); // \
		g2d.drawLine(-1, gsize, gsize, -1); // /
		g2d.drawLine(0, 0+1, gsize-1, gsize); // \
		g2d.drawLine(0+1, 0, gsize, gsize-1); // \
		g2d.drawLine(-1, gsize+1, gsize, 0); // /
		g2d.drawLine(-1, gsize-1, gsize, -2); // /

		Stroke triplePixel = new BasicStroke(9.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND);
		Stroke singlePixel = new BasicStroke(5.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);

		// paint with the texturing brush
		Rectangle2D rect = new Rectangle2D.Double(0, 0, gsize, gsize);
		g2.setPaint(new TexturePaint(bufferedImage, rect));
		g2.fill(thisArea);


		g2.setStroke(triplePixel);
		g2.setColor(backdropColor);
		g2.draw(thisArea);

		g2.setStroke(singlePixel);
		g2.setColor(foregroundColor);
		g2.draw(thisArea);

		g2.setStroke(memStroke);
		g2.setComposite(memComposite);
		g2.setPaint(memPaint);
		g2.setColor(memColor);
	}


	///////   Technology-specific code, not just Java Swing GUI
	
	
	/** Extract training set and train. */
	public void trainClassifier() {
		long t = System.currentTimeMillis();
		WritableRaster rawdata = freshPaint.getRaster();// for direct access to the bitmap index, not its mapped color

		// extract positive examples from each fresh paint pixel that is FRESH_POS, negative if negative
		// getting [x,y] pairs
		Rectangle f = freshPaintArea.getBounds();
		List<int[]> positives = sampleFreshPosNeg(rawdata, f, FRESH_POS, maxPositives);
		int npos1 = positives.size();

		Rectangle g = antiPaintArea.getBounds();
		List<int[]> negatives = sampleFreshPosNeg(rawdata, g, FRESH_NEG, maxNegatives / 2);//MAYDO: Random vs. intentional negs.
		int nneg1 = negatives.size();

		t = reportTime(t, "Total time of obtaining xys for %,d positives and %,d negatives.",
				npos1, nneg1);

		if (npos1 < 30) {// not enough
			return;// silently return
		}
		//TODO: smarter testing / picking
		//thoughts: Area provides getBounds rectangle, we could choose within that or at least a few times that.
		// if not enough negatives, add additional negatives collected randomly
		Random rand = new Random();
		while (negatives.size() <= 2 * npos1 && negatives.size() <= maxNegatives) {// try 2x or 3x as many negatives
			int x = rand.nextInt(width);
			int y = rand.nextInt(height);
			int index = rawdata.getSample(x, y, 0);// returns 0 or 1
			if (index == FRESH_UNLABELED) {
				negatives.add(new int[]{x,y});
			}
		}
		nneg1 = negatives.size();
		t = reportTime(t, "We got indexes of enough negatives to complement the positives fully.");

		int npos = Math.min(maxPositives, npos1);
		int nneg = Math.min(maxNegatives, nneg1);
		int nall = npos + nneg;

		// Convert xys to feature vectors and convert dataset into SMILE format
		double[][] fvs = Streams.concat(positives.stream().limit( maxPositives),
										negatives.stream().limit( maxNegatives))
				.map(x -> getFeatureVector(x))
				.toArray(double[][]::new);
		int[] ylabels = IntStream.range(0, nall)
				.map(i -> i < npos ? 1 : 0)// positives first
				.toArray();

		int nFeatures = fvs[0].length;

		t = reportTime(t, "Converted all the xy to feature vectors, %,d pos and %,d neg.",npos,nneg);
		t = reportTime(t, "no op -- ready to train classifier: %d rows x %d features, %.1f%% positive",
				nall, nFeatures, 100.0 * npos / nall);

		// train the SVM or whatever model
		int maxIters = 500;
		double C = 1.0;//TODO
		double lambda = 0.1;// 
		double tolerance = 1e-5;
		classifier = LogisticRegression.fit(fvs, ylabels, lambda , tolerance, maxIters); // Maybe try positive-unlabeled training.
		// classifier = SVM.fit(fvs, ylabels, C, tolerance);
		// classifier = LDA.fit(fvs, ylabels, new double[] {0.5, 0.5}, tolerance);
		t = reportTime(t, "trained classifier: %d rows x %d features, %.1f%% positive",
				nall, nFeatures, 100.0 * npos / nall);
		
		if (classifierOutput != null) {
			classifierOutput = runClassifier();
		}
	}

	private List<int[]> sampleFreshPosNeg(WritableRaster rawdata, Rectangle f, int code, int hopedSampleSize) {
		long t = System.currentTimeMillis();          				//MAYDO: Why have a max capacity?Shouldn't we randomly sample if so?
		List<int[]> acquisitions = Lists.newArrayListWithCapacity(hopedSampleSize);

		if (f.isEmpty()) return acquisitions;

		int floory = f.y < 0 ? 0 : f.y ;
		int capy = f.y + f.height + 1 > height ? height : f.y + f.height + 1;
		int floorx = f.x < 0 ? 0 : f.x;
		int capx = f.x + f.width + 1 > width ? width : f.x + f.width + 1;
		if (capx == width || capy == height || floory == 0 || floorx == 0) {
			System.out.println("We flew to a world edge to constrain our feature vector collection.");
		}
		System.out.printf("Here is top x: %,d. \n",f.x);
		System.out.printf("Here is top y: %,d. \n",f.y);
		System.out.printf("Here is width: %,d. \n",f.width);
		System.out.printf("Here is height: %,d. \n",f.height);
		System.out.printf("Here is floory: %,d. \n",floory);
		System.out.printf("Here is capy: %,d. \n",capy);
		System.out.printf("Here is floorx: %,d. \n",floorx);
		System.out.printf("Here is capx: %,d. \n",capx);
		t = reportTime(t, "Setup using the Shape Area bounds, freshpaint, etc.");

		// 1) Get a good grid size: Area / gridLength^2 < maxPositives/10
		int gridLength = 1;
		int area = (capx - floorx)*(capy - floory);
		while (area / Math.pow(gridLength, 2) > hopedSampleSize/50) {
			gridLength *= 2;
		}
		// 2) Get a list of XY relational points to visit
		List<int[]> relations = Lists.newArrayListWithCapacity(gridLength*gridLength);
		relations.add(new int[]{0,0});
		for (int k = 0; Utils.intPow(4,k) < gridLength*gridLength; k++) {
			int j = Utils.intPow(4,k);
			int sep = gridLength / Utils.intPow(2,(k+1));
			extendRelations(relations, j, sep, sep);
			extendRelations(relations, j, sep,0);
			extendRelations(relations, j, 0, sep);
		}

		// 3) While acquisitions are lower than desired, scan through the area at gridLength resolution
		int[] histogram = new int[4];
		for (int[] offset : relations) {
			if (acquisitions.size() >= hopedSampleSize) {
				break;
			}
			int upx = offset[0];
			int upy = offset[1];
			for (int i = floorx; i < capx; i+= gridLength) {
				int x = i + upx;
				if (x >= capx) continue;
				for (int j = floory; j < capy; j+= gridLength) {
					int y = j + upy;
					if (y >= capy) continue;
					int index = rawdata.getSample(x, y, 0);// band 0
					if (index == code) {
						acquisitions.add(new int[]{x, y});
					}
					histogram[index]++;
				}
			}
		}

		System.out.printf("L319  %s\n", Arrays.toString(histogram));

		int nacquired = histogram[code];

		if (code == FRESH_POS) {
			int estimateTotal = (int) ( area * (double) histogram[code] / Arrays.stream(histogram).sum() );
			freshPaintNumPositives = estimateTotal;
			System.out.printf("We set freshPaintNumPositives to %,d. \n", estimateTotal);
		}

		t = reportTime(t, "We got the x,y for each of the fresh paint pixels in the image.\n" +
						"extracted %,d of code %,d from %s x %s bounds for fresh paint",
				nacquired, code, (capx-floorx), (capy - floory));
		return acquisitions;
	}

	private void extendRelations(List<int[]> relations, int j, int upx, int upy) {
		for (int i = 0; i < j; i++) {
			int[] lleftPt = relations.get(i);
			int lleftx = lleftPt[0];
			int llefty = lleftPt[1];
			relations.add(new int[]{lleftx + upx, llefty + upy});
		}
	}

	private double[] getFeatureVector(int... xy) {
		//return getPatchFeatures(xy);
		return getColorVector(xy);
	}

	private double[] getColorVector(int... xy){
		//Preconditions.checkArgument(xy.length == 2, "This is not an xy pair.");
		int x = xy[0];
		int y = xy[1];
		//MAYDO: iff this gets to be the CPU bottleneck, then we could cache answers
		Color clr = new Color(image.getRGB(x, y));
        int red =   clr.getRed();
        int green = clr.getGreen();
        int blue =   clr.getBlue();
        // TODO include other image layers, possibly also computed textures/etc.
        float[] hsb = new float[3];
		Color.RGBtoHSB(red, green, blue, hsb);
		double[] rr = {red/255.0, green/255.0, blue/255.0, hsb[0], hsb[1], hsb[2]};
		return rr;
	}

	private double[] getPatchFeatures(int... xy) {
		//Preconditions.checkArgument(xy.length == 2, "This is not an xy pair.");
		int x = xy[0];
		int y = xy[1];

		int patchEdge = 3;
		int xstart = x - patchEdge/2;
		int xend = xstart + patchEdge;
		int ystart = y - patchEdge / 2;
		int yend = ystart + patchEdge;

		StatsAccumulator[] fv = IntStream.range(0,6)
				.mapToObj(i -> new StatsAccumulator())
				.toArray(StatsAccumulator[]::new);

		for (int i=xstart; i < xend; i++) {
			for (int j=ystart; j<yend; j++) {
				if (isXYOutsideImage(i,j)) continue;
				double[] t = getColorVector(i,j);
				IntStream.range(0,6).forEach(k -> {
					fv[k].add(t[k]);
				});
			}
		}

		return Streams.concat(
				Arrays.stream(fv).mapToDouble(m -> m.mean()),
				Arrays.stream(fv).mapToDouble(m -> m.sampleStandardDeviation())
		).toArray();
	}

	private void initDijkstra() {
		long t = System.currentTimeMillis();
		// set all of doubles[width][height] to ZERO. Should be done beforehand with the initializeFreshPaint.
		setDistancesZero(distances);
		t = reportTime(t, "Set Dijkstra distances matrix to 0, width x height, %,d x %,d",
				distances.length, distances[0].length);
		// initialize empty listQueues for fuelCost MyPoints
		listQueues = new ArrayList<PriorityQueue<MyPoint>>();
		PriorityQueue<MyPoint> queue = new PriorityQueue<>(1000);// lowest totalCost first
		// Add seedPoints to the queue and thence to distances  MAYDO: More than one
		for (MyPoint item: getDijkstraSeedPoints()) {
			queue.add(item);
			fillDistancesBiggerXY(item.fuelCost, item.x, item.y, dijkstraStep);
		}
		listQueues.add(queue);
		t = reportTime(t, "Initialized the queue.");

		int repsIncrement = (int) (freshPaintNumPositives*QUEUE_GROWTH);
		t = reportTime(t, "");
		for (int i=0; i<queueBoundsIdx +1; i++) {
			growDijkstra(repsIncrement);
		}
		t = reportTime(t, "Initialized Dijkstra with 20 growDijkstras.");
	}

	/* Given existence of distances only up till this point,
	 * and then taking the final queue in the listQueues,
	 * add to listQueues a new queue enclosing int reps more area.
	 */
	private void growDijkstra(int reps) {
		//https://math.mit.edu/~rothvoss/18.304.3PM/Presentations/1-Melissa.pdf
		long t = System.currentTimeMillis();
		PriorityQueue<MyPoint> prevQueue = listQueues.get(listQueues.size()-1);
		PriorityQueue<MyPoint> queue = new PriorityQueue<MyPoint>(prevQueue);
		for (int i=0; i < reps; i++) {
			// Repeat until stopping condition... for now, 2x positive training examples//MAYDO: Find shoulders in the advance
			//		choicePoint = least getTotalDistance in queue, & delete
			MyPoint choicePoint = queue.poll(); //Maydo: bugsafe this
			int[][] adjFour = {		{choicePoint.x,choicePoint.y+dijkstraStep}, //Maydo: 8-connectivity w/*sqrt2 penalty on diagonals
									{choicePoint.x,choicePoint.y-dijkstraStep},
									{choicePoint.x+dijkstraStep,choicePoint.y},
									{choicePoint.x-dijkstraStep,choicePoint.y}};
			//		for (x,y) in [(x+1,y), (x-1,y), (x,y+1), (x,y-1)]:
			for (int[] pair : adjFour) {
				int xmine = pair[0];
				int ymine = pair[1];
				if (isXYOutsideImage(xmine, ymine)) continue; //	if isOutsideImage: continue
				if (distances[xmine][ymine] == 0) { //Maydo: consider safer way to tell it's new
					double proposedCost = getEdgeDistance(xmine, ymine) + (double) distances[choicePoint.x][choicePoint.y];
					MyPoint queuePoint = new MyPoint(proposedCost,xmine, ymine);
					queue.add(queuePoint);
					fillDistancesBiggerXY(proposedCost, queuePoint.x, queuePoint.y, dijkstraStep);
				}
			}
		}
		listQueues.add(queue);
		t = reportTime(t, "Grow Dijkstra by one step.");
	}

	private boolean isXYOutsideImage(int x, int y) {
		return isXYOutsideRect(x,y, 0, 0, width, height);
	}
	private boolean isXYOutsideRect(int x, int y, int minx, int miny, int capx, int capy) {
		boolean failure = (x < minx || y < miny || x >= capx || y >= capy);
		return failure;
	}

	/** Fill in a _x_ patch in the distances array with a cost. Ensure not too big.
	 * I assume that x,y is within the bounds. */
	private void fillDistancesBiggerXY(double proposedCost, int x, int y, int dijkstraStep) {
		int greaterx = Math.min(x + dijkstraStep, width);
		int greatery = Math.min(y + dijkstraStep, height);
		for (int i=x; i < greaterx; i++) {
			for (int j=y; j < greatery; j++) {
				distances[i][j] = (float) proposedCost;
				//TODO: This currently allows for (dijkstraGridLength -1) pixels of encroachment on previous labels.
			}
		}
	}

	private void setDistancesZero(float[][] x) {
		for (float[] row: x) {
			Arrays.fill(row, (float) 0.0);
		}
		return;
	}

	//This is not needed
	/** initialize Dijkstra distance grid
	private void initDistances(PriorityQueue<MyPoint> queue) {
		WritableRaster rawdata = freshPaint.getRaster();
		for (int x = 0; x < width; x++) {
			Arrays.fill(distances[x], Double.POSITIVE_INFINITY);
			for (int y = 0; y < height; y++) {						//& fill the queue with fresh paint locations @ fuel cost 0
				int index = rawdata.getSample(x, y, 0);// 0 or 1
				if (index == FRESH_POS) {
					distances[x][y] = 0;
					queue.add(new MyPoint(0.0, x, y)); //Are not all of these connected?
				}
			}
		}
	}*/

	/* This function gets the cost of traversing a single pixel——the classifier score modified by labels.
	*   	Warning: The cost could be infinite. */
	private double getEdgeDistance(int x, int y){
			// If the coordinates are already labeled in the real image, don't even consider it.
		WritableRaster rawdata = labels.getRaster();
		int labelsVal = rawdata.getSample(x,y,0);
		if (labelsVal != UNLABELED){
			return Double.POSITIVE_INFINITY;
		}
			// If freshPaint positive, return  MIN_DISTANCE_VALUE, probably 0.
			// If freshPaint negative, return +INF
		rawdata = freshPaint.getRaster();
		int freshPaintVal = rawdata.getSample(x,y,0);
		if (freshPaintVal == FRESH_POS){
			return EDGE_DISTANCE_FRESH_POS;
		} else if (freshPaintVal == FRESH_NEG){
			return Double.POSITIVE_INFINITY;
		}
			// MAYDO: If it's off the affine view screen, don't label it.
		double out = getClassifierProbNeg(x,y);
		out = getSoftScoreDistanceTransform(out);
		return out;
	}

	private double getSoftScoreDistanceTransform(double softScore) {  //
		double out = softScore;
		out = Math.pow(out, scorePower); //S-curve   exp(-x/.5)^2, worse: atan, 1/(1+x)
		//out += 1;
		return out;
	}

	/** Return a bunch of seed MyPoints with 0 initialization distance
	 * 	Maydo: Do not allow a suggestion outside of view
	 * 	Maydo: Get connected components and for each get a lowest classifier score
	 */
	private List<MyPoint> getDijkstraSeedPoints() {
		System.out.printf("Possible seedPoints for Dijkstra is length %,d.",dijkstraPossibleSeeds.size());
//		WritableRaster rawData = freshPaint.getRaster();
//		WritableRaster labels0 = labels.getRaster();
//		MyPoint smallest = new MyPoint(Double.POSITIVE_INFINITY, 0,0);
//		for (Point2D p2 : dijkstraPossibleSeeds) {
//			int x = (int) p2.getX();
//			int y = (int) p2.getY();
//			if (isXYOutsideImage(x,y)) continue;
//
//			int index = rawData.getSample(x, y, 0);// 0 or 1
//			int driedLabel = labels0.getSample(x,y,0);
//			if (index == FRESH_POS && driedLabel == UNLABELED) {
//				double score0 = getClassifierProbNeg(x,y);
//				if (score0 < smallest.fuelCost) {
//					smallest = new MyPoint(score0,x,y);
//				}
//			}
//		}
//		List<MyPoint> rr = new ArrayList<MyPoint>();
//		rr.add(smallest);
//		return rr;

		List<MyPoint> rr = new ArrayList<MyPoint>();
		WritableRaster labels0 = labels.getRaster();
		WritableRaster fp = freshPaint.getRaster();
		for (Point2D p2 : dijkstraPossibleSeeds) {
			int x = (int) p2.getX();
			int y = (int) p2.getY();
			x -= x%dijkstraStep;
			y -= y%dijkstraStep;
			if (isXYOutsideImage(x, y)) continue;
			if (labels0.getSample(x,y,0) != UNLABELED) continue;
			if (fp.getSample(x,y,0) != FRESH_POS) continue;

			rr.add(new MyPoint(1.0, x,y));
		}
		return rr;
	}

	/**Return the probability of a negative value, so positive is low. */
	private double getClassifierProbNeg(int x, int y) {
		double[] outputs = new double[2];
		double[] fv = getFeatureVector(x, y);
		classifier.predict(fv, outputs);
		double score0 = outputs[0];// probability in [0,1] of class 0, negative
		double score1 = outputs[1];// probability in [0,1] of class 1, positive
		return score0;
	}


	public BufferedImage runClassifier() { //GROK: Why did you make this private?
		long t = System.currentTimeMillis();
		Preconditions.checkNotNull(classifier, "Must put positive paint down first");
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);// grayscale from 0.0 to 1.0 (aka 255)
		WritableRaster raster = out.getRaster();
		IntStream.range(0, width).parallel().forEach(x -> {// run in parallle for speed
			for (int y = 0; y < height; y++) {
				double score0 = getClassifierProbNeg(x,y);
				int index = (int) (255 * score0);
				raster.setSample(x, y, 0, index);
			}
		});
		//reportTime(t,"Computed classifier on whole image.");
		return out;
	}

	public void growSuggestion() {
		if (queueBoundsIdx < 0) return;
		queueBoundsIdx += 1;
		Preconditions.checkArgument(!(listQueues.size() < queueBoundsIdx), "I can't imagine how growSuggestion is more than queue size, except speed issue.");
		if (listQueues.size() == queueBoundsIdx) {
			int repsIncrement = (int) (freshPaintNumPositives*QUEUE_GROWTH);
			growDijkstra(repsIncrement);
		}
		repaint();
	}

	public void shrinkSuggestion() {
		if (queueBoundsIdx <= 0) return;
		queueBoundsIdx -= 1;
		repaint();
	}


	private void writeSuggestionToLabels(int labelIndex) {
		long t = System.currentTimeMillis();
		System.out.println("writeSuggestionToLabels called \n");
		if (listQueues == null || distances == null || labels == null || queueBoundsIdx < 0) {
			return;
		}
		double thresholdDistance = getThresholdDistance();
		WritableRaster labels0 = labels.getRaster();
		for (int x=0; x<width; x++){
			for (int y=0; y<height; y++){
				float distance = distances[x][y];
				if (distance < thresholdDistance && distance > 0) {
					labels0.setSample( x, y, 0, labelIndex);
				}
			}
		}
		initializeFreshPaint();
		repaint();
		t = reportTime(t, "We wrote the suggestion to labels via distances[][] < threshold & >0.");
	}

	private double getThresholdDistance() {
		PriorityQueue<MyPoint> thisQueue = listQueues.get(queueBoundsIdx);
		MyPoint lowestPoint = thisQueue.peek();
		return lowestPoint.fuelCost;
	}
}
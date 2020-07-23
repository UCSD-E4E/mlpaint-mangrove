package org.djf.mlpaint;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;

import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadata;
import javax.swing.*;

import org.djf.util.SwingApp;

import com.google.common.collect.Maps;
import com.google.common.io.MoreFiles;
import org.djf.util.SwingUtil;

import static org.djf.util.SwingUtil.setRGBNoAlpha;

/** Magic Label Paint ~ ML Paint
 *  	A GUI for assisted labeling, using interactive machine learning suggestions
 */
public class MLPaintApp extends SwingApp {

	public static void main(String[] args) {
		//MAYDO: handle startup arguments on the command line
		SwingUtilities.invokeLater(() -> new MLPaintApp());
	}

	private static final int maxPixels = (int) Math.pow(2,26); //(2,26); //(int) Math.pow(2,31) / 4; //Used to be 196,000,000 = 14,000^2 //GROC: Static vs. non-static
	private ImageResamplingDims xy;

	private Path currentImageFile;

	/** magic label paint panel that holds the secret sauce */
	private MLPaintPanel mlp = null;

	private IIOMetadata currentImageMetadata = null;
	private IIOMetadata currentLabelsMetadata = null;


	private JCheckBoxMenuItem showClassifier = new JCheckBoxMenuItem("Show classifier output", false);

	//private JCheckBoxMenuItem noRelabel = new JCheckBoxMenuItem("Keep accepted labels locked.", true);
	private AbstractAction lock = newAction("Lock accepted labels against change.", (name,ev) -> lockLabels());
	private AbstractAction lockFromBox = newAction("Lock accepted labels against change (Ctrl-L|control L", (name,ev) -> lockLabelsFromControlBox());
	private JCheckBox noRelabel = new JCheckBox(lock);

	private AbstractAction penMode = newAction("Set to nearly-pen mode.", (name,ev) -> makePenMode());
	private AbstractAction penModeFromBox = newAction("Set to nearly-pen mode (Ctrl-P)|control P", (name,ev) -> makePenModeFromControlBox());
	private JCheckBox isPenMode = new JCheckBox(penMode);

	private ActionTracker enter = new ActionTracker("Accept suggestion as mangrove (Enter)| ENTER",
			(name,ev) -> mlp.writeSuggestionToLabels(mlp.POSITIVE)); //MAYDO: UI key choice
	private ActionTracker space = new ActionTracker("Accept suggestion as non-mangrove (Space)| SPACE",
			(name,ev) -> mlp.writeSuggestionToLabels(mlp.NEGATIVE));

	private ActionTracker undo = new ActionTracker("Undo accepted label (Ctrl-Z)|control Z", (name, ev) -> mlp.undo());
	private ActionTracker save = new ActionTracker("Save labels... (Ctrl-S)|control S", this::saveLabels);

	private ActionTracker delete = 		new ActionTracker("Clear suggestion (Backspace)|BACK_SPACE", (name,ev) -> mlp.initializeFreshPaint());
	private ActionTracker right = 		new ActionTracker("Grow suggestion (X)|X", (name,ev) -> mlp.growSuggestion());
	private ActionTracker left = 		new ActionTracker("Shrink suggestion (Z)|Z", (name,ev) -> mlp.shrinkSuggestion());

	private ActionTracker up = 		new ActionTracker("Bigger brush (S)|S",    (name,ev) -> mlp.multiplyBrushRadius(1.5));
	private ActionTracker down = 		new ActionTracker("Smaller brush (A)|A", (name,ev) -> mlp.multiplyBrushRadius(1.0/1.5));

	//Less interesting abstract actions
	private ActionTracker ctrl0 = new ActionTracker("Accept suggestion as unlabeled|control 0",
			(name,ev) -> mlp.writeSuggestionToLabels(mlp.UNLABELED));
	private ActionTracker digit = 		new ActionTracker("Set brush size to __ (click any digit 1-9)",   (name,ev) -> mlp.actOnChar('5'));
	private ActionTracker plus = 		new ActionTracker("Weight pixel similarity more in suggestion | W", (name,ev) -> adjustPower(+0.25));
	private ActionTracker minus = 		new ActionTracker("Weight distance more in suggestion | Q", (name, ev) -> adjustPower(-0.25));

	private SwingLink workflowLink = new SwingLink("   An Intro to the MLPaint Labeling Workflow ",
			"https://www.youtube.com/watch?v=m0N1C22AFdc");
	private SwingLink setupLink = new SwingLink("   Loading image, previous labels",
			"https://www.youtube.com/watch?v=ynDJ86NST30&feature=youtu.be");

	/*main passes this function into the EDT TODO: check that*/
	private MLPaintApp() {
		super();
		setTitle("ML Paint, version 2020.07.21 Beta Internal Release");// update version number periodically   //Superclass somewhere above swingApp
		restoreDirectory(MLPaintApp.class);// remember directory from previous run	//SwingApp method
		makeContent();												// MLPaintApp method
		makeBehavior();												// MLPaintApp method
		setJMenuBar(makeMenus());									//JFrame method
		setSize(1250, 800);// initial width, height  	//JFrame method
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);				// JFrame method
		setVisible(true);											// JFrame method
		try {
			openImage();
		} catch (IOException e) {
			//
		}
	}

	/** Make the control panel boxes with actions within the frame.
	 * So far controls on the west and little else.
	 */
	private void makeContent() {
		// NORTH- nothing
		
		// WEST
		Box controls = getControlBox();

		// CENTER
		mlp = new MLPaintPanel();
		mlp.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		//add(mlp, BorderLayout.OPEN_CENTER);

		//Create a split pane with the two scroll panes in it.
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				controls, mlp);
		splitPane.setOneTouchExpandable(true);
		splitPane.setDividerLocation(355);

		//Provide minimum sizes for the two components in the split pane
		Dimension minimumSize = new Dimension(200, 50);
		controls.setMinimumSize(minimumSize);
		mlp.setMinimumSize(minimumSize);
		add(splitPane, BorderLayout.CENTER);
		// EAST- nothing
		
		// SOUTH
		add(status, BorderLayout.SOUTH);
	}

	private Box getControlBox() {
//		JButton b0 = new JButton("Weight \ndistance \n more \nin suggestion");
//		JButton b1 = new JButton("Weight \nclassifier \n more \nin suggestion");
//		b0.addActionListener(ev -> adjustPower(-0.25));
//		b1.addActionListener(ev -> adjustPower(0.25));
//		b0.setFocusable(false);
//		b1.setFocusable(false);//https://stackoverflow.com/questions/4472530/disabling-space-bar-triggering-click-for-jbutton
//
//		controls.add(b1);
//		controls.add(b0);
//		add(controls, BorderLayout.WEST);  							// JFrame method, add(child)
//

		Box controls = Box.createVerticalBox();
		controls.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		controls.add(new JSeparator());
		controls.add(new JLabel("Setup:"));
		controls.add(setupLink);

		controls.add(new JSeparator());
		controls.add(new JLabel("Workflow:"));
		controls.add(workflowLink);
		controls.add(new JSeparator());

		controls.add(new JLabel("1. Choose a region to label as mangrove or not."));
		controls.add(new JSeparator());

		controls.add(new JLabel("2. Click-and-drag to brush on select-paint."));
		up.addAsButton(controls);
		down.addAsButton(controls);
		controls.add(new JSeparator());

		controls.add(new JLabel("3. Control the auto-selection."));

		JLabel a = new JLabel("  A. Brush on more select-paint. (click-and-drag)");
		JLabel b = new JLabel("  B. Brush on anti-paint. (Shift + click-and-drag)");
		JLabel b1 = new JLabel("       -> \"Avoid these pixels.\"");
		JLabel b2 = new JLabel("       -> \"Try to avoid pixels like these.\"");
		JLabel c = new JLabel("  C. Erase paint. (Alt or Option + click-and-drag)");

		Font font = a.getFont();
		a.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));
		b.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));
		c.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));

		controls.add(a);
		controls.add(b);
		controls.add(b1);
		controls.add(b2);
		controls.add(c);

		right.addAsButton(controls);
		left.addAsButton(controls);
		controls.add(isPenMode);
		//SwingUtil.putActionIntoBox(controls, "penModeFromBoxCode", penModeFromBox);

//		final JCheckBox checkBox5 = new JCheckBox("Racing");
//		checkBox5.setMnemonic(KeyEvent.VK_P);
//		checkBox5.addItemListener(new ItemListener() {
//			public void itemStateChanged(ItemEvent e) {
//				System.out.println("WE are calling somethin.");
//				makePenMode();
//			}
//		});
//		controls.add(checkBox5);

		controls.add(new JSeparator());

		controls.add(new JLabel("4. Deal with auto-selection."));
		delete.addAsButton(controls);

		enter.addAsButton(controls);
		space.addAsButton(controls);
		controls.add(new JSeparator());

		controls.add(new JLabel("5. Move to the next spot."));
		controls.add(new JLabel("     Pan (Ctrl + click-and-drag)"));
		controls.add(new JLabel("     Zoom (Ctrl + scroll) <- Not Pinch!"));
		controls.add(new JSeparator());

		controls.add(new JLabel("Act on labels:"));
		undo.addAsButton(controls);
		noRelabel.setMnemonic(KeyEvent.VK_C);
		controls.add(noRelabel);
		//SwingUtil.putActionIntoBox(controls, "lockFromBoxCode", lockFromBox);
		save.addAsButton(controls);
		controls.add(new JSeparator());

		return controls;
	}

	private void adjustPower(double v) {
		mlp.scorePower += v;
		//Re-start the suggestion
		mlp.initAutoSuggest();
		status("Score Power = %.2f", mlp.scorePower);
	}

	/** Make further controls beyond makeContent, beyond getting active children to a JFrame */
	private void makeBehavior() {
		showClassifier.setAccelerator(KeyStroke.getKeyStroke("control  T"));
		showClassifier.addActionListener(event -> {
			mlp.showClassifierC = showClassifier.isSelected(); //JAR mlp.showClassifier.set(showClassifier.isSelected());
			if (mlp.showClassifierC) {
				mlp.classifierOutput = mlp.runClassifier();
				mlp.repaint();
			}
			status("showClassifier %s  %s", showClassifier.isSelected(), mlp.showClassifierC);
		});

	}

	private void lockLabels() {
		mlp.noRelabel = (noRelabel.isSelected());
		mlp.initDijkstra();
		mlp.repaint();
		status("Locking down labels so they cannot be changed: %s", noRelabel.isSelected());
	}

	private void lockLabelsFromControlBox() {
		noRelabel.setSelected(!noRelabel.isSelected());
		lockLabels();
	}

	private void makePenMode() {
		System.out.println("Turning on or off the pen mode.");
		if (isPenMode.isSelected() == true) {
			mlp.dijkstraGrowth = mlp.INTERIOR_STEPS;
			mlp.queueBoundsIdx = mlp.INTERIOR_STEPS;
		} else {
			mlp.dijkstraGrowth =  mlp.DEFAULT_DIJSKTRA_GROWTH;
			mlp.queueBoundsIdx = mlp.DEFAULT_DIJSKTRA_GROWTH;
		}
		mlp.initDijkstra();
		mlp.repaint();
	}

	private void makePenModeFromControlBox() {
		isPenMode.setSelected(!isPenMode.isSelected());
		makePenMode();
	}

	/** Make the menus, with associated actions and often keyboard shortcuts.
	 * @return JMenuBar
	 */
	private JMenuBar makeMenus() {				 						//MAYDO: Allow ctrl and command, maybe ever same
		JMenu file = newMenu("File",
				newMenuItem("Open image, labels...|control O", this::openImage),
				save.menuItem,
				newMenuItem("Save and Exit", this::exit),
				null);

		JMenu view = newMenu("View",
				showClassifier,
				newMenuItem("Reset zoom|ESCAPE", (name,ev) -> mlp.resetView()),
				newMenuItem("Refresh", (name,ev) -> refresh()),
				null);

		JMenu label = newMenu("Label",
				digit.menuItem,
						null,
						ctrl0.menuItem,
						null,
//						right.menuItem,
//						left.menuItem,
//						null,
//						up.menuItem,
//						down.menuItem,
//						null,
						plus.menuItem,
						minus.menuItem,
				null);


		// PAGE_UP/PAGE_DOWN keys
		// https://docs.oracle.com/javase/8/docs/api/java/awt/event/KeyEvent.html#VK_PAGE_UP

		JMenuBar rr = new JMenuBar();
		rr.add(file);
		rr.add(view);
		rr.add(label);
		return rr;
	}

	/*The rest of the file serves makeMenus(), providing action functions called there.*/

	private void openImage(String command, ActionEvent ev) throws IOException {
		openImage();
	}

	private void openImage() throws IOException {

		JFileChooser jfc = new JFileChooser();
		jfc.setDialogTitle("Select your image, any pre-existing labels, and other layers.");
		jfc.setCurrentDirectory(directory.toFile());

		// currently the user selects all the layers to load
		// MAYDO: instead, the user could just select the main file,
		// and we could auto-identify the others somehow; (optionally offering to the user to filter away some
		// that they don't want to spent time/RAM/network loading)
		jfc.setMultiSelectionEnabled(true);
		int rr = jfc.showOpenDialog(this);
		if (rr != JFileChooser.APPROVE_OPTION) {
			return;
		}
		directory = jfc.getCurrentDirectory().toPath();
		storeDirectory(MLPaintApp.class);// remember it for future runs of the program


		// 1. determine image dimensions on disk via Util.readImageDimensions
		// 2. If too big to load, determine how much down-sampling:  2x2?  3x3? 4x4?
		xy = new ImageResamplingDims(jfc.getSelectedFiles()[0], maxPixels);
		boolean consistent = SwingUtil.isSameDimensions(xy.bigDim, jfc.getSelectedFiles());
		if (!consistent) {
			status("Not all the selected images had the same dimensions.");
			//return;
		}
		// 3. Load downsampled images for all the layers
		// 4. When saving to _labels.png, remember to upsample the result    //REDUCE the DEM layer to 8 bits, grayscale, per pixel, reduce distances to a byte, not a double. Size of things match.

		BufferedImage image = null;
		BufferedImage labels = null;
		Path possibleImageFileNo_RGB = null;
		Path labelsFile = null;
		LinkedHashMap<String, BufferedImage> extraLayers = Maps.newLinkedHashMap();// keeps order
		long t = System.currentTimeMillis();
		for (File file : jfc.getSelectedFiles()) {
			IIOMetadata metadata = null;
			BufferedImage img = SwingUtil.subsampleImageFile(file, xy, metadata);
			//BufferedImage img = ImageIO.read(file);
			t = reportTime(t, "loaded %s", file.toPath()); //GROK: Why toPath not getAbsolutePath?
			System.out.println(file.toString());
			if (MoreFiles.getNameWithoutExtension(file.toPath()).endsWith("_RGB")) {
				image = setRGBNoAlpha(img);
				currentImageFile = file.toPath();
				//currentImageMetadata = metadata;
				//System.out.print(metadata);
			} else if (MoreFiles.getNameWithoutExtension(file.toPath()).endsWith("_labels")) {
				labels = img;
				labelsFile = file.toPath();
				currentLabelsMetadata = metadata;
				//System.out.print(metadata);
				System.out.println("We got a labels file.");
			} else {
				extraLayers.put(file.getName(), img);
				possibleImageFileNo_RGB = file.toPath();
			}
		}
		if (image == null && extraLayers.size() == 1) {
			//Maydo: Allow adding an extra layer after initially loading an image.
			//That way, one might load an extra layer in a different directory or that is hard to select.
			for (BufferedImage bi : extraLayers.values()) {
				image = bi;
				currentImageFile = possibleImageFileNo_RGB;
			}
		} else if (image == null && extraLayers.size() == 0) {
			image = labels;
			currentImageFile = labelsFile;
		}
		if (image == null) {
			throw new IllegalArgumentException("Must provide the _RGB image");// appears in status bar in red
		}
		// My original design REPLACED the mlp, but it was forever not re-painting.
		// Instead, I'll just change its data.
		showClassifier.setSelected(false);
		noRelabel.setSelected(true);
		mlp.resetData(image, labels, extraLayers);
		mlp.revalidate();// https://docs.oracle.com/javase/8/docs/api/javax/swing/JComponent.html#revalidate--
		status("Opened %s  %,d x %,d       %s", currentImageFile, image.getWidth(), image.getHeight(),
				image.getColorModel().toString());
		System.out.printf("image color model: %s", image.getColorModel().toString());
	}

	private void saveLabels(String command, ActionEvent ev) throws IOException {
		saveLabels();
	}
	private void saveLabels() throws IOException {
		//TODO  figure out exactly how to output for downstream consumption
		// For now: compressed TIFF is good
		String extension = ".tif"; //".tif"".png";
		String formatName = "tiff"; //"tiff" "png";
		String filename = MoreFiles.getNameWithoutExtension(currentImageFile).replace("_RGB", "_labels") + extension;
		Path outfile = directory.resolve(filename);
		// There is a 2^31 limit on the number of pixels in an image, a limit divided by four for RGBA.
		// It makes sense that an 8-bit grayscale as opposed to a RGBA 32-bit image allows 4x the image size, full 2^31.
		//  It seems that  4-bit image rather than 8-bit allows for double the image size, 2^32, or 65,500^2.
		BufferedImage labelsToScale = SwingUtil.upsampleImage0Channel(mlp.labels, xy.bigDim, xy.samplingEdge);
		boolean a = ImageIO.write(labelsToScale, formatName, outfile.toFile());
		status("Saved %d x %d labels to %s, with message %s", labelsToScale.getWidth(), labelsToScale.getHeight(), outfile, a);
		mlp.safeToSave = true;
		//https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/imageio/metadata/IIOMetadata.html
	}

	private void exit(String command, ActionEvent ev) {
		//TODO: if latest changes not saved
		// JOptionDialog "Do you want to save your labels first?
		// save and quit
		try {
			this.saveLabels();
		} catch (IOException e) {
			return;
		}
		this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
	}

	private void  label(String command, ActionEvent ev) {
		// Using mlp.proposed somehow
		status("TODO %s\n", command);
	}


}

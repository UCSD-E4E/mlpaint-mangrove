package org.djf.mlpaint;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Key;
import java.util.Collections;
import java.util.LinkedHashMap;

import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadata;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
		SwingUtilities.invokeLater(() -> new MLPaintApp(false));
	}

	private static int SMALLER_PIXELS = (int) Math.pow(2,23); //(2,26); //(int) Math.pow(2,31) / 4; //Used to be 196,000,000 = 14,000^2 //GROC: Static vs. non-static
	private static int BIGGER_PIXELS = (int) Math.pow(2,27); //(2,26); //(int) Math.pow(2,31) / 4; //Used to be 196,000,000 = 14,000^2 //GROC: Static vs. non-static
	private static int maxPixels = BIGGER_PIXELS;
	private ImageResamplingDims xy;

	private Path currentImageFile;

	private boolean alreadyTold = false;

	/** magic label paint panel that holds the secret sauce */
	private MLPaintPanel mlp = new MLPaintPanel();

	private IIOMetadata currentImageMetadata = null;
	private IIOMetadata currentLabelsMetadata = null;


	JSlider brushRadiusSlider = new JSlider((int) (mlp.radiusFromChDigit('1')*10),
			(int) (mlp.radiusFromChDigit('9')*10),
			(int) (mlp.radiusFromChDigit('4')*10));

	private JCheckBoxMenuItem showClassifier = new JCheckBoxMenuItem("Show classifier output", false);
	private JCheckBoxMenuItem resizeVisuals = new JCheckBoxMenuItem("Adjust paint for small image", false);
	private JCheckBoxMenuItem highlightUnlabeled = new JCheckBoxMenuItem("Highlight unlabeled regions", false);

	private JCheckBoxMenuItem loadHighRes = new JCheckBoxMenuItem("Load image at lower resolution", false);


	//private JCheckBoxMenuItem noRelabel = new JCheckBoxMenuItem("Keep accepted labels locked.", true);
	private AbstractAction lock = newAction("Lock accepted labels against change.", (name,ev) -> lockLabels());
	private AbstractAction lockFromBox = newAction("Lock accepted labels against change (Ctrl-L|control L", (name,ev) -> lockLabelsFromControlBox());
	private JCheckBox noRelabel = new JCheckBox(lock);

	private AbstractAction penMode = newAction("Set auto-selection to pen size.", (name,ev) -> makePenMode());
	private AbstractAction penModeFromBox = newAction("Set to nearly-pen mode (Ctrl-P)|control P", (name,ev) -> makePenModeFromControlBox());
	private JCheckBox isPenMode = new JCheckBox(penMode);

	private ActionTracker enter = new ActionTracker("Accept auto-selection as positive (Enter)| ENTER",
			(name,ev) -> mlp.writeSuggestionToLabels(mlp.POSITIVE)); //MAYDO: UI key choice
	private ActionTracker space = new ActionTracker("Accept auto-selection as negative (Space)| SPACE",
			(name,ev) -> mlp.writeSuggestionToLabels(mlp.NEGATIVE));
	private ActionTracker ctrl0 = new ActionTracker("Accept auto-selection as unlabeled (Ctrl-U)|control U",
			(name,ev) -> mlp.writeSuggestionToLabels(mlp.UNLABELED));
	private ActionTracker c1 = new ActionTracker("Accept auto-selection as class 1 ('negative')| SPACE", (name,ev) -> mlp.writeSuggestionToLabels(mlp.NEGATIVE));
	private ActionTracker c2 = new ActionTracker("Accept auto-selection as class 2 ('positive')| ENTER", (name,ev) -> mlp.writeSuggestionToLabels(mlp.POSITIVE));
	private ActionTracker c3 = new ActionTracker("Accept auto-selection as class 3|3", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_3));
	private ActionTracker c4 = new ActionTracker("Accept auto-selection as class 4|4", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_4));
	private ActionTracker c5 = new ActionTracker("Accept auto-selection as class 5|5", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_5));
	private ActionTracker c6 = new ActionTracker("Accept auto-selection as class 6|6", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_6));
	private ActionTracker c7 = new ActionTracker("Accept auto-selection as class 7|7", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_7));
	private ActionTracker c8 = new ActionTracker("Accept auto-selection as class 8|8", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_8));
	private ActionTracker c9 = new ActionTracker("Accept auto-selection as class 9|9", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_9));
	private ActionTracker c10 = new ActionTracker("Accept auto-selection as class 10|0", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_10));
	private ActionTracker c11 = new ActionTracker("Accept auto-selection as class 11|control 1", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_11));
	private ActionTracker c12 = new ActionTracker("Accept auto-selection as class 12|control 2", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_12));
	private ActionTracker c13 = new ActionTracker("Accept auto-selection as class 13|control 3", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_13));
	private ActionTracker c14 = new ActionTracker("Accept auto-selection as class 14|control 4", (name,ev) -> mlp.writeSuggestionToLabels(mlp.CLASS_14));

	private ActionTracker noData = new ActionTracker("Block off regions of selected color as no data|control N", (name,ev) -> mlp.getNoData());


	private ActionTracker undo = new ActionTracker("Undo accepted label (Ctrl-Z)|control Z", (name, ev) -> mlp.undo());
	private ActionTracker save = new ActionTracker("Save labels in image directory (Ctrl-S)|control S", this::saveLabels);

	private ActionTracker delete = 		new ActionTracker("Undo select-paint (Backspace)|BACK_SPACE", (name,ev) -> mlp.initializeFreshPaint());
	private ActionTracker right = 		new ActionTracker("Grow auto-selection (X)|X", (name,ev) -> mlp.growSuggestion());
	private ActionTracker left = 		new ActionTracker("Shrink auto-selection (Z)|Z", (name,ev) -> mlp.shrinkSuggestion());

	private ActionTracker up = 		new ActionTracker("Bigger brush (S)|S",    (name,ev) -> { double b = mlp.multiplyBrushRadius(1.3);
																										brushRadiusSlider.setValue((int) (b*10));
																										});
	private ActionTracker down = 		new ActionTracker("Smaller brush (A)|A", (name,ev) -> {double b = mlp.multiplyBrushRadius(1.0/1.3);
																										brushRadiusSlider.setValue((int) (b*10));
																										});

	//Less interesting abstract actions
	private ActionTracker digit = 		new ActionTracker("Set brush size to __ (click any digit 1-9)",   (name,ev) -> {
		setBrush('4');
	});
	private ActionTracker digitOne = 		new ActionTracker("Set brush size to 1 |D",   (name,ev) -> setBrush('1'));
	private ActionTracker digitTwo = 		new ActionTracker("Set brush size to 2 |2",   (name,ev) -> setBrush('2'));
	private ActionTracker digitThree = 		new ActionTracker("Set brush size to 3 |3",   (name,ev) -> setBrush('3'));
	private ActionTracker digitFour = 		new ActionTracker("Set brush size to 4 |F",   (name,ev) -> setBrush('4'));
	private ActionTracker digitFive = 		new ActionTracker("Set brush size to 5 |5",   (name,ev) -> setBrush('5'));
	private ActionTracker digitSix = 		new ActionTracker("Set brush size to 6 |6",   (name,ev) -> setBrush('6'));
	private ActionTracker digitSeven = 		new ActionTracker("Set brush size to 7 |7",   (name,ev) -> setBrush('7'));
	private ActionTracker digitEight = 		new ActionTracker("Set brush size to 8 |8",   (name,ev) -> setBrush('8'));
	private ActionTracker digitNine = 		new ActionTracker("Set brush size to 9 |G",   (name,ev) -> setBrush('9'));

	private ActionTracker plus = 		new ActionTracker("Weight pixel similarity more in suggestion | alt W", (name,ev) -> adjustPower(+0.25));
	private ActionTracker minus = 		new ActionTracker("Weight distance more in suggestion | alt Q", (name, ev) -> adjustPower(-0.25));

	private SwingLink workflowLink = new SwingLink("   An Intro to the MLPaint Labeling Workflow ",
			"https://www.youtube.com/watch?v=m0N1C22AFdc");
	private SwingLink setupLink = new SwingLink("   Loading image, previous labels",
			"https://www.youtube.com/watch?v=ynDJ86NST30&feature=youtu.be");
	private SwingLink documentationLink = new SwingLink( "Tutorials: Setup & Labeling Workflow",
			"https://ucsd-e4e.github.io/mangrove/Labeling%20Tool/#tutorial-loading-an-image-to-label");

	/*main passes this function into the EDT TODO: check that*/
	// changed from private
	
	 MLPaintApp(boolean testing) {
		super();
		setTitle("ML Paint, version 1.0, 2020.08.04");// update version number periodically   //Superclass somewhere above swingApp
		restoreDirectory(MLPaintApp.class);// remember directory from previous run	//SwingApp method
		makeContent();												// MLPaintApp method
		makeBehavior();												// MLPaintApp method
		setJMenuBar(makeMenus());									//JFrame method
		setSize(1250, 800);// initial width, height  	//JFrame method
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);				// JFrame method
		setVisible(true);											// JFrame method
		// only open image if not testing
		if (!testing) {
			try {
				openImage();
			} catch (IOException e) {
				//
			}
		}

	}

	/** Make the control panel boxes with actions within the frame.
	 * So far controls on the west and little else.
	 */
	private void makeContent() {
		// NORTH- nothing


		// CENTER
		//mlp = new MLPaintPanel();
		mlp.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		//add(mlp, BorderLayout.OPEN_CENTER);

		// WEST  (It is important that mlp is initialized first, for the sake of the JSlider.)
		Box controls = getControlBox();

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

//		controls.add(new JSeparator());
//		controls.add(new JLabel("Setup:"));
//		controls.add(setupLink);
//
//		controls.add(new JSeparator());
//		controls.add(new JLabel("Workflow:"));
//		controls.add(workflowLink);
		controls.add(documentationLink);
		controls.add(new JSeparator());

		controls.add(new JLabel("1. Choose a region to label."));
		controls.add(new JSeparator());

		controls.add(new JLabel("2. Brush on select-paint and avoid-paint."));

		JLabel a = new JLabel("  Select-paint: (click-and-drag)");
		JLabel a1 = new JLabel("       -- \"Select these pixels.\"");
		JLabel a2 = new JLabel("       -- \"Try to select pixels like these.\"");
		JLabel b = new JLabel("  Avoid-paint: (Shift + click-and-drag)");
		JLabel b1 = new JLabel("       -- \"Avoid these pixels.\"");
		JLabel b2 = new JLabel("       -- \"Try to avoid pixels like these.\"");
		JLabel c = new JLabel("  Erase-paint: (Alt or Option + click-and-drag)");
		
		// test for JUnit
		a.setName("test");

		Font font = a.getFont();
		a.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));
		b.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));

		//controls.add(new JLabel("  "));
		controls.add(a); controls.add(a1); controls.add(a2);
		controls.add(b);
		controls.add(b1);
		controls.add(b2);
		controls.add(c);
		//up.addAsButton(controls);
		//down.addAsButton(controls);

		String sliderHoverText = "Keys (A) and (S) shrink and grow the brush size.  Digits (1)-(9) set the brush size.";
		brushRadiusSlider.setToolTipText(sliderHoverText);

		//...where initialization occurs:
		class BrushSliderListener implements ChangeListener {
			public void stateChanged(ChangeEvent e) {
				JSlider source = (JSlider)e.getSource();
				if (!source.getValueIsAdjusting()) {
					double brushRadius = (double)source.getValue() / 10.0;
					mlp.brushRadius = brushRadius;
				}
			}
		}
		brushRadiusSlider.addChangeListener(new BrushSliderListener());

		SwingUtil.putActionIntoBox(controls, digitOne.keyStroke, digitOne.action);
//		SwingUtil.putActionIntoBox(controls, digitTwo.keyStroke, digitTwo.action);
//		SwingUtil.putActionIntoBox(controls, digitThree.keyStroke, digitThree.action);
		SwingUtil.putActionIntoBox(controls, digitFour.keyStroke, digitFour.action);
//		SwingUtil.putActionIntoBox(controls, digitFive.keyStroke, digitFive.action);
//		SwingUtil.putActionIntoBox(controls, digitSix.keyStroke, digitSix.action);
//		SwingUtil.putActionIntoBox(controls, digitSeven.keyStroke, digitSeven.action);
//		SwingUtil.putActionIntoBox(controls, digitEight.keyStroke, digitEight.action);
		SwingUtil.putActionIntoBox(controls, digitNine.keyStroke, digitNine.action);

		SwingUtil.putActionIntoBox(controls, up.keyStroke, up.action);
		SwingUtil.putActionIntoBox(controls, down.keyStroke, down.action);

		SwingUtil.putActionIntoBox(controls, plus.keyStroke, plus.action);
		SwingUtil.putActionIntoBox(controls, minus.keyStroke, minus.action);

		controls.add(new JLabel("  "));
		JLabel sliderText = new JLabel("Brush size--Shrink & grow (A)&(S)--Set (D)&(F)");
		sliderText.setToolTipText(sliderHoverText);
		controls.add(sliderText);
		controls.add(brushRadiusSlider);
		//controls.add(new JLabel("Shrink & grow brush size: (A) & (S)"));
		//controls.add(new JLabel("Set brush size: digits (1)-(9)"));
		controls.add(new JSeparator());

		controls.add(new JLabel("3. Resize the auto-selection."));

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

		controls.add(new JLabel("4. Label the auto-selection."));

		enter.addAsButton(controls);
		space.addAsButton(controls);
		ctrl0.addAsButton(controls);
		controls.add(new JSeparator());

		controls.add(new JLabel("5. Move to the next spot."));
		controls.add(new JLabel("     Pan (Ctrl + click-and-drag)"));
		controls.add(new JLabel("               (Or middle mouse button drag)"));
		controls.add(new JLabel("     Zoom (Two-finger scroll)  --Not pinch"));
		controls.add(new JSeparator());

		controls.add(new JLabel("Deal with mistakes:"));
		delete.addAsButton(controls);
		undo.addAsButton(controls);
		//noRelabel.setMnemonic(KeyEvent.VK_C);
		controls.add(new JLabel("  "));
		controls.add(noRelabel);
		controls.add(new JLabel("      (Unlock labels to edit them.)"));
		//SwingUtil.putActionIntoBox(controls, "lockFromBoxCode", lockFromBox);
		controls.add(new JSeparator());
		controls.add(new JLabel("And the surest recovery method:"));
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

		resizeVisuals.setAccelerator(KeyStroke.getKeyStroke("control V"));
		resizeVisuals.addActionListener(event -> {
			mlp.makeBigger(!resizeVisuals.isSelected());
		});

		highlightUnlabeled.setAccelerator(KeyStroke.getKeyStroke("control H"));
		highlightUnlabeled.addActionListener(event -> {
			mlp.hideLabeled = highlightUnlabeled.isSelected();
			mlp.repaint();
		});

		loadHighRes.addActionListener(event -> {
			if (loadHighRes.isSelected()) {
				maxPixels = BIGGER_PIXELS;
			} else {
				maxPixels = SMALLER_PIXELS;
			}
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
				loadHighRes,
				save.menuItem,
				newMenuItem("Exit the Program After Saving the Labels", this::exit),
				null);

		JMenu view = newMenu("View",
				showClassifier,
						resizeVisuals,
						highlightUnlabeled,
				newMenuItem("Reset zoom|ESCAPE", (name,ev) -> mlp.resetView()),
				newMenuItem("Refresh", (name,ev) -> refresh()),
				null);

		JMenu label = newMenu("Labels",
				//digit.menuItem,
				//		null,

						noData.menuItem,
						null,
								ctrl0.menuItem,
						null,
							c1.menuItem, c2.menuItem, c3.menuItem, c4.menuItem,
							c5.menuItem, c6.menuItem, c7.menuItem, c8.menuItem,
							c9.menuItem, c10.menuItem, c11.menuItem, c12.menuItem,
							c13.menuItem, c14.menuItem,


//						right.menuItem,
//						left.menuItem,
//						null,
//						up.menuItem,
//						down.menuItem,
//						null,
				null);


		// PAGE_UP/PAGE_DOWN keys
		// https://docs.oracle.com/javase/8/docs/api/java/awt/event/KeyEvent.html#VK_PAGE_UP
		JLabel proviso = new JLabel("                                                   " +
											"                                        "+
				"If used for commercial or academic work, please contact davidf4983@gmail.com for attribution.");

		JMenuBar rr = new JMenuBar();
		rr.add(file);
		rr.add(view);
		rr.add(label);
		rr.add(proviso);
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
		jfc.setPreferredSize(new Dimension(800,400));
		// test for JUnit
		jfc.setName("opener");

		// currently the user selects all the layers to load
		// MAYDO: instead, the user could just select the main file,
		// and we could auto-identify the others somehow; (optionally offering to the user to filter away some
		// that they don't want to spent time/RAM/network loading)
		jfc.setMultiSelectionEnabled(true);
		int rr = jfc.showOpenDialog(this);
		if (rr != JFileChooser.APPROVE_OPTION) {
			return;
		}

		xy = new ImageResamplingDims(jfc.getSelectedFiles()[0], maxPixels);
		if (xy.bigx > 4000 && !alreadyTold) {
			JOptionPane.showMessageDialog(this, "This may take a moment to load. MLPaint will be unresponsive for maybe a minute after you proceed.");
			alreadyTold = true;
		}
		directory = jfc.getCurrentDirectory().toPath();
		storeDirectory(MLPaintApp.class);// remember it for future runs of the program


		// 1. determine image dimensions on disk via Util.readImageDimensions
		// 2. If too big to load, determine how much down-sampling:  2x2?  3x3? 4x4?
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
			if (MoreFiles.getNameWithoutExtension(file.toPath()).toLowerCase().endsWith("rgb")) {
				image = setRGBNoAlpha(img);
				currentImageFile = file.toPath();
				//currentImageMetadata = metadata;
				//System.out.print(metadata);
			} else if (MoreFiles.getNameWithoutExtension(file.toPath()).toLowerCase().endsWith("labels")) {
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
			JOptionPane.showMessageDialog(this,
					"MLPaint depend on filename--2 files: one filename must end in \"rgb\" OR \"labels\"; 3 files: one must end in \"rgb\".");
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
		
		// if no image, return
		if (currentImageFile == null) return;
		
		String extension = ".tif"; //".tif"".png";
		String formatName = "tiff"; //"tiff" "png";
		String filename = MoreFiles.getNameWithoutExtension(currentImageFile) + "_MLPaintLabels" + extension;
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

	private void setBrush(char ch) {
		double b = mlp.radiusFromChDigit(ch);
		mlp.brushRadius = b;
		brushRadiusSlider.setValue((int)(b*10));
	}

}

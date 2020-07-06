package org.djf.mlpaint;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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

	private IIOMetadata currentImageMetadata = null;
	private IIOMetadata currentLabelsMetadata = null;

	/** magic label paint panel that holds the secret sauce */
	private MLPaintPanel mlp = null;

	private JCheckBoxMenuItem showClassifier = new JCheckBoxMenuItem("Show classifier output", false);
	private JCheckBoxMenuItem noRelabel = new JCheckBoxMenuItem("Lock accepted labels", false);
	/*main passes this function into the EDT TODO: check that*/
	private MLPaintApp() {
		super();
		setTitle("ML Paint, version 2020.07.01");// update version number periodically   //Superclass somewhere above swingApp
		restoreDirectory(MLPaintApp.class);// remember directory from previous run	//SwingApp method
		makeContent();												// MLPaintApp method
		makeBehavior();												// MLPaintApp method
		setJMenuBar(makeMenus());									//JFrame method
		setSize(1000, 800);// initial width, height  	//JFrame method
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
		JButton b0 = new JButton("-");
		JButton b1 = new JButton("+");
		b0.addActionListener(ev -> adjustPower(-0.25));
		b1.addActionListener(ev -> adjustPower(0.25));
		b0.setFocusable(false);
		b1.setFocusable(false);//https://stackoverflow.com/questions/4472530/disabling-space-bar-triggering-click-for-jbutton

		Box controls = Box.createVerticalBox();
		controls.add(b1);
		controls.add(b0);
		add(controls, BorderLayout.WEST);  							// JFrame method, add(child)

		// CENTER
		mlp = new MLPaintPanel();
		mlp.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		add(mlp, BorderLayout.CENTER);
		
		// EAST- nothing
		
		// SOUTH
		add(status, BorderLayout.SOUTH);
	}

	private void adjustPower(double v) {
		mlp.scorePower += v;
		status("Score Power = %.2f", mlp.scorePower);
	}

	/** Make further controls beyond makeContent, beyond getting active children to a JFrame */
	private void makeBehavior() {
		showClassifier.setAccelerator(KeyStroke.getKeyStroke("control  T"));
		showClassifier.addActionListener(event -> {
			mlp.showClassifier.set(showClassifier.isSelected());
			if (mlp.classifierOutput == null) {  //TODO: I feel terrible about this programming. It's 1 am.
				mlp.classifierOutput = mlp.runClassifier();
			}
			status("showClassifier %s  %s", showClassifier.isSelected(), mlp.showClassifier.get());
		});

		noRelabel.setAccelerator(KeyStroke.getKeyStroke("control L"));
		noRelabel.addActionListener(event -> {
			mlp.noRelabel = (noRelabel.isSelected());
			mlp.initDijkstra();
			mlp.repaint();
			status("Locking down labels so they cannot be changed: %s", noRelabel.isSelected());
		});

	}

	/** Make the menus, with associated actions and often keyboard shortcuts.
	 * @return JMenuBar
	 */
	private JMenuBar makeMenus() {				 						//MAYDO: Allow ctrl and command, maybe ever same
		JMenu file = newMenu("File",
				newMenuItem("Open image, labels...|control O", this::openImage),
				newMenuItem("Save labels...|control S", this::saveLabels),
				newMenuItem("Exit", this::exit),
				null);

		JMenu view = newMenu("View",
				showClassifier,
				newMenuItem("Reset zoom|ESCAPE", (name,ev) -> mlp.resetView()),
				newMenuItem("Refresh", (name,ev) -> refresh()),
				null);

		JMenu label = newMenu("Label",

				noRelabel, null,
				newMenuItem("Accept suggestion as positive| ENTER",
						(name,ev) -> mlp.writeSuggestionToLabels(mlp.POSITIVE)), //MAYDO: UI key choice
				newMenuItem("Accept suggestion as negative -| SPACE",
						(name,ev) -> mlp.writeSuggestionToLabels(mlp.NEGATIVE)),
				newMenuItem("Accept suggestion as unlabeled|control 0",
						(name,ev) -> mlp.writeSuggestionToLabels(mlp.UNLABELED)),
				null,
				newMenuItem("Undo accepted label|control Z", (name, ev) -> mlp.undo()),
				null,
				newMenuItem("Clear suggestion|BACK_SPACE", (name,ev) -> mlp.initializeFreshPaint()),
				newMenuItem("Grow suggestion|RIGHT", (name,ev) -> mlp.growSuggestion()),
				newMenuItem("Shrink suggestion|LEFT", (name,ev) -> mlp.shrinkSuggestion()),
				null,
				newMenuItem("Bigger brush|UP",    (name,ev) -> mlp.multiplyBrushRadius(1.5)),
				newMenuItem("Smaller brush|DOWN", (name,ev) -> mlp.multiplyBrushRadius(1.0/1.5)),
				newMenuItem("Set brush size to __ (any digit)",   (name,ev) -> mlp.actOnChar('5')),
				null);

		JMenu help = new JMenu("Help—With an Intro to the MLPaint Labeling Workflow ");
		SwingLink link = new SwingLink(" An Intro to the MLPaint Labeling Workflow ",
				"https://ucsd-e4e.github.io/mangrove/Labeling%20Tool/");
		help.add(link);
		// PAGE_UP/PAGE_DOWN keys
		// https://docs.oracle.com/javase/8/docs/api/java/awt/event/KeyEvent.html#VK_PAGE_UP

		JMenuBar rr = new JMenuBar();
		rr.add(file);
		rr.add(view);
		rr.add(label);
		rr.add(link);
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
		boolean consistent = SwingUtil.isSameDimensions(xy.bigDim,jfc.getSelectedFiles());
		if (!consistent) {
			status("Not all the selected images had the same dimensions.");
			return;
		}
		// 3. Load downsampled images for all the layers
		// 4. When saving to _labels.png, remember to upsample the result    //REDUCE the DEM layer to 8 bits, grayscale, per pixel, reduce distances to a byte, not a double. Size of things match.

		BufferedImage image = null;
		BufferedImage labels = null;
		LinkedHashMap<String, BufferedImage> extraLayers = Maps.newLinkedHashMap();// keeps order
		long t = System.currentTimeMillis();
		for (File file: jfc.getSelectedFiles()) {
			IIOMetadata metadata = null;
			BufferedImage img = SwingUtil.subsampleImageFile(file, xy, metadata);
			//BufferedImage img = ImageIO.read(file);
			t = reportTime(t, "loaded %s", file.toPath()); //GROK: Why toPath not getAbsolutePath?
			System.out.println(file.toString());
			if (file.toString().contains("_RGB")) {
				image = setRGBNoAlpha(img);
				currentImageFile = file.toPath();
				currentImageMetadata = metadata;
				System.out.print(metadata);
			} else if (file.toString().contains("_labels")) {
				labels = img;
				currentLabelsMetadata = metadata;
				System.out.print(metadata);
				System.out.println("We got a labels file.");
			} else {
				extraLayers.put(file.getName(), img);
			}
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
		//https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/imageio/metadata/IIOMetadata.html
	}

	private void exit(String command, ActionEvent ev) {
		//TODO: if latest changes not saved
		// JOptionDialog "Do you want to save your labels first?
		// save or just quit
		status("TODO %s\n", command);
	}

	private void  label(String command, ActionEvent ev) {
		// Using mlp.proposed somehow
		status("TODO %s\n", command);
	}


}

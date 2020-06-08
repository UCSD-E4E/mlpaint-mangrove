package org.djf.mlpaint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.djf.util.SwingApp;

import com.google.common.collect.Maps;
import com.google.common.io.MoreFiles;


/** Magic Label Paint ~ ML Paint
 *  	A GUI for assisted labeling, using interactive machine learning suggestions
 */
public class MLPaintApp extends SwingApp {

	public static void main(String[] args) {
		//MAYDO: handle startup arguments on the command line
		SwingUtilities.invokeLater(() -> new MLPaintApp());
	}


	private Path currentImageFile;

	/** magic label paint panel that holds the secret sauce */
	private MLPaintPanel mlp = null;

	private JCheckBoxMenuItem showClassifier = new JCheckBoxMenuItem("Show classifier output", false);

	/*main passes this function into the EDT TODO: check that*/
	private MLPaintApp() {
		super();
		setTitle("ML Paint, version 2020.06.02b");// update version number periodically   //GROC: where from?
		restoreDirectory(MLPaintApp.class);// remember directory from previous run	//SwingApp method
		makeContent();												// MLPaintApp method
		makeBehavior();												// MLPaintApp method
		setJMenuBar(makeMenus());									//JFrame method
		setSize(1000, 800);// initial width, height  	//JFrame method
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);				// JFrame method
		setVisible(true);											// JFrame method
	}

	/** Make the control panel boxes with actions within the frame.
	 * So far controls on the west and little else.
	 */
	private void makeContent() {
		// NORTH- nothing
		
		// WEST
		JButton b0 = new JButton("-");
		JButton b1 = new JButton("+");
		b0.addActionListener(ev -> status("Ahhhh. Thanks."));
		b1.addActionListener(ev -> status("Slap!"));

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
		add(status, BorderLayout.SOUTH);							// GROC: Where do we get status?
	}

	/** Make further controls beyond makeContent, beyond getting active children to a JFrame */
	private void makeBehavior() {
		showClassifier.setAccelerator(KeyStroke.getKeyStroke("control  T"));
		showClassifier.addActionListener(ev -> {
			if (mlp != null) {
				mlp.setShowClassifierOutput(showClassifier.isSelected());
			}
		});

	}

	/** Make the menus, with associated actions and often keyboard shortcuts.
	 * @return JMenuBar
	 */
	private JMenuBar makeMenus() {				 						//MAYDO: Allow ctrl and command, maybe ever same
		JMenu file = newMenu("File",
				newMenuItem("Open image...|control O", this::openImage),
				newMenuItem("Save labels...|control S", this::saveLabels),
				newMenuItem("Exit", this::exit),
				null);

		JMenu view = newMenu("View",
				showClassifier,
				newMenuItem("Reset zoom|ESCAPE", (name,ev) -> mlp.resetView()),
				newMenuItem("Refresh", (name,ev) -> refresh()),
				null);

		JMenu label = newMenu("Label",
				newMenuItem("Label proposed as positive +|control 1", this::label),
				newMenuItem("Label proposed as negative -|control 2", this::label),
				newMenuItem("Label proposed as unlabeled|control 0", this::label),
				newMenuItem("Clear proposed|DELETE", (name,ev) -> mlp.clearFreshPaint()),
				newMenuItem("Bigger brush|UP",    (name,ev) -> mlp.brushRadius *= 1.5),
				newMenuItem("Smaller brush|DOWN", (name,ev) -> mlp.brushRadius /= 1.5),
				newMenuItem("Reset brush size",   (name,ev) -> mlp.brushRadius = 10),
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
		JFileChooser jfc = new JFileChooser();
		jfc.setDialogTitle("Open images...");
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
		directory = jfc.getCurrentDirectory().toPath();						//GROC: directory sourcing
		storeDirectory(MLPaintApp.class);// remember it for future runs of the program		//GROC: SwingApp, same Q about prefs.


		// TODO: if image too big to load:
		// 1. determine image dimensions on disk via Util.readImageDimensions
		// 2. If too big to load, determine how much down-sampling:  2x2?  3x3? 4x4?
		// 3. Load downsampled images for all the layers
		// 4. When saving to _labels.png, remember to upsample the result 

		BufferedImage image = null;
		BufferedImage labels = null;
		LinkedHashMap<String, BufferedImage> extraLayers = Maps.newLinkedHashMap();// keeps order
		long t = System.currentTimeMillis();
		for (File file: jfc.getSelectedFiles()) {
			BufferedImage img = ImageIO.read(file);
			t = reportTime(t, "loaded %s", file.toPath());
			if (file.toString().contains("_RGB")) {
				image = img;
				currentImageFile = file.toPath();
			} else if (file.toString().contains("_labels")) {
				labels = img;
			} else {
				extraLayers.put(file.getName(), img);
			}
		}
		if (image == null) {
			throw new IllegalArgumentException("Must provide the _RGB image");// appears in status bar in red
		}
		// if no previously existing labels loaded, create an unlabeled layer of same size.  Initially all 0's == UNLABELED
		if (labels == null) {
			labels = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			//MAYDO: to reduce RAM   BufferedImage.TYPE_BYTE_BINARY, new ColorModel(with just 4 or 16 colors));
		}
		if (image.getWidth() != labels.getWidth() || image.getHeight() != labels.getHeight()) {  //GROC: Is this a fine idea?
			throw new IOException("The loaded labels ought to be the same shape as the image.");
		}

		// My original design REPLACED the mlp, but it was forever not re-painting.
		// Instead, I'll just change its data.
		showClassifier.setSelected(false);
		mlp.resetData(image, labels, extraLayers);
		mlp.revalidate();// https://docs.oracle.com/javase/8/docs/api/javax/swing/JComponent.html#revalidate--
		status("Opened %s", currentImageFile);
	}

	private void saveLabels(String command, ActionEvent ev) throws IOException {
		//TODO  figure out exactly how to output for downstream consumption
		// For now: compressed PNG is good
		String filename = MoreFiles.getNameWithoutExtension(currentImageFile).replace("_RGB", "_labels") + ".png";
		Path outfile = directory.resolve(filename);
		ImageIO.write(mlp.labels, "png", outfile.toFile());
		status("Saved %d x %d labels to %s", mlp.width, mlp.height, outfile);
	}

	private void exit(String command, ActionEvent ev) {
		//TODO: if latest changes not saved
		// JOptionDialog "Do you want to save your labels first?
		// save or just quit
		status("TODO %s\n", command);
	}

	private void label(String command, ActionEvent ev) {
		//TODO 
		// Using mlp.proposed somehow
		status("TODO %s\n", command);
	}


}

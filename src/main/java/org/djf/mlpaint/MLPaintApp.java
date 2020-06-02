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
 * 
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


	private MLPaintApp() {
		super();
		setTitle("ML Paint, version 2020.06.02b");// update version number periodically
		restoreDirectory(MLPaintApp.class);// remember directory from previous run
		makeContent();
		makeBehavior();
		setJMenuBar(makeMenus());
		setSize(1000, 800);// initial width, height
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
	}

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
		add(controls, BorderLayout.WEST);

		// CENTER
		mlp = new MLPaintPanel();
		mlp.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		add(mlp, BorderLayout.CENTER);
		
		// EAST- nothing
		
		// SOUTH
		add(status, BorderLayout.SOUTH);
	}

	private void makeBehavior() {
		showClassifier.setAccelerator(KeyStroke.getKeyStroke("control  T"));
		showClassifier.addActionListener(ev -> {
			if (mlp != null) {
				mlp.setShowClassifierOutput(showClassifier.isSelected());
			}
		});

	}

	private JMenuBar makeMenus() {
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

	private void openImage(String command, ActionEvent ev) throws IOException {
		JFileChooser jfc = new JFileChooser();
		jfc.setDialogTitle("Open images...");
		jfc.setCurrentDirectory(directory.toFile());
		jfc.setMultiSelectionEnabled(true);
		int rr = jfc.showOpenDialog(this);
		if (rr != JFileChooser.APPROVE_OPTION) {
			return;
		}
		directory = jfc.getCurrentDirectory().toPath();
		storeDirectory(MLPaintApp.class);// remember it for future runs of the program


		// TODO: if image too big to load:
		// 1. determine image dimensions on disk via Util.readImageDimensions
		// 2. If too big to load, determine how much down-sampling:  2x2?  3x3? 4x4?
		// 3. Load downsampled images for all the layers
		// 4. When saving, upsample the _labels.png

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

		// My original design REPLACED the mlp, but it was forever not re-painting.
		// Instead, I'll just change it's data.
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

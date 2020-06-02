package org.djf.mlpaint;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.djf.util.SwingApp;

import com.google.common.collect.Maps;
import com.google.common.io.MoreFiles;


/** Magic Label Paint ~ ML Paint
 * 
 */
public class MLPaintApp extends SwingApp {
	
	public static void main(String[] args) {
		//MAYDO: process startup arguments on the command line
		SwingUtilities.invokeLater(() -> new MLPaintApp());
	}

	
	
	Path currentImageFile;

	/** magic label paint panel that holds the secret sauce */
	MLPaintPanel mlp = null;
	
	
	public MLPaintApp() {
		super();
		setTitle("ML Paint, version 2020.06.02b");// update version number periodically
		restoreDirectory(MLPaintApp.class);// remember directory from previous run
		setJMenuBar(makeMenus());
		addContent();
		setSize(1000, 800);// initial width, height
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
		addBehavior();
	}

	private void addContent() {
		// WEST
		JButton b0 = new JButton("-");
		JButton b1 = new JButton("+");
		b0.addActionListener(ev -> status("Ahhhh."));
		b1.addActionListener(ev -> status("Ahhhh."));
		
		JPanel controls = new JPanel(new FlowLayout());
		controls.add(b1);
		controls.add(b0);
		add(controls, BorderLayout.WEST);
		
		// CENTER
		JPanel blank = new JPanel();// Initially the middle panel is blank
		add(blank, BorderLayout.CENTER);
	}
	
	private void addBehavior() {
		// TODO Auto-generated method stub
		
	}


	private JMenuBar makeMenus() {
		JMenu file = new JMenu("File");
		file.add(newMenuItem("Open image...", this::openImage));
		file.add(newMenuItem("Save labels...", this::saveLabels));
		file.add(newMenuItem("Exit", this::exit));
		
		JMenu view = new JMenu("View");
		
		JMenu label = new JMenu("Label");
		label.add(newMenuItem("Label + positive", this::label));
		label.add(newMenuItem("Label - negative", this::label));
		label.add(newMenuItem("Delete labeled area", this::label));
		label.add(newMenuItem("Clear fresh paint", this::label));

		JMenuBar rr = new JMenuBar();
		rr.add(file);
		rr.add(label);
		rr.add(view);
		return rr;
	}
	
	protected void openImage(String command, ActionEvent ev) throws IOException {
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
			t = reportTime(t, "loaded %s", file);
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
			throw new IllegalArgumentException("Must provide the _RGB image");
		}
		// if no previously existing labels loaded, create an unlabeled layer of same size.  Initially all 0's == UNLABELED
		if (labels == null) {
			labels = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			//MAYDO: to reduce RAM   BufferedImage.TYPE_BYTE_BINARY, new ColorModel(with 4 or 16 colors));
		}
		
		mlp = new MLPaintPanel(image, labels, extraLayers);
		add(mlp, BorderLayout.CENTER);// replace the center
	}

	protected void saveLabels(String command, ActionEvent ev) throws IOException {
		//TODO  figure out exactly how to output for downstream consumption
		String filename = MoreFiles.getNameWithoutExtension(currentImageFile).replace("_RGB", "_labels");
		File outputfile = directory.resolve(filename).toFile();
	    ImageIO.write(mlp.labels, "png", outputfile);
	}
	
	protected void exit(String command, ActionEvent ev) {
		//TODO: if latest changes not saved
		// JOptionDialog "Do you want to save your labels first?
		// save or just quit
	}
	
	protected void label(String command, ActionEvent ev) {
	}
	
	
}

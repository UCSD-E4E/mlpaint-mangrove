package org.djf.mlpaint;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.text.html.ImageView;

import org.djf.util.SwingApp;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.MoreFiles;


/** Magic Label Paint ~ ML Paint
 * 
 */
public class MLPaint extends SwingApp {
	
	// label pixel value codes
	static final int UNLABELED = 0;
	static final int FRESH = 11;
	
	public static void main(String[] args) {
		//MAYDO: process startup arguments on the command line
		SwingUtilities.invokeLater(() -> new MLPaint());
	}

	
	/** current image file */
	Path masterImageFile;
	
	/** current image */
	BufferedImage masterImage;

	/** labels: 0=UNLABELED, 1=mangrove, 2=not mangrove, ... 11=FRESH mangrove, 12=FRESH non-mangrove */
	BufferedImage labels;
	
	/** input layers:  filename & image.  Does not contain master image or labels layers. */
	LinkedHashMap<String, BufferedImage> extraLayers;

	
	
	public MLPaint() {
		super();
		restoreDirectory(MLPaint.class);
		setTitle("ML Paint, version 2020.06.02b");// update version number periodically
		setJMenuBar(makeMenus());
		addContent();
		setSize(1000, 800);// initial width, height
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
		addBehavior();
	}

	private void addContent() {
		// WEST
		JButton b = new JButton("Push me");
		b.addActionListener(ev -> status("Ahhhh."));
		add(b, BorderLayout.WEST);
		
		// CENTER
		//JComponent imageViewer = null;//todo
		//add(imageViewer, BorderLayout.CENTER);
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
		file.add(newMenuItem("Label positive", this::label));
		file.add(newMenuItem("Label negative", this::label));
		file.add(newMenuItem("Delete labeled area", this::label));

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
		storeDirectory(MLPaint.class);// remember it for future runs of the program
		
		extraLayers.clear();
		labels = null;
		masterImage = null;
		masterImageFile = null;
		
		long t = System.currentTimeMillis();
		File[] files = jfc.getSelectedFiles();
		for (File file: files) {
			BufferedImage img = ImageIO.read(file);
			t = reportTime(t, "loaded %s", file);
			extraLayers.put(file.getName(), img);
			if (file.toString().contains("_RGB")) {
				masterImage = img;
				masterImageFile = file.toPath();
			} else if (file.toString().contains("_labels")) {
				labels = img;
			}
		}

		// if no previously existing labels loaded, create an unlabeled layer
		if (labels == null) {
			labels = new BufferedImage(masterImage.getWidth(), masterImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			//MAYDO: to save RAM   BufferedImage.TYPE_BYTE_BINARY, new ColorModel(with 4 or 16 colors));
		}
	}

	protected void saveLabels(String command, ActionEvent ev) throws IOException {
		//TODO  figure out exactly how to output for downstream consumption
		String filename = MoreFiles.getNameWithoutExtension(masterImageFile).replace("_RGB", "_labels");
		File outputfile = directory.resolve(filename).toFile();
	    ImageIO.write(labels, "png", outputfile);
	}
	
	protected void exit(String command, ActionEvent ev) {
		//TODO: if latest changes not saved
		// JOptionDialog "Do you want to save your labels first?
		// save or just quit
	}
	
	protected void label(String command, ActionEvent ev) {
	}
	
	
}

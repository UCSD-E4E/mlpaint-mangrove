package org.djf.mlpaint;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimerTask;

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

import org.yaml.snakeyaml.Yaml;

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


	JSlider brushRadiusSlider = new JSlider(JSlider.VERTICAL,(int) (mlp.radiusFromChDigit('1')*10),
			(int) (mlp.radiusFromChDigit('9')*10),
			(int) (mlp.radiusFromChDigit('4')*10));
	//JSlider slider = new JSlider(JSlider.VERTICAL,0,100,10);

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
	
	private AbstractAction autoSaveMode = newAction("Turn on auto-save", (name,ev) -> startAutoSave());
	private JCheckBox isAutoSave = new JCheckBox(autoSaveMode);

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
	
//	private long AUTOSAVE_INTERVAL = 5*60; //seconds
	
	private class AutoSave implements Runnable {
		private volatile boolean running = true;
		
		@Override
        public void run() {
			status ("Autosave started with interval %d seconds", mlp.getAutosave());
    		try {
    			autoSaveLabels();
    		} catch (IOException e) {
    			
    		}
        	while (running) {
        		try {
        			Thread.sleep(mlp.getAutosave() * 1000);
        		} catch (InterruptedException e) {
        			return;
        		}
        		try {
        			autoSaveLabels();
        		} catch (IOException e) {
        			
        		}
        	}

        }
        
        public void terminate() {
        	running = false;
        }
	}

	private Thread thread = null;
	private AutoSave autoSave = null;


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
		
	    Map conf = new HashMap();
	    Yaml yaml = new Yaml();
	    File confPath = new File("config.yml");
	    
	    try {
	        InputStream stream = new FileInputStream(confPath);
	        
	        conf = (Map) yaml.load(stream);
	        if (conf == null || conf.isEmpty() == true) {
	            throw new RuntimeException("Failed to read config file");
	        }
	        status("%s", "Reading config file");
	        try {
		        mlp.setAutosave((int) conf.get("autosave"));
		        status("%s", "Setting autosave config");
	        } catch (Exception e) {
	        	throw new RuntimeException("autosave not found");
	        }
	        
	    } catch (FileNotFoundException e) {
	        System.out.println("No such file " + confPath);
	        throw new RuntimeException("No config file");
	    } catch (Exception e1) {
	        e1.printStackTrace();
	        throw new RuntimeException("Failed to read config file");
	    }
   

	}

	/** Make the control panel boxes with actions within the frame.
	 * So far controls on the west and little else.
	 */
	private void makeContent() {

	    
		// NORTH- nothing


		// CENTER
		mlp = new MLPaintPanel();
		mlp.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		add(mlp, BorderLayout.CENTER);

//		// WEST  (It is important that mlp is initialized first, for the sake of the JSlider.)
//		Box controls = getControlBox();
//
//		//Create a split pane with the two scroll panes in it.
//		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
//				controls, mlp);
//		splitPane.setOneTouchExpandable(true);
//		splitPane.setDividerLocation(355);
//
//		//Provide minimum sizes for the two components in the split pane
//		Dimension minimumSize = new Dimension(200, 50);
//		controls.setMinimumSize(minimumSize);
//		mlp.setMinimumSize(minimumSize);
//		add(splitPane, BorderLayout.CENTER);
		
		SwingUtil.putActionIntoMLP(mlp, digitOne.keyStroke, digitOne.action);
//		SwingUtil.putActionIntoMLP(mlp, digitTwo.keyStroke, digitTwo.action);
//		SwingUtil.putActionIntoMLP(mlp, digitThree.keyStroke, digitThree.action);
		SwingUtil.putActionIntoMLP(mlp, digitFour.keyStroke, digitFour.action);
//		SwingUtil.putActionIntoMLP(mlp, digitFive.keyStroke, digitFive.action);
//		SwingUtil.putActionIntoMLP(mlp, digitSix.keyStroke, digitSix.action);
//		SwingUtil.putActionIntoMLP(mlp, digitSeven.keyStroke, digitSeven.action);
//		SwingUtil.putActionIntoMLP(mlp, digitEight.keyStroke, digitEight.action);
		SwingUtil.putActionIntoMLP(mlp, digitNine.keyStroke, digitNine.action);

		SwingUtil.putActionIntoMLP(mlp, up.keyStroke, up.action);
		SwingUtil.putActionIntoMLP(mlp, down.keyStroke, down.action);

		SwingUtil.putActionIntoMLP(mlp, plus.keyStroke, plus.action);
		SwingUtil.putActionIntoMLP(mlp, minus.keyStroke, minus.action);
		
		//WEST
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.NORTHWEST;
		c.weightx = 1;
		c.weighty = 1;
		c.gridx = 0;
	    c.gridy = 0;
	    
		right.addAsButton(mlp, "Grow select", 0,10,110,25, c);
		c.gridy = 1;
		left.addAsButton(mlp, "Shrink select", 0,35,110,25, c);
		c.gridy = 2;
		delete.addAsButton(mlp, "Undo select", 0,60,110,25, c);
		
		c.gridy = 3;
		mlp.add(new JLabel(" "), c);
		
		JLabel label = new JLabel("<html><div style='text-align: center;'>&nbsp;&nbsp;Label</div></html>");
		label.setBounds(10, 95, 50, 30);
		c.gridy = 4;
		mlp.add(label, c);
		
		c.gridy = 5;
		enter.addAsButton(mlp, "+", 0,120,50,25, c);
		c.gridy = 6;
		space.addAsButton(mlp, "-", 0,145,50,25, c);
		c.gridy = 7;
		ctrl0.addAsButton(mlp, "neu", 0,170,50,25, c);
		c.gridy = 8;
		undo.addAsButton(mlp, "Undo label", 0,195,100,25, c);
		
		c.gridy = 9;
		mlp.add(new JLabel(" "), c);
		
		JLabel brushSize = new JLabel("<html><div style='text-align: center;'>&nbsp;Brush<br/>&nbsp;&nbsp;size</div></html>");
		brushSize.setBounds(10, 240, 50, 30);
		c.gridy = 10;
		mlp.add(brushSize, c);
		
		String sliderHoverText = "Keys (A) and (S) shrink and grow the brush size.  Keys (D), (F), (G) set the brush size.";
		brushRadiusSlider.setToolTipText(sliderHoverText);
		brushRadiusSlider.setBounds(15,260,20,200);

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
		c.gridy = 11;
		c.ipady = 200;
		mlp.add(brushRadiusSlider, c);
		c.gridy = 12;
		mlp.add(new JLabel(" "), c);
		
		// EAST
		c.anchor = GridBagConstraints.NORTHEAST;
		JPanel settings = getSettings();
		c.gridx = 1;
		c.gridy = 2;
		c.ipadx = 3;
		c.ipady = 0;
		c.gridheight = 6;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.VERTICAL;
		mlp.add(settings, c);
		JButton settingButton = getSettingsButton(settings);
		c.fill = GridBagConstraints.NONE;
		c.gridx = 0;
		c.gridy = 0;
		c.gridheight = 0;
		c.gridwidth = 0;
		mlp.add(settingButton, c);
		
		JPanel help = getHelp();
		c.gridx = 1;
		c.gridy = 8;
		c.gridheight = 20;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.VERTICAL;
		mlp.add(help, c);
		JButton helpButton = getHelpButton(help);
		c.fill = GridBagConstraints.NONE;
		c.gridx = 0;
		c.gridy = 1;
		c.gridheight = 0;
		c.gridwidth = 0;
		mlp.add(helpButton, c);
		
		// SOUTH
		add(status, BorderLayout.SOUTH);
	}
	
	private JButton getSettingsButton(JPanel settings) {
		JButton settingButton = new JButton("Settings");
		settingButton.setBounds(1150,10,100,25);
		settingButton.addActionListener(new ActionListener () {
			public void actionPerformed (ActionEvent e) {
				if (settings.isVisible()) {
					settings.setVisible(false);
				} else {
					settings.setVisible(true);
				}
			}
		});
		return settingButton;
	}
	
	private JPanel getSettings() {
		JPanel settings = new JPanel();
		settings.setLayout(new GridBagLayout());
		settings.setBounds(945,35,300,130);
		settings.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.NORTHWEST;
		c.gridx = 0;
	    c.gridy = 0;
		settings.add(isPenMode, c);
		
	    c.gridy = 1;
		settings.add(noRelabel, c);
		
	    c.gridy = 2;
		settings.add(new JLabel("      (Unlock labels to edit them.)                     " ), c);
		
		c.gridy = 3;
		settings.add(isAutoSave, c);
		
		c.gridy = 4;
		save.addAsButton(settings, c);
		
		return settings;
	}
	
	private JButton getHelpButton(JPanel help) {
		JButton helpButton = new JButton("Help");
		helpButton.setBounds(1060,10,100,25);
		helpButton.addActionListener(new ActionListener () {
			public void actionPerformed (ActionEvent e) {
				if (help.isVisible()) {
					help.setVisible(false);
				} else {
					help.setVisible(true);
				}
			}
		});
		return helpButton;
	}
	
	private JPanel getHelp() {
		JPanel help = new JPanel();
		help.setLayout(new GridBagLayout());
		help.setBounds(945,170,300,500);
		help.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = new Insets(2,2,2,2);
		c.gridx = 1;
	    c.gridy = 0;
		help.add(documentationLink, c);
		
	    c.gridy = 1;
	    JLabel h = new JLabel("Hover over buttons for info/shortcuts");
	    Font font = h.getFont();
	    h.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));
	    help.add(h, c);
	    
	    c.gridy = 2;
		help.add(new JLabel("1. Choose a region to label."), c);
		
	    c.gridy = 3;

		help.add(new JLabel("2. Brush on select-paint and avoid-paint."), c);
		
	    c.gridy = 4;

		JLabel a = new JLabel("  Select-paint: (click-and-drag)");
		JLabel a1 = new JLabel("       -- \"Select these pixels.\"");
		JLabel a2 = new JLabel("       -- \"Try to select pixels like these.\"");
		JLabel b = new JLabel("  Avoid-paint: (Shift + click+drag)");
		JLabel b1 = new JLabel("       -- \"Avoid these pixels.\"");
		JLabel b2 = new JLabel("       -- \"Try to avoid pixels like these.\"");
		JLabel c1 = new JLabel("  Erase-paint: (Alt or Option + click+drag)");
		
		a.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));
		b.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));
		c1.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));

		//controls.add(new JLabel("  "));
		help.add(a, c); 
	    c.gridy = 5;
		help.add(a1, c);
	    c.gridy = 6;
		help.add(a2, c);
	    c.gridy = 7;
		help.add(b, c);
	    c.gridy = 8;
		help.add(b1, c);
	    c.gridy = 9;
		help.add(b2, c);
	    c.gridy = 10;
		help.add(c1, c);
	    c.gridy = 11;
		
		help.add(new JLabel("3. Resize the auto-selection."), c);
	    c.gridy = 12;
	    help.add(new JLabel("     Grow/Shrink buttons"), c);
	    c.gridy = 13;
	    help.add(new JLabel("     (X)/(Z) shortcuts"), c);
	    c.gridy = 14;
		help.add(new JLabel("4. Label the auto-selection."), c);
	    c.gridy = 15;
	    help.add(new JLabel("     +/-/neu buttons"), c);
	    c.gridy = 16;
	    help.add(new JLabel("     (Enter)/(Space)/(Ctrl-U) shortcuts"), c);

		
	    c.gridy = 17;
		help.add(new JLabel("5. Move to the next spot."), c);
	    c.gridy = 18;
		help.add(new JLabel("     Pan (Ctrl + click+drag)"), c);
	    c.gridy = 19;
		help.add(new JLabel("               (Or middle mouse button drag)"), c);
	    c.gridy = 20;
		help.add(new JLabel("     Zoom (Two-finger scroll)  --Not pinch"), c);
	    c.gridy = 21;
		help.add(new JLabel("Deal with mistakes:"), c);
		c.gridy = 22;
		help.add(new JLabel("     Undo select/Undo label buttons"), c);
		c.gridy = 23;
		help.add(new JLabel("     (Backspace)/(Ctrl-Z) shortcuts"), c);
		
		return help;
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
	
	private void startAutoSave() {
		if (isAutoSave.isSelected() == true) {
			autoSave = new AutoSave();
			thread = new Thread(autoSave);
			thread.start();
		} else {
			if (thread != null) {
				autoSave.terminate();
				thread.interrupt();
				status("Autosave stopped ");
			}
		}
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
		System.out.printf("image color model: %s \n", image.getColorModel().toString());
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
		String filename = MoreFiles.getNameWithoutExtension(currentImageFile) + "_MLPaintlabels" + extension;
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
	
	private void autoSaveLabels() throws IOException {
		//TODO  figure out exactly how to output for downstream consumption
		// For now: compressed TIFF is good
		
		// if no image, return
		if (currentImageFile == null) return;
		
		SimpleDateFormat formatter = new SimpleDateFormat("_dd-MM-yyyy_HH.mm.ss");  
		Date date = new Date();  
		String extension = ".tif"; //".tif"".png";
		String formatName = "tiff"; //"tiff" "png";
		String filename = MoreFiles.getNameWithoutExtension(currentImageFile) + "_MLPaintlabels" + formatter.format(date) + extension;
		Path outfile = directory.resolve(filename);
		// There is a 2^31 limit on the number of pixels in an image, a limit divided by four for RGBA.
		// It makes sense that an 8-bit grayscale as opposed to a RGBA 32-bit image allows 4x the image size, full 2^31.
		//  It seems that  4-bit image rather than 8-bit allows for double the image size, 2^32, or 65,500^2.
		BufferedImage labelsToScale = SwingUtil.upsampleImage0Channel(mlp.labels, xy.bigDim, xy.samplingEdge);
		boolean a = ImageIO.write(labelsToScale, formatName, outfile.toFile());
		status("Saved %d x %d labels to %s, with message %s at %s", labelsToScale.getWidth(), labelsToScale.getHeight(), outfile, a, date);
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

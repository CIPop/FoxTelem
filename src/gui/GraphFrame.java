package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JCheckBox;

import telemetry.BitArrayLayout;
import telemetry.FramePart;
import telemetry.PayloadStore;
import common.Config;
import common.Log;
import common.Spacecraft;

/**
 * 
 * FOX 1 Telemetry Decoder
 * @author chris.e.thompson g0kla/ac2cz
 *
 * Copyright (C) 2015 amsat.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
@SuppressWarnings("serial")
public class GraphFrame extends JFrame implements WindowListener, ActionListener, ItemListener {

	private String fieldName;
	String displayTitle;
	private int payloadType;
	private JPanel contentPane;
	private GraphPanel panel;
	private JPanel titlePanel;
	private JPanel footerPanel;
	
	private JButton btnLatest;
	private JButton btnCSV;
	private JButton btnCopy;
	private JButton btnDerivative;
	private JButton btnMain;
	private JButton btnAvg;
	
	public Spacecraft fox;
	public static int DEFAULT_SAMPLES = 180;
	public int SAMPLES = DEFAULT_SAMPLES;
	public static long DEFAULT_START_UPTIME = 0;
	public static int DEFAULT_START_RESET = 0;
	public long START_UPTIME = DEFAULT_START_UPTIME;
	public int START_RESET = DEFAULT_START_RESET;
	public static final int MAX_SAMPLES = 99999;
	public static final int MAX_AVG_SAMPLES = 999;
	public static int DEFAULT_AVG_PERIOD = 12;
	public int AVG_PERIOD = DEFAULT_AVG_PERIOD;
	//private JLabel lblActual;
	public static final int DEFAULT_UPTIME_THRESHOLD = 60*60*1;// plot continuous uptime unless more than 1 hour gap
	public static final int CONTINUOUS_UPTIME_THRESHOLD = -1;
	public double UPTIME_THRESHOLD =DEFAULT_UPTIME_THRESHOLD; 
	private JCheckBox chckbxPlotAllUptime;
	private JLabel lblFromUptime;
	private JTextField textFromUptime;
	private JLabel lblPlot;
	JLabel lblSamplePeriod; // The number of samples to grab for each graph
	private JTextField txtSamplePeriod;
	private JLabel lblAvg;
	JLabel lblAvgPeriod; 
	private JTextField txtAvgPeriod;
	private JLabel lblFromReset;
	private JTextField textFromReset;
	private DiagnosticTextArea textArea;
	private DiagnosticTable diagnosticTable;
	
	public boolean plotDerivative;
	public boolean dspAvg;
	public boolean displayMain = true;
	
	boolean textDisplay = false;
	
	/**
	 * Create the frame.
	 */
	public GraphFrame(String title, String fieldName, int conversionType, int plType, Spacecraft sat) {
		fox = sat;
		this.fieldName = fieldName;
		payloadType = plType;
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		addWindowListener(this);
		loadProperties();
		
//		Image img = Toolkit.getDefaultToolkit().getImage(getClass().getResource("images/fox.jpg"));
//		setIconImage(img);
		
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);
		titlePanel = new JPanel();
		contentPane.add(titlePanel, BorderLayout.NORTH);
		titlePanel.setLayout(new BorderLayout(0,0));
		JPanel titlePanelLeft = new JPanel();
		JPanel titlePanelcenter = new JPanel();
		titlePanel.add(titlePanelLeft, BorderLayout.EAST);
		titlePanel.add(titlePanelcenter, BorderLayout.CENTER);

		displayTitle = title;
		if (plType != 0) // measurement
			displayTitle = sat.name + " " + title;
		if (conversionType == BitArrayLayout.CONVERT_FREQ) {
			int freqOffset = sat.telemetryDownlinkFreqkHz;
			displayTitle = title + " delta from " + freqOffset + " kHz";
		}

		
//		JLabel lblTitle = new JLabel(displayTitle);
//		lblTitle.setFont(new Font("SansSerif", Font.BOLD, Config.graphAxisFontSize + 3));
//		titlePanelcenter.add(lblTitle);

		if (conversionType == BitArrayLayout.CONVERT_IHU_DIAGNOSTIC || conversionType == BitArrayLayout.CONVERT_HARD_ERROR || 
				conversionType == BitArrayLayout.CONVERT_SOFT_ERROR ) {   // Should not hard code this - need to update
			//textArea = new DiagnosticTextArea(title, fieldName, this);
			diagnosticTable = new DiagnosticTable(title, fieldName, conversionType, this);
			//JScrollPane scroll = new JScrollPane (diagnosticTable, 
			//		   JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
			contentPane.add(diagnosticTable, BorderLayout.CENTER);
			textDisplay = true;
		} else {
			panel = new GraphPanel(title, fieldName, conversionType, payloadType, this, sat);
			contentPane.add(panel, BorderLayout.CENTER);
		}

		
		// Toolbar buttons
		
		btnMain = new JButton("Hide");
		btnMain.setMargin(new Insets(0,0,0,0));
		btnMain.setToolTipText("Hide the unprocessed telemetry data");
		btnMain.addActionListener(this);
		titlePanelLeft.add(btnMain);
		if (this.textDisplay) btnMain.setVisible(false);

		btnDerivative = createIconButton("/images/derivSmall.png","Deriv","Plot 1st Derivative (1st difference)");
		titlePanelLeft.add(btnDerivative);
		if (this.textDisplay) btnDerivative.setVisible(false);

		btnAvg = new JButton("AVG");
		btnAvg.setMargin(new Insets(0,0,0,0));
		btnAvg.setToolTipText("Running Average / Low Pass Filter");
		btnAvg.addActionListener(this);
		
		titlePanelLeft.add(btnAvg);
		if (this.textDisplay) btnAvg.setVisible(false);

		if (conversionType == BitArrayLayout.CONVERT_STATUS_BIT || conversionType == BitArrayLayout.CONVERT_ANTENNA || 
				conversionType == BitArrayLayout.CONVERT_BOOLEAN ) {
			btnDerivative.setVisible(false);
			btnAvg.setVisible(false);
		}

		btnMain.setBackground(Color.GRAY);
		btnDerivative.setBackground(Color.GRAY);
		btnAvg.setBackground(Color.GRAY);

		btnLatest = createIconButton("/images/refreshSmall.png","Reset","Reset to default range and show latest data");
		titlePanelLeft.add(btnLatest);
		//if (this.textDisplay) btnLatest.setEnabled(false);
		
		btnCSV = createIconButton("/images/saveSmall.png","CSV","Save this data to a CSV file");
		titlePanelLeft.add(btnCSV);
		if (this.textDisplay) btnCSV.setEnabled(false);
		
		btnCopy = createIconButton("/images/copySmall.png","Copy","Copy graph to clipboard");
		titlePanelLeft.add(btnCopy);
		if (this.textDisplay) btnCopy.setEnabled(false);

		
		
		
		footerPanel = new JPanel();
		contentPane.add(footerPanel, BorderLayout.SOUTH);
		footerPanel.setLayout(new BorderLayout(0,0));
		JPanel footerPanelLeft = new JPanel();
		JPanel footerPanelRight = new JPanel();
		footerPanel.add(footerPanelLeft, BorderLayout.EAST);
		footerPanel.add(footerPanelRight, BorderLayout.CENTER);
		
		lblAvg = new JLabel("Avg");
		txtAvgPeriod = new JTextField();
//		txtSamplePeriod.setPreferredSize(new Dimension(30,14));
		txtAvgPeriod.addActionListener(this);
		lblAvgPeriod = new JLabel("samples  ");
		
		setAvgVisible(false);
		
		footerPanelLeft.add(lblAvg);
		footerPanelLeft.add(txtAvgPeriod);
		footerPanelLeft.add(lblAvgPeriod);
		txtAvgPeriod.setText(Integer.toString(AVG_PERIOD));
		txtAvgPeriod.setColumns(3);

		lblPlot = new JLabel("Plot");
		txtSamplePeriod = new JTextField();
//		txtSamplePeriod.setPreferredSize(new Dimension(30,14));
		txtSamplePeriod.addActionListener(this);

		
		footerPanelLeft.add(lblPlot);
		footerPanelLeft.add(txtSamplePeriod);
		lblSamplePeriod = new JLabel("samples");
		footerPanelLeft.add(lblSamplePeriod);
		txtSamplePeriod.setText(Integer.toString(SAMPLES));
		txtSamplePeriod.setColumns(6);
		//lblActual = new JLabel("(180)");
		//footerPanel.add(lblActual);
		
		lblFromReset = new JLabel("        from Reset");
		footerPanelLeft.add(lblFromReset);
		
		textFromReset = new JTextField();
		footerPanelLeft.add(textFromReset);
//		if (START_RESET == 0)
//			textFromReset.setText("Last");
//		else
			textFromReset.setText(Integer.toString(START_RESET));

		textFromReset.setColumns(8);
//		textFromReset.setPreferredSize(new Dimension(50,14));
		textFromReset.addActionListener(this);
		
		lblFromUptime = new JLabel(" from Uptime");
		footerPanelLeft.add(lblFromUptime);
		
		textFromUptime = new JTextField();
		footerPanelLeft.add(textFromUptime);
//		if (START_UPTIME == 0)
//			textFromUptime.setText("Last");
//		else
			textFromUptime.setText(Long.toString(START_UPTIME));
		textFromUptime.setColumns(8);
//		textFromUptime.setPreferredSize(new Dimension(50,14));
		textFromUptime.addActionListener(this);
		
		chckbxPlotAllUptime = new JCheckBox("Continuous");
		chckbxPlotAllUptime.setToolTipText("");
		footerPanelLeft.add(chckbxPlotAllUptime);
	
		chckbxPlotAllUptime.addItemListener(this);
	
	}

	private void setAvgVisible(boolean f) {
		lblAvg.setVisible(f);
		txtAvgPeriod.setVisible(f);
		lblAvgPeriod.setVisible(f);
		
	}
	
	public JButton createIconButton(String icon, String name, String toolTip) {
		JButton btn;
		BufferedImage wPic = null;
		try {
			wPic = ImageIO.read(this.getClass().getResource(icon));
		} catch (IOException e) {
			e.printStackTrace(Log.getWriter());
		}
		if (wPic != null) {
			btn = new JButton(new ImageIcon(wPic));
			btn.setMargin(new Insets(0,0,0,0));
		} else {
			btn = new JButton(name);	
		}
		btn.setToolTipText(toolTip);
		
		btn.addActionListener(this);
		return btn;
	}
	
	public void updateGraphData() {
		if (this.textDisplay) {
			diagnosticTable.updateData();
			//textArea.updateData();			
		} else
			panel.updateGraphData();
	}

	/**
	 * Save properties that are not captured realtime.  This is mainly generic properties such as the size of the
	 * window that are not tied to a control that we have added.
	 */
	public void saveProperties(boolean open) {
		//Log.println("Saving graph properties: " + fieldName);
		Config.saveGraphIntParam(fox.getIdString(), fieldName, "windowHeight", this.getHeight());
		Config.saveGraphIntParam(fox.getIdString(), fieldName, "windowWidth", this.getWidth());
		Config.saveGraphIntParam(fox.getIdString(), fieldName, "windowX", this.getX());
		Config.saveGraphIntParam(fox.getIdString(), fieldName, "windowY", this.getY());
		
		Config.saveGraphIntParam(fox.getIdString(), fieldName, "numberOfSamples", this.SAMPLES);
		Config.saveGraphIntParam(fox.getIdString(), fieldName, "fromReset", this.START_RESET);
		Config.saveGraphLongParam(fox.getIdString(), fieldName, "fromUptime", this.START_UPTIME);
		Config.saveGraphBooleanParam(fox.getIdString(), fieldName, "open", open);
	}

	public boolean loadProperties() {
		int windowX = Config.loadGraphIntValue(fox.getIdString(), fieldName, "windowX");
		int windowY = Config.loadGraphIntValue(fox.getIdString(), fieldName, "windowY");
		int windowWidth = Config.loadGraphIntValue(fox.getIdString(), fieldName, "windowWidth");
		int windowHeight = Config.loadGraphIntValue(fox.getIdString(), fieldName, "windowHeight");
		if (windowX == 0 ||windowY == 0 ||windowWidth == 0 ||windowHeight == 0)
			setBounds(100, 100, 740, 400);
		else
			setBounds(windowX, windowY, windowWidth, windowHeight);

		this.SAMPLES = Config.loadGraphIntValue(fox.getIdString(), fieldName, "numberOfSamples");
		if (SAMPLES == 0) SAMPLES = DEFAULT_SAMPLES;
		if (SAMPLES > MAX_SAMPLES) {
			SAMPLES = MAX_SAMPLES;
		}
			
		this.START_RESET = Config.loadGraphIntValue(fox.getIdString(), fieldName, "fromReset");
		this.START_UPTIME = Config.loadGraphLongValue(fox.getIdString(), fieldName, "fromUptime");
		boolean open = Config.loadGraphBooleanValue(fox.getIdString(), fieldName, "open");
		return open;
	}

	
	@Override
	public void windowActivated(WindowEvent e) {
		
		
	}


	@Override
	public void windowClosed(WindowEvent e) {
		saveProperties(false);
	}


	@Override
	public void windowClosing(WindowEvent e) {
		//saveProperties(false);
	}


	@Override
	public void windowDeactivated(WindowEvent e) {
		
		
	}


	@Override
	public void windowDeiconified(WindowEvent e) {
		
		
	}


	@Override
	public void windowIconified(WindowEvent e) {
		
		
	}


	@Override
	public void windowOpened(WindowEvent e) {
		
		
	}

	private void parseAvgPeriod() {
		String text = txtAvgPeriod.getText();
		try {
			AVG_PERIOD = Integer.parseInt(text);
			if (AVG_PERIOD > MAX_AVG_SAMPLES) {
				AVG_PERIOD = MAX_AVG_SAMPLES;
				text = Integer.toString(MAX_AVG_SAMPLES);
			}
		} catch (NumberFormatException ex) {
			
		}
		if (textDisplay)
			diagnosticTable.updateData();
		else
			panel.updateGraphData();
	}
	
	private void parseTextFields() {
		String text = txtSamplePeriod.getText();
		try {
			SAMPLES = Integer.parseInt(text);
			if (SAMPLES > MAX_SAMPLES) {
				SAMPLES = MAX_SAMPLES;
				text = Integer.toString(MAX_SAMPLES);
			}
			//System.out.println(SAMPLES);
			
			//lblActual.setText("("+text+")");
			//txtSamplePeriod.setText("");
		} catch (NumberFormatException ex) {
			
		}
		text = textFromReset.getText();
		try {
			START_RESET = Integer.parseInt(text);
			if (START_RESET < 0) START_RESET = 0;
			
		} catch (NumberFormatException ex) {
			if (text.equals("")) {
				START_RESET = DEFAULT_START_RESET;
				
			}
		}
		text = textFromUptime.getText();
		try {
			START_UPTIME = Integer.parseInt(text);
			if (START_UPTIME < 0) START_UPTIME = 0;
			
		} catch (NumberFormatException ex) {
			if (text.equals("")) {
				START_UPTIME = DEFAULT_START_UPTIME;
				
			}
		}
		if (textDisplay)
			diagnosticTable.updateData();
		else
			panel.updateGraphData();
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == this.txtSamplePeriod) {
			parseTextFields();
			
		} else if (e.getSource() == this.textFromReset) {
			parseTextFields();
			
		} else if (e.getSource() == this.textFromUptime) {
			parseTextFields();
			
		} else if (e.getSource() == this.txtAvgPeriod) {
				parseAvgPeriod();
		} else if (e.getSource() == btnLatest) {
			textFromReset.setText(Long.toString(DEFAULT_START_UPTIME));
			textFromUptime.setText(Integer.toString(DEFAULT_START_RESET));
			txtSamplePeriod.setText(Integer.toString(DEFAULT_SAMPLES));

			parseTextFields();
		} else if (e.getSource() == btnCSV) {
			File file = null;
			File dir = null;
			if (!Config.csvCurrentDirectory.equalsIgnoreCase("")) {
				dir = new File(Config.csvCurrentDirectory);
			}
			if(Config.useNativeFileChooser) {
				// use the native file dialog on the mac
				FileDialog fd =
						new FileDialog(this, "Choose or enter CSV file to save graph results",FileDialog.SAVE);
				if (dir != null) {
					fd.setDirectory(dir.getAbsolutePath());
				}
	//			FilenameFilter filter = new FilenameFilter("CSV Files", "csv", "txt");
	//			fd.setFilenameFilter(filter);
				fd.setVisible(true);
				String filename = fd.getFile();
				String dirname = fd.getDirectory();
				if (filename == null)
					Log.println("You cancelled the choice");
				else {
					Log.println("File: " + filename);
					Log.println("DIR: " + dirname);
					file = new File(dirname + filename);
				}
			} else {

				JFileChooser fc = new JFileChooser();
				fc.setApproveButtonText("Save");
				if (dir != null) {
					fc.setCurrentDirectory(dir);	
				}				
				FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files", "csv", "txt");
				fc.setFileFilter(filter);
				fc.setDialogTitle("Choose or enter CSV file to save graph results");
				fc.setPreferredSize(new Dimension(Config.windowFcWidth, Config.windowFcHeight));
				int returnVal = fc.showOpenDialog(this);
				Config.windowFcHeight = fc.getHeight();
				Config.windowFcWidth = fc.getWidth();		


				if (returnVal == JFileChooser.APPROVE_OPTION) { 
					file = fc.getSelectedFile();
				}
			}
			if (file != null) {
				Config.csvCurrentDirectory = file.getParent();
				try {
					saveToCSV(file);
				} catch (IOException e1) {
					JOptionPane.showMessageDialog(MainWindow.frame,
							e1.toString(),
							"ERROR WRITING FILE",
							JOptionPane.ERROR_MESSAGE) ;

					e1.printStackTrace(Log.getWriter());
				}
			} else {
				System.out.println("No Selection ");
			}
		}  else if (e.getSource() == btnCopy) {
			copyToClipboard();
			Log.println("Graph copied to clipboard");
		}  else if (e.getSource() == btnDerivative) {
			plotDerivative = !plotDerivative;
			//Log.println("Plot Derivative " + plotDerivative);
			if (plotDerivative) {	
				btnDerivative.setBackground(Color.RED);
			} else
				btnDerivative.setBackground(Color.GRAY);
			panel.updateGraphData();
		}  else if (e.getSource() == btnAvg) {
			dspAvg = !dspAvg;
			if (dspAvg) {	
				btnAvg.setBackground(Color.RED);
			} else
				btnAvg.setBackground(Color.GRAY);
			setAvgVisible(dspAvg);
			//Log.println("Calc Average " + dspAvg);
			panel.updateGraphData();
		} else if (e.getSource() == btnMain) {
			displayMain = !displayMain;
			if (!displayMain) {	
				btnMain.setBackground(Color.RED);
			} else
				btnMain.setBackground(Color.GRAY);

			panel.updateGraphData();
		}
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getSource() == chckbxPlotAllUptime) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				UPTIME_THRESHOLD = DEFAULT_UPTIME_THRESHOLD;
			} else {
				UPTIME_THRESHOLD = CONTINUOUS_UPTIME_THRESHOLD;
			}
			if (textDisplay)
				textArea.updateData();
			else
				panel.updateGraphData();
		}
	}

	private void saveToCSV(File aFile) throws IOException {
		double[][] graphData = null;
		
		if (payloadType == FramePart.TYPE_REAL_TIME)
			graphData = Config.payloadStore.getRtGraphData(fieldName, this.SAMPLES, fox, this.START_RESET, this.START_UPTIME);
		else if (payloadType == FramePart.TYPE_MAX_VALUES)
			graphData = Config.payloadStore.getMaxGraphData(fieldName, this.SAMPLES, fox, this.START_RESET, this.START_UPTIME);
		else if (payloadType == FramePart.TYPE_MIN_VALUES)
			graphData = Config.payloadStore.getMinGraphData(fieldName, this.SAMPLES, fox, this.START_RESET, this.START_UPTIME);
		else if  (payloadType == 0) // measurement
			graphData = Config.payloadStore.getMeasurementGraphData(fieldName, this.SAMPLES, this.fox, this.START_RESET, this.START_UPTIME);
		
		if (graphData != null) {
			if(!aFile.exists()){
				aFile.createNewFile();
			} else {

			}
			Writer output = new BufferedWriter(new FileWriter(aFile, false));

			for (int i=0; i< graphData[0].length; i++) {
				output.write( graphData[PayloadStore.RESETS_COL][i] + ", " +
						graphData[PayloadStore.UPTIME_COL][i] + ", " +
						graphData[PayloadStore.DATA_COL][i] + "\n" );
			}

			output.flush();
			output.close();

		}
		
	}
	
	/*
	 * Copy the graph panel to the clipboard
	 */
	public void copyToClipboard() {
		
		BufferedImage img = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics imgGraphics = img.getGraphics(); // grab the graphics "pen" for the image
		panel.printAll(imgGraphics); // write the data to the image
		
		write(img);	
	}
	
	 /**
     *  Image to system clipboard
     *  @param  image - the image to be added to the system clipboard
     */
    public static void write(Image image)
    {
        if (image == null)
            throw new IllegalArgumentException ("Image can't be null");

        ImageTransferable transferable = new ImageTransferable( image );
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
    }

    static class ImageTransferable implements Transferable
    {
        private Image image;

        public ImageTransferable (Image image)
        {
            this.image = image;
        }

        public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException
        {
            if (isDataFlavorSupported(flavor))
            {
                return image;
            }
            else
            {
                throw new UnsupportedFlavorException(flavor);
            }
        }

        public boolean isDataFlavorSupported (DataFlavor flavor)
        {
            return flavor == DataFlavor.imageFlavor;
        }

        public DataFlavor[] getTransferDataFlavors ()
        {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }
    }

}
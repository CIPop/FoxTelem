package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import common.Config;
import common.Log;
import common.PassManager;
import decoder.Decoder;
import decoder.Fox200bpsDecoder;
import decoder.Fox9600bpsDecoder;
import decoder.SinkAudio;
import decoder.SourceAudio;
import decoder.SourceIQ;
import decoder.SourceSoundCardAudio;
import decoder.SourceWav;
import fcd.FcdDevice;
import fcd.FcdException;

import javax.swing.JProgressBar;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.PopupMenuEvent;

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
 * This class implements the Input Tab and logic required to stop / start the decoders, display the diagnostic graphs
 * and listen to the audio.
 *
 */
@SuppressWarnings("serial")
public class SourceTab extends JPanel implements ItemListener, ActionListener, PropertyChangeListener {
	Thread audioGraphThread;
	Thread eyePanelThread;

	Thread fftPanelThread;
	Thread decoderThread;
	AudioGraphPanel audioGraph;
	EyePanel eyePanel;
	public FFTPanel fftPanel;
	JButton btnMonitorAudio;
	JCheckBox rdbtnMonitorFilteredAudio;
	JCheckBox rdbtnViewFilteredAudio;
	JCheckBox rdbtnSquelchAudio;
	JCheckBox rdbtnFilterOutputAudio;
	JCheckBox rdbtnWriteDebugData;
	//JCheckBox rdbtnApplyBlackmanWindow;
	//JCheckBox rdbtnUseLimiter;
	//JCheckBox rdbtnShowIF;
	JCheckBox rdbtnTrackSignal;
	JCheckBox rdbtnFindSignal;
	JCheckBox rdbtnShowLog;
	JCheckBox rdbtnShowFFT;
	JCheckBox rdbtnFcdLnaGain;
	JCheckBox rdbtnFcdMixerGain;
	//JCheckBox rdbtnUseNco;
	JComboBox<String> speakerComboBox;
	JButton btnStartButton;
	JComboBox<String> soundCardComboBox;
	JLabel lblFileName;
	JLabel lblFile;
	JComboBox<String> cbSoundCardRate;
	JPanel panelFile;
	JRadioButton highSpeed;
	JRadioButton lowSpeed;
	JRadioButton iqAudio;
	JRadioButton afAudio;
	JTextArea log;
	JScrollPane logScrollPane;
	
	FilterPanel filterPanel;
	
	FcdDevice fcd;
	
	static final int RATE_96000_IDX = 2;
	static final int RATE_192000_IDX = 3;
	
	private Task task;
	Thread progressThread;
	
	// Swing File Chooser
	JFileChooser fc = null;
	//AWT file chooser for the Mac
	FileDialog fd = null;
	
	// Variables
	public static final String FUNCUBE = "FUNcube";
//	public static final String FUNCUBE = "XXXXXXX";  // hack to disable the func cube option
	Decoder decoder;
	SourceIQ iqSource;
	//SourceAudio audioSource = null; // this is the source of the audio for the decoder.  We select it in the GUI and pass it to the decoder to use
	SinkAudio sink = null;
	private boolean monitorFiltered;
	
	public static boolean STARTED = false;
	//private JPanel leftPanel_1;
	JLabel lblFreq;
	JLabel lblkHz;
	private JTextField txtFreq;
	MainWindow mainWindow;
	private JProgressBar progressBar;
	
	public SourceTab(MainWindow mw) {
		mainWindow = mw;
		setLayout(new BorderLayout(3, 3));
		
		JPanel bottomPanel = new JPanel();
		buildBottomPanel(this, BorderLayout.CENTER, bottomPanel);
		JPanel optionsRowPanel = new JPanel();
		buildOptionsRow(this, BorderLayout.SOUTH, optionsRowPanel);
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BorderLayout(3, 3));
		add(topPanel, BorderLayout.NORTH);
		
		JPanel rightPanel = new JPanel();	
		buildRightPanel(topPanel, BorderLayout.EAST, rightPanel);

		JPanel leftPanel_1 = new JPanel();
		buildLeftPanel(topPanel,  BorderLayout.CENTER, leftPanel_1);

		//if (Config.useNativeFileChooser) {
			fd = new FileDialog(MainWindow.frame, "Select Wav file",FileDialog.LOAD);
			fd.setFile("*.wav");
		//} else {
			fc = new JFileChooser();
			// initialize the file chooser
			FileNameExtensionFilter filter = new FileNameExtensionFilter(
			        "Wav files", "wav", "wave");
			fc.setFileFilter(filter);
		//}
		
	}
	
	public void showFilters(boolean b) { filterPanel.setVisible(b); }
//	public void showDecoderOptions(boolean b) { optionsPanel.setVisible(b); }
	
	public void enableFilters(boolean b) {
		Component[] components = filterPanel.getComponents();
		for (Component c : components) {
			c.setEnabled(b);
		}
	}

	private void buildOptionsRow(JPanel parent, String layout, JPanel optionsPanel) {
		parent.add(optionsPanel, layout);

		optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.X_AXIS));

		rdbtnShowFFT = new JCheckBox("Show FFT");
		rdbtnShowFFT.addItemListener(this);
		rdbtnShowFFT.setSelected(true);
		optionsPanel.add(rdbtnShowFFT);
		rdbtnShowFFT.setVisible(false);
		
		/*
		rdbtnApplyBlackmanWindow = new JCheckBox("Blackman IF (vs Tukey)");
		optionsPanel.add(rdbtnApplyBlackmanWindow);
		rdbtnApplyBlackmanWindow.addItemListener(this);
		rdbtnApplyBlackmanWindow.setSelected(Config.applyBlackmanWindow);
		rdbtnApplyBlackmanWindow.setVisible(false);
		 
		rdbtnShowIF = new JCheckBox("Show IF");
		optionsPanel.add(rdbtnShowIF);
		rdbtnShowIF.addItemListener(this);
		rdbtnShowIF.setSelected(Config.showIF);
		rdbtnShowIF.setVisible(false);
		 */
		rdbtnTrackSignal = new JCheckBox("Track Doppler");
		optionsPanel.add(rdbtnTrackSignal);
		rdbtnTrackSignal.addItemListener(this);
		rdbtnTrackSignal.setSelected(Config.trackSignal);
		rdbtnTrackSignal.setVisible(true);

		rdbtnFindSignal = new JCheckBox("Find Signal");
		optionsPanel.add(rdbtnFindSignal);
		rdbtnFindSignal.addItemListener(this);
		rdbtnFindSignal.setSelected(Config.findSignal);
		rdbtnFindSignal.setVisible(true);

		/*
		rdbtnUseNco = new JCheckBox("Use NCO carrier");
		rdbtnUseNco.addItemListener(this);
		rdbtnUseNco.setSelected(SourceIQ.useNCO);
		optionsPanel.add(rdbtnUseNco);
		*/

		rdbtnFcdLnaGain = new JCheckBox("LNA Gain");
		rdbtnFcdLnaGain.addItemListener(this);
		rdbtnFcdLnaGain.setSelected(false);
		optionsPanel.add(rdbtnFcdLnaGain);
		rdbtnFcdLnaGain.setVisible(false);

		rdbtnFcdMixerGain = new JCheckBox("Mixer Gain");
		rdbtnFcdMixerGain.addItemListener(this);
		rdbtnFcdMixerGain.setSelected(false);
		optionsPanel.add(rdbtnFcdMixerGain);
		rdbtnFcdMixerGain.setVisible(false);

	}
	
	private void buildBottomPanel(JPanel parent, String layout, JPanel bottomPanel) {
		parent.add(bottomPanel, layout);
		////bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		bottomPanel.setLayout(new BorderLayout(3, 3));
		bottomPanel.setPreferredSize(new Dimension(800, 250));
		/*
		JPanel audioOpts = new JPanel();
		bottomPanel.add(audioOpts, BorderLayout.NORTH);

		rdbtnShowFFT = new JCheckBox("Show FFT");
		rdbtnShowFFT.addItemListener(this);
		rdbtnShowFFT.setSelected(true);
		audioOpts.add(rdbtnShowFFT);
		*/
		audioGraph = new AudioGraphPanel();
		audioGraph.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		bottomPanel.add(audioGraph, BorderLayout.CENTER);
		audioGraph.setBackground(Color.LIGHT_GRAY);
		//audioGraph.setPreferredSize(new Dimension(800, 250));
		
		if (audioGraphThread != null) { audioGraph.stopProcessing(); }		
		audioGraphThread = new Thread(audioGraph);
		audioGraphThread.setUncaughtExceptionHandler(Log.uncaughtExHandler);
		audioGraphThread.start();

		eyePanel = new EyePanel();
		eyePanel.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		bottomPanel.add(eyePanel, BorderLayout.EAST);
		eyePanel.setBackground(Color.LIGHT_GRAY);
		eyePanel.setPreferredSize(new Dimension(200, 100));
		eyePanel.setMaximumSize(new Dimension(200, 100));
		
		fftPanel = new FFTPanel();
		fftPanel.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		fftPanel.setBackground(Color.LIGHT_GRAY);
		
		bottomPanel.add(fftPanel, BorderLayout.SOUTH);
		fftPanel.setVisible(false);
		fftPanel.setPreferredSize(new Dimension(100, 150));
		fftPanel.setMaximumSize(new Dimension(100, 150));
		
	}
	
	private void buildRightPanel(JPanel parent, String layout, JPanel rightPanel) {
		parent.add(rightPanel, layout);
//		rightPanel.setPreferredSize(new Dimension(800, 250));
//		rightPanel.setLayout(new GridLayout(0, 3, 0, 0));
//		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));
			
		JPanel opts = new JPanel();
		rightPanel.add(opts);
//		opts.setLayout(new BoxLayout(opts, BoxLayout.Y_AXIS));
		opts.setLayout(new BorderLayout());
		
		JPanel optionsPanel = new JPanel();
		optionsPanel.setBorder(new TitledBorder(null, "Audio Options", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		opts.add(optionsPanel, BorderLayout.CENTER);
		optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
		
//		spacecraftPanel = new SpacecraftPanel();
//		opts.add(spacecraftPanel, BorderLayout.CENTER);
		
		filterPanel = new FilterPanel();
		opts.add(filterPanel, BorderLayout.NORTH);
		
		rdbtnViewFilteredAudio = new JCheckBox("View Filtered Audio");
		optionsPanel.add(rdbtnViewFilteredAudio);
		rdbtnViewFilteredAudio.addItemListener(this);
		rdbtnViewFilteredAudio.setSelected(Config.viewFilteredAudio);
		
		rdbtnMonitorFilteredAudio = new JCheckBox("Monitor Filtered Audio");
		optionsPanel.add(rdbtnMonitorFilteredAudio);
		rdbtnMonitorFilteredAudio.addItemListener(this);
		rdbtnMonitorFilteredAudio.setSelected(Config.monitorFilteredAudio);

		rdbtnSquelchAudio = new JCheckBox("Squelch when no telemetry");
		optionsPanel.add(rdbtnSquelchAudio);
		rdbtnSquelchAudio.addItemListener(this);
		rdbtnSquelchAudio.setSelected(Config.squelchAudio);

		rdbtnFilterOutputAudio = new JCheckBox("LPF Monitored Audio");
		optionsPanel.add(rdbtnFilterOutputAudio);
		rdbtnFilterOutputAudio.addItemListener(this);
		rdbtnFilterOutputAudio.setSelected(Config.filterOutputAudio);
		rdbtnFilterOutputAudio.setVisible(false);
		
	//	rdbtnUseLimiter = new JCheckBox("Use FM Limiter");
	//	optionsPanel.add(rdbtnUseLimiter);
	//	rdbtnUseLimiter.addItemListener(this);
	//	rdbtnUseLimiter.setSelected(Config.useLimiter);
	//	rdbtnUseLimiter.setVisible(true);


	//	rdbtnWriteDebugData = new JCheckBox("Debug Values");
	//	optionsPanel.add(rdbtnWriteDebugData);
	//	rdbtnWriteDebugData.addItemListener(this);
	//	rdbtnWriteDebugData.setSelected(Config.debugValues);
	//	rdbtnWriteDebugData.setVisible(true);

//		optionsPanel.setVisible(true);
		filterPanel.setVisible(true);

	}
	
	public void log(String text) {
		if (rdbtnShowLog.isSelected()) {
			log.append(text);
			//log.setLineWrap(true);
			log.setCaretPosition(log.getLineCount());
		}
	}
	
	private void buildLeftPanel(JPanel parent, String layout, JPanel leftPanel) {
		parent.add(leftPanel, layout);

		leftPanel.setLayout(new BorderLayout(3, 3));
		
		JPanel panel_2 = new JPanel();		
		leftPanel.add(panel_2, BorderLayout.NORTH);
		panel_2.setMinimumSize(new Dimension(10, 35));
		panel_2.setLayout(new BoxLayout(panel_2, BoxLayout.X_AXIS));
		
		JLabel lblSource = new JLabel("Source");	
		panel_2.add(lblSource);
		lblSource.setAlignmentX(Component.LEFT_ALIGNMENT);
		lblSource.setMinimumSize(new Dimension(180, 14));
		lblSource.setMaximumSize(new Dimension(180, 14));
		
		lowSpeed = addRadioButton("Low Speed", panel_2 );
		highSpeed = addRadioButton("High Speed", panel_2 );
		ButtonGroup group = new ButtonGroup();
		group.add(lowSpeed);
		group.add(highSpeed);
		if (Config.highSpeed) {
			highSpeed.setSelected(true);
			enableFilters(false);
		} else {
			lowSpeed.setSelected(true);
			enableFilters(true);
		}
		
		JPanel centerPanel = new JPanel();		
		leftPanel.add(centerPanel, BorderLayout.CENTER);	
		centerPanel.setLayout( new BorderLayout(3, 3));
		
		JPanel panel_1 = new JPanel();
		centerPanel.add(panel_1, BorderLayout.NORTH);

		btnStartButton = new JButton("Start");
		panel_1.add(btnStartButton);
		btnStartButton.addActionListener(this);
		btnStartButton.setEnabled(false);
		btnStartButton.setAlignmentX(Component.CENTER_ALIGNMENT);

		soundCardComboBox = new JComboBox<String>(SourceSoundCardAudio.getAudioSources());
		soundCardComboBox.addPopupMenuListener(new PopupMenuListener() {
			public void popupMenuCanceled(PopupMenuEvent arg0) {
			}
			public void popupMenuWillBecomeInvisible(PopupMenuEvent arg0) {
			}
			public void popupMenuWillBecomeVisible(PopupMenuEvent arg0) {
				//Log.println("Rebuild Sound card List");
				soundCardComboBox.removeAllItems();
				for (String s: SourceSoundCardAudio.getAudioSources()) {
					soundCardComboBox.addItem(s);
				}
				//soundCardComboBox.showPopup();
			}
		});
		soundCardComboBox.addActionListener(this);
		panel_1.add(soundCardComboBox);
		
		String[] scRates = {"44100", "48000", "96000", "192000"}; 
		cbSoundCardRate = new JComboBox<String>(scRates);
		cbSoundCardRate.setVisible(false);
		cbSoundCardRate.addActionListener(this);
		panel_1.add(cbSoundCardRate);
		cbSoundCardRate.setSelectedItem(Integer.toString(Config.scSampleRate));
		
		afAudio = addRadioButton("AF", panel_1 );
		iqAudio = addRadioButton("IQ", panel_1 );
		ButtonGroup group2 = new ButtonGroup();
		group2.add(afAudio);
		group2.add(iqAudio);
		
		JPanel panel_c = new JPanel();
		centerPanel.add(panel_c);
		panel_c.setLayout(new BoxLayout(panel_c, BoxLayout.Y_AXIS));
		

		JPanel panelSDR = new JPanel();
		panel_c.add(panelSDR);
		//panelSDR.setLayout(new BoxLayout(panelSDR, BoxLayout.Y_AXIS));
		panelSDR.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
		
		JPanel panelFreq = new JPanel();
		panelSDR.add(panelFreq, BorderLayout.CENTER);
		panelFreq.setLayout(new BoxLayout(panelFreq, BoxLayout.X_AXIS));
		
		lblFreq = new JLabel("Center Frequency ");
		panelFreq.add(lblFreq);
		lblFreq.setVisible(false);
		
		txtFreq = new JTextField(Long.toString(Config.fcdFrequency));
		txtFreq.addActionListener(this);
		panelFreq.add(txtFreq);
		txtFreq.setColumns(10);
		txtFreq.setVisible(false);

		lblkHz = new JLabel(" kHz");
		panelFreq.add(lblkHz);
		lblkHz.setVisible(false);

		if (Config.iq) {
			iqAudio.doClick();  // we want to trigger the action event so the window is setup correctly at startup
		} else {
			afAudio.doClick();
		}

		panelFile = new JPanel();
	//	leftPanel.add(panelFile, BorderLayout.SOUTH);	
		panel_c.add(panelFile);
		panelFile.setLayout(new BoxLayout(panelFile, BoxLayout.Y_AXIS));
		
		lblFile = new JLabel("File: ");
		lblFileName = new JLabel("none");
//		panel_2.add(lblFileName);
		JPanel fileNamePanel = new JPanel();
		fileNamePanel.setLayout(new BoxLayout(fileNamePanel, BoxLayout.X_AXIS));
		panelFile.add(fileNamePanel);	
		fileNamePanel.add(lblFile);	
		fileNamePanel.add(lblFileName);	
		lblFileName.setForeground(Color.GRAY);
		lblFileName.setHorizontalAlignment(SwingConstants.LEFT);
		
		progressBar = new JProgressBar();
		panelFile.add(progressBar);
		progressBar.setValue(0);
        progressBar.setStringPainted(true);
		
		panelFile.setVisible(false);
		
//		log = new JTextArea();
//		log.setRows(20); 
//		log.setEditable(false);
//		logScrollPane = new JScrollPane (log, 
//				   JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
//		centerPanel.add(logScrollPane);
//		logScrollPane.setVisible(false);
		
//		spacecraftPanel = new SpacecraftPanel();
//		centerPanel.add(spacecraftPanel, BorderLayout.SOUTH);
		
//		JSeparator separator = new JSeparator();
//		centerPanel.add(separator);
		
		JPanel southPanel = new JPanel();
		leftPanel.add(southPanel, BorderLayout.SOUTH);
		southPanel.setLayout(new BorderLayout(3, 3));
		//southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));

		JPanel panel_3 = new JPanel();
		southPanel.add(panel_3, BorderLayout.NORTH);
		
		panel_3.setMinimumSize(new Dimension(10, 35));
		panel_3.setLayout(new BoxLayout(panel_3, BoxLayout.Y_AXIS));
		
//		rdbtnShowLog = new JCheckBox("Show Log");
//		panel_3.add(rdbtnShowLog);
//		rdbtnShowLog.addItemListener(this);
//		rdbtnShowLog.setSelected(Config.showLog);
		
		
		JLabel lblOutput = new JLabel("Output");	
		panel_3.add(lblOutput);
		lblSource.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		//sink = new SinkAudio();
		
		JPanel panelCombo = new JPanel();
		southPanel.add(panelCombo, BorderLayout.CENTER);
		speakerComboBox = new JComboBox<String>(SinkAudio.getAudioSinks());

		speakerComboBox.addPopupMenuListener(new PopupMenuListener() {
			public void popupMenuCanceled(PopupMenuEvent arg0) {
			}
			public void popupMenuWillBecomeInvisible(PopupMenuEvent arg0) {
			}
			public void popupMenuWillBecomeVisible(PopupMenuEvent arg0) {
				//Log.println("Rebuild Sink List");
				speakerComboBox.removeAllItems();
				for (String s:SinkAudio.getAudioSinks()) {
					speakerComboBox.addItem(s);
				}
				//speakerComboBox.showPopup();
			}
		});
		speakerComboBox.addActionListener(this);
		
		btnMonitorAudio = new JButton("Monitor Audio");
		if (Config.monitorAudio) {
			btnMonitorAudio.setText("Silence Speaker");
			speakerComboBox.setEnabled(false);
		}
		panelCombo.add(btnMonitorAudio);
		btnMonitorAudio.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnMonitorAudio.addActionListener(this);
		//btnMonitorAudio.setEnabled(false);
		panelCombo.add(speakerComboBox);
		
		if (Config.soundCard != null && !Config.soundCard.equalsIgnoreCase(Config.NO_SOUND_CARD_SELECTED)) {
			soundCardComboBox.setSelectedIndex(SourceSoundCardAudio.getDeviceIdByName(Config.soundCard));
		}

		if (Config.audioSink != null && !Config.audioSink.equalsIgnoreCase(Config.NO_SOUND_CARD_SELECTED)) {
			speakerComboBox.setSelectedIndex(SinkAudio.getDeviceIdByName(Config.audioSink));
		}
		
	}

	private JRadioButton addRadioButton(String name, JPanel panel) {
		JRadioButton radioButton = new JRadioButton(name);
		radioButton.setEnabled(true);
		radioButton.addActionListener(this);
		panel.add(radioButton);
		return radioButton;
	}
		

	public boolean fileActions() {
		File file = null;
		File dir = null;
		if (Config.windowCurrentDirectory != "") {
			dir = new File(Config.windowCurrentDirectory);
		}
		soundCardComboBox.hidePopup();

		if(Config.useNativeFileChooser) {
			// use the native file dialog on the mac

			if (dir != null) {
				fd.setDirectory(dir.getAbsolutePath());
			}
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
			fc.setPreferredSize(new Dimension(Config.windowFcWidth, Config.windowFcHeight));
			if (dir != null)
				fc.setCurrentDirectory(dir);
			// This toggles the details view on
			//		Action details = fc.getActionMap().get("viewTypeDetails");
			//		details.actionPerformed(null);

			int returnVal = fc.showOpenDialog(this);
			Config.windowFcHeight = fc.getHeight();
			Config.windowFcWidth = fc.getWidth();		
			//System.out.println("dialog type: " + fc.getDialogType());
			//System.out.println("select mode: " + fc.getFileSelectionMode());
			//System.out.println("select model: " + fc.getFileSelectionMode());
			//System.out.println("ch file filter: "); for ( FileFilter F: fc.getChoosableFileFilters()) System.out.println(F);
			//System.out.println("select model: " + fc.getFileSystemView());


			//Config.save();
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				file = fc.getSelectedFile();
			}
		}

		if (file != null) {
			Config.windowCurrentDirectory = file.getParent();					
			lblFileName.setText(file.getName());
			panelFile.setVisible(true);
			btnStartButton.setEnabled(true);
			cbSoundCardRate.setVisible(false);
			return true;
		}
		return false;
	}
	
	public void chooseFile() {
		soundCardComboBox.setSelectedIndex(SourceAudio.FILE_SOURCE);
	}

	public void chooseIQFile() {
		soundCardComboBox.setSelectedIndex(SourceAudio.FILE_SOURCE);
		iqAudio.setSelected(true);
		//soundCardComboBox.setSelectedIndex(SourceAudio.IQ_FILE_SOURCE);
	}
	

	private void setIQVisible(boolean b) {
		fftPanel.setVisible(b);
		rdbtnShowFFT.setVisible(b);
//		rdbtnShowIF.setVisible(b);
		rdbtnTrackSignal.setVisible(b);
		rdbtnFindSignal.setVisible(b);
//		rdbtnApplyBlackmanWindow.setVisible(b);
		setFreqVisible(b);
	}
	
	private void setFreqVisible(boolean b) {
		lblFreq.setVisible(b);
		lblkHz.setVisible(b);
		txtFreq.setVisible(b);
	}
	
	
	@Override
	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource() == highSpeed) { 
				Config.highSpeed = true;
				enableFilters(false);
				//Config.save();
		}
		if (e.getSource() == lowSpeed) { 
			Config.highSpeed = false;
			enableFilters(true);
			//Config.save();
		}

		if (e.getSource() == iqAudio) { 
			Config.iq = true;
			setIQVisible(true);
		}
		if (e.getSource() == afAudio) { 
			Config.iq = false;
			setIQVisible(false);
		}

		// Frequency Text Field
		if (e.getSource() == this.txtFreq) {
			//String text = txtFreq.getText();
			txtFreq.selectAll();
			int freq = Integer.parseInt(txtFreq.getText());
			if (iqSource != null)
				(iqSource).setCenterFreqkHz(freq);
			Config.fcdFrequency = freq;
			if (fcd != null) {
				if (freq < 100 || freq > 2500000) {
					Log.errorDialog("FCD ERROR", "Frequency must be between 100 and 2500000");
				} else {
					try {
						fcd.setFcdFreq(freq*1000);
					} catch (FcdException e1) {
						Log.errorDialog("ERROR", e1.getMessage());
						e1.printStackTrace(Log.getWriter());
					}
				}
				//lblFreqvalue.setText(Integer.toString(freq));
				//textFileName.setText("");
			} else {
//				Log.errorDialog("ERROR", "Cant set the frequency of the FCD");
			}
		}
		
		// SOUND CARD AND FILE COMBO BOX
		if (e.getSource() == soundCardComboBox) {
			processSoundCardComboBox();
		}

		// SPEAKER OUTPUT COMBO BOX
		if (e.getSource() == speakerComboBox) {
			//String source = (String)speakerComboBox.getSelectedItem();
		}
		
		// USER CHANGES THE SAMPLE RATE
		if (e.getSource() == cbSoundCardRate) {
			// store the value so it is saved if we exit
			Config.scSampleRate = Integer.parseInt((String) cbSoundCardRate.getSelectedItem());
		}
		
		// MONITOR AUDIO BUTTON
		if (e.getSource() == btnMonitorAudio) {
			try {
			if (decoder != null) { // then we want to toggle the live audio	
				Config.monitorAudio = decoder.toggleAudioMonitor(sink, monitorFiltered, speakerComboBox.getSelectedIndex());
			} else {
				Config.monitorAudio = !Config.monitorAudio; // otherwise just note that we want to change it for the next decoder run
			}
			} catch (IllegalArgumentException e1) {
				JOptionPane.showMessageDialog(this,
						e1.toString(),
						"ARGUMENT ERROR",
					    JOptionPane.ERROR_MESSAGE) ;
				//e1.printStackTrace();	
			} catch (LineUnavailableException e1) {
				JOptionPane.showMessageDialog(this,
						e1.toString(),
						"LINE UNAVAILABLE ERROR",
					    JOptionPane.ERROR_MESSAGE) ;
			}
			if (Config.monitorAudio) { 
				btnMonitorAudio.setText("Silence Speaker");
				speakerComboBox.setEnabled(false);
			} else {
				btnMonitorAudio.setText("Monitor Audio");
				speakerComboBox.setEnabled(true);
			}
		}

		// START BUTTON
		if (e.getSource() == btnStartButton) {
			processStartButtonClick();
		}

		
	}

	private void setupAudioSink() {
		int position = speakerComboBox.getSelectedIndex();
		
		try {
			sink = new SinkAudio(decoder.getAudioFormat());
			sink.setDevice(position);
		if (position != -1) {
			Config.audioSink = SinkAudio.getDeviceName(position);
			//Config.save();
		}

		} catch (LineUnavailableException e1) {
			JOptionPane.showMessageDialog(this,
					e1.toString(),
					"LINE UNAVAILABLE ERROR",
				    JOptionPane.ERROR_MESSAGE) ;
			//e1.printStackTrace();
			
		} catch (IllegalArgumentException e1) {
			JOptionPane.showMessageDialog(this,
					e1.toString(),
					"ARGUMENT ERROR",
				    JOptionPane.ERROR_MESSAGE) ;
			//e1.printStackTrace();	
		}

	}

	private SourceWav setWavFile() {
		SourceWav audioSource = null;
		//if (audioSource != null)
		//	audioSource.stop();
		try {
			audioSource = new SourceWav(Config.windowCurrentDirectory + File.separator + lblFileName.getText());
		} catch (UnsupportedAudioFileException e) {
			Log.errorDialog("ERROR With Audio File", e.toString());
			e.printStackTrace(Log.getWriter());
			stopButton();
		} catch (IOException e) {
			Log.errorDialog("ERROR With File", e.toString());
			e.printStackTrace(Log.getWriter());
			stopButton();
		}
		if (task != null) { task.resetProgress(); }
		return audioSource;
	}
	
	/**
	 * Process the action from the sound card combo box when an item is selected.  The user has not pressed start, but they have selected
	 * the audio source that we are going to use.  We do nothing at this point other than changes the visibility of GUI components
	 * and setup the FCD if it is selected
	 */
	private void processSoundCardComboBox() {
		String source = (String)soundCardComboBox.getSelectedItem();
		int position = soundCardComboBox.getSelectedIndex();
		if (source == null) {
			// Do nothing
		} else
		if (position <= 0) {

		} else if (position == SourceAudio.FILE_SOURCE) {
			if (fileActions()) {
//				releaseFcd();
			}
		} else { // its not a file so its a sound card or FCD that was picked
			boolean fcdSelected = usingFcd();
			if (fcdSelected) {
				setFcdSampleRate();
			} else {
				Config.scSampleRate = Integer.parseInt((String) cbSoundCardRate.getSelectedItem());	
			}
			
			Config.soundCard = SourceSoundCardAudio.getDeviceName(position); // store this so it gets saved
			btnStartButton.setEnabled(true);
			cbSoundCardRate.setVisible(true);
			panelFile.setVisible(false);
			if (Config.iq) {
				setIQVisible(true);
			} else {
				setIQVisible(false);
			}
		}


	}

	private void releaseFcd() {
		if (fcd != null) { // release the FCD device
			try {
				fcd.cleanup();
			} catch (IOException e) {
				e.printStackTrace(Log.getWriter());
			} catch (FcdException e) {
				e.printStackTrace(Log.getWriter());
			}
			fcd = null;
		}
	}
	
	private boolean usingFcd() {
		boolean fcdSelected = false;
		String source = (String)soundCardComboBox.getSelectedItem();
		Pattern pattern = Pattern.compile(FUNCUBE);
		Matcher matcher = pattern.matcher(source);
		if (matcher.find()) {
			fcdSelected = true;
			try {
				if (fcd == null) {
					fcd = new FcdDevice();
				}
			} catch (IOException e) {
				Log.errorDialog("ERROR", e.getMessage());
				e.printStackTrace(Log.getWriter());
			} catch (FcdException e) {
				Log.errorDialog("ERROR", e.getMessage());
				e.printStackTrace(Log.getWriter());
			}
			Config.iq = true;
			iqAudio.setSelected(true);
			setIQVisible(true);
		} 			

		return fcdSelected;
	}

	private void setFcdSampleRate() {
		Config.scSampleRate = fcd.SAMPLE_RATE;
		if (fcd.SAMPLE_RATE == 96000)
			cbSoundCardRate.setSelectedIndex(RATE_96000_IDX);
		else
			cbSoundCardRate.setSelectedIndex(RATE_192000_IDX);

	}
	
	private SourceSoundCardAudio setupSoundCard(boolean highSpeed, int sampleRate) {
		int position = soundCardComboBox.getSelectedIndex();
		int circularBufferSize = sampleRate * 4;
		if (highSpeed) {
			circularBufferSize = sampleRate * 4;
		} else {
		}		SourceSoundCardAudio audioSource = null;
		try {
			audioSource = new SourceSoundCardAudio(circularBufferSize, sampleRate, position);
		} catch (LineUnavailableException e1) {
			JOptionPane.showMessageDialog(this,
					e1.toString(),
					"LINE UNAVAILABLE ERROR",
				    JOptionPane.ERROR_MESSAGE) ;
			e1.printStackTrace(Log.getWriter());
			stopButton();
		} catch (IllegalArgumentException e1) {
			JOptionPane.showMessageDialog(this,
					e1.toString(),
					"ARGUMENT ERROR",
					JOptionPane.ERROR_MESSAGE) ;
			e1.printStackTrace(Log.getWriter());
			stopButton();
		}
		return audioSource;
	}
	
	
	/**
	 * Create a new Decoder with the setup params
	 * Start a new thread with this decoder and run it.
	 * @param highSpeed
	 */
	private void setupDecoder(boolean highSpeed, SourceAudio audioSource) {
		
		if (highSpeed) {
			decoder = new Fox9600bpsDecoder(audioSource);
		} else {
			decoder = new Fox200bpsDecoder(audioSource);
		}
		setupAudioSink();
	}
	

	
	/**
	 * The user has clicked the start button.  We already know the audio Source.
	 */
	private void processStartButtonClick() {
		//String source = (String)soundCardComboBox.getSelectedItem();
		int position = soundCardComboBox.getSelectedIndex();
				
		if (STARTED) {
			// we stop everything
			stopButton();
		} else {
			STARTED = true;
			btnStartButton.setText("Stop");
			stopDecoder(); // make sure everything is stopped
			Config.scSampleRate = Integer.parseInt((String) cbSoundCardRate.getSelectedItem());
			
			if (task != null) {
				Log.println("Stopping file progress task-");
				task.end();
			}

			if (position == 0) {
				// we don't have a selection
			} else {
				if (position == SourceAudio.FILE_SOURCE) { // || position == SourceAudio.IQ_FILE_SOURCE) {
					SourceWav wav = setWavFile();
					if (wav != null) {
						if (task != null) {
							task.end();
						}
						task = new Task(wav);
						progressThread = new Thread(task);
						progressThread.setUncaughtExceptionHandler(Log.uncaughtExHandler);
						if (Config.iq) { //position == SourceAudio.IQ_FILE_SOURCE) {
							if (iqSource != null) iqSource.stop();
							iqSource = new SourceIQ((int)wav.getAudioFormat().getSampleRate()*4);
							iqSource.setAudioSource(wav);
							setupDecoder(highSpeed.isSelected(), iqSource);
							setCenterFreq();
							Config.passManager.setDecoder(decoder, iqSource);
						} else {
							setupDecoder(highSpeed.isSelected(), wav);
						}
						progressThread.start(); // need to start after the audio source wav is created
					} else {
						stopButton();
					}
				} else { // soundcard - fcd or normal
					SourceAudio audioSource;
					boolean fcdSelected = usingFcd();
					if (fcdSelected) {
						setFcdSampleRate();
					} else {
						Config.scSampleRate = Integer.parseInt((String) cbSoundCardRate.getSelectedItem());	
					}
					
					Config.soundCard = SourceSoundCardAudio.getDeviceName(position); // store this so it gets saved
					
					audioSource = setupSoundCard(highSpeed.isSelected(), Config.scSampleRate);
					if (audioSource != null)
					if (fcdSelected || Config.iq) {
						Log.println("IQ Source Selected");
						iqSource = new SourceIQ(Config.scSampleRate * 4);
						iqSource.setAudioSource(audioSource);
						setupDecoder(highSpeed.isSelected(), iqSource);
						Config.passManager.setDecoder(decoder, iqSource);
						setCenterFreq();
					} else {
						setupDecoder(highSpeed.isSelected(), audioSource);
					}	
				}
				
				if (decoder != null) {
					decoderThread = new Thread(decoder);
					decoderThread.setUncaughtExceptionHandler(Log.uncaughtExHandler);				

					try {
						decoder.setMonitorAudio(sink, Config.monitorAudio, speakerComboBox.getSelectedIndex());
					} catch (IllegalArgumentException e) {
						Log.errorDialog("ERROR", "Can't monitor the audio " + e.getMessage());
						e.printStackTrace(Log.getWriter());
					} catch (LineUnavailableException e) {
						Log.errorDialog("ERROR", "Can't monitor the audio " + e.getMessage());
						e.printStackTrace(Log.getWriter());
					}
				}

				if (decoderThread != null) {
					try {
						decoderThread.start();
						//if (audioGraphThread != null) audioGraph.stopProcessing();
						audioGraph.startProcessing(decoder);
						if (eyePanelThread == null) {	
							eyePanelThread = new Thread(eyePanel);
							eyePanelThread.setUncaughtExceptionHandler(Log.uncaughtExHandler);
							eyePanelThread.start();
						}
						eyePanel.startProcessing(decoder);
						enableSourceSelectionComponents(false);
						
						if (iqSource != null) {
							if (fftPanelThread == null) { 		
								fftPanelThread = new Thread(fftPanel);
								fftPanelThread.setUncaughtExceptionHandler(Log.uncaughtExHandler);
								fftPanelThread.start();
							}
							fftPanel.startProcessing(iqSource);
						}
					} catch (IllegalThreadStateException e2) {
						JOptionPane.showMessageDialog(this,
								e2.toString(),
								"THREAD LAUNCH ERROR",
								JOptionPane.ERROR_MESSAGE) ;
					}
				}
			}
		}

	}
	
	private void setCenterFreq() {
		try {
			int freq = Integer.parseInt(txtFreq.getText());
			iqSource.setCenterFreqkHz(freq);
		} catch (NumberFormatException n) {
			// not much to say here, just catch the error
		}
	}

	private void stopDecoder() {
//		releaseFcd();
		if (decoder != null) {
			decoder.stopProcessing(); // This blocks and waits for the audiosource to be done
			decoder = null;
			iqSource = null;
			decoderThread = null;
			Config.passManager.setDecoder(decoder, iqSource);
		}
	}
	
	private void stopButton() {
		if (Config.passManager.getState() == PassManager.FADED) {
			Object[] options = {"Yes",
	        "No"};
			int n = JOptionPane.showOptionDialog(
					MainWindow.frame,
					"The pass manager is still processing a satellite pass. If the satellite has\n"
					+ "faded it waits 2 minutes in case contact is re-established, even when it is at the\n"
					+ "horizon.  If you stop the decoder now the LOS will not be logged and TCA will not be calculated.\n"
					+ "Do you want to stop?",
					"Stop decoding while pass in progress?",
				    JOptionPane.YES_NO_OPTION, 
				    JOptionPane.ERROR_MESSAGE,
				    null,
				    options,
				    options[1]);
						
			if (n == JOptionPane.NO_OPTION) {
				// don't exit
			} else {
				stop();

			}
		} else {
			stop();
		}
	}
	
	private void stop() {
		stopDecoder();
		STARTED = false;
		btnStartButton.setText("Start");
		enableSourceSelectionComponents(true);
	}
	
	private void enableSourceSelectionComponents(boolean t) {
		soundCardComboBox.setEnabled(t);
		cbSoundCardRate.setEnabled(t);
		highSpeed.setEnabled(t);
		lowSpeed.setEnabled(t);
		iqAudio.setEnabled(t);
		afAudio.setEnabled(t);
		MainWindow.enableSourceSelection(t);
	}
	
	@Override
	public void itemStateChanged(ItemEvent e) {
		//public void itemStateChanged(ItemEvent e) {
		if (e.getSource() == rdbtnViewFilteredAudio) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
	            audioGraph.showUnFilteredAudio();
	            Config.viewFilteredAudio=false;
	            //Config.save();
	        } else {
	        	audioGraph.showFilteredAudio();
	        	Config.viewFilteredAudio=true;
	        	//Config.save();
	        }
			
		}
		if (e.getSource() == rdbtnMonitorFilteredAudio) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				monitorFiltered=false;
	            Config.monitorFilteredAudio=false;
	            //Config.save();
	        } else {
	        	Config.monitorFilteredAudio=true;
	        	monitorFiltered=true;
	        	//Config.save();
	        }
		}
		if (e.getSource() == rdbtnSquelchAudio) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
	            Config.squelchAudio=false;
	            //Config.save();
	        } else {
	        	Config.squelchAudio=true;
	        	//Config.save();
	        }
		}
		if (e.getSource() == rdbtnFilterOutputAudio) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
	            Config.filterOutputAudio=false;
	            //Config.save();
	        } else {
	        	Config.filterOutputAudio=true;
	        	//Config.save();
	        }
		}
		if (e.getSource() == rdbtnWriteDebugData) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				
	            Config.debugValues=false;
	            //Config.save();
	        } else {
	        	Config.debugValues=true;
	        	
	        	//Config.save();
	        }
		}
		/*
		if (e.getSource() == rdbtnApplyBlackmanWindow) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				
	            Config.applyBlackmanWindow=false;
	            //Config.save();
	        } else {
	        	Config.applyBlackmanWindow=true;
	        	
	        	//Config.save();
	        }
		}
		*/
		if (e.getSource() == rdbtnShowFFT) {
			if (fftPanel != null)
			if (e.getStateChange() == ItemEvent.DESELECTED) {
	            fftPanel.setVisible(false);
	        } else {
	            fftPanel.setVisible(true);
	        	
	        }
		}
		/*
		if (e.getSource() == rdbtnUseNco) {
			if (fftPanel != null)
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				if (iqSource != null)
					SourceIQ.useNCO = false;
	        } else {
	        	if (iqSource != null)
	        		SourceIQ.useNCO = true;
	        	
	        }
		}
		if (e.getSource() == rdbtnUseLimiter) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				
	            Config.useLimiter=false;
	            //Config.save();
	        } else {
	        	Config.useLimiter=true;
	        	
	        	//Config.save();
	        }
		}
		if (e.getSource() == rdbtnShowIF) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				
	            Config.showIF=false;
	            //Config.save();
	        } else {
	        	Config.showIF=true;
	        	//Decoder.SAMPLE_WINDOW_LENGTH = 1000;  //// cause a crash for testing
//	        	String s = null;
//	        	int i = s.length();  // crash the GUI EDT for testing 
	        	//Config.save();
	        }
		}
		*/
		if (e.getSource() == rdbtnTrackSignal) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				
	            Config.trackSignal=false;
	            //Config.save();
	        } else {
	        	Config.trackSignal=true;
	        	
	        	//Config.save();
	        }
		}
		if (e.getSource() == rdbtnFindSignal) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				
	            Config.findSignal=false;
	            //Config.save();
	        } else {
	        	Config.findSignal=true;
	        	
	        	//Config.save();
	        }
		}
		if (e.getSource() == rdbtnShowLog) {
			if (e.getStateChange() == ItemEvent.DESELECTED) {
				logScrollPane.setVisible(false);
				log.setVisible(false);
				Log.setGUILog(null);
				
//	            Config.applyBlackmanWindow=false;
	            //Config.save();
	        } else {
	        	logScrollPane.setVisible(true);
	        	log.setVisible(true);
	        	MainWindow.frame.repaint(); // need to kick this to get it to redraw straight away.  Not sure why
	    		Log.setGUILog(this);

	        	//	        	Config.applyBlackmanWindow=true;
	        	
	        	//Config.save();
	        }
		}
		
	}

	/**
	 * Try to close any open OS resources
	 */
	public void shutdown() {
		if (fcd != null)
			try {
				fcd.cleanup();
			} catch (IOException e) {
				e.printStackTrace(Log.getWriter());
			} catch (FcdException e) {
				e.printStackTrace(Log.getWriter());
			}

		if (decoder != null)
			decoder.stopProcessing(); 
	}
	
	/**
     * Invoked when task's progress property changes.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress" == evt.getPropertyName()) {
            int progress = (Integer) evt.getNewValue();
            progressBar.setValue(progress);
            
        } 
    }
        
    /**
     * A task sub class that we use to track the progress of a wav file as it is being played.  This thread updates the
     * progress bar
     * @author chris.e.thompson
     *
     */
	class Task implements Runnable { 
		int progress;
		SourceWav wavSource;
		
		public Task(SourceWav wav) {
			wavSource = wav;
			progress = 0;
		}
		
		public void setProgress(int p) { progress = p; }
		
		public void resetProgress() {
			progress = 0;
			setProgress(progress); 
		}
	
		/*
         * Main task. Executed in background thread.
         */
        @Override
        public void run() { 
            
            //Initialize progress property.
            setProgress(0);
            while (progress < 100 && wavSource != null) {
                //Sleep for a bit
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignore) {}
                //Get progress from decoder
    //            System.out.println("task..");
                //if (audioSource instanceof SourceWav)
                if (wavSource != null)
                	progress = wavSource.getPercentProgress();
                else
                	progress = -1;
                if (progress != -1)
                SwingUtilities.invokeLater(new Runnable() {
                	public void run() {
                		progressBar.setValue(Math.min(progress,100));
                	}
                });
            }
            setProgress(100);
            done();
            Log.println("WORKER IS DONE");
        }
 
        
        
        /*
         * Executed in event dispatching thread
         */
        public void done() {
        	
            Toolkit.getDefaultToolkit().beep();
            stopButton();    
            //btnStartButton.doClick(); // toggle start to stop
            //System.out.println("WORKER IS DONE");
        }
        
        public void end() {
        	
            setProgress(100);
            //System.out.println("WORKER IS DONE");
        }
    }


}
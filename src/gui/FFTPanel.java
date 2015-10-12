package gui;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.text.DecimalFormat;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import common.Config;
import common.Log;
import common.Spacecraft;
import decoder.RfData;
import decoder.SourceIQ;

import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

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
 *
 * A panel that displays an FFT of the data that it reads from a source
 * The frequency of each bin in the result is SAMPLE_RATE/fftSamples
 * 
 * We retrieve the Power Spectral Density from the Decoder.  This is the magnitude of the I/Q samples
 * after the FFT.  The FFT results are organized with the real (positive) frequencies from 0 - N/2
 * and the complex (negative) frequencies from N/2 - N.
 * 
 * The negative frequencies go to the left of the center and the positive to the right.  The selected carrier
 * frequency is in the middle of the screen.  We effectively draw to graphs side  by side.
 * 
 * We tune the SDR from here.  The user can click the mouse on the display or use the arrow buttons.  If automatic tracking
 * is enabled then this module calculates a new frequency as though the user changed it.
 * 
 *
 */
@SuppressWarnings("serial")
public class FFTPanel extends JPanel implements Runnable, MouseListener {
	private static final double TRACK_SIGNAL_THRESHOLD = -80;
	Spacecraft fox;
	
	int fftSamples = SourceIQ.FFT_SAMPLES;
	double[] fftData = new double[fftSamples*2];
	
	private double[] psd = null;
	
	boolean running = true;
	boolean done = false;
	int centerFreqX = 145950;
	int selectedBin = 0; // this is the actual FFT bin, with negative and positve freq flipped
	int selection = 0; // this is the bin that the user clicked on, which runs from left to right
	boolean showFilteredAudio = false;

	int sideBorder = 2 * Config.graphAxisFontSize;
	int topBorder = Config.graphAxisFontSize;
	int labelWidth = 4 * Config.graphAxisFontSize;

	int graphWidth;
	int graphHeight;
	
	Color graphColor = Config.AMSAT_RED;
	Color graphAxisColor = Color.BLACK;
	Color graphTextColor = Color.DARK_GRAY;
	
	SourceIQ iqSource;

	RfData rfData;
	boolean liveData = false; // true if we have not received a NULL buffer from the decoder.

	JLabel title;
	
	FFTPanel() {
		title = new JLabel();
		add(title);
		addMouseListener(this);
		String TUNE_LEFT = "left";
		String TUNE_RIGHT = "right";
		InputMap inMap = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		inMap.put(KeyStroke.getKeyStroke("LEFT"), TUNE_LEFT);
		inMap.put(KeyStroke.getKeyStroke("RIGHT"), TUNE_RIGHT);
		ActionMap actMap = this.getActionMap();
		actMap.put(TUNE_LEFT, new AbstractAction() {
	        @Override
	        public void actionPerformed(ActionEvent e) {
	         //       System.out.println("TUNE LEFT");
	                selectedBin = selectedBin-1;
	                Config.selectedBin = selectedBin;
	          //     printBin();
	        }
	    });
		actMap.put(TUNE_RIGHT, new AbstractAction() {
	        @Override
	        public void actionPerformed(ActionEvent e) {
	         //       System.out.println("TUNE RIGHT");
	                selectedBin = selectedBin+1;
	                Config.selectedBin = selectedBin;
	         //       printBin();
	        }
	    });
	}
	
	@SuppressWarnings("unused")
	private void printBin() {
		int freq=0;
		 if (selectedBin < SourceIQ.FFT_SAMPLES/2)
         	freq= 192000*selectedBin/SourceIQ.FFT_SAMPLES;
         else
         	freq = 96000-192000*(selectedBin-SourceIQ.FFT_SAMPLES/2)/SourceIQ.FFT_SAMPLES;
		 
		 double cycles = getCycles();
		 System.out.println("fft bin "+selectedBin + " freq: " + freq + " cyc: " + cycles);
	}
	
	public void stopProcessing() { 
		running = false;
		//source.drain();
	}
	
	@Override
	public void run() {
		done = false;
		running = true;
		double[] buffer = null;
		psd = new double[fftSamples+1];
		while(running) {
			if (iqSource != null) {
				buffer = iqSource.getPowerSpectralDensity();
				centerFreqX = iqSource.getCenterFreqkHz();
				selectedBin = Config.selectedBin;
				rfData = iqSource.getRfData();
			}
			try {
				Thread.sleep(1000/30); // 30Hz
			} catch (InterruptedException e) {
				e.printStackTrace();
			} 
			if (buffer != null) {
				psd = buffer;
				liveData = true;
			} else {
				liveData = false;
			}
			this.repaint();
			
		}
		done = true;
	}
	
		public static int littleEndian2(byte b[]) {
			byte b1 = b[0];
			byte b2 = b[1];
			int value =  ((b2 & 0xff) << 8)
			     | ((b1 & 0xff) << 0);
			if (value > (32768-1)) value = -65536 + value;
			return value;
		}

	private int getSelectionFromBin(int bin) {
		int selection;
		if (bin < fftSamples/2)
			selection = bin + fftSamples/2;
		else
			selection = bin - fftSamples/2;

		return selection;
	}
		
	public void paintComponent(Graphics g) {				
		super.paintComponent( g ); // call superclass's paintComponent  
		
		sideBorder = 2 * Config.graphAxisFontSize;
		topBorder = Config.graphAxisFontSize;
		labelWidth = 4 * Config.graphAxisFontSize;

		if (!running) return;
		if (psd == null) return;
		Graphics2D g2 = ( Graphics2D ) g; // cast g to Graphics2D  
		g2.setColor(graphAxisColor);

		graphHeight = getHeight() - topBorder*2;
		graphWidth = getWidth() - sideBorder*2; // width of entire graph
		
								
		int minTimeValue = centerFreqX-iqSource.IQ_SAMPLE_RATE/2000;//96;
		int maxTimeValue = centerFreqX+iqSource.IQ_SAMPLE_RATE/2000;//96;
		int numberOfTimeLabels = graphWidth/labelWidth;
		int zeroPoint = graphHeight;
		
		
		double maxValue = 10;
		double minValue = -100;

		int labelHeight = 14;
		int sideLabel = 3;
		// calculate number of labels we need on vertical axis
		int numberOfLabels = graphHeight/labelHeight;
		
		// calculate the label step size
		double[] labels = GraphPanel.calcAxisInterval(minValue, maxValue, numberOfLabels);
		// check the actual number
		numberOfLabels = labels.length;
		
		DecimalFormat f1 = new DecimalFormat("0.0");
		DecimalFormat f2 = new DecimalFormat("0");
	
		// Draw vertical axis - always in the same place
		g2.drawLine(sideBorder, getHeight()-topBorder, sideBorder, 0);
		g.setFont(new Font("SansSerif", Font.PLAIN, Config.graphAxisFontSize));

		for (int v=0; v < numberOfLabels; v++) {
			
			int pos = getRatioPosition(minValue, maxValue, labels[v], graphHeight);
			pos = graphHeight-pos;
			String s = null;
			if (labels[v] == Math.round(labels[v]))
				s = f2.format(labels[v]);
			else
				s = f1.format(labels[v]);
			if (v < numberOfLabels-1 
					&& !(v == 0 && pos > graphHeight)
					) {
				g2.setColor(graphTextColor);
				g.drawString(s, sideLabel, pos+topBorder+4); // add 4 to line up with tick line
				g2.setColor(graphAxisColor);
				g.drawLine(sideBorder-5, pos+topBorder, sideBorder+5, pos+topBorder);

			}
		}
		
		if (iqSource != null) {
			// Draw the current selected frequency to decode
			// Only show half the filter width because of the taper of the filter shape
			selection = getSelectionFromBin(Config.selectedBin);

			int c = getRatioPosition(0, fftSamples, selection, graphWidth);
			int lower = getRatioPosition(0, fftSamples, selection-iqSource.getFilterWidth()/2, graphWidth);
			int upper = getRatioPosition(0, fftSamples, selection+iqSource.getFilterWidth()/2, graphWidth);


			g2.setColor(graphAxisColor);
			g2.drawLine(c+sideBorder, topBorder, c+sideBorder, zeroPoint);
			
			// draw line either side of signal
			g2.setColor(Color.gray);
			g2.drawLine(lower+sideBorder, topBorder, lower+sideBorder, zeroPoint);
			g2.drawLine(upper+sideBorder, topBorder, upper+sideBorder, zeroPoint);

			// draw the upper and lower freq bounds
			/*  FIXME THIS BREAKS THE WAV IQ DECODER....
			 * 
			 */ 
			if (Config.findSignal) {
				if (fox != null) {
					g.drawString(Config.passManager.getStateName() + ": Fox-"+fox.getIdString(), graphWidth-5*Config.graphAxisFontSize, 4*Config.graphAxisFontSize  );
				} else
					g.drawString("Scanning..", graphWidth-5*Config.graphAxisFontSize, 4*Config.graphAxisFontSize );
				
				g2.setColor(Config.PURPLE);

				int upperSelection = getSelectionFromBin(Config.toBin);
				int lowerSelection = getSelectionFromBin(Config.fromBin);

				c = getRatioPosition(0, fftSamples, upperSelection, graphWidth);
				g2.drawLine(c+sideBorder, topBorder, c+sideBorder, zeroPoint);
				c = getRatioPosition(0, fftSamples, lowerSelection, graphWidth);
				g2.drawLine(c+sideBorder, topBorder, c+sideBorder, zeroPoint);
			}
			
			if (rfData != null) {
				g2.setColor(Config.AMSAT_BLUE);
				//int width = 10;
				int peak = getRatioPosition(minValue, maxValue, rfData.getAvg(RfData.PEAK), graphHeight);
				peak=graphHeight-peak-topBorder;
				
				int peakBin = 0;
				if (rfData.getBinOfPeakSignal() < fftSamples/2) {
					peakBin = getRatioPosition(0, fftSamples/2, rfData.getBinOfPeakSignal(), graphWidth/2);
					peakBin = peakBin + sideBorder + graphWidth/2;
				} else {
					peakBin = getRatioPosition(0, fftSamples/2, rfData.getBinOfPeakSignal()-fftSamples/2, graphWidth/2);
					peakBin = peakBin + sideBorder;
				}
	
	
//				System.out.println("BIN:" + peakBin +" "+ rfData.getBinOfPeakSignal());
				
				
//				g2.drawLine(graphWidth/2-width , (int)peak, graphWidth/2+width, (int)peak);
				//g2.drawLine(peakBin , (int)peak, peakBin, (int)peak);

				//double r = GraphPanel.roundToSignificantFigures(rfData.getAvg(RfData.PEAK),3);
				//double n = GraphPanel.roundToSignificantFigures(rfData.getAvg(RfData.NOISE),3);
				double snr = GraphPanel.roundToSignificantFigures(rfData.rfSNR,3);
				//String pk = Double.toString(r) + "";
				//String noise = Double.toString(n) + "";
				String s = Double.toString(snr) + "";
				long f = rfData.getPeakFrequency();
				g.drawString("| " /*+ rfData.getBinOfPeakSignal()*/ , peakBin, peak  );

				g2.drawLine(peakBin-5 , (int)peak-3, peakBin+5, (int)peak-3);
				g.drawString("snr: " + s + "dB", peakBin+10, peak  );
				g.drawString("Freq:"+f, graphWidth-5*Config.graphAxisFontSize, 2*Config.graphAxisFontSize  );

				if (Config.findSignal) {
					int strongestpeak = getRatioPosition(minValue, maxValue, rfData.getAvg(RfData.STRONGEST_SIG), graphHeight);
					strongestpeak=graphHeight-strongestpeak-topBorder;
					
					int strongestBin = 0;
					if (rfData.getBinOfStrongestSignal() < fftSamples/2) {
						strongestBin = getRatioPosition(0, fftSamples/2, rfData.getBinOfStrongestSignal(), graphWidth/2);
						strongestBin = strongestBin + sideBorder + graphWidth/2;
					} else {
						strongestBin = getRatioPosition(0, fftSamples/2, rfData.getBinOfStrongestSignal()-fftSamples/2, graphWidth/2);
						strongestBin = strongestBin + sideBorder;
					}

					//double strongsnr = GraphPanel.roundToSignificantFigures(rfData.strongestSigRfSNR,3);
					//				double strongsnr = GraphPanel.roundToSignificantFigures(rfData.getAvg(RfData.STRONGEST_SIG),5);
					//String strong = Double.toString(strongsnr) + "";

					g.drawString("* " , strongestBin, strongestpeak - 5  );
					//				g.drawString("* " + strong + "dB" , strongestBin, strongestpeak - 10  );
				}
				// auto tune
				if (Config.trackSignal && liveData && rfData.getAvg(RfData.PEAK) > TRACK_SIGNAL_THRESHOLD) {
					// move half the distance to the bin
					int targetBin = 0;
					if (Config.findSignal)
						targetBin = rfData.getBinOfStrongestSignal();
					else
						targetBin = rfData.getBinOfPeakSignal();
					int move = targetBin - selectedBin;
					if (targetBin < selectedBin) {
						if (move > 100)
							selectedBin -= 50;
						else
						if (move > 10)
							selectedBin -= 5;
						else
						selectedBin--;
					}
					if (targetBin > selectedBin) {
						if (move < -100)
							selectedBin += 50;
						else if (move < -10)
							selectedBin += 5;
						else
						selectedBin++;
					}
					if (Config.findSignal) {
						if (selectedBin > Config.fromBin && selectedBin < Config.toBin)
							Config.selectedBin = selectedBin;
					} else
						Config.selectedBin = selectedBin;
				}
			} else {
				Log.println("RF DATA NULL");
			}
				
		}

		
		int lastx = sideBorder+1; 
		int lasty = graphHeight;
		int x = 0;
		int y = 0;
		//int skip = 0;
		g2.setColor(graphColor);
		
		//192000/2 samples is too long for any window so we skip some of it
		int stepSize = Math.round((fftSamples - 1)/graphWidth);
		
		// Draw the graph, one half at a time
		for (int i=fftSamples/2; i< (fftSamples); i+= stepSize) {
			
			
			x = getRatioPosition(0, fftSamples/2, i-fftSamples/2, graphWidth/2);
			x = x + sideBorder;
			
			y = getRatioPosition(minValue, maxValue, psd[i], graphHeight);
			
			// psd 
			y=graphHeight-y-topBorder;
			if (i == 1) {
				lastx=x;
				lasty=y;
			}
			g2.drawLine(lastx, lasty, x, y);
			lastx = x;
			lasty = y;
			
			//g2.drawLine((10+(pos*2-1)), (int)(getHeight()- baseline-(psd[x-1]*gain)), 10+pos*2, (int)(getHeight()-baseline -(psd[x]*gain)));
		
		}
		
		// We want 0 - fftSamples/2 to get the real part		
		for (int i=1; i< (fftSamples/2); i+= stepSize) {
						
			x = getRatioPosition(0, fftSamples/2, i, graphWidth/2);
			x = x + sideBorder + graphWidth/2;
			
			y = getRatioPosition(minValue, maxValue, psd[i], graphHeight);
			
			// psd 
			y=graphHeight-y-topBorder;
			if (i == 1) {
				lastx=x;
				lasty=y;
			}
			g2.drawLine(lastx, lasty, x, y);
			lastx = x;
			lasty = y;
			
			//g2.drawLine((10+(pos*2-1)), (int)(getHeight()- baseline-(psd[x-1]*gain)), 10+pos*2, (int)(getHeight()-baseline -(psd[x]*gain)));
		
		}
		
		// Draw the horizontal axis
		double[] freqlabels = GraphPanel.calcAxisInterval(minTimeValue, maxTimeValue, numberOfTimeLabels);

		DecimalFormat d = new DecimalFormat("0");
		for (int v=0; v < numberOfTimeLabels; v++) {
			int timepos = getRatioPosition(minTimeValue, maxTimeValue, freqlabels[v], graphWidth);

			// dont draw the label if we are too near the start or end
			if ((timepos) > 2 && (graphWidth - timepos) > labelWidth/6) {
				String s = d.format(freqlabels[v]);

				g2.setColor(graphTextColor);
				g.drawString(s, timepos+sideBorder+2-labelWidth/2, zeroPoint+Config.graphAxisFontSize );

				g2.setColor(graphAxisColor);
				g.drawLine(timepos+sideBorder, zeroPoint, timepos+sideBorder, zeroPoint+5);
			}
		}
		
		g2.setColor(graphAxisColor);
		g2.drawLine(0, zeroPoint, getWidth(), zeroPoint);

		
	}

	public void setFox(Spacecraft f) {
		fox = f;
	}
	
	public void startProcessing(SourceIQ d) {
		iqSource = d;
		//title.setText("Sample rate: " +  d.upstreamAudioFormat.getSampleRate());
		running = true;
	}
	

	/**
	 * Given a minimum and maximum and the length of a dimension, usually in pixels, return the pixel position when passed
	 * a value between the min and max
	 * @param min
	 * @param max
	 * @param value
	 * @param dimension
	 * @return
	 */
	private int getRatioPosition(double min, double max, double value, int dimension) {
		double ratio = (max - value) / (max - min);
		int position = (int)Math.round(dimension * ratio);
		return dimension-position;
	}

	private double getCycles() {
		double binBW = 192000f / 2 / SourceIQ.FFT_SAMPLES; // nyquist freq / fft length
		double freq = (double)selectedBin * binBW;
		double samples = 192000f / freq; // number of samples in one period of this freq
		double cycles = (double)SourceIQ.FFT_SAMPLES / samples; // how many complete cycles this freq makes in the fft length
		return cycles;
	}

	@SuppressWarnings("unused")
	private int getRequiredBin(int bin) {
		double binBW = 192000f / 2 / SourceIQ.FFT_SAMPLES; // nyquist freq / fft length
		double freq = (double)selectedBin * binBW;
		
		//int actBin = (int) (Math.round(SourceIQ.FFT_SAMPLES * freq / (30 * 192000)) * 30);
		int dc = 0;
		if (selectedBin < SourceIQ.FFT_SAMPLES/2) dc = +2; else dc = -1;
		int actBin = (int) (Math.round(bin / 30) * 30 + dc) ; // -1 because we translate to the first bin after DC??
		return actBin;
	}

	
	@Override
	public void mouseClicked(MouseEvent e) {
		int x=e.getX();
	    //int y=e.getY();
	    x = x - sideBorder;
		selection = getRatioPosition(0, graphWidth, x, fftSamples );
		if (selection >= fftSamples/2) 
			selectedBin = selection - fftSamples/2;
		else
			selectedBin = selection + fftSamples/2;
		System.out.println(x+" is fft bin "+selectedBin);//these co-ords are relative to the component
	
		//double cyc = getCycles();
//		System.out.println("Trying bin: " + selectedBin);
	//	selectedBin = getRequiredBin(selectedBin);
//		System.out.println("Trying bin: " + selectedBin);
				
		Config.selectedBin = selectedBin;
		if (rfData != null)
			rfData.reset(); // reset the average calcs so the UI is more responsive
//		printBin();
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		
		
	}

	@Override
	public void mouseExited(MouseEvent e) {
		
		
	}

	@Override
	public void mousePressed(MouseEvent e) {
		
		
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		
		
	}
}
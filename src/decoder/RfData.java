package decoder;

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
 * Measurements made against the RF spectrum
 * 
 *
 */
public class RfData extends DataMeasure {

    public static final int PEAK = 0; // The peak signal within the filter width
    public static final int BIN = 1; // The bin that the peak signal is in within the filter width
    public static final int NOISE = 2; // The average level of the noise by sampling half the filter width either side of the filter
    public static final int AVGSIG = 3; // The average signal in the filter width.
    public static final int STRONGEST_SIG = 4;
    public static final int STRONGEST_BIN = 5;
    public double rfSNR;
    public double strongestSigRfSNR;
    
    protected long AVERAGE_PERIOD = 100; // 1000 = 1 sec average time
    
    double binBandwidth;
    SourceIQ iqSource;
    
    public RfData(SourceIQ iq) {
    	MEASURES = 6;
    	iqSource = iq;
    	init();
    }
    
	public int getBinOfPeakSignal() {
		return (int)getAvg(BIN);
	}

	public int getBinOfStrongestSignal() {
		return (int)getAvg(STRONGEST_BIN);
	}

	
	public long getPeakFrequency() {
		return iqSource.getFrequencyFromBin(getBinOfPeakSignal());
	}

	public long getStrongestFrequency() {
		return iqSource.getFrequencyFromBin(getBinOfStrongestSignal());
	}

	public void calcAverages() {
    	if (readyToAverage()) {
    		
			if (getAvg(AVGSIG) != 0 && getAvg(NOISE) != 0) {
				double p = getAvg(AVGSIG);
				double n = getAvg(NOISE);
				rfSNR = (p - n);  // these are in dB so subtract rather than divide
			}
			if (getAvg(STRONGEST_SIG) != 0 && getAvg(NOISE) != 0) {
				double p = getAvg(STRONGEST_SIG);
				double n = getAvg(NOISE);
				strongestSigRfSNR = (p - n);  // these are in dB so subtract rather than divide
			}
    		reset();
    	}
    }

    /**
     * Store paramaters about the peak signal in the filter passband
     * @param p
     * @param b
     * @param sig
     * @param n
     */
	public void setPeakSignal(double p, int b, double sig, double n) {
		setValue(PEAK, p);
		setValue(BIN, b);
		setValue(AVGSIG, sig);
		setValue(NOISE, n);
	}
	
	public void setStrongestSignal(double sig, int b) {
		setValue(STRONGEST_SIG, sig);
		setValue(STRONGEST_BIN, b);
		
	}
}
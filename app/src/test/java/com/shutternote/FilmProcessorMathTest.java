package com.shutternote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FilmProcessorMathTest {

    @Test
    public void exposureLookupTable_preservesIdentityMultiplier() {
        int[] lookupTable = FilmProcessor.buildExposureLookupTable(1.0);

        for (int channel = 0; channel < lookupTable.length; channel++) {
            assertEquals(channel, lookupTable[channel]);
        }
    }

    @Test
    public void exposureLookupTable_brightensAndClampsChannels() {
        int[] lookupTable = FilmProcessor.buildExposureLookupTable(2.0);

        assertEquals(0, lookupTable[0]);
        assertTrue(lookupTable[128] > 128);
        assertEquals(255, lookupTable[255]);
    }

    @Test
    public void exposureLookupTable_isMonotonic() {
        int[] lookupTable = FilmProcessor.buildExposureLookupTable(0.5);

        for (int channel = 1; channel < lookupTable.length; channel++) {
            assertTrue(lookupTable[channel] >= lookupTable[channel - 1]);
        }
    }
}

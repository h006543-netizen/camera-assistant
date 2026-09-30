package com.shutternote;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExposureSettingsStoreInstrumentedTest {

    @Test
    public void saveAndLoad_persistsExposureValuesAcrossStoreInstances() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ExposureSettingsStore firstStore = new ExposureSettingsStore(context);
        ExposureSettingsStore.Values originalValues = firstStore.load();

        try {
            firstStore.save("80", "f/5.6", "1/60", "40mm");

            ExposureSettingsStore.Values restored =
                    new ExposureSettingsStore(context).load();

            assertEquals("80", restored.getIso());
            assertEquals("f/5.6", restored.getAperture());
            assertEquals("1/60", restored.getShutter());
            assertEquals("40mm", restored.getFieldOfView());
        } finally {
            firstStore.save(
                    originalValues.getIso(),
                    originalValues.getAperture(),
                    originalValues.getShutter(),
                    originalValues.getFieldOfView()
            );
        }
    }
}

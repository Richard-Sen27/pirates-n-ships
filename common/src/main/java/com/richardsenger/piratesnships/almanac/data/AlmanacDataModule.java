package com.richardsenger.piratesnships.almanac.data;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;

import java.util.List;

/**
 * The Almanac's data side as its own module (ALM2), forwarding to {@link AlmanacData}. When ALM1's
 * {@code AlmanacModule} calls the {@link AlmanacData} hooks itself, this class and its line in {@code ModModules} go.
 */
public final class AlmanacDataModule implements ModModule {

    @Override
    public String id() {
        return "almanac.data";
    }

    @Override
    public void registerConfig() {
        AlmanacData.registerConfig();
    }

    @Override
    public void registerContent() {
        AlmanacData.registerContent();
    }

    @Override
    public void registerPayloads() {
        AlmanacData.registerPayloads();
    }

    @Override
    public void registerEvents() {
        AlmanacData.registerEvents();
    }

    @Override
    public void initClient() {
        AlmanacData.initClient();
    }

    @Override
    public void gatherData(DataContributions data) {
        AlmanacData.gatherData(data);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return AlmanacData.gameTestClasses();
    }
}

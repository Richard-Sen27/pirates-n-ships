package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.services.IAttachmentHelper;
import com.richardsenger.piratesnships.platform.services.ICapabilityHelper;
import com.richardsenger.piratesnships.platform.services.IConfigHelper;
import com.richardsenger.piratesnships.platform.services.INetworkHelper;
import com.richardsenger.piratesnships.platform.services.IPlatformHelper;
import com.richardsenger.piratesnships.platform.services.IRegistryHelper;

import java.util.ServiceLoader;

/**
 * Loader-specific services, located with {@link ServiceLoader}. Each loader module provides one implementation per
 * interface and lists it in {@code META-INF/services/<interface FQN>}.
 *
 * <p>Events are not a service: the callback hubs {@link com.richardsenger.piratesnships.platform.event.CommonEvents}
 * and {@link com.richardsenger.piratesnships.platform.event.ClientEvents} are plain common code that the loader
 * modules fire. See docs/design.md §3.3.
 *
 * <p>All services are loaded when this class is first touched, which fails without a loader. Pure-logic code and
 * JUnit tests must therefore never reach this class (config handles and attachment keys are designed not to).
 */
public final class Services {

    /** Platform name, loaded mods, dev environment, physical side. */
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    /** Registers registry content (blocks, items, ...). Use {@code core.registry.ModRegistry} instead. */
    public static final IRegistryHelper REGISTRY = load(IRegistryHelper.class);
    /** Payload registration and sending. */
    public static final INetworkHelper NETWORK = load(INetworkHelper.class);
    /** Typed data attachments on entities, levels and chunks. */
    public static final IAttachmentHelper ATTACHMENTS = load(IAttachmentHelper.class);
    /** Binds our config schema to the loader's config system. Features use {@code core.config}, never this. */
    public static final IConfigHelper CONFIG = load(IConfigHelper.class);
    /** Exposes our containers to other mods' automation (item handler capability / transfer API). */
    public static final ICapabilityHelper CAPABILITIES = load(ICapabilityHelper.class);

    private Services() {
    }

    /** Loads the single implementation of {@code clazz} for the current loader. */
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}

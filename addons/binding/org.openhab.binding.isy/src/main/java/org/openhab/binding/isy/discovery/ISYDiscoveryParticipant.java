package org.openhab.binding.isy.discovery;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.UpnpDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.jupnp.model.meta.RemoteDevice;
import org.openhab.binding.isy.ISYBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ISYDiscoveryParticipant implements UpnpDiscoveryParticipant {

    private static final Logger logger = LoggerFactory.getLogger(ISYDiscoveryParticipant.class);

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(ISYBindingConstants.THING_TYPE_ISY);
    }

    @Override
    public DiscoveryResult createResult(RemoteDevice device) {
        ThingUID thingUID = getThingUID(device);
        if (thingUID != null) {
            String baseUrl = device.getDetails().getBaseURL().toString();

            String host = null;
            try {
                host = new URL(baseUrl).getHost();
            } catch (MalformedURLException e) {
                logger.error("Invalid baseUrl: {}", baseUrl);
                return null;
            }

            return DiscoveryResultBuilder.create(thingUID).withLabel("ISY (" + host + ")")
                    .withProperty(ISYBindingConstants.PARAMETER_BASE_URL, baseUrl).build();
        }
        return null;
    }

    @Override
    public ThingUID getThingUID(RemoteDevice device) {

        String manufacturer = device.getDetails().getManufacturerDetails().getManufacturer();
        String friendlyName = device.getDetails().getFriendlyName();
        String uuid = device.getDetails().getUpc();

        if (ISYBindingConstants.UNIVERSAL_DEVICES_INC.equals(manufacturer)
                && ISYBindingConstants.ISY.equals(friendlyName) && uuid != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Found ISY {}", uuid);
            }

            return new ThingUID(ISYBindingConstants.THING_TYPE_ISY, uuid.replace(":", "_"));
        }

        return null;
    }

}

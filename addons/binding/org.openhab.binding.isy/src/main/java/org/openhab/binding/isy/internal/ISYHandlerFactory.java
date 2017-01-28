/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.isy.internal;

import static org.openhab.binding.isy.ISYBindingConstants.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.isy.discovery.ISYDeviceDiscovery;
import org.openhab.binding.isy.handler.GenericContactHandler;
import org.openhab.binding.isy.handler.GenericDimmerHandler;
import org.openhab.binding.isy.handler.GenericSwitchHandler;
import org.openhab.binding.isy.handler.ISYHandler;
import org.openhab.binding.isy.handler.MotionSensorHandler;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ISYHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Jason Marchioni - Initial contribution
 */
public class ISYHandlerFactory extends BaseThingHandlerFactory {

    private Logger logger = LoggerFactory.getLogger(ISYHandlerFactory.class);

    private Map<String, ServiceRegistration<?>> discoveryServices = Collections
            .synchronizedMap(new HashMap<String, ServiceRegistration<?>>());

    private final static Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = new HashSet<>(
            Arrays.asList(THING_TYPE_ISY, THING_TYPE_DIMMER, THING_TYPE_SWITCH, THING_TYPE_MOTION_SENSOR,
                    THING_TYPE_CONTACT_SENSOR, THING_TYPE_OUTLET));

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {

        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(THING_TYPE_ISY)) {
            ISYHandler handler = new ISYHandler((Bridge) thing);
            registerDiscoveryService(handler);
            return handler;
        } else if (thingTypeUID.equals(THING_TYPE_DIMMER)) {
            return new GenericDimmerHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_SWITCH)) {
            return new GenericSwitchHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_MOTION_SENSOR)) {
            return new MotionSensorHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_CONTACT_SENSOR)) {
            return new GenericContactHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_OUTLET)) {
            return new GenericSwitchHandler(thing, true);
        } else {
            return null;
        }
    }

    @Override
    public void unregisterHandler(Thing thing) {
        super.unregisterHandler(thing);
        if (thing.getHandler() instanceof ISYHandler) {
            discoveryServices.remove(thing.getUID().getAsString());
        }
    }

    private void registerDiscoveryService(ISYHandler handler) {
        logger.info("Registering Node DiscoveryService");
        ISYDeviceDiscovery deviceDiscovery = new ISYDeviceDiscovery(handler);
        ServiceRegistration<?> registration = bundleContext.registerService(DiscoveryService.class.getName(),
                deviceDiscovery, new Hashtable<String, Object>());
        discoveryServices.put(handler.getThing().getUID().getAsString(), registration);
    }

}

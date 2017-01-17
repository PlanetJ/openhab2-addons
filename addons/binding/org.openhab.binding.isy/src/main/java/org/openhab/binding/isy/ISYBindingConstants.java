/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.isy;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link ISYBinding} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jason Marchioni - Initial contribution
 */
public class ISYBindingConstants {

    public static final String BINDING_ID = "isy";

    // List of all Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_ISY = new ThingTypeUID(BINDING_ID, "isy");
    public final static ThingTypeUID THING_TYPE_DIMMER = new ThingTypeUID(BINDING_ID, "dimmer");
    public final static ThingTypeUID THING_TYPE_SWITCH = new ThingTypeUID(BINDING_ID, "switch");
    public final static ThingTypeUID THING_TYPE_MOTION_SENSOR = new ThingTypeUID(BINDING_ID, "motionSensor");

    // List of all Channel ids
    public final static String CHANNEL_SWITCH_STATE = "switchState";
    public final static String CHANNEL_BRIGHTNESS = "brightness";

    // Upnp Discovery
    public static final String ISY = "ISY";
    public static final String UNIVERSAL_DEVICES_INC = "Universal Devices Inc.";

    // Parameters
    public static final String PARAMETER_USERNAME = "username";
    public static final String PARAMETER_PASSWORD = "password";
    public static final String PARAMETER_BASE_URL = "baseUrl";

    public static String addresstoId(String address) {
        int lastGap = address.lastIndexOf(" ");
        return address.substring(0, lastGap).replace(" ", "_");
    }

}

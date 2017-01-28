package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.isy.ISYBindingConstants;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MotionSensorHandler extends BaseHandler {

    private Logger logger = LoggerFactory.getLogger(MotionSensorHandler.class);

    private static String[] channelIds = new String[] { ISYBindingConstants.CHANNEL_MOTION,
            ISYBindingConstants.CHANNEL_DUSK_DAWN, ISYBindingConstants.CHANNEL_BATTERY };

    public MotionSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH) {
            for (int c = 1; c <= 3; c++) {
                logger.debug("Refreshing status for node {}", address.channel(c));
                Properties properties = isyHandler.getStatus(address.channel(c));
                if (properties != null) {
                    for (Property prop : properties.getProperties()) {
                        propertyUpdated(c, prop.getId(), prop.getValue());
                    }
                }
            }
        }
    }

    @Override
    public void propertyUpdated(int deviceChannel, String property, String value) {
        if (property.equals(ISYHandler.PROPERY_STATUS) && deviceChannel >= 1 && deviceChannel <= 3) {
            boolean isOn = Integer.parseInt(value) > 0;
            updateState(channelIds[deviceChannel - 1], isOn ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        }
    }

}

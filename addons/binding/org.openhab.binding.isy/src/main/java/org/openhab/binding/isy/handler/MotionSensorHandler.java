package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class MotionSensorHandler extends BaseInsteonHandler {

    private static final String CHANNEL_MOTION = "motion";
    private static final String CHANNEL_DUSK_DAWN = "duskDawn";
    private static final String CHANNEL_BATTERY = "battery";

    private Logger logger = LoggerFactory.getLogger(MotionSensorHandler.class);

    private static String[] channelIds = new String[] { CHANNEL_MOTION, CHANNEL_DUSK_DAWN, CHANNEL_BATTERY };

    public MotionSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH) {
            for (int c = 1; c <= 3; c++) {
                final int index = c;
                logger.debug("Refreshing status for node {}", address.channel(c));
                Futures.addCallback(isyHandler.getStatus(address.channel(c)), new FutureCallback<Properties>() {

                    @Override
                    public void onFailure(Throwable arg0) {
                        // TODO Auto-generated method stub

                    }

                    @Override
                    public void onSuccess(Properties properties) {
                        if (properties != null) {
                            for (Property prop : properties.getProperties()) {
                                propertyUpdated(index, prop.getId(), prop.getValue());
                            }
                        }
                    }
                });
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

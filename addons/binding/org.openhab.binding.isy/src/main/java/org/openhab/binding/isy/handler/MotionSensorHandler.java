package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.isy.internal.WebsocketEventHandler;

public class MotionSensorHandler extends BaseThingHandler implements WebsocketEventHandler {

    public MotionSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

    }

    @Override
    public void nodePropertyUpdated(String address, String property, String value) {

    }

}

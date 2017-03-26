package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.openhab.binding.isy.internal.InsteonAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseInsteonHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(BaseInsteonHandler.class);

    protected ISYHandler isyHandler;
    protected InsteonAddress address;

    public BaseInsteonHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        isyHandler = (ISYHandler) getBridge().getHandler();
        address = InsteonAddress.parseNodeAddress(getThing().getProperties().get("address"));
        updateStatus(ThingStatus.ONLINE);
    }

    public abstract void propertyUpdated(int channel, String property, String value);

}

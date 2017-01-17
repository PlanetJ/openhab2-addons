package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.openhab.binding.isy.internal.InsteonAddress;
import org.openhab.binding.isy.internal.InsteonAddress.InsteonAddressChannel;
import org.openhab.binding.isy.internal.WebsocketEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseHandler extends BaseThingHandler implements WebsocketEventHandler {

    private Logger logger = LoggerFactory.getLogger(BaseHandler.class);

    protected ISYHandler isyHandler;
    protected InsteonAddress address;

    public BaseHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        isyHandler = (ISYHandler) getBridge().getHandler();
        address = InsteonAddress.parseNodeAddress(getThing().getProperties().get("address"));
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void nodePropertyUpdated(String address, String property, String value) {
        InsteonAddressChannel addressChannel = InsteonAddress.parseNodeAddressChannel(address);
        if (addressChannel.getInsteonAddress().equals(this.address)) {
            propertyUpdated(addressChannel.getChannel(), property, value);
        }
    }

    public abstract void propertyUpdated(int channel, String property, String value);

}

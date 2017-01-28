package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.isy.ISYBindingConstants;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;

public class GenericSwitchHandler extends BaseHandler {

    private final boolean dual;

    public GenericSwitchHandler(Thing thing) {
        this(thing, false);
    }

    public GenericSwitchHandler(Thing thing, boolean dual) {
        super(thing);
        this.dual = dual;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        int channel = channelUID.getIdWithoutGroup().equals(ISYBindingConstants.CHANNEL_STATE_2) ? 2 : 1;
        if (command == OnOffType.ON) {
            isyHandler.sendNodeCommandOn(address.channel(channel));
        } else if (command == OnOffType.OFF) {
            isyHandler.sendNodeCommandOff(address.channel(channel));
        } else if (command == RefreshType.REFRESH) {
            Properties props = isyHandler.getStatus(address.channel(channel));
            if (props != null) {
                for (Property prop : props.getProperties()) {
                    propertyUpdated(channel, prop.getId(), prop.getValue());
                }
            }
        }
    }

    @Override
    public void propertyUpdated(int channel, String property, String value) {
        if (ISYHandler.PROPERY_STATUS.equals(property)) {
            boolean isOn = Integer.parseInt(value) > 0;
            updateState(channel == 1 ? ISYBindingConstants.CHANNEL_STATE : ISYBindingConstants.CHANNEL_STATE_2,
                    isOn ? OnOffType.ON : OnOffType.OFF);
        }
    }
}

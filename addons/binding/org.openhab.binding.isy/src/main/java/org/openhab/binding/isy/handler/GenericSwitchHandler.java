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

    public GenericSwitchHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == OnOffType.ON) {
            isyHandler.sendNodeCommandOn(address.channel(1));
        } else if (command == OnOffType.OFF) {
            isyHandler.sendNodeCommandOff(address.channel(1));
        } else if (command == RefreshType.REFRESH) {
            Properties props = isyHandler.getStatus(address.channel(1));
            for (Property prop : props.getProperties()) {
                if (prop.getId().equals("ST")) {
                    updateSwitchState(prop.getValue());
                }
            }
        }
    }

    @Override
    public void propertyUpdated(int channel, String property, String value) {
        if ("ST".equals(property)) {
            updateSwitchState(value);
        }
    }

    private void updateSwitchState(String state) {
        try {
            int value = Integer.parseInt(state);
            updateState(ISYBindingConstants.CHANNEL_SWITCH_STATE, value > 0 ? OnOffType.ON : OnOffType.OFF);
        } catch (NumberFormatException e) {
            // should never happen
        }
    }

}

package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class GenericSwitchHandler extends BaseInsteonHandler {

    private static final String CHANNEL_STATE = "state";
    private static final String CHANNEL_STATE_2 = "state2";

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
        final int channel = channelUID.getIdWithoutGroup().equals(CHANNEL_STATE_2) ? 2 : 1;
        if (command == OnOffType.ON) {
            isyHandler.sendNodeCommandOn(address.channel(channel));
        } else if (command == OnOffType.OFF) {
            isyHandler.sendNodeCommandOff(address.channel(channel));
        } else if (command == RefreshType.REFRESH) {
            Futures.addCallback(isyHandler.getStatus(address.channel(channel)), new FutureCallback<Properties>() {

                @Override
                public void onFailure(Throwable arg0) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onSuccess(Properties props) {
                    if (props != null) {
                        for (Property prop : props.getProperties()) {
                            propertyUpdated(channel, prop.getId(), prop.getValue());
                        }
                    }
                }

            });

        }
    }

    @Override
    public void propertyUpdated(int channel, String property, String value) {
        if (ISYHandler.PROPERY_STATUS.equals(property)) {
            boolean isOn = Integer.parseInt(value) > 0;
            updateState(channel == 1 ? CHANNEL_STATE : CHANNEL_STATE_2, isOn ? OnOffType.ON : OnOffType.OFF);
        }
    }
}

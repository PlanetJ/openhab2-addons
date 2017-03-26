package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.isy.ISYBindingConstants;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class GenericDimmerHandler extends BaseInsteonHandler {

    private Logger logger = LoggerFactory.getLogger(GenericDimmerHandler.class);

    public GenericDimmerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType) {
            // Fast ON/OFF
            if (command == OnOffType.ON) {
                isyHandler.sendNodeCommand(address.channel(1), true);
            } else {
                isyHandler.sendNodeCommandOff(address.channel(1), true);
            }
        } else if (command instanceof DecimalType) {
            // Dim
            int brightness = ((DecimalType) command).intValue();
            if (brightness != 0) {
                isyHandler.sendNodeCommandOn(address.channel(1), brightness);
            } else {
                isyHandler.sendNodeCommandOff(address.channel(1));
            }
        } else if (command instanceof RefreshType) {
            // Update status
            logger.debug("Dimmer '{}' got REFRESH command", thing.getLabel());
            Futures.addCallback(isyHandler.getStatus(address.channel(1)), new FutureCallback<Properties>() {

                @Override
                public void onFailure(Throwable arg0) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onSuccess(Properties properties) {
                    if (properties != null) {
                        for (Property prop : properties.getProperties()) {
                            if (prop.getId().equals("ST")) {
                                updateBrightness(prop.getValue());
                            }
                        }
                    }
                }
            });
        }
    }

    @Override
    public void propertyUpdated(int channel, String property, String value) {
        if ("ST".equals(property)) {
            updateBrightness(value);
        }
    }

    private void updateBrightness(String state) {
        try {
            int brightness = Integer.parseInt(state);
            Double percent = brightness / 255d * 100;
            updateState(ISYBindingConstants.CHANNEL_BRIGHTNESS, new PercentType(percent.intValue()));
        } catch (NumberFormatException e) {
            // Should never happen
        }
    }

}

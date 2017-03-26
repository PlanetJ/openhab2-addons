package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

/**
 *
 *
 *
 * @author jmarchioni
 *
 */
public class GenericContactHandler extends BaseInsteonHandler {

    private static final String CHANNEL_STATE = "state";

    private boolean inverse = false;

    public GenericContactHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        super.initialize();

        inverse = (boolean) getConfig().get("inverse");
    }

    private Logger logger = LoggerFactory.getLogger(GenericContactHandler.class);

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH) {
            logger.debug("Refreshing node {} channel 1" + address.toString());
            Futures.addCallback(isyHandler.getStatus(address.channel(1)), new FutureCallback<Properties>() {

                @Override
                public void onFailure(Throwable arg0) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onSuccess(Properties properties) {
                    if (properties != null) {
                        for (Property prop : properties.getProperties()) {
                            propertyUpdated(1, prop.getId(), prop.getValue());
                        }
                    } else {
                        updateStatus(ThingStatus.UNKNOWN);
                    }
                }
            });

        }
    }

    @Override
    public void propertyUpdated(int channel, String property, String value) {
        if (ISYHandler.PROPERY_STATUS.equals(property)) {
            try {
                boolean isOpen = Integer.parseInt(value) > 0;
                updateState(CHANNEL_STATE, (isOpen ^ inverse) ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
            } catch (NumberFormatException e) {
                updateStatus(ThingStatus.UNKNOWN);
            }
        }
    }

}

package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.isy.ISYBindingConstants;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 *
 * @author jmarchioni
 *
 */
public class GenericContactHandler extends BaseHandler {

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
            Properties properties = isyHandler.getStatus(address.channel(1));
            if (properties != null) {
                for (Property prop : properties.getProperties()) {
                    propertyUpdated(1, prop.getId(), prop.getValue());
                }
            } else {
                updateStatus(ThingStatus.UNKNOWN);
            }
        }
    }

    @Override
    public void propertyUpdated(int channel, String property, String value) {
        if (ISYHandler.PROPERY_STATUS.equals(property)) {
            try {
                boolean isOpen = Integer.parseInt(value) > 0;
                updateState(ISYBindingConstants.CHANNEL_STATE,
                        (isOpen ^ inverse) ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
            } catch (NumberFormatException e) {
                updateStatus(ThingStatus.UNKNOWN);
            }
        }
    }

}

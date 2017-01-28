package org.openhab.binding.isy.discovery;

import java.util.Collections;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.isy.ISYBindingConstants;
import org.openhab.binding.isy.handler.ISYHandler;
import org.openhab.binding.isy.internal.InsteonAddress;
import org.openhab.binding.isy.internal.protocol.Node;
import org.openhab.binding.isy.internal.protocol.Nodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ISYDeviceDiscovery extends AbstractDiscoveryService {

    private Logger logger = LoggerFactory.getLogger(ISYDeviceDiscovery.class);

    public ISYHandler isyHandler;

    public ISYDeviceDiscovery(ISYHandler isyHandler) {
        super(Collections.singleton(ISYBindingConstants.THING_TYPE_ISY), 30);
        this.isyHandler = isyHandler;
    }

    public void discoverNodes() {
        if (isyHandler.getThing().getStatus() == ThingStatus.ONLINE) {
            logger.info("Starting discovery of nodes");
            Nodes nodes = isyHandler.getNodes();
            for (Node node : nodes.getNodes()) {
                String address = node.getAddress();
                if (!address.endsWith(" 1")) {
                    // Not primary device, skipping
                    continue;
                }
                InsteonAddress insteonAddress = InsteonAddress.parseNodeAddress(address);

                String[] parts = node.getType().split("\\.");
                if (parts.length != 4) {
                    // Invalid type or we dont support it
                    continue;
                }

                int type = 0;
                int device = 0;
                try {
                    type = Integer.parseInt(parts[0]);
                    device = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }

                // Openhab valid id cannot have spaces
                String id = insteonAddress.toString().replace(" ", "_");
                ThingTypeUID thingTypeUID = null;
                if (type == 1) {
                    // Dimmer types
                    logger.info("Found dimmer {}", node.getAddress());
                    thingTypeUID = ISYBindingConstants.THING_TYPE_DIMMER;
                } else if (type == 2) {
                    // Switch types
                    logger.info("Found switch device {}", node.getAddress());
                    if (device == 57) {
                        thingTypeUID = ISYBindingConstants.THING_TYPE_OUTLET;
                    } else {
                        thingTypeUID = ISYBindingConstants.THING_TYPE_SWITCH;
                    }
                } else if (type == 16) {
                    if (device == 1) {
                        logger.info("Found motion sensor {}", node.getAddress());
                        thingTypeUID = ISYBindingConstants.THING_TYPE_MOTION_SENSOR;
                    } else if (device == 2) {
                        logger.info("Found triggerlinc sensor {}", node.getAddress());
                        thingTypeUID = ISYBindingConstants.THING_TYPE_CONTACT_SENSOR;
                    }
                } else if (type == 7) {
                    logger.info("Found contact sensor {}", node.getAddress());
                    thingTypeUID = ISYBindingConstants.THING_TYPE_CONTACT_SENSOR;
                }

                if (thingTypeUID != null) {
                    thingDiscovered(DiscoveryResultBuilder.create(new ThingUID(thingTypeUID, id))
                            .withBridge(isyHandler.getThing().getUID()).withLabel(node.getName())
                            .withProperty("address", address).build());
                }

            }
        }
    }

    @Override
    protected void startScan() {
        discoverNodes();
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

}

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
import org.openhab.binding.isy.internal.ZwaveAddress;
import org.openhab.binding.isy.internal.protocol.Node;
import org.openhab.binding.isy.internal.protocol.Nodes;
import org.openhab.binding.isy.internal.protocol.elk.Area;
import org.openhab.binding.isy.internal.protocol.elk.Topology;
import org.openhab.binding.isy.internal.protocol.elk.Zone;
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

            Nodes nodes;
            try {
                nodes = isyHandler.getNodes().get();
                for (Node node : nodes.getNodes()) {
                    String address = node.getAddress();
                    if (address.startsWith("ZW")) {
                        // Zwave
                        handleZwaveDevice(node);
                    } else {
                        // Default to insteon
                        handleInsteonDevice(node);
                    }

                }

                discoverElkDevices();
            } catch (Exception e) {
                logger.error("Error discovering nodes", e);
            }

        }
    }

    private void handleInsteonDevice(Node node) {
        String address = node.getAddress();
        if (!address.endsWith(" 1")) {
            // Not primary device, skipping
            return;
        }
        InsteonAddress insteonAddress = null;
        try {
            insteonAddress = InsteonAddress.parseNodeAddress(address);
        } catch (IllegalArgumentException e) {
            return;
        }

        String[] parts = node.getType().split("\\.");
        if (parts.length != 4) {
            // Invalid type or we dont support it
            return;
        }

        int type = 0;
        int device = 0;
        try {
            type = Integer.parseInt(parts[0]);
            device = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
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
        } else {
            logger.info("Unsupported insteon device {}", node.getType());
        }

        if (thingTypeUID != null) {
            thingDiscovered(DiscoveryResultBuilder.create(new ThingUID(thingTypeUID, id))
                    .withBridge(isyHandler.getThing().getUID()).withLabel(node.getName())
                    .withProperty("address", address).build());
        }
    }

    private void handleZwaveDevice(Node node) {
        ZwaveAddress zwaveAddress;
        int category = 0;
        try {
            zwaveAddress = ZwaveAddress.parse(node.getAddress());
            category = Integer.parseInt(node.getDevtype().getCat());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid zwave address {} or category {}", node.getAddress(), node.getDevtype().getCat());
            return;
        }

        ThingTypeUID thingTypeUID = null;
        if (category == 104) {
            // Binary Sensor

        }

        // thingDiscovered(DiscoveryResultBuilder.create(new ThingUID()));
    }

    private void discoverElkDevices() throws Exception {
        Topology elkTopology = isyHandler.getElkTopology().get();
        if (elkTopology != null) {
            for (Area area : elkTopology.getAreas().getAreas()) {
                for (Zone zone : area.getZones()) {
                    thingDiscovered(DiscoveryResultBuilder
                            .create(new ThingUID(ISYBindingConstants.THING_TYPE_ELK_ZONE, "" + zone.getId()))
                            .withBridge(isyHandler.getThing().getUID()).withLabel(zone.getName())
                            .withProperty("zone", zone.getId()).withProperty("area", area.getId()).build());
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

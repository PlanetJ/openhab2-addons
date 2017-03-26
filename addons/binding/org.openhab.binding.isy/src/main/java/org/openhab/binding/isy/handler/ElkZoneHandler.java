package org.openhab.binding.isy.handler;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.isy.internal.ElkAddress;
import org.openhab.binding.isy.internal.ElkAddress.Type;
import org.openhab.binding.isy.internal.protocol.elk.ElkStatus;
import org.openhab.binding.isy.internal.protocol.elk.ZoneEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class ElkZoneHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(ElkZoneHandler.class.getName());

    private static final String CHANNEL_STATUS = "status";
    private static final String CHANNEL_VOLTAGE = "voltage";

    private ISYHandler isyHandler;
    private final ElkAddress address;
    private int area;

    public ElkZoneHandler(Thing thing) {
        super(thing);
        int id = Integer.parseInt(thing.getProperties().get("zone"));
        area = Integer.parseInt(thing.getProperties().get("area"));
        this.address = new ElkAddress(Type.ZONE, id);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.OFFLINE);
        isyHandler = (ISYHandler) getBridge().getHandler();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH) {
            logger.debug("Zone {} got REFRESH command", address.getId());
            Futures.addCallback(isyHandler.getElkStatus(), new FutureCallback<ElkStatus>() {

                @Override
                public void onFailure(Throwable arg0) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onSuccess(ElkStatus elkStatus) {
                    if (elkStatus == null) {
                        updateStatus(ThingStatus.OFFLINE);
                        return;
                    }
                    for (ZoneEvent zoneEvent : elkStatus.getZones()) {
                        if (zoneEvent.getZone() == address.getId()) {
                            handleZoneEvent(zoneEvent);
                            return;
                        }
                    }
                    updateStatus(ThingStatus.OFFLINE);
                }

            });

        }
    }

    public void handleZoneEvent(ZoneEvent zoneEvent) {
        if (zoneEvent.getZone() == getAddress().getId()) {
            updateStatus(ThingStatus.ONLINE);
            switch (zoneEvent.getType()) {
                case 51: // LogicalStatus
                    updateState(new ChannelUID(thing.getUID(), CHANNEL_STATUS), new DecimalType(zoneEvent.getVal()));
                    break;
                case 53: // Voltage
                    updateState(new ChannelUID(thing.getUID(), CHANNEL_VOLTAGE),
                            new DecimalType(zoneEvent.getVal() / 10.0d));
                    break;
                default:
            }
        }
    }

    public ElkAddress getAddress() {
        return address;
    }

}

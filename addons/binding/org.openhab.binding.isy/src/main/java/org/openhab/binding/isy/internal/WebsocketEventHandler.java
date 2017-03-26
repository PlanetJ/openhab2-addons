package org.openhab.binding.isy.internal;

import org.openhab.binding.isy.internal.protocol.elk.AreaEvent;
import org.openhab.binding.isy.internal.protocol.elk.ZoneEvent;

public interface WebsocketEventHandler {

    void nodePropertyUpdated(String address, String property, String value);

    void elkZoneEvent(ZoneEvent zoneEvent);

    void elkAreaEvent(AreaEvent areaEvent);

}

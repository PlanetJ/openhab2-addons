package org.openhab.binding.isy.internal.protocol;

import org.openhab.binding.isy.internal.protocol.elk.AreaEvent;
import org.openhab.binding.isy.internal.protocol.elk.ZoneEvent;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("eventInfo")
public class EventInfo {

    @XStreamAlias("ae")
    private AreaEvent areaEvent;

    @XStreamAlias("ze")
    private ZoneEvent zoneEvent;

    public AreaEvent getAreaEvent() {
        return areaEvent;
    }

    public void setAreaEvent(AreaEvent areaEvent) {
        this.areaEvent = areaEvent;
    }

    public ZoneEvent getZoneEvent() {
        return zoneEvent;
    }

    public void setZoneEvent(ZoneEvent zoneEvent) {
        this.zoneEvent = zoneEvent;
    }

}

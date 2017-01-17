package org.openhab.binding.isy.internal;

public interface WebsocketEventHandler {

    void nodePropertyUpdated(String address, String property, String value);

}

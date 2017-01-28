package org.openhab.binding.isy.internal;

public class SceneAddress implements NodeAddress {

    private final String address;

    public SceneAddress(String address) {
        this.address = address;
    }

    @Override
    public String getAddress() {
        return address;
    }

}

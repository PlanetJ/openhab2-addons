package org.openhab.binding.isy.internal;

public class ZwaveAddress implements NodeAddress {

    private int id;
    private int channel;

    public ZwaveAddress(int id, int channel) {
        this.id = id;
        this.channel = channel;
    }

    public static ZwaveAddress parse(String address) {
        String[] parts = address.split("_");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid address");
        }

        int id = 0;
        int channel = 0;
        try {
            id = Integer.parseInt(parts[0].substring(2));
            channel = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid zwave node address");
        }

        return new ZwaveAddress(id, channel);
    }

    @Override
    public String getAddress() {
        return String.format("ZW%03d_%d", id, channel);
    }

}

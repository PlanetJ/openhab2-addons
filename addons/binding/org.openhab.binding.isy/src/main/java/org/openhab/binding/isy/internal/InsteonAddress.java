package org.openhab.binding.isy.internal;

/**
 * A class to represent a InsteonAddress so that we don't need to manage
 * the format of the string representation everywhere.
 *
 * @author jmarchioni
 *
 */
public class InsteonAddress {

    private final String deviceNodeAddress;

    private InsteonAddress(String deviceNodeAddress) {
        this.deviceNodeAddress = deviceNodeAddress;
    }

    @Override
    public String toString() {
        return deviceNodeAddress;
    }

    public InsteonAddressChannel channel(int channel) {
        return new InsteonAddressChannel(this, channel);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((deviceNodeAddress == null) ? 0 : deviceNodeAddress.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        InsteonAddress other = (InsteonAddress) obj;
        if (deviceNodeAddress == null) {
            if (other.deviceNodeAddress != null) {
                return false;
            }
        } else if (!deviceNodeAddress.equals(other.deviceNodeAddress)) {
            return false;
        }
        return true;
    }

    /**
     * Parse address from the node address form
     *
     * @param nodeAddress in the form '[A]A [B]B [C]C D'
     * @return new instance of {@link InsteonAddress}
     */
    public static InsteonAddress parseNodeAddress(String nodeAddress) {
        int lastPos = nodeAddress.lastIndexOf(" ");
        String deviceAddress = nodeAddress.substring(0, lastPos);
        return new InsteonAddress(deviceAddress);
    }

    public static InsteonAddressChannel parseNodeAddressChannel(String nodeAddress) {
        int lastPos = nodeAddress.lastIndexOf(" ");
        int channel = Integer.parseInt(nodeAddress.substring(lastPos + 1));
        return new InsteonAddressChannel(parseNodeAddress(nodeAddress), channel);
    }

    public static class InsteonAddressChannel implements NodeAddress {

        private final InsteonAddress address;
        private final int channel;

        private InsteonAddressChannel(InsteonAddress address, int channel) {
            this.address = address;
            this.channel = channel;
        }

        public int getChannel() {
            return channel;
        }

        public InsteonAddress getInsteonAddress() {
            return address;
        }

        @Override
        public String getAddress() {
            return address.toString() + " " + channel;
        }
    }

}

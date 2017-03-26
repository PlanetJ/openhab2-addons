package org.openhab.binding.isy.internal;

public class ElkAddress {

    public static enum Type {
        AREA,
        ZONE,
        OUTPUT
    }

    private Type type;
    private int id;

    public ElkAddress(Type type, int id) {
        this.type = type;
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ((type == null) ? 0 : type.hashCode());
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
        ElkAddress other = (ElkAddress) obj;
        if (id != other.id) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        return true;
    }

}

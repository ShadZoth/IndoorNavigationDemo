package org.vaervo.indoornavigationdemo;

import java.io.Serializable;
import java.util.List;

class Record implements Serializable {
    private int location;
    private List<WifiNetworkInfo> description;

    Record(int location, List<WifiNetworkInfo> description) {
        this.location = location;
        this.description = description;
    }

    int getLocation() {
        return location;
    }

    List<WifiNetworkInfo> getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Record{" +
                "location=" + location +
                ", description=" + description +
                '}';
    }
}

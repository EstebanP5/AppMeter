package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class LocationsEnergyResponse {

    @SerializedName("Totals")
    private EnergyFlowTotals totals;

    @SerializedName("Locations")
    private List<LocationsViewModel> locations;

    public EnergyFlowTotals getTotals()           { return totals; }
    public List<LocationsViewModel> getLocations() { return locations; }
}

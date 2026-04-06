package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class LocationsEnergyPowerResponse {

    @SerializedName("Totals")
    private EnergyFlowTotals totals;

    @SerializedName("Locations")
    private List<LocationEnergyPower> locations;

    public EnergyFlowTotals getTotals()              { return totals; }
    public List<LocationEnergyPower> getLocations()   { return locations; }
}

package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class NodeTrafficLightResponse {

    @SerializedName("Nodes")
    private List<NodeTrafficLight> nodes;

    public List<NodeTrafficLight> getNodes() { return nodes; }
}

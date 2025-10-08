package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class CircuitsViewModel {
    @SerializedName("IdCircuit")
    private Integer idCircuit;

    @SerializedName("IdLocation")
    private Integer idLocation;

    @SerializedName("Name")
    private String name;

    @SerializedName("Description")
    private String description;

    @SerializedName("Rate")
    private String rate;

    @SerializedName("RPU")
    private String rpu;

    @SerializedName("kWhPrice")
    private Double kWhPrice;

    @SerializedName("IdCustomer")
    private Integer idCustomer;

    // Constructor vacío
    public CircuitsViewModel() {}

    // Constructor completo
    public CircuitsViewModel(Integer idCircuit, Integer idLocation, String name,
                             String description, String rate, String rpu,
                             Double kWhPrice, Integer idCustomer) {
        this.idCircuit = idCircuit;
        this.idLocation = idLocation;
        this.name = name;
        this.description = description;
        this.rate = rate;
        this.rpu = rpu;
        this.kWhPrice = kWhPrice;
        this.idCustomer = idCustomer;
    }

    // Constructor para crear nuevo circuito (sin ID)
    public CircuitsViewModel(Integer idLocation, String name, String description,
                             String rate, String rpu, Double kWhPrice, Integer idCustomer) {
        this.idLocation = idLocation;
        this.name = name;
        this.description = description;
        this.rate = rate;
        this.rpu = rpu;
        this.kWhPrice = kWhPrice;
        this.idCustomer = idCustomer;
    }

    // Getters y Setters
    public Integer getIdCircuit() {
        return idCircuit;
    }

    public void setIdCircuit(Integer idCircuit) {
        this.idCircuit = idCircuit;
    }

    public Integer getIdLocation() {
        return idLocation;
    }

    public void setIdLocation(Integer idLocation) {
        this.idLocation = idLocation;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRate() {
        return rate;
    }

    public void setRate(String rate) {
        this.rate = rate;
    }

    public String getRpu() {
        return rpu;
    }

    public void setRpu(String rpu) {
        this.rpu = rpu;
    }

    public Double getkWhPrice() {
        return kWhPrice;
    }

    public void setkWhPrice(Double kWhPrice) {
        this.kWhPrice = kWhPrice;
    }

    public Integer getIdCustomer() {
        return idCustomer;
    }

    public void setIdCustomer(Integer idCustomer) {
        this.idCustomer = idCustomer;
    }

    @Override
    public String toString() {
        return name != null ? name : "Circuito sin nombre";
    }
}
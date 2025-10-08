package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class LocationsViewModel {
    @SerializedName("IdLocation")
    private Integer idLocation;

    @SerializedName("IdCustomer")
    private Integer idCustomer;

    @SerializedName("Name")
    private String name;

    @SerializedName("Description")
    private String description;

    @SerializedName("Ubiety")
    private String ubiety;

    @SerializedName("Latitude")
    private Double latitude;

    @SerializedName("Longitude")
    private Double longitude;

    // Constructor vacío
    public LocationsViewModel() {}

    // Constructor completo
    public LocationsViewModel(Integer idLocation, Integer idCustomer, String name,
                              String description, String ubiety, Double latitude, Double longitude) {
        this.idLocation = idLocation;
        this.idCustomer = idCustomer;
        this.name = name;
        this.description = description;
        this.ubiety = ubiety;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Constructor para crear nueva localidad (sin ID)
    public LocationsViewModel(Integer idCustomer, String name, String description,
                              String ubiety, Double latitude, Double longitude) {
        this.idCustomer = idCustomer;
        this.name = name;
        this.description = description;
        this.ubiety = ubiety;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters y Setters
    public Integer getIdLocation() {
        return idLocation;
    }

    public void setIdLocation(Integer idLocation) {
        this.idLocation = idLocation;
    }

    public Integer getIdCustomer() {
        return idCustomer;
    }

    public void setIdCustomer(Integer idCustomer) {
        this.idCustomer = idCustomer;
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

    public String getUbiety() {
        return ubiety;
    }

    public void setUbiety(String ubiety) {
        this.ubiety = ubiety;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return name != null ? name : "Localidad sin nombre";
    }
}
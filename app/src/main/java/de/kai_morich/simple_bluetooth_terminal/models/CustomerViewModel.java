// File: CustomerViewModel.java
package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class CustomerViewModel implements Serializable {
    @SerializedName("IdCustomer")
    private Integer id;

    @SerializedName("Name")
    private String name;

    @SerializedName("Email")
    private String email;

    @SerializedName("Telephone")
    private String telephone;

    @SerializedName("State")
    private String state;

    @SerializedName("Logo")
    private String logo;

    @SerializedName("Config")
    private String config;

    // Constructor vacío
    public CustomerViewModel() {}

    // Constructor completo
    public CustomerViewModel(Integer id, String name, String email,
                             String telephone, String state,
                             String logo, String config) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.telephone = telephone;
        this.state = state;
        this.logo = logo;
        this.config = config;
    }

    // Constructor para crear nuevo cliente (sin ID)
    public CustomerViewModel(String name, String email, String telephone, String state) {
        this.name = name;
        this.email = email;
        this.telephone = telephone;
        this.state = state;
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    // Métodos de utilidad
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
                email != null && !email.trim().isEmpty();
    }

    public String getDisplayName() {
        return name != null ? name : "Cliente sin nombre";
    }


    @Override
    public String toString() {
        return name != null ? name : "Sin nombre";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerViewModel that = (CustomerViewModel) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}

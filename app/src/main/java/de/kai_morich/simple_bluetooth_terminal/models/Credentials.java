// File: models/Credentials.java
package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Modelo para el body de POST /api/octosunlux/login/login
 *
 * JSON esperado:
 * {
 *   "Email"   : "ergosolar@ergosolar.mx",
 *   "Password": "12345678"
 * }
 */
public class Credentials implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Correo electrónico del usuario */
    @SerializedName("Email")
    private String email;

    /** Contraseña del usuario */
    @SerializedName("Password")
    private String password;

    /** Constructor vacío requerido por Gson */
    public Credentials() {
    }

    /**
     * Constructor con parámetros
     * @param email    Correo electrónico
     * @param password Contraseña
     */
    public Credentials(String email, String password) {
        this.email    = email;
        this.password = password;
    }

    // --- Getters & Setters ---

    /**
     * @return el correo electrónico
     */
    public String getEmail() {
        return email;
    }

    /**
     * @param email el correo electrónico
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * @return la contraseña
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password la contraseña
     */
    public void setPassword(String password) {
        this.password = password;
    }

    // --- toString() para debugging ---

    @Override
    public String toString() {
        return "Credentials{" +
                "email='" + email + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
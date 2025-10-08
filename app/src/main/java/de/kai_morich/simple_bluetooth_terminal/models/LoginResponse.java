// File: LoginResponse.java
package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Modelo para la respuesta de POST /api/octosunlux/login/login
 *
 * Ejemplo de JSON devuelto:
 * {
 *   "Token": "eyJhbGciOiJ…",
 *   "Roll" : "Customer"
 * }
 */
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** El JWT que usarás en todas las llamadas protegidas */
    @SerializedName("Token")
    private String token;

    /** Rol de usuario devuelto por el servidor (p.ej. "Customer", "Admin") */
    @SerializedName("Roll")
    private String roll;

    // --- Constructores ---

    /** Constructor vacío requerido por Gson */
    public LoginResponse() {
    }

    /**
     * Constructor con campos
     * @param token JWT
     * @param roll  Rol
     */
    public LoginResponse(String token, String roll) {
        this.token = token;
        this.roll  = roll;
    }

    // --- Getters & Setters ---

    /**
     * @return el JWT para autenticación
     */
    public String getToken() {
        return token;
    }

    /**
     * @param token el JWT para autenticación
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * @return el rol de usuario (Customer, Admin…)
     */
    public String getRoll() {
        return roll;
    }

    /**
     * @param roll el rol de usuario (Customer, Admin…)
     */
    public void setRoll(String roll) {
        this.roll = roll;
    }

    // --- toString() para facilitar logging/debugging ---

    @Override
    public String toString() {
        return "LoginResponse{" +
                "token='" + token + '\'' +
                ", roll='"  + roll  + '\'' +
                '}';
    }
}

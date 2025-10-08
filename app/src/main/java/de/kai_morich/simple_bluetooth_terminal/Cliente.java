// app/src/main/java/de/kai_morich/simple_bluetooth_terminal/Cliente.java
package de.kai_morich.simple_bluetooth_terminal;

public class Cliente {
    private String nombre;
    private String email;
    private String telefono;
    private String estado;

    public Cliente(String nombre, String email, String telefono, String estado) {
        this.nombre = nombre;
        this.email = email;
        this.telefono = telefono;
        this.estado = estado;
    }

    public String getNombre() { return nombre; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public String getEstado() { return estado; }
}

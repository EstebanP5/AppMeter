// app/src/main/java/de/kai_morich/simple_bluetooth_terminal/DeviceItem.java
package de.kai_morich.simple_bluetooth_terminal;

public class DeviceItem {
    private String name;
    private String nodo;
    private String ultimaConexion;
    private String potencia;

    public DeviceItem(String name, String nodo, String ultimaConexion, String potencia) {
        this.name = name;
        this.nodo = nodo;
        this.ultimaConexion = ultimaConexion;
        this.potencia = potencia;
    }

    public String getName() { return name; }
    public String getNodo() { return nodo; }
    public String getUltimaConexion() { return ultimaConexion; }
    public String getPotencia() { return potencia; }
}

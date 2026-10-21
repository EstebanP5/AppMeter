package de.kai_morich.simple_bluetooth_terminal.util;

import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;

/** Clasifica un nodo por su tipo (Type 0 = CFE/red, 2 = paneles, 3 = BESS) o, si falta, por su TypeName. */
public final class NodeKind {

    public static final int SOLAR = 0, CFE = 1, BESS = 2, OTHER = 3;

    private NodeKind() {}

    public static int of(NodeTrafficLight n) {
        return of(n.getType(), n.getTypeName());
    }

    public static int of(Integer type, String typeName) {
        int t = type != null ? type : -1;
        String tn = typeName != null ? typeName.toLowerCase(Locale.ROOT) : "";
        if (t == 2 || tn.contains("solar") || tn.contains("fotovolt") || tn.contains("panel")) return SOLAR;
        if (t == 3 || tn.contains("bater") || tn.contains("bess")) return BESS;
        if (t == 0 || tn.contains("cfe") || tn.contains("transform") || tn.contains("red")) return CFE;
        return OTHER;
    }
}

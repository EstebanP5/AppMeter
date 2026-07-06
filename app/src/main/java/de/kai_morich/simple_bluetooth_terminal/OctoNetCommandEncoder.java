package de.kai_morich.simple_bluetooth_terminal;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Codificador/Decodificador COMPLETO para protocolo OctoNet
 */
public class OctoNetCommandEncoder {

    // Constantes del protocolo
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte CMD_READ = 0x52;
    private static final byte CMD_WRITE = 0x77;

    // Comandos
    private static final byte CMD_DEVICE_ID = 0x00;
    private static final byte CMD_DEVICE_TIME = 0x01;
    private static final byte CMD_NODE_SETTINGS = 0x20;
    private static final byte CMD_NODE_CURRENT = 0x21;
    private static final byte CMD_NODE_REC_INFO = 0x22;
    private static final byte CMD_NODE_REC_ERASE = 0x25;
    private static final byte CMD_SETTINGS_WIFI = (byte)0xE3;
    private static final byte CMD_SETTINGS_SUB1G = (byte)0xE4;

    // IDs de configuración s
    private static final byte ID_METER_SETTINGS = (byte)0xEA;
    private static final byte ID_CELL_SETTINGS = (byte)0xCA;
    private static final byte ID_ENERGY_3PHA_LOAD = (byte)0xC3;

    // Tamaños de estructura
    private static final int METER_SETTINGS_SIZE = 12;
    private static final int ENERGY_3PHA_PAGE_SIZE = 64;
    private static final int WIFI_SETTINGS_SIZE = 247;
    private static final int SUB1G_SETTINGS_SIZE = 16;   // Tabla 23 - SETTINGS_SUB1G_STRC
    private static final byte ID_SUB1G_SETTINGS = (byte)0xA4;



    // =================================================================
    // MÁQUINA DE ESTADOS PARA DECODIFICACIÓN
    // =================================================================

    // Estados de decodificación
    public enum CmdState {
        Start,      // Esperando byte de inicio (STX)
        Type,       // Leyendo tipo de comando
        Command,    // Leyendo comando específico
        Ndata,      // Leyendo número de bytes de datos
        Data,       // Leyendo datos del comando
        Checksum,   // Verificando checksum
        End,        // Verificando byte de fin (ETX)
        Done        // Comando completado
    }

    // Tipos de comando
    public enum CmdType {
        READ((byte)0x52),
        WRITE((byte)0x77),
        ERASE((byte)0x73),
        CONFIRMATION((byte)0x43),
        INFORMATION((byte)0x69), // 69h según protocolo
        ERROR((byte)0x45);

        private final byte value;
        CmdType(byte value) { this.value = value; }
        public byte getValue() { return value; }
    }

    // Lista de comandos soportados
    public enum CmdList {
        DEVICE_ID((byte)0x00),
        DEVICE_TIME((byte)0x01),
        NODE_SETTINGS((byte)0x20),
        NODE_CURRENT((byte)0x21),
        NODE_REC_INFO((byte)0x22),
        NODE_REC_ERASE((byte)0x25),
        SETTINGS_WIFI((byte)0xE3),
        SETTINGS_SUB1G((byte)0xE4),
        NOT_SUPPORTED((byte)0xFF);

        private final byte value;
        CmdList(byte value) { this.value = value; }
        public byte getValue() { return value; }
    }

    // Bytes de inicio y fin
    public enum CmdStartEnd {
        START((byte)0x02),
        END((byte)0x03);

        private final byte value;
        CmdStartEnd(byte value) { this.value = value; }
        public byte getValue() { return value; }
    }

    // Conjunto de comandos combinados
    public enum CmdSet {
        // Comandos de lectura
        DEVICE_ID_READ(0x5200),
        DEVICE_TIME_READ(0x5201),
        NODE_SETTINGS_READ(0x5220),
        NODE_CURRENT_READ(0x5221),
        NODE_REC_INFO_READ(0x5222),
        SETTINGS_WIFI_READ(0x52E3),
        SETTINGS_SUB1G_READ(0x52E4),

        // Comandos de escritura
        DEVICE_ID_WRITE(0x7700),
        DEVICE_TIME_WRITE(0x7701),
        NODE_SETTINGS_WRITE(0x7720),
        NODE_CURRENT_WRITE(0x7721),
        NODE_REC_ERASE_WRITE(0x7725),
        SETTINGS_WIFI_WRITE(0x77E3),
        SETTINGS_SUB1G_WRITE(0x77E4),

        // Respuestas de confirmación
        DEVICE_ID_CONFIRMATION(0x4300),
        DEVICE_TIME_CONFIRMATION(0x4301),
        NODE_SETTINGS_CONFIRMATION(0x4320),
        NODE_CURRENT_CONFIRMATION(0x4321),
        NODE_REC_INFO_CONFIRMATION(0x4322),
        NODE_REC_ERASE_CONFIRMATION(0x4325),
        SETTINGS_WIFI_CONFIRMATION(0x43E3),
        SETTINGS_SUB1G_CONFIRMATION(0x43E4),

        // Errores
        TYPE_INVALID_ERROR(0x0001),
        NOT_SUPPORTED_ERROR(0x0002),
        CHECKSUM_ERROR(0x0003),
        END_MISSING_ERROR(0x0004);

        private final int value;
        CmdSet(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    public static float bytesToFloat(byte b0, byte b1, byte b2, byte b3) {
        // Convertir 4 bytes little-endian a float IEEE 754
        int intBits = ((b3 & 0xFF) << 24) |
                ((b2 & 0xFF) << 16) |
                ((b1 & 0xFF) << 8) |
                (b0 & 0xFF);
        return Float.intBitsToFloat(intBits);
    }

    // Estado del decodificador
    public static class DecodeState {
        public CmdState decodeState = CmdState.Start;
        public CmdSet type = CmdSet.TYPE_INVALID_ERROR;
        public byte[] data = new byte[512]; // ✅ INCREMENTADO para WiFi (247 bytes)
        public int ind = 0;                 // Índice en el buffer de datos
        public int tcpIndex = 0;           // Índice en el buffer TCP
        public long checksum = 0;          // Checksum calculado
        public int dataBytes = 0;          // Número de bytes de datos esperados
        public int byteCounter = 0;        // Contador de bytes de datos restantes

        public void reset() {
            decodeState = CmdState.Start;
            type = CmdSet.TYPE_INVALID_ERROR;
            Arrays.fill(data, (byte)0);
            ind = 0;
            tcpIndex = 0;
            checksum = 0;
            dataBytes = 0;
            byteCounter = 0;
        }
    }

    // Callback interface para procesamiento de comandos
    public interface CommandCallback {
        void onCommandReceived(CmdSet commandType, byte[] commandData);
        void onCommandError(CmdSet errorType, String errorMessage);
    }

    // =================================================================
    // MÁQUINA DE ESTADOS
    // =================================================================

    public static class StateMachine {
        private final DecodeState decode = new DecodeState();
        private final CommandCallback callback;

        public StateMachine(CommandCallback callback) {
            this.callback = callback;
        }

        /**
         * Procesa bytes recibidos del TCP y decodifica comandos
         */
        public void processReceivedBytes(byte[] buffer, int bytesReceived) {
            int tcpBytesRx = bytesReceived;
            decode.tcpIndex = 0;

            // Procesa todos los bytes recibidos
            while (tcpBytesRx > 0) {
                // Interpreta byte a byte de manera ordenada
                switch (decode.decodeState) {
                    case Start:
                        handleStartState(buffer);
                        break;

                    case Type:
                        handleTypeState(buffer);
                        break;

                    case Command:
                        handleCommandState(buffer);
                        break;

                    case Ndata:
                        handleNdataState(buffer);
                        break;

                    case Data:
                        handleDataState(buffer);
                        break;

                    case Checksum:
                        handleChecksumState(buffer);
                        break;

                    case End:
                        handleEndState(buffer);
                        return; // Salir después de procesar comando completo

                    default:
                        decode.reset();
                        break;
                }

                // Avanzar al siguiente byte
                decode.tcpIndex++;
                tcpBytesRx--;
            }
        }

        private void handleStartState(byte[] buffer) {
            if (buffer[decode.tcpIndex] == CmdStartEnd.START.getValue()) {
                // Define el índice de entrada para el comando
                decode.ind = 0;
                // Limpia array donde se almacena el nuevo comando recibido
                Arrays.fill(decode.data, (byte)0);

                // Almacena el byte "Start"
                decode.data[decode.ind++] = buffer[decode.tcpIndex];
                // Calcula checksum
                decode.checksum = CmdStartEnd.START.getValue() & 0xFF;
                // Siguiente estado
                decode.decodeState = CmdState.Type;
            }
        }

        private void handleTypeState(byte[] buffer) {
            byte currentByte = buffer[decode.tcpIndex];

            if (currentByte == CmdType.READ.getValue() ||
                    currentByte == CmdType.WRITE.getValue() ||
                    currentByte == CmdType.ERASE.getValue() ||
                    currentByte == CmdType.CONFIRMATION.getValue() ||
                    currentByte == CmdType.INFORMATION.getValue() ||
                    currentByte == CmdType.ERROR.getValue()) {

                // Almacena el byte "Type"
                decode.data[decode.ind++] = currentByte;
                // Almacena el tipo de comando (shift left 8 bits)
                decode.type = findCmdSetByValue((currentByte & 0xFF) << 8);
                // Calcula checksum
                decode.checksum += (currentByte & 0xFF) * decode.ind;
                // Siguiente estado
                decode.decodeState = CmdState.Command;
            } else {
                // Tipo inválido
                decode.type = CmdSet.TYPE_INVALID_ERROR;
                decode.decodeState = CmdState.Done;
                processCommand();
            }
        }

        private void handleCommandState(byte[] buffer) {
            byte currentByte = buffer[decode.tcpIndex];

            if ((currentByte & 0xFF) < CmdList.NOT_SUPPORTED.getValue() ||
                    (currentByte & 0xFF) == (CMD_NODE_REC_INFO & 0xFF) ||
                    (currentByte & 0xFF) == (CMD_NODE_REC_ERASE & 0xFF) ||
                    (currentByte & 0xFF) == (CMD_SETTINGS_WIFI & 0xFF) ||
                    (currentByte & 0xFF) == (CMD_SETTINGS_SUB1G & 0xFF)) {

                // Almacena el byte "Command"
                decode.data[decode.ind++] = currentByte;

                // Reconstruir comando completo correctamente
                int typeValue = decode.data[1] & 0xFF; // Byte TYPE del comando
                int commandValue = currentByte & 0xFF;  // Byte COMMAND actual
                int fullCommandValue = (typeValue << 8) | commandValue;

                // Buscar el comando completo
                decode.type = findCmdSetByValue(fullCommandValue);

                // Calcula checksum
                decode.checksum += (currentByte & 0xFF) * decode.ind;
                // Siguiente estado
                decode.decodeState = CmdState.Ndata;
            } else {
                // Comando no soportado
                decode.type = CmdSet.NOT_SUPPORTED_ERROR;
                decode.decodeState = CmdState.Done;
                processCommand();
            }
        }

        private void handleNdataState(byte[] buffer) {
            byte currentByte = buffer[decode.tcpIndex];

            // Almacena el número de bytes que vienen en el área de datos
            decode.dataBytes = currentByte & 0xFF;

            // ✅ CORRECCIÓN: Solo NODE_CURRENT usa +1, otros comandos usan el tamaño exacto
            if (decode.data[2] == CMD_NODE_CURRENT) {
                decode.byteCounter = decode.dataBytes + 1; // Solo NODE_CURRENT
            } else {
                decode.byteCounter = decode.dataBytes; // Todos los demás, incluyendo WiFi
            }

            // Almacena el byte "Ndata"
            decode.data[decode.ind++] = currentByte;
            // Calcula checksum
            decode.checksum += (currentByte & 0xFF) * decode.ind;
            // Siguiente estado
            decode.decodeState = CmdState.Data;
        }

        private void handleDataState(byte[] buffer) {
            byte currentByte = buffer[decode.tcpIndex];

            // Almacena datos del comando
            decode.data[decode.ind++] = currentByte;
            // Calcula checksum
            decode.checksum += (currentByte & 0xFF) * decode.ind;
            // Decrementa el número de bytes por almacenar
            decode.byteCounter--;

            // ¿Fueron almacenados todos los bytes?
            if (decode.byteCounter == 0) {
                decode.decodeState = CmdState.Checksum;
            }
        }

        private void handleChecksumState(byte[] buffer) {
            byte currentByte = buffer[decode.tcpIndex];

            // ¿Checksum correcto?
            if ((byte)decode.checksum == currentByte) {
                // Almacena el byte "Checksum"
                decode.data[decode.ind++] = currentByte;
                // Siguiente estado
                decode.decodeState = CmdState.End;
            } else {
                // Error de checksum
                decode.type = CmdSet.CHECKSUM_ERROR;
                decode.decodeState = CmdState.Done;
                processCommand();
            }
        }

        private void handleEndState(byte[] buffer) {
            byte currentByte = buffer[decode.tcpIndex];

            // ¿Byte de fin correcto?
            if (currentByte == CmdStartEnd.END.getValue()) {
                // Almacena el byte "END"
                decode.data[decode.ind++] = currentByte;
                // Comando completado exitosamente
                decode.decodeState = CmdState.Done;
            } else {
                // Byte de fin faltante
                decode.type = CmdSet.END_MISSING_ERROR;
                decode.decodeState = CmdState.Done;
            }

            processCommand();
        }

        private void processCommand() {
            if (isErrorType(decode.type)) {
                // Reportar error
                String errorMessage = getErrorMessage(decode.type);
                callback.onCommandError(decode.type, errorMessage);
            } else {
                // Procesar comando válido
                byte[] commandData = Arrays.copyOf(decode.data, decode.ind);
                callback.onCommandReceived(decode.type, commandData);
            }

            // Resetear para el próximo comando
            decode.reset();
        }

        private boolean isErrorType(CmdSet type) {
            return type == CmdSet.TYPE_INVALID_ERROR ||
                    type == CmdSet.NOT_SUPPORTED_ERROR ||
                    type == CmdSet.CHECKSUM_ERROR ||
                    type == CmdSet.END_MISSING_ERROR;
        }

        private String getErrorMessage(CmdSet errorType) {
            switch (errorType) {
                case TYPE_INVALID_ERROR:
                    return "Tipo de comando inválido";
                case NOT_SUPPORTED_ERROR:
                    return "Comando no soportado";
                case CHECKSUM_ERROR:
                    return "Error de checksum";
                case END_MISSING_ERROR:
                    return "Byte de fin faltante";
                default:
                    return "Error desconocido";
            }
        }

        private CmdSet findCmdSetByValue(int value) {
            // Primero buscar coincidencia exacta
            for (CmdSet cmd : CmdSet.values()) {
                if (cmd.getValue() == value) {
                    return cmd;
                }
            }

            return CmdSet.TYPE_INVALID_ERROR;
        }

        /**
         * Obtiene información sobre el comando actual
         */
        public String getCommandInfo() {
            return String.format("Estado: %s, Tipo: %s, Índice: %d, Checksum: 0x%02X",
                    decode.decodeState, decode.type, decode.ind, decode.checksum & 0xFF);
        }

        /**
         * Resetea la máquina de estados
         */
        public void reset() {
            decode.reset();
        }
    }

    // =================================================================
    // ENCODER
    // =================================================================

    /**
     * Calcula el checksum según la implementación C#
     */
    private static byte calculateChecksum(byte[] data, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (data[i] & 0xFF) * (i + 1);
        }
        return (byte)(sum & 0xFF);
    }

    /**
     * ✅ MÉTODO PÚBLICO para codificar comandos (incluyendo WiFi)
     */
    public static byte[] encodeCommand(byte type, byte command, byte[] data) {
        ArrayList<Byte> cmdList = new ArrayList<>();

        // 1. Byte de inicio (STX)
        cmdList.add(STX);

        // 2. Tipo de comando
        cmdList.add(type);

        // 3. Comando específico
        cmdList.add(command);

        // 4. Tamaño de datos y datos
        if (type == CMD_READ) {
            // Para comandos READ: SIZE = 0, agregar solo 1 byte de data (0x00)
            cmdList.add((byte)0x00);  // SIZE = 0
            cmdList.add((byte)0x00);  // DATA = 0x00
        } else {
            // ✅ CORRECCIÓN: Para comandos WRITE: SIZE = data.length - 1
            int dataSize = (data != null) ? data.length : 0;

            if (dataSize > 0) {
                // ✅ AQUÍ ESTÁ LA CORRECCIÓN: Restamos 1 al tamaño pero enviamos todos los datos
                cmdList.add((byte)(dataSize - 1));  // SIZE = data.length - 1

                // Agregar TODOS los datos (sin quitar ningún byte)
                for (byte b : data) {
                    cmdList.add(b);
                }
            } else {
                cmdList.add((byte)0x00);  // SIZE = 0 si no hay datos
            }
        }

        // 5. Calcular checksum sobre los bytes agregados hasta ahora
        byte[] tempArray = new byte[cmdList.size()];
        for (int i = 0; i < cmdList.size(); i++) {
            tempArray[i] = cmdList.get(i);
        }
        byte checksum = calculateChecksum(tempArray, tempArray.length);
        cmdList.add(checksum);

        // 6. Byte de fin (ETX)
        cmdList.add(ETX);

        // Convertir a array de bytes
        byte[] result = new byte[cmdList.size()];
        for (int i = 0; i < cmdList.size(); i++) {
            result[i] = cmdList.get(i);
        }

        return result;
    }

    // ===== COMANDOS BÁSICOS  =====

    public static byte[] createDeviceIdReadCommand() {
        byte[] cmd = encodeCommand(CMD_READ, CMD_DEVICE_ID, null);
        System.out.println("🔍 DEVICE_ID read generado: " + bytesToHexString(cmd));
        return cmd;
    }

    public static byte[] createDeviceTimeReadCommand() {
        byte[] cmd = encodeCommand(CMD_READ, CMD_DEVICE_TIME, null);
        System.out.println("🔍 DEVICE_TIME read generado: " + bytesToHexString(cmd));
        return cmd;
    }

    public static byte[] createNodeSettingsReadCommand() {
        byte[] cmd = encodeCommand(CMD_READ, CMD_NODE_SETTINGS, null);
        System.out.println("🔍 NODE_SETTINGS read generado: " + bytesToHexString(cmd));
        return cmd;
    }

    public static byte[] createNodeCurrentReadCommand() {
        byte[] cmd = encodeCommand(CMD_READ, CMD_NODE_CURRENT, null);
        System.out.println("🔍 NODE_CURRENT read generado: " + bytesToHexString(cmd));
        return cmd;
    }

    // Comando para leer configuración WiFi
    public static byte[] createWiFiSettingsReadCommand() {
        byte[] cmd = encodeCommand(CMD_READ, CMD_SETTINGS_WIFI, null);
        System.out.println("🔍 SETTINGS_WIFI read generado: " + bytesToHexString(cmd));
        return cmd;
    }

    //  MÉTODO PARA DEVICE_TIME_WRITE
    public static byte[] createDeviceTimeWriteCommand(int year, int month, int day, int hour, int minute, int second, int dayOfWeek) {
        // Validar parámetros
        if (year < 1900 || year > 2100) {
            throw new IllegalArgumentException("Año fuera de rango válido (1900-2100)");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Mes fuera de rango válido (1-12)");
        }
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException("Día fuera de rango válido (1-31)");
        }
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Hora fuera de rango válido (0-23)");
        }
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Minuto fuera de rango válido (0-59)");
        }
        if (second < 0 || second > 59) {
            throw new IllegalArgumentException("Segundo fuera de rango válido (0-59)");
        }
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("Día de semana fuera de rango válido (1-7)");
        }


        byte[] timeData = new byte[6];

        // Formato completo: 6 bytes con segundos
        timeData[0] = (byte)(year - 2000);  // Año (25 para 2025)
        int dayWeekMonth = (dayOfWeek * 16) + month; // Ej: día 3 × 16 + mes 7 = 55
        timeData[1] = (byte)dayWeekMonth;   // Un solo byte con día_semana y mes
        timeData[2] = (byte)day;            // Día (1-31)
        timeData[3] = (byte)hour;           // Hora (0-23)
        timeData[4] = (byte)minute;         // Minuto (0-59)
        timeData[5] = (byte)second;         // Segundo (0-59)

        System.out.println("🔍 DEVICE_TIME_WRITE datos generados (6 bytes, SIZE será 5):");
        System.out.printf("   Byte 0 - Año: %d -> 0x%02X%n", year - 2000, timeData[0] & 0xFF);
        System.out.printf("   Byte 1 - Día_semana(%d) × 16 + Mes(%d) = %d -> 0x%02X%n", dayOfWeek, month, dayWeekMonth, timeData[1] & 0xFF);
        System.out.printf("   Byte 2 - Día: %d -> 0x%02X%n", day, timeData[2] & 0xFF);
        System.out.printf("   Byte 3 - Hora: %d -> 0x%02X%n", hour, timeData[3] & 0xFF);
        System.out.printf("   Byte 4 - Minuto: %d -> 0x%02X%n", minute, timeData[4] & 0xFF);
        System.out.printf("   Byte 5 - Segundo: %d -> 0x%02X%n", second, timeData[5] & 0xFF);


        byte[] cmd = encodeCommand(CMD_WRITE, CMD_DEVICE_TIME, timeData);
        System.out.println("🔍 DEVICE_TIME write generado: " + bytesToHexString(cmd));

        return cmd;
    }

    public static byte[] createNodeSettingsWriteCommand(boolean recording, int period, int sensorAmps, int meteringType) {
        // Array de 12 bytes según TABLA 11 - METER_SETTINGS_STRC
        byte[] settingsData = new byte[12];

        int index = 0;

        // Byte 0: ID (0xEA - Identificador de Ajustes)
        settingsData[index++] = (byte)0xEA;

        // Byte 1: FW_VER (2 dígitos más significativos del FW)
        settingsData[index++] = (byte)0x01;  // Versión 1 por defecto

        // Bytes 2-4: YY/MM/DD (Fecha de último cambio de Ajustes)
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year = cal.get(java.util.Calendar.YEAR) % 100;
        int month = cal.get(java.util.Calendar.MONTH) + 1;
        int day = cal.get(java.util.Calendar.DAY_OF_MONTH);

        settingsData[index++] = (byte)year;   // Byte 2: Año (00h-63h)
        settingsData[index++] = (byte)month;  // Byte 3: Mes (01h-0Ch)
        settingsData[index++] = (byte)day;    // Byte 4: Día (01h-1Fh)

        // Bytes 5-7: HH:MM:SS (Hora de último cambio de Ajustes)
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = cal.get(java.util.Calendar.MINUTE);
        int second = cal.get(java.util.Calendar.SECOND);

        settingsData[index++] = (byte)hour;   // Byte 5: Hora (00h-17h)
        settingsData[index++] = (byte)minute; // Byte 6: Minuto (00h-3Bh)
        settingsData[index++] = (byte)second; // Byte 7: Segundo (00h-3Bh)

        // Byte 8: REC_ON/OFF
        settingsData[index++] = recording ? (byte)0x01 : (byte)0x00;

        // Byte 9: PERIOD
        switch (period) {
            case 0:  settingsData[index++] = (byte)0x00; break;  // 1 minuto
            case 1:  settingsData[index++] = (byte)0x01; break;  // 5 minutos
            case 2:  settingsData[index++] = (byte)0x02; break;  // 10 minutos
            case 3:  settingsData[index++] = (byte)0x03; break;  // 15 minutos
            default: settingsData[index++] = (byte)0x01; break;  // Default 5 minutos
        }

        // Byte 10: SENSORS
        switch (sensorAmps) {
            case 0:   settingsData[index++] = (byte)0x00; break;  // Shunt-20A
            case 1:   settingsData[index++] = (byte)0x01; break;  // CT-50A
            case 2:   settingsData[index++] = (byte)0x02; break;  // CT-200A
            case 3:   settingsData[index++] = (byte)0x03; break;  // CT-400A
            case 4:   settingsData[index++] = (byte)0x04; break;  // RoGo-1000A
            case 5:   settingsData[index++] = (byte)0x05; break;  // RoGo-3000A
            default:  settingsData[index++] = (byte)0x02; break;  // Default CT-200A
        }

        // Byte 11: WIRING_SET (Tipo de cableado)
        switch (meteringType) {
            case 0: settingsData[index++] = (byte)0x00; break;  // 1PH_2W_4LOADS
            case 1: settingsData[index++] = (byte)0x01; break;  // 1PH_3W_2LOAD
            case 2: settingsData[index++] = (byte)0x02; break;  // 3PH_4W_1LOAD
            case 3: settingsData[index++] = (byte)0x03; break;  // 3PH_4W_N_1LOAD
            default: settingsData[index++] = (byte)0x02; break; // ✅ Default cambia a 3PH_4W_1LOAD
        }

        byte[] cmd = encodeCommand(CMD_WRITE, CMD_NODE_SETTINGS, settingsData);

        // ✅ LOGS MEJORADOS Y DETALLADOS
        System.out.println("🔍 METER_SETTINGS write generado (Tabla 11):");
        System.out.println("   📦 Comando HEX: " + bytesToHexString(cmd));
        System.out.println("   ⚙️  Configuración:");
        System.out.println("      • ID: 0xEA (Identificador de Ajustes)");
        System.out.println("      • FW_VER: 0x01");
        System.out.println("      • REC: " + (recording ? "ON (0x01)" : "PAUSA (0x00)"));
        System.out.println("      • PERIOD: " + getPeriodName(period) + " (0x0" + period + ")");
        System.out.println("      • SENSORS: " + getSensorName(sensorAmps) + " (0x0" + sensorAmps + ")");
        System.out.println("      • WIRING_SET: " + getWiringSetName(meteringType) + " (0x0" + meteringType + ")");
        System.out.println("   📅 Fecha/Hora de último cambio:");
        System.out.printf("      • %02d/%02d/20%02d %02d:%02d:%02d%n", day, month, year, hour, minute, second);

        return cmd;
    }

    // ✅ MÉTODOS HELPER PARA LOGS DESCRIPTIVOS
    private static String getPeriodName(int period) {
        switch (period) {
            case 0: return "1 minuto";
            case 1: return "5 minutos";
            case 2: return "10 minutos";
            case 3: return "15 minutos";
            default: return "Desconocido";
        }
    }

    private static String getSensorName(int sensor) {
        switch (sensor) {
            case 0: return "Shunt-20A";
            case 1: return "CT-50A";
            case 2: return "CT-200A ✅";
            case 3: return "CT-400A";
            case 4: return "RoGo-1000A ✅";
            case 5: return "RoGo-3000A ✅";
            default: return "Desconocido";
        }
    }

    private static String getWiringSetName(int wiring) {
        switch (wiring) {
            case 0: return "1PH_2W_4LOADS ✅";
            case 1: return "1PH_3W_2LOAD ✅";
            case 2: return "3PH_4W_1LOAD ✅";
            case 3: return "3PH_4W_N_1LOAD";
            default: return "Desconocido";
        }
    }




    // Comando para escribir configuración WiFi
    public static byte[] createWiFiSettingsWriteCommand(String ssid, String password, String mac, String ip,
                                                        String gateway, String netmask, String dnsPri,
                                                        String dnsSec, String dnsTrd, int rssi) {
        // Crear estructura SETTINGS_WIFI_STRC  (247 bytes total)
        byte[] wifiData = new byte[WIFI_SETTINGS_SIZE];

        int offset = 0;

        // SSID (64 bytes) - Bytes 0-63
        if (ssid != null && !ssid.isEmpty()) {
            byte[] ssidBytes = ssid.getBytes();
            System.arraycopy(ssidBytes, 0, wifiData, offset,
                    Math.min(ssidBytes.length, 64));
        }
        offset += 64;

        // PASSWORD (64 bytes) - Bytes 64-127
        if (password != null && !password.isEmpty()) {
            byte[] passwordBytes = password.getBytes();
            System.arraycopy(passwordBytes, 0, wifiData, offset,
                    Math.min(passwordBytes.length, 64));
        }
        offset += 64;

        // MAC (20 bytes) - Bytes 128-147
        if (mac != null && !mac.isEmpty()) {
            byte[] macBytes = mac.getBytes();
            System.arraycopy(macBytes, 0, wifiData, offset,
                    Math.min(macBytes.length, 20));
        }
        offset += 20;

        // IP (16 bytes) - Bytes 148-163
        if (ip != null && !ip.isEmpty()) {
            byte[] ipBytes = ip.getBytes();
            System.arraycopy(ipBytes, 0, wifiData, offset,
                    Math.min(ipBytes.length, 16));
        }
        offset += 16;

        // RSSI (4 bytes) - Bytes 164-167
        wifiData[offset] = (byte)(rssi & 0xFF);
        wifiData[offset + 1] = (byte)((rssi >> 8) & 0xFF);
        wifiData[offset + 2] = (byte)((rssi >> 16) & 0xFF);
        wifiData[offset + 3] = (byte)((rssi >> 24) & 0xFF);
        offset += 4;

        // GATEWAY (16 bytes) - Bytes 168-183
        if (gateway != null && !gateway.isEmpty()) {
            byte[] gatewayBytes = gateway.getBytes();
            System.arraycopy(gatewayBytes, 0, wifiData, offset,
                    Math.min(gatewayBytes.length, 16));
        }
        offset += 16;

        // NETMASK (16 bytes) - Bytes 184-199
        if (netmask != null && !netmask.isEmpty()) {
            byte[] netmaskBytes = netmask.getBytes();
            System.arraycopy(netmaskBytes, 0, wifiData, offset,
                    Math.min(netmaskBytes.length, 16));
        }
        offset += 16;

        // DNS_PRI (16 bytes) - Bytes 200-215
        if (dnsPri != null && !dnsPri.isEmpty()) {
            byte[] dnsPriBytes = dnsPri.getBytes();
            System.arraycopy(dnsPriBytes, 0, wifiData, offset,
                    Math.min(dnsPriBytes.length, 16));
        }
        offset += 16;

        // DNS_SEC (16 bytes) - Bytes 216-231
        if (dnsSec != null && !dnsSec.isEmpty()) {
            byte[] dnsSecBytes = dnsSec.getBytes();
            System.arraycopy(dnsSecBytes, 0, wifiData, offset,
                    Math.min(dnsSecBytes.length, 16));
        }
        offset += 16;

        // DNS_TRD (16 bytes) - Bytes 232-247
        if (dnsTrd != null && !dnsTrd.isEmpty()) {
            byte[] dnsTrdBytes = dnsTrd.getBytes();
            System.arraycopy(dnsTrdBytes, 0, wifiData, offset,
                    Math.min(dnsTrdBytes.length, 16));
        }

        byte[] cmd = encodeCommand(CMD_WRITE, CMD_SETTINGS_WIFI, wifiData);
        System.out.println("🔍 SETTINGS_WIFI write generado: " + bytesToHexString(cmd));
        System.out.println("   WiFi Config - SSID: " + ssid + ", Password: [HIDDEN]");

        return cmd;
    }

    // ✅ MÉTODO SIMPLIFICADO para WiFi solo con SSID y PASSWORD
    public static byte[] createWiFiSettingsWriteCommand(String ssid, String password) {
        return createWiFiSettingsWriteCommand(ssid, password, "", "", "", "", "", "", "", 0);
    }

    // =================================================================
    // SETTINGS_SUB1G (E4h) - Tabla 23
    // =================================================================

    // Comando para LEER la configuración Sub-1G
    public static byte[] createSub1gSettingsReadCommand() {
        byte[] cmd = encodeCommand(CMD_READ, CMD_SETTINGS_SUB1G, null);
        System.out.println("🔍 SETTINGS_SUB1G read generado: " + bytesToHexString(cmd));
        return cmd;
    }

    // Comando para LEER la información de grabaciones (NODE_REC_INFO - 22h)
    public static byte[] createNodeRecInfoReadCommand() {
        byte[] cmd = encodeCommand(CMD_READ, CMD_NODE_REC_INFO, null);
        System.out.println("🔍 NODE_REC_INFO read generado: " + bytesToHexString(cmd));
        return cmd;
    }

    // Comando para BORRAR la memoria (NODE_REC_ERASE - 25h), formateo rápido = 0x01
    public static byte[] createNodeRecEraseCommand() {
        byte[] cmd = encodeCommand(CMD_WRITE, CMD_NODE_REC_ERASE, new byte[]{0x01});
        System.out.println("🔍 NODE_REC_ERASE write generado: " + bytesToHexString(cmd));
        return cmd;
    }

    public static NodeRecInfo processNodeRecInfoResponse(byte[] data) {
        NodeRecInfo info = new NodeRecInfo();
        if (data == null || data.length < 20) return info;
        try {
            info.newRecords   = ((data[0] & 0xFFL))       | ((data[1] & 0xFFL) << 8)
                              | ((data[2] & 0xFFL) << 16) | ((data[3] & 0xFFL) << 24);
            info.totalRecords = ((data[4] & 0xFFL))       | ((data[5] & 0xFFL) << 8)
                              | ((data[6] & 0xFFL) << 16) | ((data[7] & 0xFFL) << 24);
            info.firstDate = String.format("%02d/%02d/20%02d",
                    data[10] & 0xFF, data[9] & 0xFF, data[8] & 0xFF);
            info.firstTime = String.format("%02d:%02d:%02d",
                    data[11] & 0xFF, data[12] & 0xFF, data[13] & 0xFF);
            info.lastDate  = String.format("%02d/%02d/20%02d",
                    data[16] & 0xFF, data[15] & 0xFF, data[14] & 0xFF);
            info.lastTime  = String.format("%02d:%02d:%02d",
                    data[17] & 0xFF, data[18] & 0xFF, data[19] & 0xFF);
        } catch (Exception e) { /* leave defaults */ }
        return info;
    }

    public static class NodeRecInfo {
        public long newRecords = 0;
        public long totalRecords = 0;
        public String firstDate = "N/A";
        public String firstTime = "N/A";
        public String lastDate  = "N/A";
        public String lastTime  = "N/A";
    }

    /**
     * Comando para ESCRIBIR la configuración Sub-1G (SETTINGS_SUB1G_STRC, 16 bytes / Tabla 23).
     *
     * @param modem        00h = GFSK 915MHz, 01h = LoRa 915MHz
     * @param channel      CHANNEL - se usa como multiplicador BW_F
     * @param hopSchema     Esquema de salto (00h/01h/02h)
     * @param broadcastAddr Dirección Broadcast
     * @param unicastAddr   Dirección Unicast
     * @param gfskBr        Configuración del Modem en GFSK (00h-03h)
     * @param loraBr        Configuración del Modem en LoRa (00h-05h)
     */
    public static byte[] createSub1gSettingsWriteCommand(int modem, int channel, int hopSchema,
                                                         int broadcastAddr, int unicastAddr,
                                                         int gfskBr, int loraBr) {
        byte[] data = new byte[SUB1G_SETTINGS_SIZE];

        // Byte 0: ID (A4h - Identificador de ajuste Sub-1G)
        data[0] = ID_SUB1G_SETTINGS;

        // Byte 1: MODEM (00h GFSK / 01h LoRa)
        data[1] = (byte)(modem & 0xFF);

        // Bytes 2-7: Fecha y hora del último cambio (YY/MM/DD - HH:MM:SS)
        java.util.Calendar cal = java.util.Calendar.getInstance();
        data[2] = (byte)(cal.get(java.util.Calendar.YEAR) % 100);   // Año (00h-63h)
        data[3] = (byte)(cal.get(java.util.Calendar.MONTH) + 1);    // Mes (01h-0Ch)
        data[4] = (byte)cal.get(java.util.Calendar.DAY_OF_MONTH);   // Día (01h-1Fh)
        data[5] = (byte)cal.get(java.util.Calendar.HOUR_OF_DAY);    // Hora (00h-17h)
        data[6] = (byte)cal.get(java.util.Calendar.MINUTE);         // Minuto (00h-3Bh)
        data[7] = (byte)cal.get(java.util.Calendar.SECOND);         // Segundo (00h-3Bh)

        // Byte 8: CHANNEL (multiplicador BW_F)
        data[8] = (byte)(channel & 0xFF);

        // Byte 9: HOP_SCHEMA
        data[9] = (byte)(hopSchema & 0xFF);

        // Byte 10: BROADCAST_ADDR
        data[10] = (byte)(broadcastAddr & 0xFF);

        // Byte 11: UNICAST_ADDR
        data[11] = (byte)(unicastAddr & 0xFF);

        // Byte 12: GFKS_BR
        data[12] = (byte)(gfskBr & 0xFF);

        // Byte 13: LORA_BR
        data[13] = (byte)(loraBr & 0xFF);

        // Bytes 14-15: DUMMY (futuro uso) - quedan en 0

        byte[] cmd = encodeCommand(CMD_WRITE, CMD_SETTINGS_SUB1G, data);
        System.out.println("🔍 SETTINGS_SUB1G write generado: " + bytesToHexString(cmd));
        System.out.printf("   Sub1G - MODEM:%d CHANNEL:%d HOP:%d BCAST:%d UCAST:%d GFSK_BR:%d LORA_BR:%d%n",
                modem, channel, hopSchema, broadcastAddr, unicastAddr, gfskBr, loraBr);

        return cmd;
    }

    /**
     * Procesa la respuesta de configuración Sub-1G (SETTINGS_SUB1G_STRC, Tabla 23).
     */
    public static Sub1gSettings processSub1gSettingsResponse(byte[] sub1gData) {
        Sub1gSettings settings = new Sub1gSettings();

        if (sub1gData == null || sub1gData.length < 14) {
            System.err.println("❌ Datos Sub-1G insuficientes: " + (sub1gData != null ? sub1gData.length : 0) + " bytes");
            return settings;
        }

        try {
            settings.id            = sub1gData[0] & 0xFF;
            settings.modem         = sub1gData[1] & 0xFF;
            settings.year          = sub1gData[2] & 0xFF;
            settings.month         = sub1gData[3] & 0xFF;
            settings.day           = sub1gData[4] & 0xFF;
            settings.hour          = sub1gData[5] & 0xFF;
            settings.minute        = sub1gData[6] & 0xFF;
            settings.second        = sub1gData[7] & 0xFF;
            settings.channel       = sub1gData[8] & 0xFF;
            settings.hopSchema     = sub1gData[9] & 0xFF;
            settings.broadcastAddr = sub1gData[10] & 0xFF;
            settings.unicastAddr   = sub1gData[11] & 0xFF;
            settings.gfskBr        = sub1gData[12] & 0xFF;
            settings.loraBr        = sub1gData[13] & 0xFF;

            System.out.println("✅ Configuración Sub-1G procesada: " + settings);

        } catch (Exception e) {
            System.err.printf("❌ Error procesando configuración Sub-1G: %s%n", e.getMessage());
        }

        return settings;
    }

    // =================================================================
    // MÉTODOS DE PROCESAMIENTO DE RESPUESTAS
    // =================================================================

    /**
     * Lee Int32 (4 bytes) en formato Little Endian
     */
    private static long readInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFFL)) |
                ((data[offset + 1] & 0xFFL) << 8) |
                ((data[offset + 2] & 0xFFL) << 16) |
                ((data[offset + 3] & 0xFFL) << 24);
    }

    /**
     * Lee UInt16 (2 bytes) en formato Little Endian
     */
    private static int readUInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * Procesa datos de un canal específico con offsets corregidos
     */
    public static void processChannelDataCorrected(byte[] data, int offset, int channelNum,
                                                   float[] voltajes, float[] corrientes, float[] potencias,
                                                   float[] frecuencias, float[] angulos) {
        int index = channelNum - 1; // Convertir a índice 0-based

        try {
            System.out.printf("🔍 Procesando CH%d en offset %d (datos disponibles: %d bytes)%n",
                    channelNum, offset, data.length);

            // W_CHx (Int32) - Potencia en 0.1W
            if (data.length >= offset + 4) {
                long powerRaw = readInt32(data, offset);
                potencias[index] = powerRaw * 0.1f; // 0.1W -> W
                System.out.printf("   CH%d W: %d (raw) = %.1f W%n", channelNum, powerRaw, potencias[index]);
            }

            // V_CHx (UInt16) - Voltaje en 0.1V
            if (data.length >= offset + 6) {
                int voltageRaw = readUInt16(data, offset + 4);
                voltajes[index] = voltageRaw * 0.1f; // 0.1V -> V
                System.out.printf("   CH%d V: %d (raw) = %.1f V%n", channelNum, voltageRaw, voltajes[index]);
            }

            // A_CHx (UInt16) - Corriente en 0.1A
            if (data.length >= offset + 8) {
                int currentRaw = readUInt16(data, offset + 6);
                corrientes[index] = currentRaw * 0.1f; // 0.1A -> A
                System.out.printf("   CH%d A: %d (raw) = %.1f A%n", channelNum, currentRaw, corrientes[index]);
            }

            // HZ_CHx (UInt16) - Frecuencia en 0.1Hz
            if (data.length >= offset + 10) {
                int frequencyRaw = readUInt16(data, offset + 8);
                frecuencias[index] = frequencyRaw * 0.1f; // 0.1Hz -> Hz
                System.out.printf("   CH%d Hz: %d (raw) = %.1f Hz%n", channelNum, frequencyRaw, frecuencias[index]);
            }

            // ANGLE_CHx (UInt16) - Ángulo en 0.1°
            if (data.length >= offset + 12) {
                int angleRaw = readUInt16(data, offset + 10);
                angulos[index] = angleRaw * 0.1f; // 0.1° -> °
                System.out.printf("   CH%d Angle: %d (raw) = %.1f°%n", channelNum, angleRaw, angulos[index]);
            } else {
                angulos[index] = index * 120.0f; // Ángulos por defecto
                System.out.printf("   CH%d Angle: DATOS INSUFICIENTES (usando %.0f° por defecto)%n", channelNum, angulos[index]);
            }

        } catch (Exception e) {
            System.err.printf("❌ Error procesando CH%d: %s%n", channelNum, e.getMessage());
        }
    }

    /**
     * Procesa respuesta ENERGY_3PHA_PAGE_STRC con todos los canales
     * MEJORADO con corrección de SIZE+1
     */
    public static void processEnergy3PhaResponse(byte[] energyData,
                                                 float[] voltajes, float[] corrientes, float[] potencias,
                                                 float[] frecuencias, float[] angulos) {

        System.out.println("🔍 Procesando ENERGY_3PHA_PAGE_STRC:");
        System.out.println("   Datos recibidos: " + energyData.length + " bytes");

        // Resetear arrays
        for (int i = 0; i < 3; i++) {
            voltajes[i] = 0.0f;
            corrientes[i] = 0.0f;
            potencias[i] = 0.0f;
            frecuencias[i] = 50.0f; // Frecuencia por defecto
            angulos[i] = i * 120.0f; // Ángulos por defecto
        }

        if (energyData.length < 64) {
            System.err.println("❌ Datos insuficientes para ENERGY_3PHA_PAGE_STRC: " + energyData.length + " bytes");
            return;
        }

        // Procesar información básica
        if (energyData.length > 0) {
            int id = energyData[0] & 0xFF;
            String sourceType = (id == 0xF3) ? "Fuente Trifásica" :
                    (id == 0xC3) ? "Carga Trifásica" : String.format("Tipo: 0x%02X", id);
            System.out.println("   ID: " + sourceType);
        }

        // Procesar canales según offsets EXACTOS de la Tabla 19
        // CH1: Bytes 28-39
        if (energyData.length >= 40) {
            processChannelDataCorrected(energyData, 28, 1, voltajes, corrientes, potencias, frecuencias, angulos);
        }

        // CH2: Bytes 40-51
        if (energyData.length >= 52) {
            processChannelDataCorrected(energyData, 40, 2, voltajes, corrientes, potencias, frecuencias, angulos);
        }

        // CH3: Bytes 52-63
        if (energyData.length >= 64) {
            processChannelDataCorrected(energyData, 52, 3, voltajes, corrientes, potencias, frecuencias, angulos);
        }

        System.out.println("✅ Procesamiento ENERGY_3PHA completado");
    }

    // ✅ NUEVO: Procesa respuesta de configuración WiFi
    public static WiFiSettings processWiFiSettingsResponse(byte[] wifiData) {
        WiFiSettings settings = new WiFiSettings();

        if (wifiData == null || wifiData.length < WIFI_SETTINGS_SIZE) {
            System.err.println("❌ Datos WiFi insuficientes: " + (wifiData != null ? wifiData.length : 0) + " bytes");
            return settings;
        }

        try {
            int offset = 0;

            // SSID (64 bytes)
            byte[] ssidBytes = new byte[64];
            System.arraycopy(wifiData, offset, ssidBytes, 0, 64);
            settings.ssid = new String(ssidBytes).trim().replaceAll("\0", "");
            offset += 64;

            // PASSWORD (64 bytes) - No la mostramos por seguridad
            offset += 64;

            // MAC (20 bytes)
            byte[] macBytes = new byte[20];
            System.arraycopy(wifiData, offset, macBytes, 0, 20);
            settings.mac = new String(macBytes).trim().replaceAll("\0", "");
            offset += 20;

            // IP (16 bytes)
            byte[] ipBytes = new byte[16];
            System.arraycopy(wifiData, offset, ipBytes, 0, 16);
            settings.ip = new String(ipBytes).trim().replaceAll("\0", "");
            offset += 16;

            // RSSI (4 bytes)
            settings.rssi = (int)readInt32(wifiData, offset);
            offset += 4;

            // GATEWAY (16 bytes)
            byte[] gatewayBytes = new byte[16];
            System.arraycopy(wifiData, offset, gatewayBytes, 0, 16);
            settings.gateway = new String(gatewayBytes).trim().replaceAll("\0", "");
            offset += 16;

            // NETMASK (16 bytes)
            byte[] netmaskBytes = new byte[16];
            System.arraycopy(wifiData, offset, netmaskBytes, 0, 16);
            settings.netmask = new String(netmaskBytes).trim().replaceAll("\0", "");
            offset += 16;

            // DNS_PRI (16 bytes)
            byte[] dnsPriBytes = new byte[16];
            System.arraycopy(wifiData, offset, dnsPriBytes, 0, 16);
            settings.dnsPri = new String(dnsPriBytes).trim().replaceAll("\0", "");
            offset += 16;

            // DNS_SEC (16 bytes)
            byte[] dnsSecBytes = new byte[16];
            System.arraycopy(wifiData, offset, dnsSecBytes, 0, 16);
            settings.dnsSec = new String(dnsSecBytes).trim().replaceAll("\0", "");
            offset += 16;

            // DNS_TRD (16 bytes)
            byte[] dnsTrdBytes = new byte[16];
            System.arraycopy(wifiData, offset, dnsTrdBytes, 0, 16);
            settings.dnsTrd = new String(dnsTrdBytes).trim().replaceAll("\0", "");

            System.out.println("✅ Configuración WiFi procesada:");
            System.out.println("   SSID: " + settings.ssid);
            System.out.println("   IP: " + settings.ip);
            System.out.println("   Gateway: " + settings.gateway);
            System.out.println("   RSSI: " + settings.rssi);

        } catch (Exception e) {
            System.err.printf("❌ Error procesando configuración WiFi: %s%n", e.getMessage());
        }

        return settings;
    }

    // =================================================================
    // CLASES AUXILIARES
    // =================================================================

    // Clase para almacenar fecha/hora decodificada
    public static class DecodedDateTime {
        public int year;
        public int month;
        public int day;
        public int hour;
        public int minute;
        public int second;
        public int dayOfWeek;

        public DecodedDateTime(int year, int month, int day, int hour, int minute, int second, int dayOfWeek) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.dayOfWeek = dayOfWeek;
        }

        @Override
        public String toString() {
            String[] dayNames = {"", "Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"};
            return String.format("%04d-%02d-%02d %02d:%02d:%02d (%s)",
                    year, month, day, hour, minute, second,
                    dayNames[dayOfWeek]);
        }
    }

    // ✅ NUEVA: Clase para configuración WiFi
    public static class WiFiSettings {
        public String ssid = "";
        public String mac = "";
        public String ip = "";
        public String gateway = "";
        public String netmask = "";
        public String dnsPri = "";
        public String dnsSec = "";
        public String dnsTrd = "";
        public int rssi = 0;

        @Override
        public String toString() {
            return String.format("WiFiSettings{SSID='%s', IP='%s', Gateway='%s', RSSI=%d}",
                    ssid, ip, gateway, rssi);
        }
    }

    // ✅ NUEVA: Clase para configuración Sub-1G (Tabla 23)
    public static class Sub1gSettings {
        public int id = 0;
        public int modem = 0;          // 00h GFSK / 01h LoRa
        public int year = 0;
        public int month = 0;
        public int day = 0;
        public int hour = 0;
        public int minute = 0;
        public int second = 0;
        public int channel = 0;        // multiplicador BW_F
        public int hopSchema = 0;
        public int broadcastAddr = 0;
        public int unicastAddr = 0;
        public int gfskBr = 0;
        public int loraBr = 0;

        @Override
        public String toString() {
            return String.format("Sub1gSettings{MODEM=%d, CHANNEL=%d, HOP=%d, BCAST=%d, UCAST=%d, GFSK_BR=%d, LORA_BR=%d}",
                    modem, channel, hopSchema, broadcastAddr, unicastAddr, gfskBr, loraBr);
        }
    }

    /**
     * Decodifica fecha/hora del dispositivo
     */
    public static DecodedDateTime decodeDateTime(byte[] timeData) {
        if (timeData == null || timeData.length != 6) {
            throw new IllegalArgumentException("Los datos de tiempo deben tener exactamente 6 bytes");
        }

        // Decodificación EXACTA según C#
        int yearTwoDigits = timeData[0] & 0xFF;
        int year = 2000 + yearTwoDigits;

        // Byte 1: Día de semana (4 bits altos) + Mes (4 bits bajos)
        int dayOfWeek = (timeData[1] & 0xF0) >> 4;
        int month = timeData[1] & 0x0F;

        // Bytes 2-5: Día, hora, minuto, segundo
        int day = timeData[2] & 0xFF;
        int hour = timeData[3] & 0xFF;
        int minute = timeData[4] & 0xFF;
        int second = timeData[5] & 0xFF;

        return new DecodedDateTime(year, month, day, hour, minute, second, dayOfWeek);
    }

    // =================================================================
    // MÉTODOS DE UTILIDAD
    // =================================================================

    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                hex.append(" ");
            }
            hex.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return hex.toString();
    }

    public static boolean verifyChecksum(byte[] data) {
        if (data == null || data.length < 7) {
            return false;
        }

        // El checksum está en el penúltimo byte (antes del ETX)
        int checksumIndex = data.length - 2;
        byte receivedChecksum = data[checksumIndex];

        // Calcular checksum esperado
        byte calculatedChecksum = calculateChecksum(data, checksumIndex);

        return receivedChecksum == calculatedChecksum;
    }

    public static String debugCommand(byte[] command, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append(description).append(": ").append(bytesToHexString(command)).append("\n");

        if (command != null && command.length >= 6) {
            sb.append("STX: ").append(String.format("%02X", command[0] & 0xFF));
            sb.append(", TYPE: ").append(String.format("%02X", command[1] & 0xFF));
            sb.append(", CMD: ").append(String.format("%02X", command[2] & 0xFF));
            sb.append(", SIZE: ").append(String.format("%02X", command[3] & 0xFF));

            if (command[1] == CMD_READ) {
                // Comando de lectura: STX + TYPE + CMD + SIZE(0) + DATA(00) + CHECKSUM + ETX
                if (command.length >= 7) {
                    sb.append(", DATA: ").append(String.format("%02X", command[4] & 0xFF));
                    sb.append(", CHECKSUM: ").append(String.format("%02X", command[5] & 0xFF));
                    sb.append(", ETX: ").append(String.format("%02X", command[6] & 0xFF));
                }
            } else {
                // Comando de escritura
                int dataSize = command[3] & 0xFF;
                if (dataSize > 0 && command.length >= 6 + dataSize) {
                    sb.append(", DATA: ");
                    for (int i = 4; i < 4 + dataSize; i++) {
                        if (i > 4) sb.append(" ");
                        sb.append(String.format("%02X", command[i] & 0xFF));
                    }
                }
                sb.append(", CHECKSUM: ").append(String.format("%02X", command[command.length-2] & 0xFF));
                sb.append(", ETX: ").append(String.format("%02X", command[command.length-1] & 0xFF));
            }
        }

        // Verificar checksum
        if (verifyChecksum(command)) {
            sb.append(" ✅ Checksum correcto");
        } else {
            sb.append(" ❌ Checksum incorrecto");
        }

        return sb.toString();
    }

    // =================================================================
    // MÉTODOS HELPER PARA LA MÁQUINA DE ESTADOS
    // =================================================================

    /**
     * Método helper para encontrar CmdSet por valor
     */
    private static CmdSet findCmdSetByValue(int value) {
        for (CmdSet cmd : CmdSet.values()) {
            if (cmd.getValue() == value) {
                return cmd;
            }
        }
        return CmdSet.NOT_SUPPORTED_ERROR;
    }

    /**
     * Extrae los datos de un comando completo decodificado
     */
    public static byte[] extractCommandData(byte[] fullCommand) {
        if (fullCommand == null || fullCommand.length < 7) {
            return new byte[0];
        }

        // Estructura: STX + TYPE + CMD + NDATA + [DATA...] + CHECKSUM + ETX
        // Según protocolo: bytes de datos = NDATA + 1
        int ndata = fullCommand[3] & 0xFF;
        int dataSize = ndata + 1; // NDATA es el conteo real - 1

        // VALIDACIÓN: STX+TYPE+CMD+NDATA + dataSize bytes + CHECKSUM+ETX
        int expectedLength = 4 + dataSize + 2;
        if (fullCommand.length < expectedLength) {
            // Fallback: intentar con dataSize = ndata (por si el dispositivo no sigue la convención)
            if (ndata == 0 || fullCommand.length < 4 + ndata + 2) {
                return new byte[0];
            }
            byte[] fallback = new byte[ndata];
            System.arraycopy(fullCommand, 4, fallback, 0, ndata);
            return fallback;
        }

        byte[] data = new byte[dataSize];
        System.arraycopy(fullCommand, 4, data, 0, dataSize);
        return data;
    }

    /**
     * Obtiene el tipo de comando de un comando completo
     */
    public static CmdSet getCommandType(byte[] fullCommand) {
        if (fullCommand == null || fullCommand.length < 4) {
            return CmdSet.TYPE_INVALID_ERROR;
        }

        int type = (fullCommand[1] & 0xFF) << 8;
        int command = fullCommand[2] & 0xFF;
        int combinedValue = type | command;

        return findCmdSetByValue(combinedValue);
    }

    /**
     * Procesa datos de energía totales según estructura C# original
     */
    public static void processEnergyTotals(byte[] data) {
        try {
            if (data.length < 28) {
                System.err.println("❌ Datos insuficientes para energías totales");
                return;
            }

            System.out.println("⚡ Procesando energías totales según C# original:");

            // Según C# original - energías están en bytes 12-27
            // EnergyLifetime (Int32) - Bytes 12-15
            if (data.length >= 16) {
                long energyLifetime = readInt32(data, 12);
                System.out.printf("   Energy Lifetime: %d%n", energyLifetime);
            }

            // EnergyMonth (Int32) - Bytes 16-19
            if (data.length >= 20) {
                long energyMonth = readInt32(data, 16);
                System.out.printf("   Energy Month: %d%n", energyMonth);
            }

            // EnergyDay (Int32) - Bytes 20-23
            if (data.length >= 24) {
                long energyDay = readInt32(data, 20);
                System.out.printf("   Energy Day: %d%n", energyDay);
            }

            // Bytes 24-27 podrían ser otra energía según C# original
            if (data.length >= 28) {
                long extraEnergy = readInt32(data, 24);
                System.out.printf("   Extra Energy: %d%n", extraEnergy);
            }

        } catch (Exception e) {
            System.err.printf("❌ Error procesando energías totales: %s%n", e.getMessage());
        }
    }

    /**
     * Método de conveniencia para procesar comando NODE_CURRENT completo
     */
    public static void processNodeCurrentComplete(byte[] response,
                                                  float[] voltajes, float[] corrientes, float[] potencias,
                                                  float[] frecuencias, float[] angulos) {
        try {
            if (response.length < 7) {
                System.err.println("❌ Respuesta NODE_CURRENT inválida");
                return;
            }

            // Extraer datos de energía
            byte[] energyData = extractCommandData(response);

            if (energyData.length >= 64) {
                System.out.println("🔍 Procesando NODE_CURRENT completo según C# original:");

                // Procesar información general (ID, fecha, etc.)
                if (energyData.length > 0) {
                    int id = energyData[0] & 0xFF;
                    System.out.printf("   ID: 0x%02X%n", id);

                    if (energyData.length >= 8) {
                        System.out.printf("   Fecha: %02d/%02d/%04d %02d:%02d:%02d%n",
                                energyData[4] & 0xFF, energyData[3] & 0xFF, 2000 + (energyData[2] & 0xFF),
                                energyData[5] & 0xFF, energyData[6] & 0xFF, energyData[7] & 0xFF);
                    }
                }

                // Procesar energías totales
                processEnergyTotals(energyData);

                // Procesar canales
                processEnergy3PhaResponse(energyData, voltajes, corrientes, potencias, frecuencias, angulos);

            } else {
                System.err.printf("❌ Datos insuficientes: %d bytes (esperados: 64)%n", energyData.length);
            }

        } catch (Exception e) {
            System.err.printf("❌ Error procesando NODE_CURRENT: %s%n", e.getMessage());
        }
    }



    /**
     * Valida estructura de comando según C# original
     */
    public static boolean validateCommandStructure(byte[] command) {
        if (command == null || command.length < 7) {
            return false;
        }

        // Validar bytes de inicio y fin
        if (command[0] != STX || command[command.length - 1] != ETX) {
            return false;
        }

        // Validar tipo de comando
        byte type = command[1];
        if (type != CMD_READ && type != CMD_WRITE &&
                type != CmdType.ERASE.getValue() &&
                type != CmdType.CONFIRMATION.getValue() &&
                type != CmdType.INFORMATION.getValue() &&
                type != CmdType.ERROR.getValue()) {
            return false;
        }

        // Validar comando específico
        byte cmd = command[2];
        if (cmd != CMD_DEVICE_ID && cmd != CMD_DEVICE_TIME &&
                cmd != CMD_NODE_SETTINGS && cmd != CMD_NODE_CURRENT &&
                cmd != CMD_NODE_REC_INFO && cmd != CMD_NODE_REC_ERASE &&
                cmd != CMD_SETTINGS_WIFI && cmd != CMD_SETTINGS_SUB1G) {
            return false;
        }

        // Validar checksum
        return verifyChecksum(command);
    }

    /**
     * Método de utilidad para debug completo de comando
     */
    public static void debugCommandComplete(byte[] command, String description) {
        System.out.println("=== DEBUG COMANDO COMPLETO ===");
        System.out.println("Descripción: " + description);
        System.out.println("Hex: " + bytesToHexString(command));
        System.out.println("Válido: " + (validateCommandStructure(command) ? "✅ SÍ" : "❌ NO"));
        System.out.println("Tipo: " + getCommandType(command));
        System.out.println("Debug: " + debugCommand(command, description));
        System.out.println("================================");
    }

    /**
     * ✅ MÉTODO PARA TESTING - Verifica comandos generados
     */
    public static void testCommands() {
        System.out.println("=== TESTING COMANDOS CORREGIDOS ===");

        // Test READ commands
        byte[] deviceId = createDeviceIdReadCommand();
        byte[] deviceTime = createDeviceTimeReadCommand();
        byte[] nodeSettings = createNodeSettingsReadCommand();
        byte[] nodeCurrent = createNodeCurrentReadCommand();
        byte[] wifiSettings = createWiFiSettingsReadCommand(); // ✅ NUEVO

        System.out.println("Comandos READ generados:");
        System.out.println("DEVICE_ID: " + debugCommand(deviceId, "DEVICE_ID_READ"));
        System.out.println("DEVICE_TIME: " + debugCommand(deviceTime, "DEVICE_TIME_READ"));
        System.out.println("NODE_SETTINGS: " + debugCommand(nodeSettings, "NODE_SETTINGS_READ"));
        System.out.println("NODE_CURRENT: " + debugCommand(nodeCurrent, "NODE_CURRENT_READ"));
        System.out.println("WIFI_SETTINGS: " + debugCommand(wifiSettings, "WIFI_SETTINGS_READ")); // ✅ NUEVO

        // Test WRITE commands
        byte[] writeSettings = createNodeSettingsWriteCommand(true, 1, 1, 3);
        byte[] writeWifi = createWiFiSettingsWriteCommand("TestSSID", "TestPassword"); // ✅ NUEVO

        System.out.println("NODE_SETTINGS_WRITE: " + debugCommand(writeSettings, "NODE_SETTINGS_WRITE"));
        System.out.println("WIFI_SETTINGS_WRITE: " + debugCommand(writeWifi, "WIFI_SETTINGS_WRITE")); // ✅ NUEVO

        System.out.println("=== FIN TESTING ===");
    }
}
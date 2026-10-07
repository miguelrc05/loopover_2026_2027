import java.util.ArrayList;
import java.util.List;

/**
 * Estado del puzzle Loopover 4x4 representado como bitboard en un long sin
 * signo, junto con las acciones que lo transforman en otro estado.
 *
 * Cada casilla ocupa 4 bits: la casilla i (fila i/4, columna i%4) ocupa los
 * bits [i*4, i*4+3], quedando la casilla (0,0) en los bits mas bajos. El
 * estado resuelto vale 0xFEDCBA98_76543210L.
 *
 * Una accion es un movimiento encadenado codificado en 5 bits: los bits 0-1
 * son la fila, los bits 2-3 la columna y el bit 4 vale 1 si el signo es '+'.
 * Con '+' la fila se desplaza a la derecha y la columna hacia abajo; con '-',
 * hacia la izquierda y hacia arriba.
 */
public final class Estado {

    public static final int LADO = 4;
    public static final int NUM_CASILLAS = LADO * LADO;
    public static final int BITS_POR_CASILLA = 4;
    public static final long MASCARA_FICHA = 15L;

    public static final int NUM_ACCIONES = 32;

    // El estado resuelto: ficha i en casilla i → nibble i = i
    private static final long BITBOARD_RESUELTO = 0xFEDCBA98_76543210L;

    // Bits de la codificacion de accion
    private static final int MASCARA_FILA_ACCION    = 0b00011;
    private static final int MASCARA_COLUMNA_ACCION = 0b01100;
    private static final int BIT_SIGNO_ACCION       = 0b10000;
    private static final int MASCARA_CODIGO_ACCION  = 0b11111;

    // Mascaras para operar filas y columnas del tablero
    private static final int  BITS_POR_FILA_TABLERO  = LADO * BITS_POR_CASILLA; // 16
    private static final long MASCARA_FILA_TABLERO   = 0xFFFFL;
    // Selecciona el nibble de columna 0 en cada una de las 4 filas
    private static final long MASCARA_COLUMNA_TABLERO = 0x000F000F000F000FL;

    /**
     * Tabla de 32 codigos de accion. Las primeras 16 entradas tienen signo '+',
     * las 16 siguientes signo '-'. Ya esta inicializada; no modificar.
     *
     * Formato de cada codigo (5 bits):
     *   bit  4  = signo (1 = '+')
     *   bits 3-2 = columna (0..3)
     *   bits 1-0 = fila    (0..3)
     */
    public static final int[] ACCIONES = new int[NUM_ACCIONES];

    static {
        for (int f = 0; f < LADO; f++) {
            for (int c = 0; c < LADO; c++) {
                int codigoBase = f | (c << 2);
                int posicion   = f * LADO + c;
                ACCIONES[posicion]               = codigoBase | BIT_SIGNO_ACCION; // signo '+'
                ACCIONES[NUM_CASILLAS + posicion] = codigoBase;                   // signo '-'
            }
        }
    }

    private final long bitboard;

    // -------------------------------------------------------------------------
    // Constructores
    // -------------------------------------------------------------------------

    /** Construye el estado directamente desde el bitboard (ya validado). */
    public Estado(long bitboard) {
        this.bitboard = bitboard;
    }

    /**
     * Construye el estado desde una cadena de 32 digitos decimales donde cada
     * par representa la ficha de una casilla (casilla 0 = par 0..1, etc.).
     * Ejemplo: "00010203040506070809101112131415" es el estado resuelto.
     */
    public Estado(String representacion) {
        if (representacion == null) {
            throw new IllegalArgumentException("La representacion no puede ser nula");
        }
        if (representacion.length() != NUM_CASILLAS * 2) {
            throw new IllegalArgumentException(
                    "La representacion debe tener 32 digitos: " + representacion.length());
        }
        int[] fichas = new int[NUM_CASILLAS];
        for (int i = 0; i < NUM_CASILLAS; i++) {
            char decenas = representacion.charAt(i * 2);
            char unidades = representacion.charAt(i * 2 + 1);
            if (!esDigito(decenas) || !esDigito(unidades)) {
                throw new IllegalArgumentException("Caracter no numerico en la casilla " + i);
            }
            fichas[i] = (decenas - '0') * 10 + (unidades - '0');
        }
        this.bitboard = construirBitboard(fichas);
    }

    /**
     * Construye el estado desde un array de 16 fichas donde fichas[i] es la
     * ficha en la casilla i.
     */
    public Estado(int[] fichas) {
        if (fichas == null || fichas.length != NUM_CASILLAS) {
            throw new IllegalArgumentException("El array de fichas debe tener 16 elementos");
        }
        this.bitboard = construirBitboard(fichas);
    }

    // -------------------------------------------------------------------------
    // Consultas
    // -------------------------------------------------------------------------

    /** Devuelve el bitboard interno (necesario para la tabla de visitados). */
    public long bitboard() {
        return bitboard;
    }

    /**
     * Devuelve la ficha (0..15) que se encuentra en la casilla i.
     * Casilla i = fila i/4, columna i%4.
     * Operacion: (bitboard >>> (i * BITS_POR_CASILLA)) & MASCARA_FICHA
     */
    public int ficha(int casilla) {
        comprobarCasilla(casilla);
        return (int) ((bitboard >>> (casilla * BITS_POR_CASILLA)) & MASCARA_FICHA);
    }

    /** Devuelve la ficha en la posicion (fila, columna). */
    public int ficha(int fila, int columna) {
        if (fila < 0 || fila >= LADO || columna < 0 || columna >= LADO) {
            throw new IllegalArgumentException(
                    "Posicion fuera de rango: fila=" + fila + ", columna=" + columna);
        }
        return ficha(fila * LADO + columna);
    }

    /** Devuelve true si el bitboard es igual al estado resuelto. */
    public boolean esResuelto() {
        return bitboard == BITBOARD_RESUELTO;
    }

    // -------------------------------------------------------------------------
    // Sucesores
    // -------------------------------------------------------------------------

    /**
     * Devuelve la lista de 32 sucesores: uno por cada accion en ACCIONES[].
     * Cada sucesor tiene: la accion aplicada, el estado resultante y costo 1.0f.
     */
    public List<Sucesor> sucesores() {
        List<Sucesor> lista = new ArrayList<>(NUM_ACCIONES);
        for (int accion : ACCIONES) {
            lista.add(new Sucesor(accion, aplicar(accion), 1.0f));
        }
        return lista;
    }

    // -------------------------------------------------------------------------
    // Aplicar acciones
    // -------------------------------------------------------------------------

    /**
     * Aplica la accion y devuelve el nuevo estado sin modificar este.
     * 1. Extraer fila y columna del codigo (bits 0-1 y 2-3).
     * 2. Extraer signo del bit 4.
     * 3. desplazarFila(bitboard, fila, positivo)
     * 4. desplazarColumna(resultado_anterior, columna, positivo)
     */
    public Estado aplicar(int accion) {
        validarAccion(accion);
        int fila = accion & MASCARA_FILA_ACCION;
        int columna = (accion & MASCARA_COLUMNA_ACCION) >>> 2;
        boolean positivo = (accion & BIT_SIGNO_ACCION) != 0;
        long movido = desplazarFila(bitboard, fila, positivo);
        movido = desplazarColumna(movido, columna, positivo);
        return new Estado(movido);
    }

    // -------------------------------------------------------------------------
    // Utilidades de acciones
    // -------------------------------------------------------------------------

    /**
     * Convierte el codigo de accion en texto legible, por ejemplo "01+" o "33-".
     * Formato: digito_fila + digito_columna + signo.
     */
    public static String accionComoTexto(int accion) {
        validarAccion(accion);
        int fila = accion & MASCARA_FILA_ACCION;
        int columna = (accion & MASCARA_COLUMNA_ACCION) >>> 2;
        char signo = (accion & BIT_SIGNO_ACCION) != 0 ? '+' : '-';
        return "" + fila + columna + signo;
    }
    /**
     * Parsea una accion en formato texto ("01+", "33-", etc.) y devuelve su codigo.
     * Validar: longitud = 3, primer caracter = digito fila 0-3,
     *          segundo = digito columna 0-3, tercero = '+' o '-'.
     */
    public static int accionDesde(String representacion) {
        if (representacion == null) {
            throw new IllegalArgumentException("La accion no puede ser nula");
        }
        if (representacion.length() != 3) {
            throw new IllegalArgumentException(
                    "La accion debe tener el formato rc+ o rc-: " + representacion);
        }

        char caracterFila = representacion.charAt(0);
        char caracterColumna = representacion.charAt(1);
        char signo = representacion.charAt(2);
        if (!esDigito(caracterFila) || caracterFila > '3') {
            throw new IllegalArgumentException("La fila debe ser un digito entre 0 y 3");
        }
        if (!esDigito(caracterColumna) || caracterColumna > '3') {
            throw new IllegalArgumentException("La columna debe ser un digito entre 0 y 3");
        }
        if (signo != '+' && signo != '-') {
            throw new IllegalArgumentException("El signo de la accion debe ser '+' o '-'");
        }

        int fila = caracterFila - '0';
        int columna = caracterColumna - '0';
        int codigo = fila | (columna << 2);
        return signo == '+' ? codigo | BIT_SIGNO_ACCION : codigo;
    }

    // -------------------------------------------------------------------------
    // equals, hashCode, toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof Estado otro && bitboard == otro.bitboard);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bitboard);
    }

    /**
     * Representacion de 32 digitos: cada casilla como dos digitos decimales,
     * empezando por la casilla 0.
     * Ejemplo del estado resuelto: "00010203040506070809101112131415"
     */
    @Override
    public String toString() {
        StringBuilder representacion = new StringBuilder(NUM_CASILLAS * 2);
        for (int i = 0; i < NUM_CASILLAS; i++) {
            int valor = ficha(i);
            if (valor < 10) {
                representacion.append('0');
            }
            representacion.append(valor);
        }
        return representacion.toString();
    }

    // =========================================================================
    // Metodos privados de ayuda
    // =========================================================================

    /**
     * Construye el bitboard a partir del array de fichas.
     * Para cada casilla i: resultado |= ((long) fichas[i]) << (i * BITS_POR_CASILLA)
     * Validar: sin duplicados, cada ficha en [0, NUM_CASILLAS).
     */
    private static long construirBitboard(int[] fichas) {
        if (fichas == null || fichas.length != NUM_CASILLAS) {
            throw new IllegalArgumentException("El array de fichas debe tener 16 elementos");
        }

        long resultado = 0L;
        int fichasVistas = 0;
        for (int casilla = 0; casilla < NUM_CASILLAS; casilla++) {
            int ficha = fichas[casilla];
            if (ficha < 0 || ficha >= NUM_CASILLAS) {
                throw new IllegalArgumentException(
                        "La ficha de la casilla " + casilla + " debe estar entre 0 y 15: " + ficha);
            }

            int mascaraFicha = 1 << ficha;
            if ((fichasVistas & mascaraFicha) != 0) {
                throw new IllegalArgumentException("Ficha duplicada: " + ficha);
            }
            fichasVistas |= mascaraFicha;
            resultado |= ((long) ficha) << (casilla * BITS_POR_CASILLA);
        }
        return resultado;
    }

    /**
     * Desplaza la fila indicada del bitboard una posicion a la derecha (positivo=true)
     * o a la izquierda (positivo=false), con retorno circular.
     *
     * La fila ocupa 16 bits a partir del bit (fila * 16).
     * Mascara de la fila:    MASCARA_FILA_TABLERO << (fila * 16)
     * Mascara del nibble de retorno: MASCARA_FICHA << (fila * 16)
     *
     * Desplazamiento derecha (positivo):
     *   rotada = ((extraida << 4) & mascaraFila) | ((extraida >>> 12) & mascaraRetorno)
     *
     * Desplazamiento izquierda (negativo):
     *   mascaraTope = mascaraRetorno << 12
     *   rotada = ((extraida >>> 4) & mascaraFila) | ((extraida << 12) & mascaraTope)
     */
    private static long desplazarFila(long bitboard, int fila, boolean positivo) {
        if (fila < 0 || fila >= LADO) {
            throw new IllegalArgumentException("Fila fuera de rango: " + fila);
        }

        int desplazamiento = fila * BITS_POR_FILA_TABLERO;
        long mascaraFila = MASCARA_FILA_TABLERO << desplazamiento;
        long extraida = (bitboard & mascaraFila) >>> desplazamiento;
        long rotada;

        if (positivo) {
            rotada = ((extraida << BITS_POR_CASILLA) & MASCARA_FILA_TABLERO)
                    | ((extraida >>> (BITS_POR_FILA_TABLERO - BITS_POR_CASILLA))
                    & MASCARA_FICHA);
        } else {
            long mascaraTope = MASCARA_FICHA << (BITS_POR_FILA_TABLERO - BITS_POR_CASILLA);
            rotada = ((extraida >>> BITS_POR_CASILLA) & MASCARA_FILA_TABLERO)
                    | ((extraida << (BITS_POR_FILA_TABLERO - BITS_POR_CASILLA)) & mascaraTope);
        }

        return (bitboard & ~mascaraFila) | (rotada << desplazamiento);
    }

    /**
     * Desplaza la columna indicada del bitboard una posicion hacia abajo (positivo=true)
     * o hacia arriba (positivo=false), con retorno circular.
     *
     * La columna ocupa 4 nibbles separados 16 bits entre si.
     * Mascara de la columna: MASCARA_COLUMNA_TABLERO << (columna * BITS_POR_CASILLA)
     * Mascara del nibble de retorno: MASCARA_FICHA << (columna * BITS_POR_CASILLA)
     *
     * Desplazamiento abajo (positivo):
     *   rotada = ((extraida << 16) & mascaraColumna) | ((extraida >>> 48) & mascaraRetorno)
     *
     * Desplazamiento arriba (negativo):
     *   mascaraSuperior = 0xF000000000000000L >>> (12 - columna * 4)
     *   rotada = ((extraida >>> 16) & mascaraColumna) | ((extraida << 48) & mascaraSuperior)
     */
    private static long desplazarColumna(long bitboard, int columna, boolean positivo) {
        if (columna < 0 || columna >= LADO) {
            throw new IllegalArgumentException("Columna fuera de rango: " + columna);
        }

        int desplazamiento = columna * BITS_POR_CASILLA;
        long mascaraColumna = MASCARA_COLUMNA_TABLERO << desplazamiento;
        long extraida = bitboard & mascaraColumna;
        long mascaraRetorno = MASCARA_FICHA << desplazamiento;
        long rotada;

        if (positivo) {
            rotada = ((extraida << BITS_POR_FILA_TABLERO) & mascaraColumna)
                    | ((extraida >>> (BITS_POR_FILA_TABLERO * (LADO - 1))) & mascaraRetorno);
        } else {
            long mascaraSuperior = 0xF000000000000000L
                    >>> (BITS_POR_FILA_TABLERO - BITS_POR_CASILLA - desplazamiento);
            rotada = ((extraida >>> BITS_POR_FILA_TABLERO) & mascaraColumna)
                    | ((extraida << (BITS_POR_FILA_TABLERO * (LADO - 1))) & mascaraSuperior);
        }

        return (bitboard & ~mascaraColumna) | rotada;
    }

    private static void comprobarCasilla(int casilla) {
        if (casilla < 0 || casilla >= NUM_CASILLAS) {
            throw new IllegalArgumentException("Casilla fuera de rango: " + casilla);
        }
    }

    private static void validarAccion(int accion) {
        if (accion < 0 || accion > MASCARA_CODIGO_ACCION) {
            throw new IllegalArgumentException(
                    "La accion debe estar entre 0 y " + MASCARA_CODIGO_ACCION + ": " + accion);
        }
    }

    private static boolean esDigito(char caracter) {
        return caracter >= '0' && caracter <= '9';
    }
}

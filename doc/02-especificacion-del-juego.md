# Especificación profunda del juego Loopover

- **Proyecto:** `loopover_2026_2027`
- **Versión del documento:** 1.0
- **Fecha:** 7 de octubre de 2026
- **Estado:** reglas de la variante del proyecto fijadas; caracterización completa de alcanzabilidad pendiente.

## 1. Propósito y decisión de alcance

Este documento define el juego que debe implementar el proyecto y reúne el contexto matemático necesario para diseñar el estado, las acciones, la búsqueda y sus heurísticas.

La palabra **Loopover** suele referirse al puzzle toroidal estándar: cada movimiento desplaza una única fila o una única columna. El código de este proyecto define otra mecánica: **una acción selecciona una fila y una columna, desplaza primero la fila y después la columna, y usa el mismo signo para ambos desplazamientos**. Esa variante acoplada es la regla normativa de este proyecto. El juego estándar se describe como referencia y no debe confundirse con ella.

## 2. Resumen del juego

El tablero contiene fichas distintas distribuidas en una cuadrícula rectangular. No hay casilla vacía. Las filas y las columnas se comportan como ciclos: una ficha que rebasa un borde reaparece por el borde opuesto. El objetivo es alcanzar una disposición objetivo ordenada.

El problema computacional consiste en encontrar una secuencia de acciones que lleve el tablero inicial al objetivo. Si se busca la secuencia de menor coste, el coste de una solución depende de la definición de movimiento; por eso la métrica forma parte de la especificación y no es un detalle de presentación.

## 3. Terminología y convenciones

- **Tablero:** matriz de 4 filas y 4 columnas.
- **Casilla:** posición `(fila, columna)`, con índices desde cero.
- **Ficha:** identificador único entre `0` y `15`, mostrado con dos dígitos (`00`–`15`).
- **Estado:** asignación de las 16 fichas a las 16 casillas.
- **Acción:** transformación completa permitida por las reglas acopladas de la sección 5.
- **Objetivo:** estado con la ficha `i` en la casilla de índice lineal `i`.
- **Signo `+`:** fila a la derecha y columna hacia abajo.
- **Signo `-`:** fila a la izquierda y columna hacia arriba.

El índice lineal de una casilla es `i = 4 × fila + columna`. Las filas se numeran de arriba abajo y las columnas de izquierda a derecha.

## 4. Loopover estándar: referencia

En el modelo estándar, un movimiento elige **una sola** línea y una dirección:

1. una fila se rota una posición a la izquierda o a la derecha; o
2. una columna se rota una posición hacia arriba o hacia abajo.

La rotación es circular y afecta a todas las fichas de la línea elegida. Las dos direcciones opuestas de una línea son inversas entre sí. Es habitual llamar a esta unidad de coste un **single-tile move** o **single-tile metric (STM)**, aunque cada movimiento cambie de posición varias fichas.

También se usa una métrica de rotación compuesta (*drag*): una línea puede desplazarse una cantidad arbitraria de posiciones en una operación. No equivale a contar cada rotación unitaria por separado.

El problema estándar se conoce matemáticamente como *Torus Puzzle* y también aparece con los nombres *Sliders*, *TwoBik* y *RotSquare*. En el artículo de Caporrella y Leucci se define el movimiento estándar como una rotación circular unitaria de una fila o una columna [R1].

## 5. Reglas normativas de la variante del proyecto

### 5.1 Parámetros de una acción

Una acción se identifica por la terna `(r, c, s)`:

- `r ∈ {0, 1, 2, 3}`: fila elegida.
- `c ∈ {0, 1, 2, 3}`: columna elegida.
- `s ∈ {+, -}`: dirección compartida por ambos desplazamientos.

Por tanto, hay `4 × 4 × 2 = 32` códigos de acción. Cada acción cuesta `1` en el grafo de búsqueda del proyecto.

### 5.2 Orden de aplicación

La acción se aplica en **dos fases secuenciales** sobre el mismo tablero:

1. Desplazar circularmente la fila `r` una posición.
2. Sobre el tablero resultante, desplazar circularmente la columna `c` una posición.

Para `+`, la primera fase es a la derecha y la segunda hacia abajo. Para `-`, la primera fase es a la izquierda y la segunda hacia arriba. El orden es parte de la regla: la fila y la columna se cruzan en una casilla y, por tanto, no se deben tratar como transformaciones independientes o intercambiables.

Formalmente, si `B` es el tablero de entrada y `B1` el resultado de la fase de fila:

- `+`: `B1[r][(j + 1) mod 4] = B[r][j]` para cada columna `j`.
- `-`: `B1[r][(j - 1) mod 4] = B[r][j]` para cada columna `j`.

Después, si `B2` es el resultado de la fase de columna:

- `+`: `B2[(i + 1) mod 4][c] = B1[i][c]` para cada fila `i`.
- `-`: `B2[(i - 1) mod 4][c] = B1[i][c]` para cada fila `i`.

Las casillas no incluidas en la línea que rota conservan su ficha durante esa fase. La rotación de la columna opera sobre `B1`, no sobre el tablero original `B`.

### 5.3 Notación y codificación ya definida en el código

La notación de usuario es `rc+` o `rc-`, con un dígito para la fila y otro para la columna. Ejemplos: `21+`, `03-`.

El código reserva cinco bits para el entero de acción:

| Bits | Significado |
|---|---|
| 0–1 | Fila, `0`–`3` |
| 2–3 | Columna, `0`–`3` |
| 4 | Signo: `1` para `+`, `0` para `-` |

La tabla `Estado.ACCIONES` enumera primero las 16 acciones `+` y después las 16 acciones `-`. La función `Estado.aplicar` implementa la fila antes que la columna, y `Estado.sucesores` asigna coste `1.0` a cada resultado.

### 5.4 Ejemplo verificable de orden

Partiendo del objetivo:

```text
00 01 02 03
04 05 06 07
08 09 10 11
12 13 14 15
```

La acción `11+` (fila 1 a la derecha y luego columna 1 hacia abajo) produce:

```text
00 13 02 03
07 01 05 06
08 04 10 11
12 09 14 15
```

Si a ese resultado se le aplica `11-`, el tablero queda:

```text
00 05 02 03
01 04 06 07
08 09 10 11
12 13 14 15
```

No vuelve al objetivo. Por tanto, **no se debe asumir que intercambiar `+` por `-` en la misma pareja fila-columna invierte la acción**. Para invertir una composición se debe deshacer primero la fase de columna y luego la fase de fila; esa composición inversa no es, en general, una única acción definida por el proyecto.

## 6. Estados válidos y representación

### 6.1 Invariantes del dominio

Un estado válido debe contener cada ficha de `0` a `15` exactamente una vez. Debe rechazarse una entrada si:

- no contiene exactamente 16 valores;
- algún valor no está en `[0, 15]`;
- hay valores repetidos o fichas ausentes;
- la representación textual no tiene exactamente 32 dígitos ASCII;
- un par de dígitos representa un número fuera de `[00, 15]`.

La transición de una acción válida siempre debe conservar las 16 fichas y su multiplicidad. Las acciones no crean ni eliminan fichas.

### 6.2 Representación textual

La entrada textual concatena 16 valores decimales de dos cifras, en orden por filas. El estado objetivo es:

```text
00010203040506070809101112131415
```

Este formato es propio del programa; no es una notación universal de Loopover.

### 6.3 Bitboard de 64 bits

El código almacena el tablero en un `long` con un nibble (4 bits) por casilla. La casilla lineal `i` ocupa los bits `[4i, 4i + 3]`; la casilla `(0,0)` está en los cuatro bits menos significativos. El objetivo está codificado como:

```text
0xFEDCBA9876543210L
```

La escritura hexadecimal se lee desde el nibble más significativo al menos significativo, mientras que las casillas se numeran desde el nibble menos significativo. Por ello el literal tiene aspecto descendente, pero representa la ficha `i` en la casilla `i`.

El constructor desde `long` está documentado como receptor de un bitboard previamente validado. Si se mantiene público, esa precondición debe documentarse claramente o validarse en el constructor para evitar estados que no sean permutaciones.

## 7. Alcanzabilidad y paridad

### 7.1 Resultado para el juego estándar

Para el Torus Puzzle estándar de dimensiones `m × n`, el artículo de Caporrella y Leucci presenta el criterio: una configuración es ordenable si y solo si al menos uno de estos tres elementos es par: `m`, `n` o la permutación que describe el tablero [R1]. En el modelo estándar 4×4, ambas dimensiones son pares, por lo que cualquier permutación de las 16 fichas es alcanzable. El espacio completo contiene `16! = 20 922 789 888 000` estados.

### 7.2 Invariante necesario para las acciones acopladas

Ese criterio **no caracteriza la variante del proyecto**. En un tablero de cuatro posiciones, una rotación unitaria de fila es un ciclo de longitud cuatro, cuya paridad es impar. Una rotación unitaria de columna también es impar. Una acción del proyecto compone una de cada tipo; el signo de la permutación producto es el producto de sus signos, por lo que cada acción es **par**.

Consecuencia demostrable: desde el objetivo, toda secuencia del proyecto conserva la paridad y ninguna configuración impar es alcanzable. El conjunto de estados alcanzables tiene como máximo `16! / 2 = 10 461 394 944 000` estados.

Esto es un límite superior, no una caracterización completa. **Está pendiente demostrar o verificar si las 32 acciones generan todas las permutaciones pares o un subgrupo propio de ellas.** No se debe aceptar una configuración par como resoluble basándose únicamente en este argumento.

Como cada acción es una permutación de un conjunto finito, sus inversas existen dentro del grupo generado (como potencias de esa acción). Sin embargo, la arista inversa no tiene por qué ser una de las 32 acciones individuales. La búsqueda hacia delante puede usar las 32 acciones; una búsqueda retrospectiva o una PBD debe construir correctamente los predecesores, sin emparejar mecánicamente cada acción `+` con la acción `-` homónima.

### 7.3 Trabajo necesario para cerrar la alcanzabilidad

Antes de diseñar un rechazo completo de estados no resolubles, se debe obtener una caracterización del subgrupo generado por las 32 permutaciones de posiciones. Opciones de verificación:

1. una demostración algebraica del grupo generado;
2. cálculo de grupo mediante generadores y estabilizadores (por ejemplo, una herramienta de álgebra computacional) y validación independiente;
3. enumeración exhaustiva en un tablero reducido como experimento exploratorio, sin extrapolar automáticamente el resultado a 4×4.

Hasta entonces, la paridad impar es un rechazo seguro y la paridad par no es una prueba suficiente de resolubilidad.

## 8. Métrica y consecuencias para la búsqueda

En este proyecto, cada par fila-columna aplicado secuencialmente cuesta `1`. En el modelo estándar se contarían como dos rotaciones unitarias separadas. Por consiguiente, una solución mínima del proyecto es óptima **respecto a esta métrica acoplada**, y sus longitudes no son directamente comparables con longitudes STM del juego estándar.

Con todas las acciones de coste `1`:

- BFS encuentra caminos de coste mínimo en este grafo dirigido.
- Coste uniforme produce el mismo orden de costes que BFS.
- A* es óptimo si su heurística es admisible y la gestión de estados reabiertos/visitados respeta los costes acumulados.
- Búsqueda voraz y profundidad no garantizan una solución óptima.
- Límites de profundidad, nodos y visitados deben distinguirse de una prueba de que el estado sea irresoluble.

Un camino devuelto debe poder reproducirse desde el estado inicial, acción por acción, y terminar exactamente en el estado objetivo. El formato textual del camino debe reflejar el orden real de aplicación.

## 9. Heurísticas y bases de patrones

### 9.1 Distancia toroidal por ficha

Para una ficha en `(r,c)` con objetivo `(rg,cg)`, la distancia toroidal geométrica es:

```text
dr = |r - rg|
dc = |c - cg|
dT = min(dr, 4 - dr) + min(dc, 4 - dc)
```

La suma de `dT` sobre todas las fichas mide desplazamiento geométrico, pero **no es directamente el número de acciones**: una acción modifica varias fichas y combina dos líneas.

Una cota admisible simple para el coste acoplado es `ceil(M / 8)`, donde `M` es esa suma. Cada acción consta de dos rotaciones unitarias, cada una mueve cuatro fichas una casilla y puede cambiar la suma en no más de cuatro; en conjunto, la suma puede disminuir como máximo ocho por acción. La cota debe seguir validándose contra una implementación de referencia y contra distancias exactas en estados poco profundos. Una Manhattan sin escalar no debe etiquetarse como admisible para este coste unitario.

### 9.2 Bases de patrones (PBD/PDB)

Una PBD debe construirse con:

- los mismos estados, acciones acopladas y orden fila→columna que el solucionador;
- coste abstracto `1` por acción;
- una proyección explícita que siga la posición de las fichas del patrón y trate las demás como indistinguibles;
- distancias mínimas del grafo abstracto al conjunto de patrones objetivo.

Si el grafo de acciones abstracto es dirigido, las distancias se calculan recorriendo aristas inversas reales. La heurística `max(h1, h2)` de dos patrones admisibles también es admisible. Sumar valores de patrones disjuntos **no es automáticamente admisible**: una acción acoplada puede contribuir simultáneamente al progreso de ambos patrones y ser contada dos veces. La opción `PBD_8_SUMA` del CLI ya se describe como no admisible.

El código declara `PBD.TAMANNO = 32 432 400`, que coincide con `15 × 14 × 13 × 12 × 11 × 10 × 9 = 15P7`. Esto es compatible con representar ocho fichas distinguidas y fijar una como referencia para normalizar su posición bajo traslaciones globales. Como la PBD está sin implementar y no hay ficheros `pdb8*.dat` en el árbol actual, esa interpretación debe confirmarse antes de fijar el formato de índices o recursos.

## 10. Requisitos de verificación derivados

La implementación del dominio debería tener pruebas que cubran, como mínimo:

1. estado objetivo y conversión texto ↔ bitboard;
2. rechazo de longitudes, valores, duplicados y caracteres inválidos;
3. rotación circular de fila en ambos sentidos;
4. rotación circular de columna en ambos sentidos;
5. composición de una acción en el orden fila→columna, con el ejemplo `11+` de la sección 5.4;
6. conservación del multiconjunto de fichas tras cada acción;
7. conservación de paridad para las 32 acciones;
8. que `11-` no se use como supuesto inverso de `11+`;
9. replay de una solución hasta alcanzar el objetivo;
10. comparación A* contra BFS en estados alcanzables poco profundos;
11. cota de cada heurística frente al coste exacto de esos estados;
12. transición abstracta y distancia correcta de cada recurso PBD.

Para la caracterización total del grupo, las pruebas de paridad no bastan: se necesita la verificación indicada en la sección 7.3.

## 11. Referencias

- **[R1]** Matteo Caporrella y Stefano Leucci, “An Almost-Optimal Upper Bound on the Push Number of the Torus Puzzle”, *FUN 2026*, LIPIcs 366, artículo 11, 2026. [DOI: 10.4230/LIPIcs.FUN.2026.11](https://doi.org/10.4230/LIPIcs.FUN.2026.11). Fuente matemática principal para la definición estándar, métricas y alcanzabilidad.
- **[R2]** [Loopover.xyz](https://loopover.xyz/). Implementación jugable de referencia y descripción del objetivo práctico.
- **[R3]** Janis Pritzkau, [repositorio `janispritzkau/loopover`](https://github.com/janispritzkau/loopover), fuente de la implementación web enlazada desde Loopover.xyz.
- **[R4]** [Torchlight, `loopsolver`](https://github.com/torchlight/loopsolver), solver de referencia para 4×4 y 5×5 bajo movimientos estándar.
- **[R5]** [Notas de heurísticas de `loopsolver`](https://github.com/torchlight/loopsolver/blob/master/heuristics.md), análisis de métricas, búsquedas y bases de patrones del Loopover estándar. Se usa como comparación, no como prueba de admisibilidad para las acciones acopladas.
- **[R6]** [Loopover-Brute-Force-and-Improvement](https://github.com/coolcomputery/Loopover-Brute-Force-and-Improvement), reúne resultados comunitarios de cotas para tamaños pequeños; se considera fuente secundaria y sus cifras solo aplican a las reglas/métricas que especifica.

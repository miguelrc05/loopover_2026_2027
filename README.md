# Loopover 2026–2027

Implementación en Java de un buscador para el puzzle Loopover 4×4. El proyecto modela una variante de **movimientos acoplados**: cada acción desplaza una fila y, a continuación, una columna.

## Estado del proyecto

> **En desarrollo.** El código contiene esqueletos de implementación para el estado, la búsqueda y las heurísticas. Por tanto, los comandos de resolución todavía no deben considerarse funcionales.

## Requisitos

- Java 17 o posterior.
- Maven.

## Construcción

Desde la raíz del proyecto:

```bash
mvn clean package
```

El `pom.xml` configura un JAR ejecutable en `target/loopover.jar`. Los comandos de abajo describen la interfaz prevista; las operaciones pendientes deben completarse antes de poder usarlos con éxito.

## Tablero y formato de entrada

El tablero es una matriz 4×4 sin hueco: contiene exactamente las fichas numeradas de `00` a `15`, una por casilla. La posición resuelta, leída por filas, es:

```text
00 01 02 03
04 05 06 07
08 09 10 11
12 13 14 15
```

En la CLI se representa como una cadena de 32 dígitos decimales, con dos dígitos por casilla. Por ejemplo, el estado resuelto es:

```text
00010203040506070809101112131415
```

## Acciones de esta variante

Una acción se escribe como `fila columna signo`, con índices de fila y columna entre `0` y `3`; por ejemplo, `21+`:

- `+`: desplaza la fila a la derecha y luego la columna hacia abajo.
- `-`: desplaza la fila a la izquierda y luego la columna hacia arriba.
- Ambos desplazamientos son circulares: las fichas que salen por un extremo reaparecen por el otro.

La acción acoplada completa cuenta como un movimiento de coste 1 en el modelo del proyecto. No es la misma métrica que la del Loopover estándar, donde se desplaza una sola fila o una sola columna por movimiento.

## Interfaz prevista

```bash
# Consultar ayuda
java -jar target/loopover.jar --help

# Ver sucesores de un estado
java -jar target/loopover.jar verify -s 00010203040506070809101112131415

# Aplicar acciones consecutivas separadas por comas
java -jar target/loopover.jar verify -s 00010203040506070809101112131415 -a 21+,03-

# Buscar una solución
java -jar target/loopover.jar solve -s 00010203040506070809101112131415
```

`solve` prevé opciones para seleccionar estrategia, heurística, profundidad y límites de memoria/estados visitados. Consulta `--help` cuando la implementación esté completada.

## Documentación

La documentación detallada de planificación se conserva localmente en `doc/` y se excluye del repositorio mediante `.gitignore`.

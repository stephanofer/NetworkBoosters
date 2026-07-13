# Plan de trabajo de NetworkBoosters

> Estado: listo para ejecución  
> Propósito: organizar la implementación completa en bloques secuenciales  
> Fuente de verdad: [Diseño final de NetworkBoosters](./diseno-final-networkboosters.md)

Este documento define exclusivamente el orden de trabajo. No sustituye, resume ni modifica el diseño aprobado. El alcance funcional, las decisiones técnicas, los flujos, los contratos, los edge cases y las pruebas requeridas se encuentran detallados en el archivo de diseño.

La implementación avanzará de un bloque al siguiente únicamente cuando el bloque actual cumpla su condición de cierre. De esta forma, cada etapa recibe bases terminadas y verificadas, sin interrumpir funcionalidades en desarrollo para construir dependencias omitidas.

## Secuencia general

```text
Bloque 0: Infraestructura base
    -> Bloque 1: Contratos y dominio
    -> Bloque 2: Configuración y definiciones
    -> Bloque 3: Persistencia, revisiones y snapshots
    -> Bloque 4: Inventario, capacidad y claims
    -> Bloque 5: Activaciones, colas y cálculo
    -> Bloque 6: Transferencias
    -> Bloque 7: Eventos y sincronización Redis
    -> Bloque 8: Localización e interfaces operativas
    -> Bloque 9: Menús y experiencia de usuario
    -> Bloque 10: Integración, endurecimiento y cierre
```

## Criterio general de avance

Un bloque se considera terminado cuando:

- todo su alcance está implementado;
- compila desde un entorno limpio;
- sus pruebas automatizadas están implementadas y pasan;
- las pruebas de los bloques anteriores continúan pasando;
- sus flujos pueden verificarse de principio a fin;
- no deja contratos provisionales ni dependencias sin resolver para el siguiente bloque;
- los fallos, la concurrencia y los edge cases aplicables al bloque están cubiertos;
- no introduce soluciones temporales que deban reemplazarse en bloques posteriores.

Las pruebas forman parte del bloque que implementa cada comportamiento. No se posponen hasta el final del proyecto.

## Bloque 0: Infraestructura base

### Objetivo

Confirmar que el proyecto y las capacidades compartidas soportan correctamente el diseño antes de construir lógica de negocio sobre ellas.

### Alcance organizativo

- completar el esqueleto de lifecycle de Paper;
- comprobar el inicio y apagado real del plugin;
- verificar la integración base de las dependencias requeridas;
- comprobar las capacidades necesarias de base de datos, migraciones, transacciones y locking;
- comprobar las capacidades necesarias de Redis y su lifecycle;
- comprobar el bootstrap y reload de zMenu;
- comprobar la integración base con NetworkPlayerSettings y cloud-paper;
- identificar cualquier capacidad faltante o inadecuada de CraftKit.

Si CraftKit no cubre correctamente una necesidad, la capacidad debe corregirse o incorporarse en el módulo correspondiente antes de continuar. No se implementarán workarounds locales.

### Condición de cierre

- el lifecycle de inicio y apagado del plugin queda implementado y verificable;
- las clases declaradas en la metadata existen y son cargables;
- las dependencias requeridas se resuelven correctamente;
- las capacidades críticas de CraftKit fueron verificadas;
- no quedan bloqueos de infraestructura conocidos para los bloques siguientes.

### Detalle de referencia

Consultar principalmente las secciones 4, 15, 18, 20, 22, 27 y 28 del diseño final.

## Bloque 1: Contratos y dominio

### Dependencia

Bloque 0 terminado.

### Objetivo

Establecer el lenguaje público del sistema y todas las reglas puras que no requieren Paper, MySQL ni Redis.

### Alcance organizativo

- tipos e identificadores del dominio;
- definiciones, scopes, targets y requisitos;
- boosters poseídos, activos, en cola y claims;
- snapshots públicos inmutables;
- requests y resultados tipados;
- contrato de `NetworkBoostersService`;
- reglas puras de capacidad, cálculo, precisión, compatibilidad, límites y tiempo;
- semántica de activación, extensión, conflictos, colas y expiración.

### Condición de cierre

- los contratos públicos están estabilizados;
- las reglas centrales pueden probarse sin iniciar Paper ni infraestructura externa;
- las colecciones y snapshots públicos son inmutables;
- el comportamiento temporal puede verificarse con un `Clock` controlado;
- todos los casos puros del bloque tienen pruebas unitarias.

### Detalle de referencia

Consultar principalmente las secciones 3, 5 a 10, 12, 13, 17, 26 y 29 del diseño final.

## Bloque 2: Configuración y definiciones

### Dependencia

Bloque 1 terminado.

### Objetivo

Convertir la configuración válida en snapshots inmutables y seguros para el resto del sistema.

### Alcance organizativo

- estructura inicial de archivos configurables;
- carga y parsing mediante BoostedYAML;
- validación individual y cruzada;
- registro inmutable de definiciones;
- carga inicial atómica;
- preparación del reemplazo atómico utilizado por reload;
- tratamiento de definiciones modificadas, deshabilitadas o eliminadas.

### Condición de cierre

- una configuración válida produce un registro completo e inmutable;
- una configuración inválida informa archivo y ruta exacta;
- los IDs duplicados y valores incoherentes son rechazados;
- un fallo de carga no reemplaza el último estado válido;
- todos los escenarios de configuración del bloque tienen pruebas automatizadas.

### Detalle de referencia

Consultar principalmente las secciones 5, 7 a 9, 19, 20 y 29 del diseño final.

## Bloque 3: Persistencia, revisiones y snapshots

### Dependencia

Bloques 1 y 2 terminados.

### Objetivo

Construir la fuente de verdad durable y el mecanismo completo de carga y lectura del estado de un jugador.

### Alcance organizativo

- migraciones Flyway;
- tablas, constraints e índices;
- repositorios y mapeos;
- soporte transaccional;
- auditoría;
- revisiones monotónicas por jugador;
- carga completa del estado de un jugador;
- construcción de `PlayerBoostSnapshot`;
- caché local y deduplicación de cargas concurrentes;
- protección contra resultados y revisiones antiguas;
- lifecycle de carga, salida y apagado.

Este bloque se limita a persistir, cargar y consultar estado. Las mutaciones funcionales se incorporan en los bloques siguientes.

### Condición de cierre

- todas las migraciones se ejecutan correctamente sobre una base vacía;
- el estado completo puede reconstruirse desde MySQL;
- la revisión impide que una carga antigua reemplace un snapshot nuevo;
- las lecturas cacheadas no realizan I/O;
- un fallo de auditoría revierte la transacción correspondiente;
- las pruebas automatizadas aplicables al bloque pasan.

### Detalle de referencia

Consultar principalmente las secciones 3, 6, 15, 17, 28, 29 y 31 del diseño final.

## Bloque 4: Inventario, capacidad y claims

### Dependencia

Bloque 3 terminado.

### Objetivo

Completar la primera capacidad de negocio durable de principio a fin.

### Alcance organizativo

- resolución de capacidad efectiva;
- grants normales y forzados;
- revoke y set administrativo;
- creación de claims para orígenes admitidos;
- reclamación de claims;
- auditoría e incremento de revisión;
- reemplazo del snapshot después del commit;
- resultados tipados para todas las condiciones de negocio.

### Condición de cierre

- inventario y claims funcionan completamente mediante el servicio;
- no existen recepciones parciales;
- ninguna caché se actualiza antes del commit;
- cantidades inválidas y overflow son rechazados de forma segura;
- los claims concurrentes no pueden reclamarse dos veces;
- las pruebas unitarias y de integración aplicables al bloque pasan.

### Detalle de referencia

Consultar principalmente las secciones 9, 10, 13, 15, 16, 29 y 31 del diseño final.

## Bloque 5: Activaciones, colas y cálculo

### Dependencia

Bloque 4 terminado.

### Objetivo

Completar el núcleo funcional que consume boosters y calcula sus efectos durante gameplay.

### Alcance organizativo

- activación inmediata;
- validación definitiva de requisitos;
- extensión del mismo booster;
- políticas `QUEUE`, `REJECT` y `REPLACE`;
- cola FIFO y fusión compatible;
- límites de duración y cantidad de entradas;
- expiración y promoción;
- reconstrucción de la línea temporal después de downtime;
- desactivación administrativa;
- cálculo síncrono conectado al snapshot real.

### Condición de cierre

- otorgar, activar y calcular un booster funciona de principio a fin;
- `calculate` es thread-safe, no bloqueante y no realiza I/O;
- la última unidad no puede consumirse dos veces;
- no existe stacking multiplicativo accidental dentro del mismo grupo;
- reinicios y downtime respetan la línea temporal absoluta;
- las pruebas de tiempo, límites y concurrencia aplicables al bloque pasan.

### Detalle de referencia

Consultar principalmente las secciones 5 a 8, 12, 13, 15 a 17, 25, 29 y 31 del diseño final.

## Bloque 6: Transferencias

### Dependencia

Bloque 5 terminado.

### Objetivo

Implementar transferencias atómicas y seguras entre jugadores conectados en la misma instancia Paper.

### Alcance organizativo

- validación de que emisor y receptor estén online en la instancia local;
- políticas y validaciones de transferencia;
- cooldown;
- locking determinista por UUID;
- débito y crédito dentro de una misma transacción;
- registros de transferencia y auditoría;
- revisiones y snapshots de ambos jugadores;
- resultados tipados.

### Condición de cierre

- nunca puede existir débito sin crédito ni crédito sin débito;
- un receptor offline o conectado en otra instancia provoca rechazo completo;
- el receptor lleno provoca rechazo completo y no genera claims;
- las transferencias cruzadas no producen inconsistencias;
- los deadlocks esperables tienen tratamiento controlado;
- ambos snapshots reflejan exclusivamente el estado confirmado;
- las pruebas de integración y concurrencia aplicables al bloque pasan.

### Detalle de referencia

Consultar principalmente las secciones 9, 11, 13, 15, 16, 29 y 31 del diseño final.

## Bloque 7: Eventos y sincronización Redis

### Dependencia

Bloques 3 a 6 terminados.

### Objetivo

Propagar de forma segura las mutaciones confirmadas y mantener coherencia práctica entre servidores.

### Alcance organizativo

- eventos públicos de Paper;
- evento previo cancelable de activación;
- eventos posteriores al commit;
- ejecución de eventos en el hilo correcto;
- invalidaciones Redis versionadas;
- deduplicación y descarte por revisión;
- recarga ante revisiones superiores;
- reconexión y estado degradado;
- reconciliación periódica;
- sincronización de ambos participantes de una transferencia.

### Condición de cierre

- dos instancias mantienen snapshots coherentes después de una mutación;
- Redis puede fallar sin revertir operaciones confirmadas en MySQL;
- mensajes duplicados o fuera de orden no degradan el estado;
- los callbacks de Redis no acceden directamente a Bukkit;
- la recuperación de Redis reconcilia a los jugadores relevantes;
- las pruebas de eventos e integración Redis aplicables al bloque pasan.

### Detalle de referencia

Consultar principalmente las secciones 14, 17, 18, 25, 28, 29 y 31 del diseño final.

## Bloque 8: Localización e interfaces operativas

### Dependencia

Bloque 7 terminado.

### Objetivo

Exponer las capacidades estables del servicio mediante interfaces operativas, sin duplicar reglas de negocio.

### Orden interno

1. Integración con NetworkPlayerSettings y Adventure.
2. Comandos administrativos e inspección.
3. Comandos de jugador.
4. PlaceholderAPI.
5. Reload completo y atómico.

### Alcance organizativo

- readiness del jugador;
- resolución del idioma efectivo;
- mensajes Adventure seguros;
- comandos cloud y suggestions;
- inspección operativa del estado;
- expansión interna PlaceholderAPI basada exclusivamente en caché;
- reload de configuración y definiciones sin estado parcial.

### Condición de cierre

- todas las operaciones previstas pueden ejecutarse sin depender de menús;
- los comandos delegan en el servicio y no replican reglas de negocio;
- los mensajes respetan el idioma efectivo del jugador;
- PlaceholderAPI nunca consulta MySQL ni Redis;
- un reload inválido conserva íntegramente el estado anterior;
- las pruebas de integración aplicables al bloque pasan.

### Detalle de referencia

Consultar principalmente las secciones 12, 20, 21, 23, 24, 28 a 31 del diseño final.

## Bloque 9: Menús y experiencia de usuario

### Dependencia

Bloque 8 terminado.

### Objetivo

Construir los flujos visuales sobre casos de uso ya terminados y verificados.

### Alcance organizativo

- menú principal;
- lista dinámica y paginación;
- filtros y orden;
- visualización de activos, colas, claims y restricciones;
- confirmación de activación;
- confirmación de transferencia;
- pending states inmutables con expiración;
- prevención de doble click;
- estados de carga, vacío y error;
- actualización ante mutaciones locales o remotas;
- reload seguro de zMenu.

### Condición de cierre

- los menús no autorizan operaciones y siempre delegan en el servicio;
- un estado visual desactualizado no puede causar consumo parcial;
- los pending states se limpian correctamente;
- desconexiones y callbacks tardíos no modifican UI inválida;
- paginación, filtros y estados vacíos son deterministas;
- las pruebas de UI aplicables al bloque pasan.

### Detalle de referencia

Consultar principalmente las secciones 8, 10, 11, 20 a 22, 25, 29 y 31 del diseño final.

## Bloque 10: Integración, endurecimiento y cierre

### Dependencia

Bloques 0 a 9 terminados.

### Objetivo

Integrar el consumidor inicial, validar el sistema completo bajo condiciones reales y cerrar el producto al 100%.

### Orden interno

1. Integrar NetworkProgression con `network_progression:points`.
2. Completar warnings y actualizaciones visuales de expiración.
3. Completar observabilidad y diagnóstico operativo.
4. Ejecutar pruebas integrales, de concurrencia, lifecycle, reinicio y degradación.
5. Corregir todos los defectos encontrados.
6. Verificar todos los criterios de aceptación.
7. Realizar la documentación completa del proyecto terminado.

### Condición de cierre

- NetworkProgression calcula recompensas mediante la API síncrona sin I/O;
- el caso inicial `10 points -> 20 points` está verificado;
- el sistema supera los escenarios críticos de concurrencia y reinicio;
- Redis degradado no provoca pérdida durable;
- el lifecycle completo del plugin queda implementado y verificado;
- todos los edge cases obligatorios están resueltos;
- todos los criterios de aceptación del diseño final se cumplen;
- no quedan funcionalidades, pruebas ni correcciones pendientes;
- la documentación final refleja el comportamiento real del producto terminado.

### Detalle de referencia

Consultar principalmente las secciones 25, 28 a 31, 33 y 34 del diseño final.

## Estado de ejecución

| Bloque | Estado | Dependencia |
| --- | --- | --- |
| 0. Infraestructura base | Terminado | Ninguna |
| 1. Contratos y dominio | Terminado | Bloque 0 |
| 2. Configuración y definiciones | Terminado | Bloque 1 |
| 3. Persistencia, revisiones y snapshots | Terminado | Bloques 1 y 2 |
| 4. Inventario, capacidad y claims | Terminado | Bloque 3 |
| 5. Activaciones, colas y cálculo | Terminado | Bloque 4 |
| 6. Transferencias | Terminado | Bloque 5 |
| 7. Eventos y sincronización Redis | Terminado | Bloques 3 a 6 |
| 8. Localización e interfaces operativas | Pendiente | Bloque 7 |
| 9. Menús y experiencia de usuario | Pendiente | Bloque 8 |
| 10. Integración, endurecimiento y cierre | Pendiente | Bloques 0 a 9 |

Los estados admitidos son `Pendiente`, `En progreso`, `Bloqueado` y `Terminado`. Solo puede comenzar un bloque cuando todas sus dependencias estén marcadas como `Terminado`.

# Diseño final de NetworkBoosters

> Estado: aprobado y listo para implementación  
> Alcance: diseño funcional, técnico, operativo y de integración  
> Plataforma inicial: Paper 26.1.2, Java 25

NetworkBoosters será la plataforma central de modificadores temporales de HERA Network. Su responsabilidad es administrar boosters poseídos, activaciones, colas, transferencias, límites de inventario, sincronización entre servidores y una API pública de evaluación para otros plugins.

El primer caso de uso será un booster personal que multiplica los points entregados por NetworkProgression. El diseño no queda acoplado a points: nuevos recursos como experiencia o monedas podrán integrarse mediante targets estables sin modificar el núcleo del sistema.

Este documento consolida las decisiones aprobadas. No presenta alternativas pendientes. Cualquier cambio posterior deberá tratarse como una modificación explícita del diseño.

## 1. Objetivos

NetworkBoosters debe:

- aumentar el tiempo de juego y crear incentivos de progresión y monetización;
- permitir que jugadores posean, consulten, activen y transfieran boosters;
- permitir boosters normales y boosters cuya activación requiera permisos de rango;
- ofrecer una API pública pequeña, estable y eficiente para NetworkProgression y futuros consumidores;
- evaluar recompensas sin I/O, bloqueos ni acceso a base de datos durante gameplay;
- mantener el estado correcto al cambiar de servidor, desconectarse, reiniciar o sufrir una caída;
- ofrecer comandos, menús, autocompletado, eventos Paper y PlaceholderAPI;
- entregar todo el contenido visible en el idioma efectivo de NetworkPlayerSettings;
- mantener una UX clara, configurable y segura ante concurrencia;
- ser liviano, auditable, depurable y extensible sin sobreingeniería.

## 2. Alcance inicial

La primera versión funcional incluye:

- boosters personales;
- target `network_progression:points`;
- multiplicadores configurables;
- inventario de boosters por jugador;
- límites totales de inventario determinados por permisos;
- activación inmediata, extensión y colas por grupo;
- requisitos de permisos para activar determinados boosters;
- transferencias entre jugadores;
- recompensas pendientes cuando una entrega del sistema no cabe en el inventario;
- persistencia MySQL;
- sincronización Redis;
- API pública y eventos Paper;
- comandos de jugador y administración;
- menús zMenu localizados;
- expansión interna PlaceholderAPI;
- recarga segura de configuración;
- auditoría de mutaciones.

No forman parte del alcance inicial:

- stacking multiplicativo entre boosters del mismo grupo;
- mercado o subasta de boosters;
- pausado manual de boosters;
- reordenamiento manual de colas;
- boosters basados en tiempo jugado online;
- integración directa con la API de LuckPerms;
- ORM o framework genérico de efectos;
- soporte Folia declarado sin pruebas específicas;
- bossbars globales para múltiples boosters personales.

## 3. Principios no negociables

### 3.1 La base de datos es la fuente de verdad

MySQL conserva inventarios, activaciones, colas, transferencias, recompensas pendientes y auditoría. Redis no sustituye la persistencia durable.

### 3.2 El hot path no realiza I/O

La evaluación de un reward usa exclusivamente snapshots inmutables en memoria. NetworkProgression nunca consulta MySQL o Redis para otorgar points.

### 3.3 El tiempo es absoluto

Un booster persiste `activated_at` y `expires_at` en UTC. El tiempo continúa aunque el jugador esté desconectado o la network esté apagada.

### 3.4 Las mutaciones son atómicas

Activar, transferir, otorgar, retirar, reclamar y promover colas se ejecuta mediante transacciones. No se permiten estados parciales.

### 3.5 La UI no autoriza operaciones

Menús y comandos presentan información, pero toda operación se valida nuevamente dentro del servicio y de la transacción definitiva.

### 3.6 Configurable no significa arbitrario

Se configurarán definiciones, textos, permisos, límites, menús y comportamientos aprobados. YAML no se convertirá en un lenguaje de programación ni expondrá detalles internos.

## 4. Decisiones tecnológicas

| Área | Decisión |
| --- | --- |
| Lenguaje | Java 25 |
| Plataforma | Paper 26.1.2 |
| Artefactos | `networkboosters-api` y `networkboosters-paper` |
| Persistencia | `craftkit-database` y Flyway |
| Sincronización | `craftkit-redis` con Pub/Sub y reconciliación |
| Menús | zMenu mediante `craftkit-zmenu` |
| Idiomas | NetworkPlayerSettings como dependencia requerida |
| Comandos | cloud-paper y cloud-minecraft-extras |
| Texto | Adventure `Component` y MiniMessage |
| YAML | BoostedYAML con versionado y auto-update |
| Placeholders | Expansión interna PlaceholderAPI |
| Permisos | API de permisos Bukkit |
| Caché local | `ConcurrentHashMap` con snapshots inmutables |
| Precisión | `BigDecimal` para multiplicadores y cantidades calculadas |
| Tiempo | `Instant`, `Duration`, `Clock` y timestamps UTC |

La metadata Paper deberá corregir la inconsistencia inicial entre la dependencia `26.1.2.build.74-stable` y `api-version: '26.2'`. Compilación y metadata deben declarar una combinación compatible real.

## 5. Conceptos del dominio

### 5.1 Definición de booster

Una definición describe un tipo configurable de booster. No representa una unidad poseída ni una activación concreta.

```java
public record BoosterDefinition(
    BoosterId id,
    BoosterTarget target,
    BigDecimal multiplier,
    Duration duration,
    BoosterScope scope,
    String activationGroup,
    ConflictPolicy conflictPolicy,
    ActivationRequirements requirements,
    TransferPolicy transferPolicy,
    boolean enabled,
    int displayOrder,
    String category
) {}
```

Las definiciones cargadas se exponen como snapshots inmutables.

### 5.2 Identificador

```java
public record BoosterId(String value) {}
```

Reglas:

- se normaliza a minúsculas;
- usa la expresión `[a-z0-9][a-z0-9_-]{0,63}`;
- no depende del nombre traducido ni del archivo visible en menús;
- es estable en comandos, persistencia, API y auditoría;
- renombrarlo con datos existentes requiere una migración explícita.

### 5.3 Target

```java
public record BoosterTarget(String key) {}
```

El primer target es:

```text
network_progression:points
```

Se usa una clave namespaced en lugar de un enum cerrado para permitir que futuros plugins integren nuevos recursos sin cambiar el núcleo.

### 5.4 Scope

Un scope define dónde puede aplicarse una activación:

- tipo personal;
- modalidades permitidas;
- todos los servidores pertenecientes a cada modalidad permitida.

La primera versión usa boosters personales. `*` significa cualquier modalidad. `server_scopes` se conserva en el contrato y la persistencia, pero las definiciones deben declarar `servers: ["*"]`: una modalidad siempre incluye todos sus servidores. La UI muestra únicamente modalidades.

### 5.5 Booster poseído

```java
public record OwnedBooster(
    UUID playerId,
    BoosterId boosterId,
    long amount
) {}
```

Las unidades idénticas se agregan por cantidad. No se crea una fila por cada unidad porque no aportaría identidad individual y aumentaría almacenamiento y complejidad. El menú proyecta cada unidad como un ítem visual independiente sin alterar este modelo agregado.

### 5.6 Booster activo

```java
public record ActiveBooster(
    UUID activationId,
    UUID playerId,
    BoosterId boosterId,
    BoosterTarget target,
    BigDecimal multiplier,
    String activationGroup,
    BoosterScope scope,
    Instant activatedAt,
    Instant expiresAt,
    ActivationSource source,
    String sourceReference
) {}
```

La activación persiste un snapshot mínimo del comportamiento. Un reload que cambie un booster de x2 a x3 no modifica activaciones ya iniciadas.

### 5.7 Booster en cola

Una entrada de cola ya fue consumida del inventario, pero su duración aún no empezó. Guarda el snapshot necesario para ejecutarse aunque la definición sea modificada o eliminada posteriormente.

### 5.8 Recompensa pendiente

Una recompensa pendiente es una entrega durable protegida que no debe perderse por falta de capacidad. Se limita a compras, compensaciones y creación administrativa explícita; no se usa para recompensas normales ni transferencias entre jugadores.

## 6. Semántica temporal

### 6.1 No existe un contador persistente por segundo

Al activar un booster de dos horas a las 18:00 UTC se guarda:

```text
activated_at = 18:00 UTC
expires_at   = 20:00 UTC
```

El tiempo restante se calcula cuando se necesita:

```text
remaining = expires_at - current_time
```

No se escribe en MySQL cada segundo.

### 6.2 Desconexión

Desconectarse no pausa el booster. Si un jugador activa dos horas, se desconecta durante una hora y vuelve, esa hora fue consumida.

### 6.3 Cambio de servidor

Cambiar de servidor no reinicia ni pausa la activación. Todos los servidores usan el mismo `expires_at`.

### 6.4 Reinicio o caída

El reinicio no conserva el contador que había al apagarse: conserva la fecha absoluta. El período apagado también consume tiempo.

Si el booster expiró mientras la network estaba apagada, al iniciar se considera expirado inmediatamente.

### 6.5 Validez independiente del cleanup

Un booster aplica únicamente cuando:

```java
activeBooster.expiresAt().isAfter(clock.instant())
```

Una tarea retrasada no extiende el booster. El cleanup materializa cambios y notificaciones, pero no define la validez.

### 6.6 Hora canónica

Los timestamps se almacenan en UTC. Las activaciones deben usar la hora de MySQL dentro de la transacción, por ejemplo `CURRENT_TIMESTAMP(3)`, para reducir diferencias entre relojes de servidores. Las máquinas deben mantener sincronización NTP.

### 6.7 Tiempo de las colas durante una caída

La cola forma una línea temporal continua. Si un activo termina a las 20:00 y el siguiente dura dos horas, el siguiente comienza conceptualmente a las 20:00 aunque la network esté apagada.

Al iniciar, la reconciliación avanza por la línea temporal hasta encontrar:

- la entrada que todavía debería estar activa; o
- una cola completamente expirada.

Esto mantiene la regla de que el tiempo personal continúa transcurriendo offline.

## 7. Grupos, extensión y colas

### 7.1 Grupo de activación

Los boosters que no deben aplicar simultáneamente comparten `activation-group`.

Ejemplo:

```yaml
activation:
  group: personal-points
```

Todos los multiplicadores personales de points compartirán inicialmente ese grupo.

### 7.2 Un activo por grupo

Solo existe un booster activo por jugador y grupo de activación. Pueden coexistir boosters de grupos independientes.

Ejemplo válido:

```text
personal-points:     x2 activo
personal-experience: x3 activo
personal-coins:      x1.5 activo
```

### 7.3 Mismo booster exacto

Activar nuevamente el mismo booster extiende su duración.

```text
Activo: x2, queda 1 hora
Nuevo:  x2, dura 2 horas
Final:  x2, quedan 3 horas
```

La unidad se consume solo si la extensión completa respeta los límites generales.

### 7.4 Booster diferente del mismo grupo

Un booster con distinto multiplicador o comportamiento entra en cola FIFO.

```text
Activo: x2 durante 1 hora
Nuevo:  x3 durante 2 horas

Resultado:
1. x2 continúa
2. x3 comienza al finalizar x2
```

No se reemplaza el activo, no se pierde tiempo y no se produce `x2 * x3 = x6`.

### 7.5 Fusión al final de la cola

Si una nueva entrada coincide exactamente con la última entrada de cola, se fusionan sus duraciones.

La compatibilidad requiere:

- mismo booster ID;
- mismo target;
- mismo multiplicador;
- mismo grupo;
- mismos scopes;
- mismos requisitos efectivos.

### 7.6 Políticas soportadas

```java
public enum ConflictPolicy {
    QUEUE,
    REJECT,
    REPLACE
}
```

- `QUEUE`: política predeterminada y recomendada;
- `REJECT`: no consume la unidad si el grupo está ocupado;
- `REPLACE`: reemplaza el activo según configuración explícita y descarta su tiempo restante.

No se implementará pausado y reanudación del activo reemplazado.

### 7.7 Límites operativos generales

Inventario, cola y duración son límites distintos.

```yaml
activation:
  maximum-total-duration: 7d
  maximum-queued-entries: 20
```

Si una activación excede el máximo, se rechaza completa y no consume la unidad. Nunca se recorta duración silenciosamente.

Los límites de cola y duración son generales, no límites por booster.

## 8. Requisitos de permisos y monetización

### 8.1 Permiso para activar

Una definición puede exigir permisos:

```yaml
activation:
  requirements:
    permissions:
      - "networkboosters.tier.phantom"
    mode: ALL
```

También se soporta `ANY` cuando varios permisos alternativos habilitan la activación.

### 8.2 Poseer no implica poder activar

Un jugador puede:

- recibir el booster;
- conservarlo;
- verlo en su inventario;
- transferirlo si la definición lo permite;
- no poder activarlo hasta obtener el permiso requerido.

Esta separación permite entregar boosters bloqueados como incentivo para adquirir un rango.

### 8.3 Validación del permiso

El permiso se comprueba:

1. al renderizar el menú;
2. al abrir la confirmación;
3. dentro de la operación definitiva de activación.

La tercera comprobación es la autoridad real.

### 8.4 Pérdida del permiso

Perder un rango:

- no elimina boosters poseídos;
- impide nuevas activaciones que requieren ese rango;
- no cancela boosters ya activos;
- no elimina entradas ya consumidas en cola.

Una activación válida continúa hasta su expiración.

### 8.5 Presentación bloqueada

El menú muestra el booster con item y lore configurables:

```text
Multiplicador personal de points x3

Duración: 2 horas
Multiplicador: x3
Requiere: Rango Phantom

Necesitás Phantom para activarlo.
```

El click bloqueado puede ejecutar acciones configurables, como mensaje, sonido o enlace a tienda. No debe cerrar el menú sin explicar el requisito.

### 8.6 Desacoplamiento de rangos

NetworkBoosters no consulta nombres de grupos ni herencias de LuckPerms. Usa `Player#hasPermission`. LuckPerms u otro proveedor decide quién posee cada permiso.

## 9. Capacidad total de inventario

### 9.1 Qué controla

El permiso de capacidad controla únicamente los boosters sin consumir del inventario.

No cuentan:

- boosters activos;
- boosters en cola;
- recompensas pendientes.

Ejemplo:

```text
Capacidad Phantom: 60
Inventario:         58/60
Activos:            3
En cola:            7
```

El uso de capacidad es `58/60`.

### 9.2 Activar libera espacio

Al activar, la unidad sale del inventario inmediatamente. Libera espacio aunque comience en ese momento o entre en cola.

### 9.3 Configuración por permisos

```yaml
inventory-limits:
  fallback: 30

  tiers:
    phantom:
      permission: "networkboosters.capacity.phantom"
      maximum: 60
      priority: 100

    legend:
      permission: "networkboosters.capacity.legend"
      maximum: 100
      priority: 200
```

El código no conoce nombres de rangos. Las keys `phantom` y `legend` son IDs configurativos.

### 9.4 Resolución

Si aplican varias reglas, gana la mayor capacidad. `priority` resuelve empates o reglas administrativas equivalentes; el orden accidental del YAML no cambia el resultado.

### 9.5 Sin límites por booster

No existen `maximum-per-booster`, `per-booster-default` ni reglas similares. Solo existe capacidad total de inventario.

### 9.6 Pérdida de rango

Si un jugador tenía `55/60` y su nueva capacidad es 30:

```text
Inventario: 55/30
Estado: excedido
```

No se elimina ninguna unidad. El jugador puede activar y transferir boosters para reducir el inventario, pero no puede recibir nuevas unidades hasta quedar por debajo del límite o recuperar capacidad.

### 9.7 Recepción completa

No hay recepciones parciales silenciosas. Si tiene dos espacios y una transferencia intenta entregar cinco unidades, la transferencia completa se rechaza.

### 9.8 Operaciones que respetan capacidad

La capacidad se valida al:

- otorgar boosters;
- comprar boosters;
- recibir transferencias;
- reclamar recompensas pendientes;
- recibir rewards automáticos;
- ejecutar integraciones externas mediante `grant`.

La activación no valida espacio porque reduce el inventario.

### 9.9 Grants administrativos forzados

Un grant administrativo puede incluir un modo `force` explícito, protegido por permiso separado y auditado. No es el comportamiento predeterminado y puede dejar al jugador excedido.

## 10. Recompensas pendientes

### 10.1 Finalidad

Una recompensa del sistema o compra verificada no puede desaparecer porque el inventario esté lleno. En ese caso se guarda como claim durable.

### 10.2 Orígenes admitidos

- compras;
- compensaciones;
- creación administrativa explícita.

Crates, pase de batalla, eventos, recompensas diarias, modalidades y entregas generales del sistema no crean claims. Si no hay capacidad, `grant` devuelve `INVENTORY_LIMIT_REACHED` y el sistema origen decide si rechaza, conserva o descarta su recompensa. Una crate consumible debe comprobar capacidad antes de gastar la llave, sin sustituir la validación transaccional definitiva.

### 10.3 Flujo

1. El sistema intenta otorgar.
2. La capacidad es insuficiente.
3. La recompensa se persiste en `booster_claims`.
4. El jugador recibe una notificación localizada.
5. Puede consultarla en `/boosters claims` o menú.
6. La reclamación vuelve a validar capacidad transaccionalmente.

### 10.4 Transferencias excluidas

Las transferencias no crean claims. Si el receptor no tiene capacidad, se rechazan. Esto evita usar claims para evadir límites.

## 11. Transferencias

### 11.1 Contrato

```java
CompletableFuture<TransferResult> transfer(BoosterTransferRequest request);
```

```java
public record BoosterTransferRequest(
    UUID senderId,
    UUID recipientId,
    BoosterId boosterId,
    long amount,
    TransferSource source,
    SourceReference sourceReference
) {}
```

### 11.2 Configuración por booster

```yaml
transfer:
  enabled: true
  minimum-amount: 1
  maximum-amount: 10
  cooldown: 30s
  permission: ""
```

Los boosters promocionales, compensaciones o recompensas vinculadas pueden declarar `enabled: false`.

### 11.3 Validaciones

La transferencia se rechaza cuando:

- emisor y receptor son el mismo UUID;
- el emisor no está online en la instancia Paper que procesa la operación;
- el receptor no está online en esa misma instancia Paper;
- el booster no existe o no es transferible;
- la cantidad es inválida;
- el emisor no posee suficientes unidades;
- el receptor excedería su capacidad total;
- no se cumple el permiso de transferencia;
- existe un cooldown vigente;
- una mutación concurrente cambió el inventario;
- el storage no está disponible.

El receptor no necesita permiso de activación para recibir. Puede conservar el booster bloqueado y activarlo cuando obtenga el rango requerido.

### 11.4 Disponibilidad de los participantes

Las transferencias requieren que emisor y receptor estén online en la misma instancia Paper que procesa la operación. `Server#getPlayer(UUID)` es la autoridad operativa para esta validación; no se usa `Bukkit.getOfflinePlayer(String)` ni se consulta una identidad central.

Un jugador offline o conectado en otra instancia no puede enviar ni recibir mediante esta operación. La transferencia se rechaza completa antes de abrir la transacción, no genera claims y no modifica inventarios, revisiones, auditoría ni cooldown. Esta restricción elimina la necesidad de resolver identidad y permisos de capacidad offline.

### 11.5 Transacción

1. Resolver ambos UUID.
2. Verificar que ambos jugadores estén online en la misma instancia Paper.
3. Resolver el permiso del emisor y la capacidad efectiva del receptor desde sus jugadores locales.
4. Revalidar inmediatamente disponibilidad, ready state, permiso y capacidad antes de delegar la escritura a MySQL.
5. Bloquear inventarios en orden determinista por UUID.
6. Revalidar saldo y capacidad con el uso real persistido.
7. Restar al emisor.
8. Sumar al receptor.
9. Insertar registro de transferencia.
10. Insertar auditoría para ambos.
11. Commit.
12. Reemplazar snapshots locales.
13. Publicar invalidaciones Redis.
14. Emitir eventos y notificaciones.

El orden determinista reduce deadlocks cuando dos jugadores se transfieren simultáneamente.

### 11.6 Resultado

```java
public enum TransferStatus {
    TRANSFERRED,
    SAME_PLAYER,
    RECIPIENT_NOT_ONLINE,
    NOT_TRANSFERABLE,
    INVALID_AMOUNT,
    INSUFFICIENT_AMOUNT,
    RECIPIENT_LIMIT_REACHED,
    COOLDOWN,
    PERMISSION_DENIED,
    PLAYER_NOT_READY,
    SERVICE_UNAVAILABLE
}
```

```java
public record TransferResult(
    TransferStatus status,
    UUID senderId,
    UUID recipientId,
    BoosterId boosterId,
    long amount,
    Optional<UUID> transferId,
    Optional<Instant> retryAt,
    OptionalLong senderRemainingAmount,
    OptionalLong recipientResultingAmount
) {}
```

`transferId` existe únicamente para `TRANSFERRED`. `retryAt` existe únicamente para `COOLDOWN`. Los saldos son opcionales porque algunos rechazos ocurren antes de consultar storage; una transferencia exitosa siempre los incluye.

## 12. API pública

La API vive en `networkboosters-api` y no expone CraftKit, Redis, JDBC, zMenu ni clases internas.

### 12.1 Servicio principal

```java
public interface NetworkBoostersService {

    Optional<BoosterDefinition> definition(BoosterId boosterId);

    Collection<BoosterDefinition> definitions();

    Optional<PlayerBoostSnapshot> cached(UUID playerId);

    PlayerBoostSnapshot getCachedOrEmpty(UUID playerId);

    CompletableFuture<PlayerBoostSnapshot> load(UUID playerId);

    CompletableFuture<PlayerBoostSnapshot> refresh(UUID playerId);

    boolean isReady(UUID playerId);

    BoostCalculation calculate(BoostRequest request);

    CompletableFuture<ActivationResult> activate(
        UUID playerId,
        BoosterId boosterId,
        ActivationSource source
    );

    CompletableFuture<InventoryMutationResult> grant(
        UUID playerId,
        BoosterId boosterId,
        long amount,
        MutationSource source
    );

    CompletableFuture<InventoryMutationResult> revoke(
        UUID playerId,
        BoosterId boosterId,
        long amount,
        MutationSource source
    );

    CompletableFuture<TransferResult> transfer(
        BoosterTransferRequest request
    );

    CompletableFuture<ClaimResult> claim(
        UUID playerId,
        UUID claimId
    );

    CompletableFuture<DeactivationResult> deactivate(
        UUID activationId,
        DeactivationReason reason
    );
}
```

### 12.2 Threading

- `calculate`, `cached`, `getCachedOrEmpty`, `definition` y `definitions` son síncronos, thread-safe y sin I/O;
- cargas y mutaciones son async;
- no se usa `.join()` o `.get()` en comandos, eventos o gameplay;
- las colecciones y snapshots públicos son inmutables;
- los callbacks vuelven al scheduler correcto antes de tocar Paper.

### 12.3 Registro

`NetworkBoostersService` se registra mediante Bukkit `ServicesManager`. Los consumidores lo obtienen en `onEnable()` después de declarar dependencia fuerte.

### 12.4 Evaluación de recompensas

NetworkProgression no pregunta solo si existe un booster. Solicita el cálculo completo:

```java
BoostCalculation calculation = boosters.calculate(
    BoostRequest.of(
        playerId,
        "network_progression:points",
        BigDecimal.valueOf(10),
        "skywars",
        serverId
    )
);
```

```java
public record BoostCalculation(
    BigDecimal baseAmount,
    BigDecimal multiplier,
    BigDecimal finalAmount,
    List<AppliedBoost> appliedBoosts
) {}
```

Esto centraliza scopes, stacking, expiración y trazabilidad.

### 12.5 Precisión

La API usa `BigDecimal`. El consumidor decide cómo convertir el resultado al tipo final mediante una política explícita de redondeo. NetworkBoosters no asume que todos los recursos sean enteros.

### 12.6 Débitos y cantidades negativas

Por defecto, cantidades base negativas no reciben multiplicadores. Un booster de recompensa no debe aumentar penalizaciones o débitos accidentalmente.

## 13. Resultados tipados

Las condiciones normales de negocio usan resultados tipados, no booleanos ambiguos ni excepciones.

```java
public enum ActivationStatus {
    ACTIVATED,
    EXTENDED,
    QUEUED,
    NOT_OWNED,
    DEFINITION_DISABLED,
    PERMISSION_DENIED,
    ALREADY_ACTIVE,
    GROUP_OCCUPIED,
    QUEUE_LIMIT_REACHED,
    DURATION_LIMIT_REACHED,
    PLAYER_NOT_READY,
    STORAGE_UNAVAILABLE
}
```

```java
public record ActivationResult(
    ActivationStatus status,
    Optional<ActiveBooster> activeBooster,
    Optional<QueuedBooster> queuedBooster,
    long remainingInventoryAmount
) {}
```

Errores de programación, corrupción o infraestructura excepcional sí completan el future excepcionalmente cuando no existe un estado de negocio recuperable.

## 14. Eventos Paper

Eventos públicos:

- `PlayerBoostersReadyEvent`;
- `BoosterPreActivateEvent`;
- `BoosterActivateEvent`;
- `BoosterExtendEvent`;
- `BoosterQueueEvent`;
- `BoosterDeactivateEvent`;
- `BoosterExpireEvent`;
- `BoosterInventoryChangeEvent`;
- `BoosterTransferEvent`;
- `BoosterClaimEvent`.

Reglas:

- `BoosterPreActivateEvent` es cancelable y ocurre antes de persistir;
- los eventos posteriores ocurren después del commit y actualización local;
- los eventos remotos indican origen y revisión;
- se disparan en el hilo correcto de Paper;
- contienen snapshots públicos inmutables;
- no existe un evento por cada `calculate`, porque ese hot path debe ser barato y determinista.

## 15. Persistencia

### 15.1 Tablas

```text
network_booster_inventory
network_booster_activations
network_booster_queue
network_booster_claims
network_booster_transfers
network_booster_audit_log
network_booster_player_revision
network_booster_flyway_schema_history
```

Los nombres reales se componen con el prefijo configurado mediante `database.table(...)`.

### 15.2 Inventario

Campos mínimos:

```text
player_uuid
booster_id
amount BIGINT UNSIGNED
updated_at TIMESTAMP(3)
PRIMARY KEY (player_uuid, booster_id)
```

La suma de `amount` de todas las filas del jugador representa el uso total de inventario.

### 15.3 Activaciones

Campos mínimos:

```text
activation_id
player_uuid
booster_id
target_key
multiplier DECIMAL(19, 6)
activation_group
conflict_policy
game_scopes
server_scopes
activated_at TIMESTAMP(3)
expires_at TIMESTAMP(3)
status
source_type
source_reference
updated_at TIMESTAMP(3)
```

Índices:

- `(player_uuid, status, expires_at)`;
- `(status, expires_at)`;
- unicidad efectiva de activo por jugador y grupo, protegida por transacción y esquema compatible con MySQL.

### 15.4 Cola

La cola está normalizada. No se serializa como strings con separadores.

```text
queue_id
player_uuid
activation_group
position
booster_id
target_key
multiplier
duration_millis
scope snapshot
requirements snapshot
queued_at
source_type
source_reference
```

### 15.5 Claims

```text
claim_id
player_uuid
booster_id
amount
source_type
source_reference
created_at
claimed_at nullable
status
```

### 15.6 Transferencias

```text
transfer_id
sender_uuid
recipient_uuid
booster_id
amount
source_type
source_reference
server_id
created_at
status
```

### 15.7 Auditoría

Registra:

- operación;
- actor UUID o consola;
- jugador afectado;
- booster y cantidad;
- valor anterior y nuevo;
- activación o transferencia relacionada;
- servidor origen;
- referencia externa;
- timestamp;
- resultado.

Si insertar auditoría forma parte de una mutación y falla, la transacción completa hace rollback.

### 15.8 Revisiones

Cada mutación incrementa una revisión monotónica por jugador. La revisión permite descartar mensajes Redis duplicados o fuera de orden y evita que un refresh antiguo reemplace un snapshot nuevo.

## 16. Flujos transaccionales

### 16.1 Activación

1. Validar player ready y definición.
2. Comprobar requisitos de permiso en el servidor origen.
3. Disparar evento previo cancelable.
4. Abrir transacción.
5. Bloquear inventario y estado del grupo.
6. Revalidar cantidad, definición y límites.
7. Decrementar exactamente una unidad.
8. Activar, extender o insertar en cola.
9. Incrementar revisión.
10. Insertar auditoría.
11. Commit.
12. Reemplazar snapshot local.
13. Publicar invalidación Redis.
14. Emitir evento posterior y feedback.

Dos clicks o dos servidores no pueden consumir la misma última unidad.

### 16.2 Grant

1. Resolver capacidad efectiva.
2. Bloquear inventario y revisión.
3. Validar cantidad y capacidad.
4. Insertar o incrementar inventario; o crear claim para orígenes admitidos.
5. Auditar e incrementar revisión.
6. Commit, actualizar snapshot y publicar.

### 16.3 Revoke

El revoke no permite cantidades negativas y no reduce por debajo de cero. La semántica exacta de insuficiencia se devuelve en un resultado tipado.

### 16.4 Transferencia

Aplica el flujo definido en la sección de transferencias y bloquea jugadores en orden determinista.

### 16.5 Claim

1. Bloquear claim e inventario.
2. Verificar que siga pendiente y pertenezca al jugador.
3. Recalcular capacidad.
4. Insertar inventario completo.
5. Marcar claim como reclamado.
6. Auditar, incrementar revisión y commit.

### 16.6 Expiración y promoción

1. Detectar `expires_at <= now`.
2. Abrir transacción.
3. Bloquear grupo del jugador.
4. Marcar activo expirado.
5. Avanzar la línea temporal de la cola usando la expiración previa como inicio.
6. Saltar y marcar entradas que también hayan expirado durante downtime.
7. Activar la primera entrada todavía vigente, si existe.
8. Incrementar revisión, auditar y commit.
9. Actualizar snapshot, publicar y emitir eventos.

## 17. Caché local

### 17.1 Estructura

```java
ConcurrentHashMap<UUID, PlayerBoostSnapshot> snapshots;
ConcurrentHashMap<UUID, CompletableFuture<PlayerBoostSnapshot>> inFlightLoads;
```

### 17.2 Reglas

- cargas simultáneas del mismo UUID comparten future;
- un snapshot se reemplaza atómicamente;
- las lecturas son O(1);
- no hay DB en `calculate`;
- quit elimina estado cuando no existen operaciones que puedan reintroducir una revisión antigua;
- un resultado async solo reemplaza caché si su revisión es más nueva.

### 17.3 Caffeine

Caffeine no se incorpora inicialmente. No existe todavía una necesidad de eviction, refresh automático o miles de snapshots offline retenidos. Se evaluará solo si métricas reales demuestran esa necesidad.

## 18. Redis y consistencia entre servidores

### 18.1 Utilidad práctica

MySQL es el libro contable, la caché local responde durante gameplay y Redis avisa rápidamente a los demás servidores que un jugador cambió.

Ejemplos:

- un jugador activa en SkyWars y cambia a BedWars;
- recibe un grant administrativo desde lobby;
- transfiere boosters mientras el receptor está en otro servidor;
- reclama una recompensa y abre el menú en otra instancia.

Sin Redis, los servidores dependerían de reconexiones o polling frecuente. Consultar MySQL por cada kill sería inaceptable.

### 18.2 Mensaje

```json
{
  "schemaVersion": 1,
  "eventId": "uuid",
  "sourceServerId": "skywars-01",
  "playerId": "uuid",
  "revision": 42,
  "type": "ACTIVATED"
}
```

### 18.3 Procesamiento

- ignorar eventos propios cuando el snapshot local ya fue aplicado;
- ignorar duplicados;
- ignorar revisiones menores o iguales;
- invalidar y recargar cuando llega una revisión superior;
- no tocar Bukkit directamente desde callbacks Lettuce;
- volver al scheduler correcto para UI, eventos o jugadores.

### 18.4 Pub/Sub no es durable

Redis Pub/Sub puede perder mensajes durante desconexiones. Por eso se usa para invalidación rápida, no para conservar operaciones de negocio.

### 18.5 Redis degradado

Redis se inicia en modo recuperable. Si cae:

- MySQL continúa aceptando mutaciones;
- el servidor origen conserva el snapshot correcto;
- las publicaciones fallidas se registran;
- joins cargan desde DB;
- al recuperarse Redis se reconcilian jugadores online;
- una operación confirmada en DB no se revierte porque falló Pub/Sub.

### 18.6 Reconciliación

La reconciliación compara revisiones locales y persistidas de jugadores relevantes. Su intervalo puede acortarse en estado degradado y normalizarse al recuperar operación.

## 19. Configuración

### 19.1 Estructura de archivos

```text
plugins/NetworkBoosters/
├── config.yml
├── messages/
│   ├── es.yml
│   └── en.yml
├── boosters/
│   └── personal_points_x2.yml
├── inventories/
│   ├── boosters.yml
│   ├── booster-confirm.yml
│   ├── booster-transfer.yml
│   └── booster-claims.yml
└── patterns/
    └── pagination.yml
```

### 19.2 Definición de ejemplo

```yaml
config-version: 1

id: personal_points_x3
enabled: true

target: network_progression:points
multiplier: 3.0
duration: 2h

scope:
  type: PERSONAL
  games:
    - "*"
  servers:
    - "*"

activation:
  group: personal-points
  conflict-policy: QUEUE
  maximum-total-duration: 7d
  maximum-queued-entries: 20

  requirements:
    permissions:
      - "networkboosters.tier.phantom"
    mode: ALL

transfer:
  enabled: true
  minimum-amount: 1
  maximum-amount: 5
  cooldown: 30s
  permission: ""

display:
  order: 200
  category: points
```

No existe configuración de máximo por booster.

### 19.3 Configuración global de ejemplo

```yaml
config-version: 2

server:
  id: "skywars-01"
  game-id: "skywars"

storage:
  table-prefix: "network_booster_"

redis:
  enabled: true
  reconciliation-interval: 30s
  degraded-reconciliation-interval: 5s

limits:
  maximum-multiplier: 10.0

inventory-limits:
  fallback: 30
  tiers:
    phantom:
      permission: "networkboosters.capacity.phantom"
      maximum: 60
      priority: 100

    legend:
      permission: "networkboosters.capacity.legend"
      maximum: 100
      priority: 200

commands:
  root: boosters
  aliases:
    - booster
    - boosts

placeholderapi:
  enabled: true

scope-display:
  games:
    skywars: "SkyWars"
    bedwars: "BedWars"
```

### 19.4 Validación

Startup y reload validan:

- IDs únicos y válidos;
- targets no vacíos;
- multiplicadores positivos, finitos y dentro del máximo;
- duraciones positivas;
- scopes reconocidos;
- grupos y políticas válidas;
- requisitos y modos válidos;
- límites coherentes;
- traducciones requeridas;
- rutas y tipos YAML;
- colisiones de configuración.

Los errores indican archivo y ruta exacta.

### 19.5 Definición eliminada

Si una definición desaparece:

- no se crean nuevas activaciones;
- activaciones y colas existentes continúan con su snapshot;
- inventario persistido no se elimina;
- se registra una advertencia operativa;
- el administrador debe restaurar o migrar ese ID.

## 20. Reload

`/boosters admin reload`:

1. carga YAML en estructuras temporales fuera del hilo principal;
2. valida configuración, idiomas, boosters y menús;
3. conserva íntegramente el estado anterior si algo falla;
4. vuelve al hilo principal de Paper;
5. recarga zMenu mediante el plan de CraftKit;
6. reemplaza el snapshot de configuración de forma atómica;
7. informa definiciones cargadas, errores y warnings.

No se recrean durante reload:

- conexión DB;
- cliente Redis;
- servicio Bukkit;
- PlaceholderExpansion;
- command manager;
- listeners enable-only.

## 21. Localización y Adventure

NetworkPlayerSettings es dependencia requerida.

### 21.1 Join

1. NetworkPlayerSettings carga y dispara `PlayerSettingsReadyEvent`.
2. NetworkBoosters carga el snapshot del jugador.
3. Marca al jugador ready.
4. Dispara `PlayerBoostersReadyEvent`.

### 21.2 Idioma

Se usa `PlayerSettingsService.resolvedLanguage(player)`. NetworkBoosters no mantiene una caché de idioma duplicada.

### 21.3 Mensajes

Adventure `Component` es la salida canónica. MiniMessage usa `TagResolver` y valores de jugador se insertan como `unparsed` o `component`, nunca concatenando texto interpretable.

### 21.4 Traducciones por booster

```yaml
boosters:
  personal_points_x3:
    name: "<aqua>Multiplicador personal de points"
    description:
      - "<gray>Triplica los points que obtengas."
```

Cada idioma soportado debe contener las keys obligatorias o aplicar un fallback explícito y registrado como warning.

### 21.5 Cambio de idioma

Los mensajes posteriores usan inmediatamente el idioma resuelto nuevo. zMenu ya proporciona el puente nativo con NetworkPlayerSettings; no se duplica esa integración.

## 22. Menús zMenu

Los menús se cargan mediante `craftkit-zmenu`, con bootstrap y reload seguro.

### 22.1 Menú principal

Incluye:

- boosters disponibles;
- cantidades;
- estado bloqueado por permiso;
- booster activo y tiempo restante;
- cola por grupo;
- filtro y orden;
- claims pendientes;
- navegación;
- estado vacío;
- estados de carga y error.

### 22.2 Lista dinámica

Se registra un único loader, por ejemplo:

```text
NETWORKBOOSTERS_OWNED_BOOSTERS
```

El botón extiende `PaginateButton` y:

- obtiene vistas ya preparadas del snapshot;
- filtra y ordena en memoria;
- resuelve solo la página actual;
- renderiza en rangos configurables;
- devuelve el total en `getPaginationSize()`;
- muestra un item central si no existen resultados;
- asigna callback individual a cada item.

```yaml
owned-boosters:
  type: NETWORKBOOSTERS_OWNED_BOOSTERS
  empty-slot: 22
  slots:
    - 10-16
    - 19-25
    - 28-34
```

No se crea un button YAML por booster.

### 22.3 Filtros

Filtros previstos:

- todos;
- activos;
- points;
- todas las modalidades;
- modalidad actual;
- bloqueados por rango;
- transferibles.

La UI oculta filtros que no produzcan una diferencia útil. Cambiar filtro vuelve a página 1.

### 22.4 Orden

Orden predeterminado:

1. activo;
2. aplicable al servidor y modalidad actual;
3. desbloqueado antes que bloqueado;
4. `display.order`;
5. duración descendente;
6. ID como desempate estable.

### 22.5 Confirmación de activación

1. Guardar `PendingActivation` inmutable.
2. Abrir menú de confirmación.
3. Mostrar preview central.
4. Confirmar con área verde amplia.
5. Cancelar con área roja y volver preservando página y filtro.
6. Limpiar pending al cerrar.
7. Bloquear doble click.
8. Mostrar item de carga.
9. Ejecutar async.
10. Volver al scheduler de Paper.
11. Mostrar resultado localizado.
12. Actualizar menú.

`PendingActivation` guarda ID, página, filtro, token y expiración corta. No guarda referencias mutables.

### 22.6 Transferencias

Los menús no inician ni confirman transferencias. La única interfaz de jugador es `/boosters transfer <player> <booster> [amount]`, con suggestions de Brigadier. Esto evita un selector de cabezas que no escala y mantiene un único flujo operativo. El servicio revalida todos los datos y ejecuta la transferencia atómicamente.

### 22.7 Estado desactualizado

Si cambia inventario, permiso, capacidad, definición o cola entre menús, la operación definitiva devuelve un resultado de negocio y refresca la UI. No se consume nada parcialmente.

## 23. Comandos

### 23.1 Jugador

```text
/boosters
/boosters menu
/boosters list
/boosters active
/boosters queue
/boosters claims
/boosters activate <booster>
/boosters transfer <player> <booster> [amount]
/boosters help
```

### 23.2 Administración

```text
/boosters admin give <player> <booster> [amount]
/boosters admin give <player> <booster> [amount] --force
/boosters admin take <player> <booster> [amount]
/boosters admin set <player> <booster> <amount>
/boosters admin claim <player> <booster> [amount]
/boosters admin activate <player> <booster>
/boosters admin deactivate <player> [activation-id|all]
/boosters admin inspect <player>
/boosters admin reload
```

### 23.3 Permisos

```text
networkboosters.command.open
networkboosters.command.list
networkboosters.command.activate
networkboosters.command.transfer
networkboosters.command.claims
networkboosters.admin.give
networkboosters.admin.give.force
networkboosters.admin.take
networkboosters.admin.set
networkboosters.admin.claim
networkboosters.admin.activate
networkboosters.admin.deactivate
networkboosters.admin.inspect
networkboosters.admin.reload
```

Los permisos de capacidad y activación se definen en configuración y no están hardcodeados.

### 23.4 Cloud

cloud-paper aporta Brigadier, parsers y suggestions. cloud-minecraft-extras se usa para help y manejo Adventure de excepciones. Los captions deben resolverse por idioma, no usar defaults fijos en inglés.

Suggestions incluyen:

- boosters que el jugador posee;
- definiciones existentes para administración;
- jugadores válidos según el parser;
- argumentos permitidos por cada subcomando.

## 24. PlaceholderAPI

Expansión interna sugerida:

```text
%networkboosters_ready%
%networkboosters_capacity%
%networkboosters_owned_total%
%networkboosters_owned_<booster-id>%
%networkboosters_active_count%
%networkboosters_active_ids%
%networkboosters_active_<target>%
%networkboosters_multiplier_<target>%
%networkboosters_time_remaining_<booster-id>%
%networkboosters_seconds_remaining_<booster-id>%
%networkboosters_queue_size_<group>%
%networkboosters_claims_count%
```

Reglas:

- solo caché local;
- nunca DB o Redis;
- comportamiento determinista sin jugador o antes de ready;
- `persist() == true`;
- registro único;
- placeholders técnicos no localizados;
- placeholders humanos localizados según jugador;
- parser paramétrico, no una expansión por booster.

## 25. Expiración y warnings

Una tarea liviana periódica:

- detecta transiciones expiradas;
- dispara mensajes y sonidos;
- actualiza menús abiertos;
- promueve colas;
- persiste expiraciones agrupadas;
- publica invalidaciones.

Warnings configurables:

```yaml
activation:
  expiry-warnings:
    - 5m
    - 1m
    - 10s
```

Cada warning se entrega una vez por activación y umbral. La tarea no determina si el multiplicador todavía aplica.

## 26. Arquitectura de módulos y paquetes

```text
NetworkBoosters/
├── networkboosters-api/
└── networkboosters-paper/
```

No se crearán más módulos Gradle sin una necesidad concreta.

### 26.1 API

```text
com.stephanofer.networkboosters.api/
├── NetworkBoostersService.java
├── booster/
├── calculation/
├── player/
├── result/
└── event/
```

### 26.2 Paper

```text
com.stephanofer.networkboosters/
├── NetworkBoostersPlugin.java
├── NetworkBoostersBootstrap.java
├── NetworkBoostersLoader.java
├── booster/
├── player/
├── persistence/
├── synchronization/
├── command/
├── menu/
├── localization/
├── placeholder/
├── config/
└── lifecycle/
```

La organización sigue responsabilidades visibles y evita capas ceremoniales con una clase.

## 27. Dependencias

### 27.1 Requeridas

- Paper API;
- Adventure provisto por Paper;
- cloud-paper;
- cloud-minecraft-extras;
- BoostedYAML sombreado y relocado según política;
- `craftkit-database`;
- `craftkit-redis`;
- `craftkit-zmenu`;
- NetworkPlayerSettings API;
- zMenu API;
- PlaceholderAPI `compileOnly`;
- JUnit;

### 27.2 No requeridas inicialmente

- Caffeine;
- LuckPerms API;
- ORM;
- framework de dependency injection;
- event bus propio;
- Redis Streams;
- plugin messaging como sincronización;
- scheduler propio;
- framework de efectos genérico.

Si CraftKit no cubre correctamente una capacidad necesaria, se deberá mejorar el módulo correspondiente antes de introducir un workaround local.

## 28. Lifecycle Paper

### 28.1 Bootstrap

- construir cloud `PaperCommandManager.Bootstrapped`;
- registrar el árbol Brigadier en la fase correcta;
- evitar registro unsafe salvo necesidad documentada.

### 28.2 Enable

Orden recomendado:

1. cargar y validar configuración;
2. abrir DB;
3. ejecutar migraciones con classloader del plugin;
4. crear repositorios;
5. crear Redis recuperable y suscripciones;
6. cargar definiciones;
7. crear servicio y cachés;
8. registrar `NetworkBoostersService`;
9. integrar NetworkPlayerSettings;
10. cargar zMenu y button loaders;
11. registrar PlaceholderAPI si existe;
12. registrar listeners;
13. iniciar expiración y reconciliación;
14. cargar jugadores ya online si aplica.

### 28.3 Disable

1. dejar de aceptar nuevas mutaciones;
2. cancelar tareas;
3. cerrar suscripciones y observers Redis;
4. desregistrar expansión y servicios;
5. limpiar estado zMenu soportado;
6. cerrar Redis;
7. cerrar DB;
8. impedir que callbacks tardíos modifiquen Paper o cachés cerradas.

## 29. Edge cases obligatorios

- doble click;
- activación simultánea en dos servidores;
- dos operaciones intentando consumir la última unidad;
- transferencia cruzada simultánea;
- deadlocks y retries transaccionales controlados;
- desconexión durante un future;
- disable durante callbacks;
- Redis caído antes o después del commit;
- mensajes Redis duplicados o fuera de orden;
- reload inválido;
- definición eliminada o modificada;
- booster bloqueado por rango;
- pérdida de permiso después de abrir confirmación;
- pérdida de rango con inventario excedido;
- emisor o receptor offline;
- receptor conectado en otra instancia Paper;
- receptor lleno;
- grant de sistema sin espacio;
- claim concurrente;
- expiración durante confirmación;
- expiración durante downtime;
- múltiples entradas de cola expiradas durante downtime;
- página inexistente después de filtrar;
- pending state abandonado;
- cantidades cero, negativas u overflow;
- multiplicadores cero, negativos, `NaN` o infinitos;
- duraciones extremas;
- scope vacío o inválido;
- target sin consumidor;
- idioma o key faltante;
- placeholder sin jugador;
- jugador todavía no ready;
- menú abierto durante invalidación remota;
- fallo de auditoría;
- clock skew entre servidores;
- cantidad base negativa;
- recepción parcial accidental;
- grant forzado sin permiso específico.

## 30. Observabilidad

Logs útiles y sin spam:

- duración de startup y migraciones;
- definiciones cargadas;
- estado Redis y transiciones;
- reconciliaciones y divergencias;
- fallos de publicación;
- retries o deadlocks DB;
- errores con archivo y ruta YAML;
- operaciones administrativas;
- latencia DB solo en modo debug.

`/boosters admin inspect <player>` muestra:

- ready state;
- revisión local y persistida;
- capacidad efectiva y permiso que la concedió;
- inventario total y desglose;
- activos y expiraciones absolutas;
- cola y línea temporal;
- claims pendientes;
- multiplicador efectivo por target;
- estado Redis;
- última reconciliación.

## 31. Pruebas

### 31.1 Unitarias

- IDs y normalización;
- scopes;
- requisitos de permisos;
- capacidad por reglas;
- pérdida de rango;
- stacking y colas;
- cálculo y precisión;
- cantidades negativas;
- expiración con `Clock.fixed`;
- avance de cola durante downtime;
- orden y filtros;
- límites y overflow;
- reload transaccional;
- formato localizado;
- parsing de placeholders.

### 31.2 Integración MySQL

- grant y revoke;
- activación consume una unidad;
- concurrencia sobre última unidad;
- extensión;
- inserción y fusión de cola;
- promoción;
- rollback por auditoría;
- transferencia atómica;
- transferencias cruzadas;
- capacidad del receptor;
- claims;
- revisiones;
- migraciones Flyway e índices.

### 31.3 Integración Redis

- mensaje duplicado;
- fuera de orden;
- origen propio;
- reconexión;
- reconciliación tras caída;
- commit exitoso con publish fallido;
- actualización de emisor y receptor.

### 31.4 Paper y UI

- registro del servicio;
- eventos en hilo correcto;
- commands y suggestions;
- paginación;
- estado vacío;
- confirmaciones;
- doble click;
- cambio de idioma;
- item bloqueado por rango;
- placeholders sin I/O.

## 32. Orden de implementación

1. Convertir el build a dos módulos y corregir metadata Paper.
2. Crear contratos de API y documentar threading.
3. Implementar configuración y registro inmutable de definiciones.
4. Crear migraciones y repositorios.
5. Implementar revisiones, snapshots y carga por jugador.
6. Implementar cálculo síncrono.
7. Implementar capacidad por permisos.
8. Implementar grant, revoke y claims.
9. Implementar activación, extensión, cola y expiración.
10. Implementar transferencias.
11. Implementar eventos.
12. Implementar Redis y reconciliación.
13. Integrar NetworkPlayerSettings y Adventure.
14. Implementar comandos cloud.
15. Implementar zMenu y flujos de confirmación.
16. Implementar PlaceholderAPI.
17. Añadir warnings y actualización visual.
18. Añadir tests unitarios e integración MySQL.
19. Documentar API de consumo.
20. Integrar NetworkProgression con `10 points -> 20 points`.
21. Ejecutar pruebas de concurrencia, reinicio y Redis degradado.

## 33. Criterios de aceptación

El sistema está listo cuando:

- NetworkProgression obtiene el resultado mediante una llamada síncrona sin I/O;
- un jugador puede recibir, consultar, activar, extender, encolar y transferir boosters;
- los boosters bloqueados comunican su rango y no se activan sin permiso;
- capacidad total se resuelve por permisos y no existen límites por booster;
- activos, cola y claims no consumen capacidad de inventario;
- perder rango no elimina datos;
- compras y rewards confiables no se pierden por inventario lleno;
- transferencias a receptores llenos se rechazan atómicamente;
- la última unidad no puede consumirse dos veces;
- no existe stacking multiplicativo accidental;
- desconexiones, lag, reinicios y caídas no pausan el tiempo;
- una cola se reconstruye correctamente después de downtime;
- Redis puede caer sin pérdida durable;
- todos los mensajes y menús respetan el idioma;
- PlaceholderAPI nunca consulta infraestructura;
- reload no deja estado parcial;
- API, eventos, comandos y configuración están documentados;
- edge cases críticos tienen pruebas automatizadas;
- los errores operativos son auditables y diagnosticables.

## 34. Decisiones finales resumidas

- NetworkBoosters es una plataforma de modificadores, no un plugin exclusivo de points.
- MySQL es la fuente de verdad.
- Redis distribuye invalidaciones y habilita reconciliación rápida.
- La caché local hace que gameplay sea síncrono y barato.
- El tiempo se basa en expiraciones UTC y continúa offline.
- Solo hay un activo por grupo; distintos grupos pueden coexistir.
- El mismo booster extiende y uno diferente del mismo grupo entra en cola por defecto.
- No existe stacking multiplicativo dentro del grupo.
- Los boosters pueden requerir permisos de activación.
- Poseer un booster no garantiza poder activarlo.
- Las transferencias están incluidas, son configurables, atómicas y auditadas.
- Las transferencias requieren que ambos jugadores estén online en la misma instancia Paper.
- La capacidad depende de permisos y cuenta solo inventario sin consumir.
- No existen límites por booster.
- Perder rango no elimina boosters.
- Solo compras, compensaciones y entregas administrativas sin espacio se guardan como claims.
- Crates, pase, eventos, diarias, modalidades y entregas generales sin espacio se rechazan sin crear claims.
- Transferencias sin espacio se rechazan y no generan claims.
- La API pública usa snapshots inmutables, resultados tipados y `BigDecimal`.
- Menús, comandos, eventos y placeholders son bordes de la misma lógica de dominio, no implementaciones paralelas.

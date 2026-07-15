# NetworkBoosters

NetworkBoosters is HERA Network's centralized personal booster service for Paper. It manages the complete booster lifecycle across the network, from acquisition and activation to reward calculation, transfer, expiration, and auditing.

The repository is public, but the project is developed primarily for HERA's internal infrastructure and gameplay systems.

## Features

- Configurable personal boosters with targets, multipliers, durations, scopes, permissions, and conflict policies.
- Persistent player inventories with permission-based capacity limits.
- Activation timelines supporting queue, reject, replace, extend, and merge behavior.
- Atomic player-to-player transfers with limits, permissions, and cooldowns.
- Claims for rewards that cannot be delivered directly to a full inventory.
- Idempotent grants for safely processing purchases and external rewards.
- Synchronous, thread-safe, I/O-free reward calculations using immutable player snapshots.
- Cross-server cache invalidation through Redis with MySQL reconciliation as a fallback.
- Player and administration commands, zMenu interfaces, localized messages, and PlaceholderAPI support.
- A public Java API with typed requests, results, snapshots, and Paper events.

## Architecture

| Module | Responsibility |
| --- | --- |
| `networkboosters-api` | Stable integration contract for other plugins. Contains domain types, requests, results, events, and `NetworkBoostersService`. |
| `networkboosters-paper` | Paper implementation. Owns persistence, caching, synchronization, commands, menus, localization, and plugin lifecycle. |

MySQL is the authoritative data store. Gameplay reads and calculations use immutable in-memory snapshots, while mutations run asynchronously inside transactions and publish updated revisions after commit. Redis distributes invalidations between servers; if it becomes unavailable, local operations continue and periodic MySQL reconciliation restores convergence.

Consumer plugins must integrate through `networkboosters-api`. They must not depend on implementation classes or access NetworkBoosters tables and Redis keys directly.

## Technical Specifications

| Component | Specification |
| --- | --- |
| Project version | `1.0.0` |
| Java | `25` |
| Build system | Gradle `9.6.1` with Kotlin DSL |
| Server platform | Paper `26.1` |
| Database | MySQL with Flyway migrations and HikariCP |
| Synchronization | Redis, optional but recommended for multi-server deployments |
| Required plugins | NetworkPlayerSettings `2.0.0`, zMenu |
| Optional plugin | PlaceholderAPI |
| Public API artifact | `com.stephanofer:networkboosters-api:1.0.0` |

Version `1.0.0` supports personal boosters. The built-in reward consumer targets `network_progression:points`; other namespaced targets can be handled by plugins through the calculation API.

## Build

Use a Java 25 JDK and the included Gradle wrapper:

```bash
./gradlew clean build
```

On Windows:

```powershell
.\gradlew.bat clean build
```

The deployable plugin is generated under `target/`. API, sources, and Javadoc artifacts are generated under `target-api/`.

Run the test suite independently with:

```bash
./gradlew test
```

## Deployment

1. Install NetworkPlayerSettings and zMenu on the Paper server. Install PlaceholderAPI when placeholders are required.
2. Place the NetworkBoosters JAR in the server's `plugins/` directory.
3. Start the server once to generate configuration, messages, booster definitions, and menu resources.
4. Configure the unique server and game identifiers, MySQL connection, and optional Redis connection in `plugins/NetworkBoosters/config.yml`.
5. Restart the server. Database migrations are applied and verified automatically during startup.

The plugin does not enable when required dependencies, configuration, or MySQL are unavailable. Redis may be disabled or temporarily degraded without blocking local booster operations.

## Integration

Other plugins obtain `NetworkBoostersService` through Paper's `ServicesManager`. The API provides cached state, reward calculations, inventory mutations, activations, transfers, claims, deactivations, and lifecycle events.

Use the API as `compileOnly`, wait for player readiness before reading or mutating state, and always inspect the typed result status of asynchronous operations.

```kotlin
dependencies {
    compileOnly("com.stephanofer:networkboosters-api:1.0.0")
}
```

## Documentation

- [Documentation overview](docs/producto/networkboosters/README.md)
- [Installation and API integration](docs/producto/networkboosters/integracion.md)
- [Domain model](docs/producto/networkboosters/dominio.md)
- [Operations and result contracts](docs/producto/networkboosters/operaciones.md)
- [Configuration](docs/producto/networkboosters/configuracion.md)
- [Commands and placeholders](docs/producto/networkboosters/superficies-operativas.md)
- [Architecture and operations](docs/producto/networkboosters/arquitectura.md)

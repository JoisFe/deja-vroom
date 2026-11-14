# Wrapping Pleos SDKs in Kotlin

The SDKs expose Kotlin function-type callbacks and listener interfaces. Convert them once, in the data layer, to `suspend` functions and `Flow`s. Signatures below are from the Vehicle 2.3.2 API reference; check `docs/contest/03-vehicle-sdk.md` for other properties.

## App-side result type

```kotlin
sealed interface VehicleResult<out T> {
    data class Success<T>(val value: T) : VehicleResult<T>
    data class Unsupported(val reason: String) : VehicleResult<Nothing>   // NONE access, not supported area/property
    data class Failure(val error: VehicleError) : VehicleResult<Nothing>
}

enum class VehicleError { PERMISSION, DRIVING_RESTRICTED, SERVICE_UNAVAILABLE, EMPTY, UNKNOWN }
```

Map SDK exceptions to `VehicleError` in one function; nothing above the repository sees SDK exceptions. In 2.3.2 `VehicleException` is obfuscated, so match the `ERROR_*` name in the message (skill `pleos-vehicle-sdk`).

## One-shot reads: `suspendCancellableCoroutine`

```kotlin
suspend fun Door.currentPosition(area: DoorArea): Int? =
    suspendCancellableCoroutine { cont ->
        getCurrentDoorPosition(
            area,
            { value -> if (cont.isActive) cont.resume(value) },
            { e -> if (cont.isActive) cont.resumeWithException(e) },
        )
    }

suspend fun Door.positionCapability(): Pair<CapabilityConfig, Map<DoorArea, AreaBounds<Int>>> =
    suspendCancellableCoroutine { cont ->
        checkDoorPositionCapabilities(
            { config, areas -> if (cont.isActive) cont.resume(config to areas) },
            { e -> if (cont.isActive) cont.resumeWithException(e) },
        )
    }
```

## Continuous values: `callbackFlow`

```kotlin
fun Door.positionUpdates(area: DoorArea): Flow<Int?> = callbackFlow {
    val listener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) { trySend(pos) }
        override fun onFailed(e: Exception) { close(e) }   // inherited from VehicleListener
    }
    registerDoorPosition(area, listener)
    awaitClose { unregisterDoorPosition(area, listener) }
}
```

`awaitClose` guarantees the listener is removed when the collector goes away (screen hidden, ViewModel cleared).

## Repository

```kotlin
interface VehicleRepository {
    fun doorPosition(area: DoorArea): Flow<VehicleResult<Int?>>
    suspend fun setDoorPosition(area: DoorArea, value: Int): VehicleResult<Unit>
}

class PleosVehicleRepository @Inject constructor(
    private val vehicle: Vehicle,
    @IoDispatcher private val io: CoroutineDispatcher,
) : VehicleRepository {

    private val door by lazy { vehicle.getDoor() }

    override fun doorPosition(area: DoorArea): Flow<VehicleResult<Int?>> = flow {
        val (config, _) = door.positionCapability()
        if (config.accessType == AccessType.NONE || config.accessType == AccessType.WRITE) {
            emit(VehicleResult.Unsupported("door position not readable"))
            return@flow
        }
        emit(VehicleResult.Success(door.currentPosition(area)))
        emitAll(door.positionUpdates(area).map { VehicleResult.Success(it) })
    }.catch { e -> emit(e.toVehicleResult()) }
        .flowOn(io)

    override suspend fun setDoorPosition(area: DoorArea, value: Int): VehicleResult<Unit> =
        withContext(io) {
            runCatching {
                suspendCancellableCoroutine { cont ->
                    door.setDoorPosition(area, value,
                        { _, _ -> if (cont.isActive) cont.resume(Unit) },
                        { e -> if (cont.isActive) cont.resumeWithException(e) })
                }
            }.fold({ VehicleResult.Success(Unit) }, { it.toVehicleResult() })
        }
}
```

Clamp `value` to the capability's `AreaBounds` before calling set. Control (set) APIs may not be granted yet ("Coming Soon" in the console); the feature must degrade to read-only.

## Fake for tests and demo

```kotlin
class FakeVehicleRepository : VehicleRepository {
    val door = MutableStateFlow<VehicleResult<Int?>>(VehicleResult.Success(0))
    override fun doorPosition(area: DoorArea) = door
    override suspend fun setDoorPosition(area: DoorArea, value: Int) =
        VehicleResult.Success(Unit).also { door.value = VehicleResult.Success(value) }
}
```

## Things the SDK doesn't provide

- **Speed**: not in Vehicle 2.3.2. Read `VehiclePropertyIds.PERF_VEHICLE_SPEED` with AAOS `CarPropertyManager` in a separate `CarPropertyRepository`, same `callbackFlow` pattern with `CarPropertyEventCallback`.
- **Gear**: in 2.3.2 (`Vehicle.getGear()`) but deprecated in favor of `GEAR_SELECTION`; the same `CarPropertyRepository` is the long-term path.
- **Driving restriction**: `CarUxRestrictionsManager` → `callbackFlow` of `CarUxRestrictions` in `core/car`.

## Other SDKs

Same shape: NaviHelper events, FusedLocation updates, STT results → `callbackFlow`; TTS `speak` → `suspend`. TTS/STT server modes need a Client ID/Secret — never ship the secret in the APK (see the security rule).

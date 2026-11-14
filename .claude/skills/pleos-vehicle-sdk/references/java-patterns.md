# Vehicle SDK — Java patterns

From the official Get/Capability pages (Java variants), combined into an Activity lifecycle.
Import packages per the API reference; names below match the docs.

## Capability → Get
```java
Door door = vehicle.getDoor();
door.checkDoorPositionCapabilities(
  (config, areaMap) -> {
    if (config.getAccessType() == AccessType.READ
        || config.getAccessType() == AccessType.READ_WRITE) {
      AreaBounds<Integer> bounds = areaMap.get(new DoorArea(DoorValue.ROW_1_LEFT));
      door.getCurrentDoorPosition(
        new DoorArea(DoorValue.ROW_1_LEFT),
        pos -> { /* update UI on main thread */ return Unit.INSTANCE; },
        e -> { handleError(e); return Unit.INSTANCE; });
    } else {
      // NONE or WRITE only — disable this UI path
    }
    return Unit.INSTANCE;
  },
  e -> { handleError(e); return Unit.INSTANCE; });
```

## Get without domain-specific area
```java
Display display = vehicle.getDisplay();
display.getCurrentTemperatureDisplayUnit(
  unit -> { /* … */ return Unit.INSTANCE; },
  e -> { handleError(e); return Unit.INSTANCE; });
```

## Lifecycle
```java
public class MainActivity extends AppCompatActivity {
    private Vehicle vehicle;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vehicle = new Vehicle(this);
        vehicle.initialize();
        // then capability checks / gets
    }

    @Override protected void onDestroy() {
        if (vehicle != null) vehicle.release();
        super.onDestroy();
    }

    private void handleError(Exception e) {
        if (e instanceof VehicleException ve) {
            Log.w("Vehicle", "error code=" + ve.getErrorCode(), ve);
        } else {
            Log.w("Vehicle", "unexpected", e);
        }
    }
}
```
`instanceof` pattern matching is Java 16+; fine at the Java 17 language level.

## Troubleshooting order (from getting-started)
minSdk ≥ 28 → manifest permission → Gradle sync of the dependency → emulator fully booted → message in `onFailed`.

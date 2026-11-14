# Pleos Vehicle permissions

Source: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission

Pattern: `pleos.car.permission.<CONSTANT>`. Read permissions are `CAR_*`; set/control uses `CONTROL_CAR_*`. Individual APIs may differ — check the API's `@RequiresPermission`.

| Domain | Constant | Manifest name |
|---|---|---|
| Door | CAR_DOORS | pleos.car.permission.CAR_DOORS |
| Window, Wiper | CAR_WINDOWS | pleos.car.permission.CAR_WINDOWS |
| HVAC | CAR_CLIMATE | pleos.car.permission.CAR_CLIMATE |
| DrivingMode, Brake, Odometer | CAR_DRIVE | pleos.car.permission.CAR_DRIVE |
| EvBattery, Fuel | CAR_ENERGY | pleos.car.permission.CAR_ENERGY |
| CarInfo, Display | CAR_INFO | pleos.car.permission.CAR_INFO |
| Light, TurnSignal | CAR_LIGHTS | pleos.car.permission.CAR_LIGHTS |
| SideMirror | CAR_MIRRORS | pleos.car.permission.CAR_MIRRORS |
| Safety, Tire | CAR_SAFETY | pleos.car.permission.CAR_SAFETY |
| Seat | CAR_SEATS | pleos.car.permission.CAR_SEATS |
| Toll | CAR_TOLL | pleos.car.permission.CAR_TOLL |

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="pleos.car.permission.CAR_DOORS" />
    <!-- declare only domains the app uses -->
</manifest>
```

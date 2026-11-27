# Virtual Gyro — libxposed API 102

Virtual Gyro emulates a gyroscope and rotation-vector sensors for apps that do not have a physical gyroscope. It estimates angular velocity and orientation from the device accelerometer and magnetometer, then delivers synthetic sensor events to registered listeners.

This project has been migrated from the legacy Xposed API 93 (`de.robv.android.xposed`) to the modern **libxposed API 102** (`io.github.libxposed`). It no longer contains the legacy Xposed stub module or `assets/xposed_init` entry point.

## Compatibility

- Requires an Xposed framework implementation with support for modern libxposed API 102 (or compatible API 102 support).
- The module's default scope is `com.samsung.android.scan3d` (the Samsung 3D Scanner / GCam Photo Sphere package used during development). Change `app/src/main/resources/META-INF/xposed/scope.list` if you want a different target.
- Hooks Java-level `android.hardware.SensorManager` calls. Native sensor access is not covered.
- A derived gyroscope is an estimate, not a replacement for physical gyro hardware. It can lag and be disturbed by magnetic interference.

## Build

Requirements: JDK 17, Android SDK platform 34, and Gradle 8.7 or newer (the GitHub Actions workflow uses Gradle 8.9).

```bash
gradle --no-daemon assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

Push the project to GitHub, then open **Actions → Build APK**. The workflow builds the debug APK and uploads it as the `VirtualGyro-debug-apk` artifact.

## Install / use

1. Install `app-debug.apk`.
2. In a modern-libxposed-compatible framework manager, enable **Virtual Gyro**.
3. Ensure the target app is included in the module scope. The default scope is `com.samsung.android.scan3d`.
4. Force-stop the target app and launch it again.

## Diagnostics

Check the framework's module log, or run:

```sh
su -c 'logcat -d | grep -i VirtualGyro'
```

Expected messages include:

```text
VirtualGyro: Module loaded
VirtualGyro: Installing modern hooks in com.samsung.android.scan3d
VirtualGyro: sensor type 4 requested: provided a virtual sensor
VirtualGyro: listener registered for fake sensor type 4
```

If the app reports that a sensor type is not available and not emulated, note the sensor type number. If there are no module messages, check framework compatibility, module activation, scope, and whether the app was force-stopped.

## Implementation notes

- `MainHook` is the modern `XposedModule` entry point.
- Entry registration, scope, and API requirements are declared under `app/src/main/resources/META-INF/xposed/`.
- Hooks use libxposed's interceptor-chain API; there are no legacy `XC_MethodHook`, `XposedBridge`, or `XposedHelpers` calls.
- Sensor estimation and event delivery remain in `GyroEstimator`, `FusionMath`, `Engine`, and `FakeSensors`.

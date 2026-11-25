# Virtual Gyro (LSPosed / Vector module)

Gives an app on a phone **without a gyroscope** (Galaxy A15 4G) a *virtual* gyroscope, gyroscope-uncalibrated,
rotation-vector and game-rotation-vector sensor, built from the accelerometer + magnetometer.
Made for GCam **Photo Sphere** (black screen: `SensorManager: sensor or listener is null`).

* Only sensors the phone does **not** have are added. On a phone with a real gyroscope nothing changes.
* Only apps in the module's scope are touched (default suggestion: `com.samsung.android.scan3d`).
* Java-level hooks on `android.hardware.SensorManager` in the target app. Native (NDK) sensor access is not covered.

## Build (GitHub Actions)

1. Create a new GitHub repository and upload everything in this folder, **including `.github/workflows/build.yml`**
   (the web uploader may skip hidden folders: use `git push`, or create that file with *Add file -> Create new file* and the path `.github/workflows/build.yml`).
2. Open the **Actions** tab -> *Build APK* (it also runs on every push; use *Run workflow* to start it by hand).
3. When it is green, open the run and download the artifact **VirtualGyro-debug-apk** (contains `app-debug.apk`).

## Install / use

1. Install `app-debug.apk`.
2. LSPosed / Vector manager -> *Modules* -> enable **Virtual Gyro**, tick GCam's package in its scope
   (`com.samsung.android.scan3d`, or the package name of the port you use).
3. Force-stop GCam, open it, try Photo Sphere.

## Check that it works

LSPosed/Vector manager -> *Logs* -> *Modules*, or:

    su -c "logcat -d | grep -i virtualgyro"

Expected lines:

    VirtualGyro: installing hooks in com.samsung.android.scan3d
    VirtualGyro: sensor type 4 requested: provided a virtual sensor
    VirtualGyro: listener registered for fake sensor type 4

* `sensor type N requested: not available and not emulated` -> the app asked for a sensor this module does not provide. Tell me the number N.
* No `VirtualGyro` lines at all -> the module is not enabled for that package, or the app was not force-stopped.

## Notes / limits

* A gyroscope derived from accelerometer + compass is noisy and lags a little (~0.1-0.25 s) and is disturbed by nearby metal/magnets.
  Photo Sphere may stitch roughly or refuse; a real gyroscope cannot be replaced.
* If GCam starts using the virtual gyro for HDR+ alignment and photo quality gets worse, remove GCam from the module's scope.
* Tuning lives in `GyroEstimator` (`alphaGravity`, `alphaMagnetic`, `alphaOmega`, `deadband`).

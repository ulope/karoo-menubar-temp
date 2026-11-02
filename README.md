# Karoo Status Bar Temp

A [Hammerhead Karoo](https://www.hammerhead.io/) extension that shows the current air temperature
in the top bar of the ride screen, between the ride time and the battery level:

```
 1:23:45        18°C        87% ▮ 10:42
 └ ride time    └ new       └ battery and clock
```

- **Temperature source:** Karoo's built-in *Temperature* data field. If you have paired a
  temperature sensor, Karoo uses that instead. The built-in sensor sits inside the device, so it
  can read high in direct sun.
- **Units** follow your Karoo profile (°C or °F).
- **Only on the ride screen:** it is hidden on the launcher, in menus, in other apps, and while a
  drawer, the control center or a dialog covers the top bar.
- **Automatic placement:** it is centered between the ride time and the battery level, in the
  same font, size and baseline as the battery level. It follows them as they change width, and
  shrinks slightly if the gap gets narrow (an icon left of the ride time, rides over 10 hours).
- Works on Karoo 2 and Karoo 3.

## Install

1. Download `karoo-statusbar-temp-<version>.apk` from the [latest release](../../releases/latest).
2. Install it:
   - **Karoo 3:** open the release page on your phone, long-press the APK link, choose
     **Share → Hammerhead Companion**, then confirm the install on the Karoo
     ([sideloading guide](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading)).
   - **Karoo 2:** enable USB debugging, connect it to a computer, then run:

     ```bash
     adb install karoo-statusbar-temp-<version>.apk
     ```

## Set up (once)

Karoo OS has no API that lets extensions add items to the status bar. During a ride, the top bar is
drawn by the ride app itself. This extension uses an **accessibility service** to find where the
ride time and battery level sit in that bar, and draws the temperature between them.

1. Open **Status Bar Temp** from the Karoo app list.
2. Tap **Open accessibility settings**, select **Status Bar Temp** and switch it on.
3. Start the ride app. The temperature appears in the top bar.

Android shows a warning that the service can "view and control the screen". The service only reads
the positions of the items in the ride screen's top bar. It never performs actions, and it neither
stores nor sends any data.

If the accessibility settings screen won't open on your Karoo, enable the service over adb
instead. This command **replaces** the list of enabled accessibility services. If you use another
one, such as karoo-phonestatus, append ours with a `:` separator instead.

```bash
adb shell settings put secure enabled_accessibility_services io.github.ulope.karoostatusbartemp/io.github.ulope.karoostatusbartemp.StatusBarTempService
```

## Settings

| Setting                 | Default | Description                                                                           |
|-------------------------|---------|---------------------------------------------------------------------------------------|
| Show temperature        | on      | Turn the temperature on or off without disabling the accessibility service.           |
| Show decimals           | off     | `18°C` or `18.4°C`; the decimal separator follows the device language.               |
| Show unit (C / F)       | off     | `18°` or `18°C`. The top bar has little room, so the unit is off by default.          |
| Text color              | Black   | Black matches the ride screen's light top bar.                                        |
| Size                    | 100 %   | Relative to the battery level text (50–200 %).                                        |
| Horizontal / vertical position | 0 dp | Fine-tune the automatic placement.                                               |

The settings screen shows a preview of the top bar and the live temperature reading.

## Troubleshooting

- **Nothing appears:** check that the settings screen says the accessibility service is on and that
  *Show temperature* is enabled, then open the ride app.
- **Shows `--°C`:** the Karoo isn't currently providing a temperature reading.
- **Slightly off position or size:** use the sliders. The size and baseline were calibrated on a
  Karoo 3. To report a placement problem, open a ride, then go to the settings screen, tap
  **Show diagnostics**, and send a photo of it together with one of the ride screen.
- Logs use the tag `StatusBarTemp`:

  ```bash
  adb logcat -s StatusBarTemp
  ```

## Build

karoo-ext is published on GitHub Packages, which requires authentication even for public packages.
Create a GitHub token with the `read:packages` scope and add it to `local.properties`:

```properties
gpr.user=<your GitHub username>
gpr.key=<token with read:packages>
```

The `GPR_USER` and `GPR_KEY` environment variables work too. Then build and test:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release

Pushing a tag such as `v0.1.0` runs the [build workflow](.github/workflows/build.yml), which
publishes a GitHub release with `karoo-statusbar-temp-<version>.apk` (for example
`karoo-statusbar-temp-0.1.3.apk`) and a `manifest.json`. You can also
start the workflow manually under **Actions → Build → Run workflow**, enter a new tag in
*release_tag*, and it creates that tag at the selected branch's head. The Karoo uses the manifest
to offer updates, and the release's APK link is what you share to the Companion app.

Add the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` to
sign releases with your own key. Without them, releases are signed with a throwaway debug key, and
new versions can't be installed over old ones.

## How it works

- `StatusBarTempService` is the accessibility service. Every second, and whenever windows change,
  it reads the ride app's (`io.hammerhead.rideapp`) window. It picks out the short, labelled
  elements at the top of the screen, takes the end of the ride time and the start of the battery
  level as the gap, and checks that no other window covers it.
- The temperature is drawn in a `TYPE_ACCESSIBILITY_OVERLAY` window, which sits above everything
  and lets touches pass through. Because of this, the "display over other apps" permission isn't
  needed.
- `KarooTemperature` streams `DataType.Type.TEMPERATURE` and the user profile's temperature unit via
  [karoo-ext](https://github.com/hammerheadnav/karoo-ext).

- The text uses Roboto Condensed, the font of the ride screen's battery level. It is bundled, trimmed to digits,
  `°`, separators and `C`/`F`, because the system's condensed font alias doesn't resolve to it on
  every Karoo. It is licensed under the [SIL Open Font License](licenses/RobotoCondensed-OFL.txt).

The status-row approach follows [karoo-phonestatus](https://github.com/alifib/karoo-phonestatus).

This is an independent project, not affiliated with Hammerhead or SRAM. Karoo OS updates that
change the ride app's layout may require adjustments.

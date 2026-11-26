# Space L

Space L is a small, black-and-white in-app panel for Spaceflight Simulator.

## Current scope

This repository currently builds a **UI-only patch**:
- square black controls with white text;
- a floating `Space L` button attached to the Activity decor view using `FrameLayout`;
- Player, Unlocker and System tabs;
- interface-scale setting;
- `SpaceL_Config.json` export to Downloads on Android 10+ (app-specific Downloads directory on older Android);
- link to https://cheatlab.fun.

It does not alter game memory or runtime physics, change damage/fuel state, teleport a craft, or modify Google Play Billing / paid DLC ownership.

## Source APK

The original APK is kept as the `Spaceflight.Simulator.apk` asset in the GitHub Release tagged `apk`; it is not copied into the source tree. The menu CI builds only the original Space L menu DEX and source patch ZIP, not a redistributed copy of the game APK.

## Build in GitHub Actions

Push to `main` or open **Actions → Build Space L menu patch → Run workflow**.

The workflow uses JDK 17, Android platform 35 / build-tools 35.0.0, D8 and Baksmali. Download the `SpaceL-Source-Patch` artifact from the completed run.

## Applying the UI hook locally

1. Decode your own APK with Apktool:
   `apktool d input.apk -o decoded`
2. Copy the generated `smali_classes2` directory from the artifact into `decoded/`.
3. Run:
   `python scripts/patch.py --decoded decoded`
4. Rebuild with Apktool:
   `apktool b decoded -o SpaceL-unsigned.apk`
5. Sign the result with your own signing key.

The patcher fails closed if it cannot find exactly one matching `ExtendedUnityActivity` or the expected superclass `onCreate(Bundle)` call.

## Compatibility

The requested Activity name and package name are treated as expected inputs, not verified facts about every game version. A successful DEX compilation does not prove that the Activity hook is compatible with a particular APK. Check the exact APK version and test on a spare device.

# Space L

Space L is a black-and-white in-app panel for Spaceflight Simulator.

## Current scope

This repository builds a UI-only patch:
- square black controls with white text;
- a floating Space L button attached to the Activity decor view with FrameLayout;
- Player, Unlocker and System tabs;
- an interface-scale slider;
- SpaceL_Config.json export to Downloads on Android 10+ (app-specific Downloads directory on older Android);
- a link to https://cheatlab.fun.

The current build does not alter game memory or runtime physics, change fuel or damage state, teleport a craft, or modify Google Play Billing / paid DLC ownership.

## Original APK

The original Spaceflight.Simulator.apk is kept as the asset in the GitHub Release tagged apk. CI downloads it temporarily, verifies its SHA-256, decodes it with Apktool and creates a metadata-only compatibility report. The original APK and rebuilt game APK are not included in the source artifact.

## GitHub Actions

Push to main or open Actions → Build Space L menu patch → Run workflow.

The workflow:
1. sets up JDK 17 and Android platform 35 / build-tools 35.0.0;
2. downloads Apktool 2.11.1 and the original release APK;
3. verifies the APK hash and inspects its manifest, DEX layout and requested Activity;
4. validates the Activity hook;
5. compiles the Java menu to DEX;
6. tests an Apktool rebuild and insertion of the menu DEX into a temporary APK;
7. uploads only SpaceL_Source_Patch.zip and the analysis report as an Actions artifact.

## Applying the UI patch locally

1. Download and extract the SpaceL-Source-Patch artifact from the completed Actions run.
2. Decode your APK with Apktool:
   apktool d -f input.apk -o decoded
3. Inject the Activity hook:
   python scripts/patch.py --decoded decoded
4. Rebuild the decoded package:
   apktool b decoded -o SpaceL-unsigned.apk
5. Append the compiled menu DEX:
   python scripts/patch.py --apk SpaceL-unsigned.apk --dex SpaceL_Menu.dex --output-apk SpaceL-with-menu-unsigned.apk
6. Sign SpaceL-with-menu-unsigned.apk with your own signing key. Signing must happen after DEX insertion.

The patcher fails closed if it cannot find exactly one matching ExtendedUnityActivity or the expected superclass onCreate(Bundle) call.

## Compatibility

The Activity and package names provided in the project brief are treated as expected inputs, not assumed facts about every game version. The CI report checks the actual release APK. A successful DEX build still does not prove correct behaviour on a physical device; runtime testing is still required.

# L.L.E Companion compatibility contract

These rules are mandatory for every agent or developer preparing an L.L.E build or public release.

Before changing version numbers, package names, signing configuration, build scripts, GitHub tags, release assets, or `LLE_VERSION.txt`, read and follow this document completely.

## Companion contract

The public L.L.E Companion recognizes only the stable ARM64 application:

- Stable package: `com.codex.lle64`
- Private tester package: `com.codex.lle64.test`
- Companion package: `com.codex.lle.companion`

The tester package is intentionally ignored by Companion. Tester builds must never be announced through `LLE_VERSION.txt` and must never replace the stable GitHub release.

The source package in `LLEUnified/AndroidManifest.xml` remains `com.codex.lle`. The ARM64 build script is responsible for generating the final stable package `com.codex.lle64`.

Do not globally rename the Java source package or JNI package paths.

## Stable version format

Every public stable version must contain exactly four numeric components:

`MAJOR.MINOR.PATCH.REVISION`

Valid examples:

- `1.0.5.3`
- `1.0.5.4`
- `1.1.0.0`

Invalid examples:

- `1.0.5`
- `1.0.5.4-beta`
- `v1.0.5.4` inside the Android manifest
- `1.0.5.4.1`

For the next stable release, update both fields in:

`LLEUnified/AndroidManifest.xml`

Requirements:

- `android:versionName` must use the exact four-part public version.
- `android:versionCode` must be a positive integer greater than every previously published stable build.
- Never reuse or decrease a stable `versionCode`.
- Never publish a tester suffix in the stable `versionName`.

Example:

```xml
android:versionCode="26"
android:versionName="1.0.5.4"


GitHub release contract
For version X.Y.Z.W, the public GitHub tag must be exactly:
vX.Y.Z.W
Example:
v1.0.5.4
The GitHub release page must therefore exist at:
https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/releases/tag/v1.0.5.4
The ARM64 release asset should be named:
LLE64-X.Y.Z.W-64-bit.apk
Example:
LLE64-1.0.5.4-64-bit.apk
The following values must all represent the same release:
Android versionName
GitHub tag without its leading v
GitHub release title/version
APK filename version
root LLE_VERSION.txt
Do not rename or replace an already published version with different APK contents. Publish a new, higher version instead.
LLE_VERSION.txt
LLE_VERSION.txt is the version advertised to Companion.
It must:
exist at the repository root
contain exactly one four-part stable version
contain no v prefix
contain no comments, spaces, labels, beta suffixes, or Markdown
be encoded as ASCII or UTF-8 without BOM
Valid content:
1.0.5.4
Never put a tester version in this file.
The currently built development Companion reads:
https://raw.githubusercontent.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/codex/lle-unified/LLE_VERSION.txt
Before publishing Companion on Google Play, its production URL should be changed to:
https://raw.githubusercontent.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/main/LLE_VERSION.txt
Always update the file on the branch actually referenced by the shipped Companion. Do not assume that changing main is sufficient until the Companion has been rebuilt with the main URL.
Mandatory publishing order
Use this exact order:
Choose the new four-part version.
Increase android:versionCode.
Set the matching four-part android:versionName.
Build the stable signed ARM64 APK.
Verify package, version, signature, installation and basic functionality.
Commit the release source.
Create the exact Git tag vX.Y.Z.W.
Create the public GitHub release from that tag.
Upload LLE64-X.Y.Z.W-64-bit.apk.
Confirm that the release page and APK asset are publicly accessible.
Only then update and push LLE_VERSION.txt.
Updating LLE_VERSION.txt before the release page exists is forbidden because Companion would announce an update whose download page is missing.

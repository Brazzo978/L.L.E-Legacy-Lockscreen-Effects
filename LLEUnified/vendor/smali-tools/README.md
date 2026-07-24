# Smali build tools

These JARs are the pinned build-time dependencies used to patch and reassemble
the bounded Samsung DEX payload:

- smali, baksmali and dexlib2 2.5.2;
- antlr-runtime 3.5.2;
- Guava 27.1-android and failureaccess 1.0.1;
- JCommander 1.64;
- smali util 2.5.2.

They are included so a source checkout does not depend on an untracked local
tool directory or a network download during release builds. They are build
tools only and are not packaged into either APK.

Upstream projects and license texts:

- <https://github.com/JesusFreke/smali>
- <https://github.com/antlr/antlr3>
- <https://github.com/google/guava>
- <https://github.com/cbeust/jcommander>

Rights in these dependencies remain with their respective authors and are
governed by their upstream licenses.

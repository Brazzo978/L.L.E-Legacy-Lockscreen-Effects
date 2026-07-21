# Original Samsung native inputs

These ARMv7 libraries are retained as byte-identical patch inputs so LLE can be
built from a clean checkout. The build never packages these opaque originals
directly: the scripts in `../native-patches` validate their expected hashes and
write transparent-composition variants into the temporary build directory.

- `libWaterRipple.so`: Galaxy S3 Water Ripple renderer.
- `libsecveAbstractTile.so`: Note 4/S4-era Abstract Tiles renderer.
- `libsecveBrilliantCut.so`: canonical S4/Note 4 Brilliant Cut renderer
  (`46B7580078F373CD5129704B8294AD1B630665F27E6877A8ECB30A41BDF039C7`).
- `libsecveGeometricMosaic.so`: Note 4 Geometric Mosaic renderer.

They are proprietary Samsung firmware components and remain subject to their
original licensing terms.

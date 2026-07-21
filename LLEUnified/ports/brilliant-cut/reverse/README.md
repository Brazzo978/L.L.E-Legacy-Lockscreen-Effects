# Brilliant Cut ARM32 reverse evidence

The canonical binary staged by build-arm32.ps1 is retained byte-for-byte at
vendor/original-native/libsecveBrilliantCut.so.

- Firmware source:
  unlock-effects-test/extracted/s4_system_files/lib/libsecveBrilliantCut.so
- Size: 226,520 bytes
- SHA-256:
  46B7580078F373CD5129704B8294AD1B630665F27E6877A8ECB30A41BDF039C7
- Patched build artifact SHA-256:
  BE85417DF8173827312FA5153B0BD13698DCAAAB579C16608D7E7B5281A2FB15

## Runtime dependencies

ELF DT_NEEDED contains libsecveSrkCommon.so and libstlport.so in addition to
Android system libraries. The checked-in ARM32 inputs are the byte-identical
S4 companions:

- libsecveSrkCommon.so:
  5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36
- libstlport.so:
  B7B845F6E446E87878152D25D6DDE9657B5260B9DC47A540C87D2F6A67A97E09

libsecveSrkCommon.so does not contain Brilliant Cut's composite shaders. The
effect library owns both final fragment shaders; the common library provides
the shared Samsung runtime and remains ABI-compatible after the unrelated
Watercolor/Brilliant Ring shader patches.

## Transparent composite patch

The original two final shaders are preserved inside the canonical binary:

| Shader | File offset | Length | Stock SHA-256 | Patched SHA-256 |
| --- | ---: | ---: | --- | --- |
| Standard/per-plane | 0x34AF8 | 908 | BE42521A0E96507924F8658A0E23B9A34EF7B6D089339898FE909E79E13B47FA | 57FEC04AB7E3F34B1D385F17AAAFE981A81E160AA44AB4C7EC3DA79F2588BDB4 |
| Alpha-UV | 0x35418 | 563 | 08AA2EE0FD1681FD42C0A827A9BACEDD45C2E46218C78FCE2BF71ED7257D24CB | F24440676716ABFF8CC3CF7E9BF3B597DF602EAA6A2FC471CB4D0F07B5E8B7AD |

For native coverage m, the stock opaque target is F = C + mG, where C is the
displaced screenshot sample, G is glare, and B is the undisplaced screenshot
already visible below LLE. Samsung enables GL_SRC_ALPHA,
GL_ONE_MINUS_SRC_ALPHA while LLE starts from transparent. The patch emits
q = sqrt(m) and RGB P/q, where P = C - (1-m)B + mG. The first blend stores
premultiplied P with alpha m; SurfaceFlinger then adds (1-m)B, reconstructing
stock F. Coverage zero writes transparent black.

The patch script validates the canonical library, both stock shader slices,
both patched slices, and the complete patched-library hash before writing an
output. No CPU simulation, geometry, timing, texture binding, or ARM code is
changed.

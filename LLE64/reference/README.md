# Native reference policy

Questa cartella non è mai aggiunta all'APK.

- `arm32-original`: copie byte-for-byte delle librerie usate dall'app legacy, mantenute per reverse engineering e confronto.
- `arm64-candidates/note5-aoj4`: librerie Samsung originali AArch64 da sottoporre ad audit `ELF`, `DT_NEEDED`, JNI e test sul Fold7 prima di abilitarle.

Una libreria passa da `reference` al packaging solo dopo che dipendenze, registrazione JNI, classi Java, shader e comportamento trasparente sono stati verificati.

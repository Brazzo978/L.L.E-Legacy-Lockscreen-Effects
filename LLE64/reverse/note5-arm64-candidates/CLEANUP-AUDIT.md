# Audit cleanup renderer Note5 ARM64

Data: 2026-07-14. Analisi statica, senza modifiche al codice dell'app o al dex Samsung.

## Conclusione

Il renderer Colour Droplet e il suo percorso GLES/JNI sono confermati funzionanti. Il mancato log finale del probe, da solo, **non dimostra ancora** che il blocco avvenga precisamente in `requestExitAndWait()`: `Note5NativeProbeActivity.destroyRenderer()` non scrive un log prima di `renderer.destroy()`, e i wrapper eseguono più operazioni sincrone prima del primo log di cleanup.

Esiste però un difetto di lifecycle certo nel codice Samsung incluso in `classes2.dex`: sia `GLThread.onPause()` sia `GLThread.requestExitAndWait()` attendono senza timeout. Non è quindi production-safe invocarli sul main thread di Android.

## Sequenza attuale e punti di blocco

I due wrapper hanno sostanzialmente la stessa sequenza:

1. `resetEffect()`;
2. accodamento del comando screen-off;
3. `destroyed = true` e rilascio audio;
4. `SamsungGlTextureShutdown.shutdown(...)`;
5. `EffectView.removeEffect()`;
6. rimozione delle view e rilascio dei bitmap/riferimenti.

Riferimenti:

- `LLE64/src/com/codex/lle/ColourDropletEffectView.java:305-334`;
- `LLE64/src/com/codex/lle/SparklingBubblesEffectView.java:309-337`;
- `LLE64/src/com/codex/lle/SamsungGlTextureShutdown.java:18-25`.

Il helper chiama prima `GLTextureView.onPause()` e poi, via reflection, `GLThread.requestExitAndWait()`. Entrambi sono sincroni.

Prima ancora del helper, `clearScreen()` e screen-off non eseguono direttamente il lavoro nativo: accodano runnable sulla GL thread. La GL thread Samsung dà precedenza alla sua `mEventQueue` prima di applicare la transizione pause. I runnable arrivano poi a `clearEffect()`/`screenTurnedOff()` e quindi alle chiamate JNI `onKeyEvent(...)`. Se uno di questi callback nativi non ritorna, `onPause()` resta in attesa e il main thread sembra bloccato prima di `requestExitAndWait()`.

## Perché le attese possono non terminare

Nel dex AOJ4 estratto:

- `GLThread.onPause()` imposta `mRequestPaused = true`, fa `notifyAll()` e attende finché `mPaused` o `mExited` non diventano true;
- `GLThread.requestExitAndWait()` imposta `mShouldExit = true`, fa `notifyAll()` e attende finché `mExited` non diventa true;
- nessuno dei due usa una deadline o un timeout;
- in caso di `InterruptedException`, il metodo reimposta l'interrupt e torna nel loop. Un successivo `wait()` può fallire immediatamente ancora, creando un loop invece di sbloccare il chiamante.

Riferimenti esatti:

- `unlock-effects-test/extracted/note5_aoj4_secvisualeffect_smali/com/samsung/android/visualeffect/common/GLTextureView$GLThread.smali`, `onPause()` righe 1677-1763 e `requestExitAndWait()` righe 2040-2122;
- `guardedRun()` righe 157-1511.

Quando vede `mShouldExit`, `guardedRun()` chiama inoltre `Renderer.onDestroy()` mentre possiede il monitor globale `sGLThreadManager`, poi distrugge surface/context EGL. Qualunque callback renderer/JNI o driver EGL che non ritorni impedisce `threadExiting()`; di conseguenza `mExited` non viene impostato e il main thread aspetta per sempre.

Per Droplet e Bubbles il renderer concreto eredita `SPhysicsRenderer_TV.onDestroy()`. Il metodo è normalmente breve, ma può chiamare `DeInit_PhysicsEngineJNI()` in uno dei rami. Il rischio non è quindi soltanto teorico, anche se il log attuale non consente di attribuire il blocco a quella specifica JNI.

## Doppio shutdown

`EffectView.removeEffect()` esegue `removeAllViews()`. Il detach del `GLTextureView` Samsung richiama a sua volta `GLThread.requestExitAndWait()` in `GLTextureView.onDetachedFromWindow()`.

La sequenza corrente esegue quindi:

1. `requestExitAndWait()` esplicito nel helper;
2. un secondo `requestExitAndWait()` durante `removeEffect()`/detach.

Se il primo è completato, il secondo ritorna subito; se si adotta solo un workaround asincrono esterno e il thread GL non è uscito, il secondo può comunque bloccare il main thread. Per questo spostare semplicemente `renderer.destroy()` o il solo helper su un thread worker non è una soluzione corretta: inoltre `removeEffect()` e `removeAllViews()` devono restare sul thread UI.

## Diagnostica minima consigliata

Prima del prossimo test di cleanup, aggiungere log `BEGIN`/`END` attorno a ciascuna fase:

- ingresso in `Note5NativeProbeActivity.destroyRenderer()`;
- `resetEffect()`;
- screen-off;
- `GLTextureView.onPause()`;
- `GLThread.requestExitAndWait()`;
- `EffectView.removeEffect()`;
- rimozione finale delle view.

Prima delle due attese, leggere e loggare via reflection:

- stato Java della GL thread (`getState()` e `getStackTrace()`);
- `mRequestPaused`, `mPaused`, `mShouldExit`, `mExited`;
- dimensione di `mEventQueue`.

Questo distingue rapidamente tra callback nativa accodata, pause, renderer `onDestroy()` e teardown EGL. Il log deve precedere la chiamata: oggi il helper scrive solo dopo il ritorno, quindi il punto esatto resta invisibile.

## Correzione production-safe minima

Soluzione preferita, dato che LLE64 include già un `vendor/secvisualeffect/classes.dex` controllato dal progetto:

1. non chiamare `GLTextureView.onPause()` durante il destroy finale; `mShouldExit` è sufficiente e la pause aggiunge una prima attesa non necessaria;
2. patchare nel dex Samsung `GLThread.requestExitAndWait()` con una deadline breve (indicativamente 1500-2000 ms), usando `wait(remainingMs)`;
3. su interrupt, preservare il flag di interrupt e **uscire** dal metodo invece di tornare nel loop;
4. al timeout lasciare `mShouldExit = true`, scrivere thread state/stack e ritornare, così il teardown UI non può produrre un ANR permanente;
5. eliminare la chiamata esplicita duplicata del helper nel percorso normale e lasciare che `EffectView.removeEffect()` provochi il singolo exit tramite `GLTextureView.onDetachedFromWindow()`;
6. applicare la stessa politica bounded a `GLThread.onPause()` se quel metodo resta raggiungibile da altri lifecycle dell'app.

Questa patch conserva il teardown sul GL thread e l'accesso alle View sul main thread. In caso anomalo può lasciare per breve tempo un GL thread/EGL context in uscita, ma evita un blocco illimitato dell'interfaccia; `mShouldExit` resta impostato e il thread può terminare appena il callback o il driver torna.

### Fallback solo Java

Se non si vuole ricostruire subito `classes2.dex`, un fallback diagnostico può eseguire l'attesa su un thread daemon e applicare un watchdog. Non va considerato il fix finale: il detach UI invoca comunque il metodo Samsung non bounded, a meno di manipolare `mGLThread` via reflection, soluzione fragile che può lasciare thread/EGL orfani.

## Correzione e verifica dinamica

La correzione e stata applicata il 2026-07-14 tramite
`vendor/secvisualeffect/patch-note5-lifecycle.ps1`, lasciando intatto il dex
Samsung originale. Il probe Note 5 usa un dex ricostruito con attese bounded a
2000 ms e i wrapper eseguono una sola richiesta di exit tramite detach.

Sul Fold7 entrambi i renderer hanno completato `reset`, screen-off,
`DeInit_PhysicsEngineJNI`, detach e `destroy()` senza usare il timeout e senza
crash. La diagnosi iniziale e quindi risolta per un ciclo completo per effetto;
restano da stress-testare ricreazioni ripetute e context loss.

## Decisione

Conviene correggere il lifecycle adesso, prima di rendere Droplet/Bubbles selezionabili nella build stabile. Il fix non mette in discussione la compatibilità ARM64 già dimostrata: separa il renderer funzionante da un teardown legacy che su Android moderno può causare ANR.

# Offtrack – tracker corsa/bici privato (Android) – Piano

> L'utente ha saltato il brainstorming ("sai cosa fare, procedi"): le scelte qui sotto sono mie.

**Goal:** app Android nativa che registra corse e uscite in bici e mostra percorso su mappa, distanza, tempo e media, senza account né server.

**Architecture:** tre Activity con View di piattaforma (nessun AndroidX), un foreground service che legge il GPS con `LocationManager` (niente Google Play Services) e scrive ogni punto subito su file CSV nella memoria interna dell'app. La mappa (osmdroid, tile OpenStreetMap) si carica solo nel dettaglio di un'attività già finita: è l'unico traffico di rete.

**Tech Stack:** Kotlin 2.2.21, AGP 8.13.2, Gradle 8.14.3, minSdk 26, compileSdk 36, targetSdk 36, osmdroid 6.1.20, JUnit 4.13.2. CI: GitHub Actions.

**Spec:** la conversazione (nessun documento).

## Global Constraints
- Nessun dato esce dal telefono: niente analytics, niente Play Services, `android:allowBackup="false"`.
- La rete serve solo per i tile della mappa, e solo nella schermata di dettaglio.
- Ogni punto viene scritto su disco appena arriva: se l'app crasha non si perde l'uscita.
- UI in italiano.
- targetSdk 36 (il lint release blocca target vecchi): tema senza ActionBar + `fitsSystemWindows` per l'edge-to-edge.
- Build solo in CI (in locale non c'è un JDK): `gradle testDebugUnitTest assembleRelease`.

## Review Focus
- Ultima riga del CSV troncata da un crash → la si scarta e il resto si legge. Test `parseSkipsBrokenLines`.
- GPS fermo che oscilla di pochi metri → la distanza non deve crescere. Test `jitterDoesNotAddDistance`.
- Pausa: il tempo e la distanza durante la pausa non contano, e i punti prima e dopo non si collegano. Test `pauseExcludedFromTimeAndDistance`.
- Uscita senza `stop` (crash) → il tempo finisce all'ultimo evento. Test `missingStopEndsAtLastEvent`.
- Stop prima del primo fix GPS → il file vuoto viene cancellato, non compare nella lista.

---

## Formato file
`filesDir/tracks/<startMillis>_<RUN|BIKE>.csv`, una riga per evento:
```
start,<t>
<lat>,<lon>,<t>
pause,<t>
resume,<t>
stop,<t>
```

### Task 1: Modello e statistiche (`Track.kt`, `Format.kt`) + test
- `sealed interface Event`; `data class Fix(lat: Double, lon: Double, t: Long)`; `data class Mark(kind: String, t: Long)`.
- `object Track`: `parse(lines: List<String>): List<Event>`, `line(e: Event): String`, `segments(events): List<List<Fix>>` (spezzati dalle pause), `distanceM(events): Double` (haversine con un punto di riferimento: si somma solo uno spostamento ≥ 3 m), `activeMs(events, now: Long?): Long`.
- `Format`: `distance(m)` → "5.23 km", `duration(ms)` → "28:14" / "1:02:03", `pace(m, ms)` → "5:24 /km" ("--" sotto i 10 m), `speed(m, ms)` → "23.4 km/h".
- Test: `TrackTest` (i quattro della Review Focus + haversine di circa 111 km per 1° di latitudine), `FormatTest`.

### Task 2: Archivio e servizio GPS (`TrackStore.kt`, `TrackingService.kt`)
- `TrackStore`: `dir`, `list()` in ordine decrescente, `read(file)`, `create(type)`, `append(file, e)`, `isRun(file)`.
- `TrackingService`: foreground di tipo `location`, GPS ogni 1 s, scarta i fix con accuratezza oltre 20 m, azioni START/PAUSE/RESUME/STOP. Lo stato live sta in un companion (`file`, `events`, `paused`). Allo STOP, se non ci sono fix, cancella il file.

### Task 3: Schermate (`MainActivity`, `RecordActivity`, `DetailActivity`) + manifest
- Main: pulsanti Corsa/Bici, permessi (posizione e notifiche), controllo che il GPS sia acceso, lista delle uscite. Se una registrazione è in corso, mostra il pulsante "Torna alla registrazione".
- Record: tempo, distanza e media aggiornati ogni secondo; pulsanti Pausa/Riprendi e Stop (con conferma), poi apre il dettaglio.
- Detail: mappa osmdroid con una polilinea per segmento, statistiche, Elimina (con conferma). La cache dei tile sta in `cacheDir`.

### Task 4: CI e firma
- `.github/workflows/android.yml`: JDK 17, Gradle 8.14.3, test + `assembleRelease`, l'APK come artifact.
- Firma: se ci sono i secret `BADA_KEYSTORE_B64`/`BADA_KEYSTORE_PASSWORD` usa quella chiave, altrimenti la chiave debug. Senza una chiave fissa ogni build ha una firma diversa e per aggiornare l'app bisogna disinstallarla, perdendo i dati.

### Task 5: Logo con la skill `logo-designer` → icona adattiva vettoriale.

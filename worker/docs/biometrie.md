# KYC Biometrie

Biometrická část obsahuje úlohy `compare_faces` a `liveness_check`.

## `compare_faces`
### Účel
Ověřit, zda selfie odpovídá portrétu z dokladu.

### Vstup (`payload`)
- `documentPath` (povinně),
- `selfiePath` (povinně).

### Výstup
`FaceComparisonResult`:
- `match` (boolean),
- `confidence` (0-1),
- `cosine_similarity`,
- `threshold`,
- `reason`,
- příznaky detekce obličeje na obou obrázcích.

### Algoritmus
1. Zajištění modelů YuNet a SFace (automatické stažení při prvním použití).
2. Detekce obličeje na obou obrazech (YuNet), volba nejkvalitnější detekce.
3. Zarovnání a ořez obličeje na standardní vstup recognizeru (SFace).
4. Výpočet embeddingů a kosinové podobnosti.
5. Rozhodnutí `match` podle prahu `COSINE_THRESHOLD = 0.363`.
6. Přepočet kosinového skóre na normalizované `confidence`.

### Chybové stavy na úrovni workeru
- `MISSING_DOCUMENT_PATH`: chybí `payload.documentPath`.
- `MISSING_SELFIE_PATH`: chybí `payload.selfiePath`.

## `liveness_check`
### Účel
Rozhodnout, zda sada snímků reprezentuje živou osobu na základě změn orientace hlavy.

### Vstup (`payload`)
- `imagePaths` (povinně): seznam snímků (doporučeně minimálně 4).

### Výstup
`LivenessResult`:
- `is_alive`, `confidence`, `reason`,
- souhrnné metriky (`yaw_range_deg`, `pitch_range_deg`, směrové zóny),
- per-image diagnostika (`FacePoseResult`).

### Algoritmus
1. Zajištění modelu MediaPipe Face Landmarker.
2. Pro každý snímek:
- detekce obličeje,
- odhad yaw/pitch/roll (preferenčně z transformační matice, fallback přes `solvePnP`),
- klasifikace směru (`left|right|up|down|center`).
3. Agregace nad detekovanými snímky.
4. Rozhodnutí `is_alive` podle prahů:
- minimálně 3 snímky s detekovaným obličejem,
- minimální yaw rozptyl 25°,
- alespoň 2 různé směrové zóny.
5. Výpočet `confidence` jako vážená kombinace yaw/pitch rozptylu a diverzity směrů.

### Chybové stavy na úrovni workeru
- `MISSING_IMAGE_PATHS`: chybí nebo je prázdné `payload.imagePaths`.

# Zpracování dokladů

Tato část popisuje proces práce s doklady v systému (občanský průkaz i pas) a jeho aktuální napojení na úlohy workeru.

## Podporované typy dokladů
- Občanský průkaz (TD1, CZE): zpracovaný v úloze `verify_czech_id`.
- Cestovní pas (TD3): zpracovaný v úloze `verify_passport`.

## Společný proces pro občanku i pas
### 1. Vstupní obraz dokladu
- Systém očekává obrázek strany obsahující MRZ.
- U občanského průkazu je to typicky zadní strana (`backImagePath`).
- U pasu je to datová strana s dvouřádkovým MRZ blokem.

### 2. Vícefázové čtení MRZ (`read_mrz_enhanced`)
MRZ je čteno více strategiemi; vybrána je varianta s nejvyšším `valid_score`:
1. PassportEye default.
2. PassportEye s `--oem 0`.
3. PassportEye nad předzpracovaným obrazem.
4. Přímý OCR spodní části dokumentu.
5. Re-OCR ROI oblasti po řádcích.

Poznámka: pipeline výslovně počítá s oběma MRZ formáty:
- TD1 (občanka) = 3 řádky,
- TD3 (pas) = 2 řádky.

### 3. Normalizace a validace MRZ
- Probíhá normalizace OCR výstupu a parsování do MRZ struktury.
- Výstup obsahuje standardní MRZ pole (jméno, příjmení, číslo dokladu, datum narození, expirace, státní kód, validace checksumů).

## Občanský průkaz (`verify_czech_id`)
### Vstup (`payload`)
- `backImagePath` (povinně): zadní strana občanského průkazu s MRZ,
- `frontImagePath` (volitelně): přední strana pro rozšíření OCR dat.

### Zpracování
1. Načte se MRZ přes společnou pipeline.
2. OCR se přečtou údaje z obou stran karty (`read_czech_id_face`).
3. Pole se sloučí (`_merge_face_fields`) s prioritou:
- adresa: zadní strana,
- místo narození: přední strana,
- rodné číslo: preferenčně zadní strana,
- čárové kódy: sjednocení + deduplikace.
4. Validuje se, že dokument je TD1 a země `CZE`.
5. Vypočte se `confidence` (`high|medium|low`) podle checksumů MRZ.

### Výstup
`CzechIDResult`:
- jméno/příjmení, datum narození, pohlaví, expirace, číslo dokladu,
- rodné číslo (je-li dostupné), místo narození, adresa,
- úroveň spolehlivosti + poznámky,
- dekódované čárové kódy,
- surový MRZ slovník (`raw`).

Typické kódy chyb:
- `MISSING_BACK_IMAGE_PATH`
- `IMAGE_READ_ERROR`
- `MRZ_NOT_FOUND`
- `INVALID_MRZ_TYPE`
- `INVALID_DOCUMENT_COUNTRY`

## Pas (`verify_passport`)
### Vstup (`payload`)
- `imagePath` (povinně): datová stránka pasu s MRZ.

### Zpracování
1. Společná MRZ pipeline (`read_mrz_enhanced`) detekuje a parsuje MRZ.
2. Parser `parse_passport` kontroluje typ dokumentu `TD3`; odchylka vyvolá `INVALID_MRZ_TYPE`.
3. Extrahují se identifikační údaje a geografická pole:
- `surname`, `given_names`, `date_of_birth`, `sex`,
- `expiration_date`, `document_number`,
- `issuing_country`, `nationality`,
- `personal_number` (volitelné pole TD3, po ořezu výplňových znaků `<`).
4. Spolehlivost je určena checksumy (`valid_number`, `valid_date_of_birth`, `valid_expiration_date`, `valid_personal_number`, `valid_composite`) do tříd `high|medium|low`.

### Výstup
`PassportResult`:
- základní identifikační pole z MRZ,
- `issuing_country`, `nationality`,
- volitelné `personal_number`,
- `confidence`, `confidence_notes`,
- `raw` MRZ slovník.

Typické kódy chyb:
- `MISSING_IMAGE_PATH`
- `IMAGE_READ_ERROR`
- `MRZ_NOT_FOUND`
- `INVALID_MRZ_TYPE`

## Poznámka k moderním OP
U českých OP může být composite checksum nevalidní i při správně přečteném dokladu (prázdné optional pole + OCR šum). Proto se při vyhodnocení spolehlivosti zohledňují zvlášť základní checksumy a composite checksum.

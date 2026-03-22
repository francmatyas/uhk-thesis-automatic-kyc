# AML screening

Tato část odpovídá úloze `aml_screen`.

## Účel
Prověřit jméno (a volitelně datum narození) proti PEP a sankčním seznamům.

## Vstup (`payload`)
- `fullName` (povinně),
- `dob` (volitelně).

Pokud `fullName` chybí, worker vrací chybu `MISSING_FULL_NAME`.
Pokud databáze neexistuje na zadané cestě, screener vrací `AML_DB_NOT_FOUND`.

## Výstup
- `hits`: seznam zásahů (`AmlHit`),
- `hitCount`: počet zásahů.

## Algoritmus
1. Otevření SQLite FTS5 databáze `data/aml.db` (read-only režim).
2. Normalizace vstupního jména (NFKD, odstranění diakritiky/interpunkce, lowercase).
3. Kandidátní výběr přes FTS5 `MATCH` výraz složený z tokenů jména (OR logika).
4. Fuzzy skórování kandidátů (`rapidfuzz.token_sort_ratio`) nad aliasy.
5. Filtrace pod prahem (výchozí `80`).
6. Volitelný bonus za shodu číslic data narození (`+5`, max 100).
7. Seřazení podle skóre, limitace výstupu (`max_hits`, výchozí 20).

## Datové zdroje AML
Screening předpokládá existenci databáze `data/aml.db`, která je buildována skriptem `src/aml/build_db.py` z:
- `data/peps.csv`,
- `data/sanctions.csv`.

Build skript:
- normalizuje jména stejnou metodikou jako runtime screener,
- ukládá originální i normalizovanou podobu jmen,
- vytváří FTS5 index nad sloupcem `search_text`,
- provádí optimalizaci indexu po ingestu.

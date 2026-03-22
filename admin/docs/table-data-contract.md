# Datový kontrakt tabulkové vrstvy (frontend <-> API)

## 1. Účel dokumentu

Tento dokument formalizuje datový kontrakt generické tabulkové vrstvy používané ve frontendové aplikaci. Popisuje:

- vstupní parametry pro API volání,
- očekávanou strukturu odpovědi,
- transformace dat před vykreslením,
- vazbu mezi URL stavem a stavem tabulky,
- známá omezení aktuální implementace.

## 2. Rozsah a referenční implementace

Kontrakt je odvozen z těchto souborů:

- `src/components/table/Table.jsx`
- `src/components/table/TableWrapper.jsx`
- `src/components/table/DataTable.jsx`
- `src/components/table/processTableData.jsx`
- `src/components/table/SubPanel.jsx`
- `src/components/table/tableSizing.js`
- `src/api/table.js`
- `src/views/shared/ResourceTablePage.jsx`

## 3. Architektonický přehled

Tabulková vrstva je tvořena třemi hlavními komponentami:

1. `Table`:
   - vypočítává `pageSize` podle dostupné výšky viewportu,
   - předává parametry do `TableWrapper`.
2. `TableWrapper`:
   - drží stav filtru, řazení a stránkování,
   - synchronizuje stav s URL query parametry,
   - provádí API dotaz přes React Query.
3. `DataTable`:
   - inicializuje `@tanstack/react-table` v manuálním režimu,
   - renderuje hlavičku, řádky, vyhledávání a paginaci,
   - zpracovává chybové a prázdné stavy.

Pro detailní transformaci sloupců/sloupcových typů se používá `processTableData`.

## 4. Parametry requestu

### 4.1 Standardní tabulka (`fetchPage`)

Volání je realizováno přes `src/api/table.js`.

Vstupní parametry:

- `module`: název modulu (použije se pro fallback endpoint)
- `basePath`: explicitní endpoint; pokud není uveden, použije se `/${module}`
- `pageIndex`: index stránky (0-based)
- `pageSize`: počet položek na stránku
- `globalFilter`: textový filtr
- `sorting`: pole sort descriptorů (frontend používá první prvek)

Přenášené query parametry:

- `page`: hodnota `pageIndex`
- `size`: hodnota `pageSize`
- `q`: pouze pokud je `globalFilter` neprázdný
- `sort`: `sorting[0].id` (pokud je řazení definováno)
- `dir`: `asc` nebo `desc` podle `sorting[0].desc`

### 4.2 Subpanel (`fetchSubPanel`)

Endpoint je odvozen jako:

- `/${module}/${parentId}/items`

Používají se stejné query parametry jako u standardní tabulky (`page`, `size`, `q`, `sort`, `dir`).

## 5. Očekávaná struktura API odpovědi

`DataTable` očekává objekt minimálně v následujícím tvaru:

```json
{
  "columns": [],
  "rows": [],
  "totalPages": 0
}
```

### 5.1 Pole `columns`

Každý sloupec může obsahovat zejména:

- `id`
- `accessorKey`
- `header`
- `type`
- `sortable`
- `filterable`
- `hidden`
- `meta.width` nebo `width`

Pro specifické typy se používají rozšiřující pole:

- `REFERENCE`: `referenceTemplate`, případně `referenceKey`
- `ENUM`: `enumValues` nebo `options`
- `ENUM`: volitelně `displayMode`, `enumDisplayMode`, `badgeVariant`, `badgeClassName`, `translate`, `translateMap`

### 5.2 Pole `rows`

Každý řádek musí mít jednoznačný identifikátor `id`, protože `DataTable` používá:

- `getRowId: (row) => row.id`

Bez `id` není garantována správná funkce výběru řádků a stabilita renderu.

### 5.3 Pole `totalPages`

- Používá se pro řízení pagination komponenty.
- Paginace se renderuje pouze pokud `totalPages > 1`.

## 6. URL stav a synchronizace

`TableWrapper` synchronizuje stav tabulky s URL pouze mimo režim subpanelu (`isSubPanel === false`).

Používané query parametry:

- `q`: globální filtr
- `sorting`: JSON serializované pole sort descriptorů
- `page`: index stránky (0-based)

Pravidla:

- změna filtru i řazení vždy resetuje stránku na `0` a odstraňuje `page` z URL,
- pokud je `pageIndex === 0`, parametr `page` se z URL odstraňuje,
- při změně URL se interní state (`globalFilter`, `sorting`, `pagination`) rehydratuje z query parametrů.

## 7. Režim řazení, filtrování a stránkování

`DataTable` používá `@tanstack/react-table` v manuálním režimu:

- `manualSorting: true`
- `manualFiltering: true`
- `manualPagination: true`

To znamená, že server je zodpovědný za:

- aplikaci filtru,
- aplikaci řazení,
- stránkování výsledků.

Frontend pouze předává parametry a renderuje přijatá data.

## 8. Typové transformace ve `processTableData`

Podporované typy sloupců:

- `CURRENCY`: formátování `cs-CZ`, měna `CZK`
- `NUMBER`: numerický výstup zarovnaný doprava
- `REFERENCE`: generování interního odkazu podle `referenceTemplate`
- `DATE`: formát `dd.mm.yyyy` (`cs-CZ`)
- `DATETIME` / `DATE_TIME`: datum a čas (`cs-CZ`)
- `ENUM`: badge nebo text podle `displayMode`
- `IMAGE`: render obrázku přes komponentu `Image`

Doplňující mechanismy:

- `enumConfig` umožňuje přepsat konfiguraci sloupce podle `accessorKey` nebo `id`,
- při `enableRowSelection` se dynamicky přidává sloupec se checkboxy.

## 9. Výpočet velikosti stránky

`Table` počítá `pageSize` dynamicky:

- `availableHeight = window.innerHeight - topOffsetTable`
- `rows = floor((availableHeight - tableReserve) / rowHeight)`
- minimální hodnota je `1`

Konfigurovatelné hodnoty (`tableSizing.js`):

- `rowHeight` (výchozí `38`)
- `tableReserve` (výchozí `160`)

Per-modulové přepsání je možné přes `TABLE_SIZING_BY_MODULE`.

## 10. Chybové stavy a retry politika

### 10.1 Retry v React Query

`TableWrapper` používá retry pravidlo:

- status `403`: bez retry
- ostatní chyby: maximálně 2 retry pokusy

### 10.2 UI chybové stavy

`DataTable`:

- při `loading` renderuje `Loader`,
- při `403` renderuje explicitní hlášení o nedostatečných oprávněních,
- při ostatních chybách renderuje obecnou chybu s fallback zprávou.

## 11. Integrace přes `ResourceTablePage`

`ResourceTablePage` poskytuje standardizovaný wrapper nad `Table`:

- propaguje `module`, `basePath`, `enumConfig`,
- volitelně přidává create tlačítko (`createPath`, `createLabel`),
- předává lokalizovaný placeholder vyhledávání (`translations.SEARCH_PLACEHOLDER`).

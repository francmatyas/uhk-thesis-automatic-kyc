# Specifikace module definitions

## 1. Účel dokumentu

Tento dokument definuje návrhový a implementační kontrakt objektů `providerModuleDefinitions` a `tenantModuleDefinitions`. Tyto definice představují konfigurační vrstvu, která sjednocuje:

- napojení na CRUD API,
- deklaraci routingu,
- konfiguraci tabulkových stránek,
- konfiguraci detailních formulářů,
- texty uživatelských hlášení.

Cílem je zajistit konzistentní rozšiřitelnost modulů bez duplikace UI logiky.

## 2. Rozsah a referenční implementace

Specifikace je odvozena z těchto souborů:

- `src/modules/provider/moduleDefinitions.js`
- `src/modules/tenant/moduleDefinitions.js`
- `src/modules/queryKeys.js`
- `src/views/shared/ResourceTablePage.jsx`
- `src/views/shared/SimpleResourceDetail.jsx`
- `src/views/shared/DetailFieldsSection.jsx`
- `src/hooks/useCrudDetail.js`

## 3. Obecná struktura module definition

Každý modul je objekt v mapě definic (např. `roles`, `users`, `apiKeys`, `members`) a typicky obsahuje tyto části:

- `key`
- `queryKeys`
- `api`
- `table` (volitelné)
- `routes`
- `messages`
- `detail`

## 4. Identifikace modulu a query klíče

### 4.1 `key`

Jednoznačný identifikátor modulu používaný v UI a interní logice.

### 4.2 `queryKeys`

Objekt query klíčů, standardně ve tvaru z `createResourceQueryKeys(resource)`:

- `all`
- `list()`
- `detail(id)`

`useCrudDetail` využívá zejména `queryKeys.detail(id)` a invalidaci přes `queryKeys.list()`.

## 5. API kontrakt

Objekt `api` poskytuje funkční kontrakt pro CRUD operace:

- `fetchOne(id)`
- `createOne(payload)`
- `updateOne(id, payload)`
- `deleteOne(id)`

Poznámky k implementaci:

- V create režimu `useCrudDetail` volá `createOne(payload)`.
- V edit režimu `useCrudDetail` volá `updateOne(id, payload)`.
- `deleteOne` je voláno pouze v edit režimu při potvrzení uživatelem.
- Moduly mohou explicitně zakázat operace (např. `members.createOne`, `members.deleteOne`) vyhozením chyby.

## 6. Konfigurace tabulkového zobrazení

Objekt `table` slouží jako vstup do `ResourceTablePage`.

Typická pole:

- `module`
- `basePath`
- `createPath` (string nebo funkce podle `tenantSlug`)
- `createLabel`
- `searchPlaceholder`
- `showCreateButton`
- `enumConfig`

Mapování:

- `ResourceTablePage` předává tyto hodnoty do generické komponenty `Table`.
- `enumConfig` je použit v tabulkové transformaci (`processTableData`) pro přepis chování vybraných sloupců.

## 7. Routing kontrakt

Objekt `routes` definuje generování list/detail tras:

- `list(...)`
- `detail(...)`

Počet argumentů závisí na scope:

- provider moduly: typicky `detail(id)`
- tenant moduly: typicky `detail(tenantSlug, id)`

`SimpleResourceDetail` používá `routes` pro post-create a post-delete navigaci.

## 8. Uživatelská hlášení

Objekt `messages` obsahuje texty uživatelských hlášení:

- `confirmDelete`
- `success.create`, `success.update`, `success.remove`
- `error.save`, `error.remove`

`useCrudDetail` tato hlášení používá pro potvrzení mazání a toast notifikace.

## 9. Konfigurace detailního formuláře

Objekt `detail` je klíčový pro generování formulářové stránky v `SimpleResourceDetail`.

Povinná a běžná pole:

- `idParam`: název route parametru pro detail (`roleId`, `tenantId`, `memberId`, ...)
- `defaultValues`: výchozí data pro `react-hook-form`
- `fields`: pole definic polí nebo funkce vracející pole

Často používaná volitelná pole:

- `breadcrumb`: `{ key, labelField }`
- `sectionTitle`, `sectionDescription`
- `columns` (počet sloupců ve `DetailFieldsSection`)
- `actionLabels`: `{ create, save, delete }`
- `showCreate`, `showDelete`
- `readOnly` (boolean nebo funkce)
- `transformEntityForForm(entity)`
- `transformSubmit(data, context)`
- `renderAfterFields(context)`

## 10. Kontrakt pole `fields`

`fields` mohou být:

- statické pole objektů,
- funkce s kontextem formuláře (`register`, `control`, `watch`, `setValue`, `entity`, `inEditMode`, ...).

Podporované typy v `DetailFieldsSection`:

- `text` (default)
- `textarea`
- `enum` (render přes `Combobox`)
- `date` (render přes `DatePicker`)
- `datetime` / `date_time` (render přes `Input type="datetime-local"`)
- `render` (vlastní render funkce)

Běžná metadata pole:

- `name`
- `label`
- `required`
- `registerOptions`
- `rules`
- `options` (u enum)
- `props`
- `fullWidth`

## 11. Runtime chování v `SimpleResourceDetail`

Po předání `moduleDef` probíhá tento proces:

1. Určení režimu (`create`/`edit`) a načtení `id` z `idParam`.
2. V edit režimu načtení entity přes `useCrudDetail` + `api.fetchOne`.
3. Volitelné mapování entity do formátu formuláře přes `transformEntityForForm`.
4. Sestavení polí přes `detail.fields`.
5. Při submitu volitelné mapování payloadu přes `transformSubmit`.
6. Volání `createOne` nebo `updateOne`.
7. Volitelně mazání přes `deleteOne`.
8. Navigace přes `routes.detail` (po create) a `routes.list` (po delete).

## 12. Scope-specifické rozdíly

### 12.1 Provider definice

Provider moduly (`roles`, `permissions`, `users`, `tenants`) používají provider trasy (`/p/...`) a provider API namespace.

### 12.2 Tenant definice

Tenant moduly (`tenantMe`, `apiKeys`, `webhooks`, `members`) používají tenant trasy (`/t/:tenantSlug/...`) a tenant API namespace.

Specifické příklady:

- `tenantMe` přesměrovává create/update na stejný endpoint a zakazuje delete.
- `members` povoluje pouze fetch/update a explicitně zakazuje create/delete.
- `webhooks` a `roles` používají transformační funkce pro form data (`transformEntityForForm`, `transformSubmit`).

## 13. Pravidla pro přidání nového modulu

Nový modul musí minimálně definovat:

- `key`
- `queryKeys`
- `api` (ve tvaru kompatibilním s `useCrudDetail`)
- `routes`
- `messages`
- `detail.idParam`, `detail.defaultValues`, `detail.fields`

Pokud má modul list stránku přes generickou tabulku, doplní se i objekt `table`.
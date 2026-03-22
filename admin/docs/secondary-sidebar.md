# Specifikace sekundárního sidebaru pro skupiny

## 1. Účel dokumentu

Tento dokument popisuje architekturu, datový kontrakt a runtime chování sekundárního sidebaru, který zobrazuje skupiny entit nad vybranými moduly aplikace.

## 2. Rozsah a referenční implementace

Specifikace je odvozena z těchto souborů:

- `src/contexts/SecondarySidebarContext.jsx`
- `src/components/sidebar/GroupsSidebar.jsx`
- `src/components/sidebar/Nav.jsx`
- `src/components/layout/Layout.jsx`
- `src/lib/groups/moduleConfigs.js`
- `src/lib/groups/prefetchGroups.js`
- `src/api/simulations.js`
- `src/views/simulations/SimulationGrid.jsx`

## 3. Architektonické zařazení

Sekundární sidebar je integrován v `Layout` mezi hlavní navigací a hlavním obsahem stránky.

Render podmínka:

- sidebar se renderuje pouze pokud `isVisible === true` a současně existuje `moduleKey` v `SecondarySidebarContext`.

Zobrazovací omezení:

- komponenta `GroupsSidebar` je na úrovni CSS skrytá pod breakpointem `lg` (`hidden lg:flex`).

## 4. Stavový model (`SecondarySidebarContext`)

Context poskytuje následující stav a operace:

- `isVisible`
- `isCollapsed`
- `moduleKey`
- `showSidebar(module)`
- `hideSidebar()`
- `toggleSidebar(module, forceShow = false)`
- `toggleCollapse()`
- `markAsPrefetched(module)`
- `isPrefetched(module)`

### 4.1 Perzistence kolapsu

Stav `isCollapsed` je perzistován do cookie:

- název cookie: `secondary-sidebar-collapsed`
- path: `/`
- expirační doba: 365 dnů

### 4.2 Inicializační chování

Při prvním průchodu (`hasInitialized === false`) context:

1. vyhodnotí, zda aktuální route podporuje groups (`supportsGroups(pathname)`),
2. pokud ano, nastaví `moduleKey` a `isVisible` s prodlevou 200 ms,
3. nastaví `hasInitialized = true`.

Poznámka: v aktuální implementaci není inicializace podmíněna přítomností query parametru `group`.

### 4.3 Reakce na změnu route

Po inicializaci platí:

- při navigaci na route bez podpory groups se sidebar automaticky skryje (`isVisible = false`, `moduleKey = null`).

## 5. Modulová konfigurace (`moduleConfigs`)

Podpora modulu je deklarována v `MODULE_CONFIGS`:

- klíč objektu: normalizovaná route bez scope prefixu (např. `/simulations`),
- hodnota: `{ moduleKey, hasGroups, displayName }`.

V aktuálním stavu je aktivně nakonfigurován modul:

- `/simulations` -> `moduleKey: "simulations"`

`getModuleConfig(path)` používá normalizaci přes `stripScopePrefix`, tj. stejná konfigurace funguje pro provider i tenant scope cestu.

## 6. Tok událostí v navigaci (`Nav`)

Při interakci s položkou navigace:

- `onMouseEnter`/`onFocus`:
  - pokud položka odpovídá modulu s groups a modul ještě nebyl prefetchnut, volá se `prefetchGroups(moduleKey)` a modul se označí přes `markAsPrefetched`.
- `onClick`:
  - pokud položka podporuje groups, po prodlevě 150 ms se volá `toggleSidebar(moduleKey, true)`.

Použití `forceShow = true` zajišťuje, že klik na položku sidebar neuzavře, ale explicitně otevře.

## 7. Datová vrstva groups (`GroupsSidebar`)

`GroupsSidebar` volí API funkce podle `moduleKey` pomocí `switch`.

Aktuálně podporovaný modul:

- `simulations`:
  - `fetchSimulationGroups`
  - `createSimulationGroup`
  - `editSimulationGroup`
  - `deleteSimulationGroup`

Pro neznámý `moduleKey` komponenta vyhazuje chybu `Unsupported moduleKey`.

### 7.1 React Query kontrakt

Načítání seznamu groups:

- `queryKey: ["groups", moduleKey]`
- `staleTime: 60_000`
- `enabled: !!moduleKey`

Mutace (`create`, `edit`, `delete`) po úspěchu volají `refetch()`.

## 8. URL kontrakt a filtrace obsahu

Aktivní skupina je určena query parametrem:

- `group` (čteno přes `useSearchParams`)

`GroupsSidebar` generuje odkazy:

- bez filtru: `basePath`
- s filtrem: `basePath?group=<slug-nebo-id>`

V případě simulací následně cílová stránka (`SimulationGrid`) čte `group` z URL a předává jej do `fetchSimulations(group)`.

## 9. Sestavení scoped base cesty

Pro správné linkování se `basePath` skládá z:

- `moduleKey -> innerPath` (`getModulePath`),
- aktuálního scope (`getScopeFromPath`),
- tenant slug (`getTenantSlugFromPath`),
- výsledného scoped path (`getScopedPath`).

Tento mechanismus zajišťuje, že groups odkazy respektují provider/tenant kontext.

## 10. Prefetch strategie

`prefetchGroups(moduleKey)` provádí dva kroky:

1. dynamický import `@/components/sidebar/GroupsSidebar` (warm-up modulu),
2. `queryClient.prefetchQuery` s `queryKey: ["groups", moduleKey]`.

V aktuálním stavu není implementována debounce vrstva prefetch volání.

## 11. Rozšiřitelnost na nový modul

Pro aktivaci groups pro nový modul je nutné upravit konzistentně tři místa:

1. `MODULE_CONFIGS` v `moduleConfigs.js`:
   - přidat route mapování na nový `moduleKey`.
2. `prefetchGroups.js`:
   - doplnit mapování `moduleKey -> fetchFn`.
3. `GroupsSidebar.jsx`:
   - doplnit mapování `moduleKey -> { fetch, create, edit, delete }`.

Bez všech tří kroků nebude modul plně funkční (prefetch, render nebo mutace).

## 12. Datový kontrakt skupiny (aktuální použití)

UI pracuje s entitou group minimálně v tomto tvaru:

- `id`
- `name`
- volitelně `slug`
- volitelně `color`
- volitelně `icon`
- volitelně `pinned`
- volitelně `order`

Řazení v UI:

1. pinned skupiny před nepinned,
2. v rámci stejné úrovně podle `order` vzestupně.

## 13. Známá omezení aktuální implementace

- Aktivně podporován je pouze modul `simulations`.
- Chybí centrální validační schéma pro group payloady.
- Chybové stavy mutací nejsou centralizovaně mapovány na uživatelská hlášení v sidebaru.
- Kontext drží `prefetchedModules` pouze in-memory (bez perzistence mezi reloady).

## 14. Doporučení pro další standardizaci

- Zavést jednotný konfigurační registr groups modulů, aby nebylo nutné udržovat paralelní `switch` v několika souborech.
- Definovat explicitní typový kontrakt group entity (runtime validace payloadu).
- Rozšířit observabilitu chyb mutací (telemetrie + konzistentní notifikace).

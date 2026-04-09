# Architektura a řízení flow ve Verify

## 1. Vstupní body a routing
- `src/app/router.jsx` definuje dvě routy:
- `/` pro ruční zadání tokenu,
- `/v/:token` pro vlastní verifikační flow.
- Layout je mobile-first, obsahuje jazykový přepínač a jednotný `StepShell`.

## 2. Zdroj pravdy pro kroky
- `src/api/verification.js` drží kanonické pořadí kroků (`STEP_ORDER`):
- `PERSONAL_INFO`
- `EMAIL_VERIFICATION`
- `PHONE_VERIFICATION`
- `DOCUMENT_IDENTITY`
- `LIVENESS_CHECK`
- `AML_QUESTIONNAIRE`
- Povinné kroky jsou nyní:
- `PERSONAL_INFO`
- `DOCUMENT_IDENTITY`
- `LIVENESS_CHECK`
- Volitelné kroky se zapínají přes `journeyConfig.optionalSteps` z API.

## 3. Orchestrace stránky `VerificationFlowPage`
`src/pages/VerificationFlowPage.jsx` řeší:
- načtení flow (`useQuery`),
- lokální stav formulářů a průběžné statusy kroků,
- mutace jednotlivých kroků (`useMutation`),
- automatickou finalizaci po dokončení povinných kroků.

Princip:
1. vybere se první krok, který není `COMPLETED`,
2. podle `step.type` se renderuje konkrétní komponenta kroku,
3. po úspěchu mutace se krok lokálně označí jako dokončený,
4. po dokončení všech required kroků se zavolá `finalize`.

## 4. Implementace kroků
- `PersonalInfoStep`: jméno, příjmení, datum narození.
- `EmailStep`: odeslání a ověření OTP kódu.
- `PhoneStep`: odeslání a ověření SMS OTP kódu.
- `DocumentStep`:
- výběr typu dokladu (`id_card` / `passport`),
- pro `id_card` požaduje `front` + `back`,
- podporuje upload souboru i focení kamerou.
- `LivenessStep`:
- 4 pozice (`front`, `left`, `right`, `up`),
- automatický capture po detekci správné pozice,
- fallback ručního snímku při chybě modelu.
- `AmlStep`: dynamické schema formuláře dle `AML_FORM_SCHEMA` + `config`.
- `FallbackStep`: ochranný fallback pro neznámý `step.type`.

## 5. Progress a UX
- Pokud je kroků více, `StepShell` zobrazuje segmentovaný progress podle pořadí kroků.
- U OTP kroků je vnitřní progress 0/50/100 podle fáze odeslání a verifikace.
- U dokumentu je progress odvozený od typu dokladu a počtu nahraných stran.
- Uživatel může pokračovat na telefonu přes QR/share dialog (`ContinueOnPhoneButton`).

## 6. Chybové stavy flow
- `verification_already_submitted`: flow už nelze měnit, zobrazeno finální info.
- `verification_expired`: token vypršel.
- ostatní chyby: fallback obrazovka s možností `Retry`.

## 7. Lokalizace
- i18n inicializace v `src/i18n.js`.
- podporované jazyky: `cs`, `en`.
- texty jsou v:
- `public/locales/cs/common.yaml`
- `public/locales/en/common.yaml`


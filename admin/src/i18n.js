import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import HttpBackend from "i18next-http-backend";
import { parse } from "yaml";

i18n
  .use(LanguageDetector) // Detects user language
  .use(initReactI18next) // Passes i18n instance to react-i18next
  .use(HttpBackend) // Enables loading translations from public static files
  .init({
    ns: ["common"],
    defaultNS: "common",
    backend: {
      loadPath: "/locales/{{lng}}/{{ns}}.yaml",
      parse: (data) => {
        // Parse YAML data
        return parse(data);
      },
    },
    supportedLngs: ["en", "cs"], // List of supported languages
    fallbackLng: ["en"], // Fallback to English if the language is not available
    debug: import.meta.env.DEV,
    interpolation: {
      escapeValue: false, // React already escapes by default
    },
    detection: {
      // Order and from where user language should be detected
      order: ["path", "cookie", "localStorage", "navigator"],
      // Keys or params to lookup language from
      lookupFromPathIndex: 0, // if your language code is in the URL path
    },
  });

export default i18n;

import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import HttpBackend from "i18next-http-backend";
import { parse } from "yaml";
const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:4000/";

i18n
  .use(LanguageDetector) // Detects user language
  .use(initReactI18next) // Passes i18n instance to react-i18next
  .use(HttpBackend) // Enables loading translations from a backend
  .init({
    backend: {
      // URL where your translations are served from.
      // For example, your Java backend could serve translations at:
      // http://your-backend.com/translations/{lng}/{ns}.json
      loadPath: `${API_URL}translations/{{lng}}/{{ns}}.yml`,
      parse: (data) => {
        // Parse YAML data
        return parse(data);
      },
    },
    supportedLngs: ["en", "cs"], // List of supported languages
    fallbackLng: ["en"], // Fallback to English if the language is not available
    debug: process.env.NODE_ENV === "development",
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

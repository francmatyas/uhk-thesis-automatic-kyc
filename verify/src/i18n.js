import i18n from 'i18next'
import HttpBackend from 'i18next-http-backend'
import LanguageDetector from 'i18next-browser-languagedetector'
import { initReactI18next } from 'react-i18next'
import YAML from 'yaml'

const isHttps = typeof window !== 'undefined' && window.location.protocol === 'https:'

i18n
  .use(HttpBackend)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    fallbackLng: 'en',
    supportedLngs: ['en', 'cs'],
    nonExplicitSupportedLngs: true,
    ns: ['common'],
    defaultNS: 'common',
    detection: {
      order: ['cookie', 'navigator'],
      lookupCookie: 'locale',
      caches: ['cookie'],
      cookieMinutes: 60 * 24 * 365,
      cookieOptions: {
        path: '/',
        sameSite: isHttps ? 'none' : 'lax',
        secure: isHttps,
      },
    },
    backend: {
      loadPath: '/locales/{{lng}}/{{ns}}.yaml',
      parse: (data) => YAML.parse(data),
    },
    interpolation: {
      escapeValue: false,
    },
  })

export default i18n

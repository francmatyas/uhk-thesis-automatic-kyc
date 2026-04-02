import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [token, setToken] = useState('')

  const onSubmit = (event) => {
    event.preventDefault()
    if (!token.trim()) return
    navigate(`/v/${encodeURIComponent(token.trim())}`)
  }

  return (
    <section className="space-y-4">
      <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <h1 className="text-xl font-semibold text-slate-900">{t('home.title')}</h1>
        <p className="mt-2 text-sm leading-6 text-slate-600">{t('home.description')}</p>
      </div>

      <form onSubmit={onSubmit} className="space-y-3 rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <label htmlFor="token" className="block text-sm font-medium text-slate-700">
          {t('home.tokenLabel')}
        </label>
        <input
          id="token"
          value={token}
          onChange={(event) => setToken(event.target.value)}
          className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm outline-none ring-blue-500/40 transition focus:ring-4"
          placeholder={t('home.tokenPlaceholder')}
        />
        <button
          type="submit"
          className="w-full rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700"
        >
          {t('home.startFlow')}
        </button>
      </form>
    </section>
  )
}

export default HomePage

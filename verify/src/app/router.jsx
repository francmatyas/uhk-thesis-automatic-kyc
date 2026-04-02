import { createBrowserRouter, Link, Outlet } from 'react-router'
import { useTranslation } from 'react-i18next'
import HomePage from '@/pages/HomePage'
import VerificationFlowPage from '@/pages/VerificationFlowPage'
import LanguageSwitcher from '@/components/LanguageSwitcher'

function AppLayout() {
  const { t } = useTranslation()

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <div className="mx-auto flex min-h-screen w-full max-w-md flex-col px-4 py-4">
        <header className="mb-4 flex items-center justify-between rounded-2xl bg-white px-4 py-3 shadow-sm ring-1 ring-slate-200">
          <Link to="/" className="text-sm font-semibold tracking-wide text-slate-700">
            {t('app.name')}
          </Link>
          <LanguageSwitcher />
        </header>

        <main className="flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'v/:token', element: <VerificationFlowPage /> },
    ],
  },
])

export default router

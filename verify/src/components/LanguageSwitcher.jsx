import { useTranslation } from 'react-i18next'
import { Check, ChevronDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'

function LanguageSwitcher() {
  const { i18n } = useTranslation()
  const currentLang = i18n.resolvedLanguage === 'cs' ? 'cs' : 'en'

  const options = [
    { code: 'en', label: 'English', icon: '🇬🇧' },
    { code: 'cs', label: 'Čeština', icon: '🇨🇿' },
  ]

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" className="h-10 rounded-xl px-3 text-sm">
          <span>{currentLang === 'cs' ? '🇨🇿' : '🇬🇧'}</span>
          <ChevronDown className="h-4 w-4 text-slate-500" />
        </Button>
      </PopoverTrigger>

      <PopoverContent align="end" className="w-48 p-1.5">
        <div className="space-y-1">
          {options.map((option) => {
            const selected = currentLang === option.code
            return (
              <button
                key={option.code}
                type="button"
                onClick={() => i18n.changeLanguage(option.code)}
                className={`flex h-11 w-full items-center justify-between rounded-lg px-3 text-left text-sm transition ${
                  selected ? 'bg-slate-100 text-slate-900' : 'hover:bg-slate-50'
                }`}
              >
                <span className="flex items-center gap-2">
                  <span>{option.icon}</span>
                  <span>{option.label}</span>
                </span>
                {selected ? <Check className="h-4 w-4 text-slate-700" /> : null}
              </button>
            )
          })}
        </div>
      </PopoverContent>
    </Popover>
  )
}

export default LanguageSwitcher

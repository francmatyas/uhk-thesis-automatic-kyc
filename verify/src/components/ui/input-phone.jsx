import { forwardRef, useEffect, useMemo, useState } from "react";
import countries from "@/assets/data/countries.json";
import { cn } from "@/lib/utils";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group";

function normalizeDial(value) {
  return String(value ?? "").replace(/\s+/g, "");
}

export function splitPhoneValue(rawValue) {
  const cleaned = String(rawValue ?? "").replace(/\s+/g, "");
  if (!cleaned.startsWith("+")) {
    return { dial: "+420", local: cleaned };
  }

  const dialMatch = countries
    .map((country) => normalizeDial(country.dial_code))
    .filter((dial) => cleaned.startsWith(dial))
    .sort((a, b) => b.length - a.length)[0];

  if (!dialMatch) {
    return { dial: "+420", local: cleaned.replace(/^\+/, "") };
  }

  return {
    dial: dialMatch,
    local: cleaned.slice(dialMatch.length),
  };
}

function countryCodeToFlagEmoji(code) {
  if (!code || code.length !== 2) return "";
  return code
    .toUpperCase()
    .split("")
    .map((char) => String.fromCodePoint(127397 + char.charCodeAt(0)))
    .join("");
}

const InputPhone = forwardRef(
  (
    {
      className,
      preferredCountries,
      label,
      value,
      onChange,
      disabled,
      id = "phoneNumber",
      name = "phone_number",
      ...props
    },
    ref,
  ) => {
    const initial = splitPhoneValue(value);

    const [countryCode, setCountryCode] = useState(initial.dial);
    const [phoneNumber, setPhoneNumber] = useState(initial.local);
    const [isOpen, setIsOpen] = useState(false);

    useEffect(() => {
      const next = splitPhoneValue(value);
      if (next.dial !== countryCode) setCountryCode(next.dial);
      if (next.local !== phoneNumber) setPhoneNumber(next.local);
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [value]);

    const countryByDial = countries.find(
      (country) => normalizeDial(country.dial_code) === countryCode,
    );
    const selectedCountry = `${countryCode}-${countryByDial?.code ?? "CZ"}`;

    const { filteredCountries, otherCountriesOptions } = useMemo(() => {
      const pref = new Set(
        (preferredCountries ?? []).map((country) => country.toLowerCase()),
      );
      const filtered = [];
      const others = [];

      for (const country of countries) {
        if (pref.has(country.code.toLowerCase())) filtered.push(country);
        else others.push(country);
      }

      return { filteredCountries: filtered, otherCountriesOptions: others };
    }, [preferredCountries]);

    const emitChange = (dial, phone) => {
      if (typeof onChange === "function") {
        onChange({ dialCode: dial, contact: String(phone ?? "").replace(/\s+/g, "") });
      }
    };

    const handleCountryChange = (valueFromSelect) => {
      const dial = valueFromSelect.split("-")[0];
      setCountryCode(dial);
      emitChange(dial, phoneNumber);
    };

    return (
      <div className={cn("w-full space-y-2", className)}>
        {label ? <Label htmlFor={id}>{label}</Label> : null}

        <InputGroup className="h-12 rounded-2xl">
          <InputGroupAddon align="inline-start" className="pr-1">
            <Select
              value={selectedCountry}
              onValueChange={handleCountryChange}
              onOpenChange={(open) => setIsOpen(open)}
              disabled={disabled}
            >
              <SelectTrigger className="h-9 min-w-24 border-0 bg-transparent px-2 text-sm shadow-none focus-visible:ring-0">
                <SelectValue>
                  {(() => {
                    const [dial, code] = selectedCountry.split("-");
                    return (
                      <div className="flex items-center gap-2">
                        <span>{countryCodeToFlagEmoji(code)}</span>
                        <span>{dial}</span>
                      </div>
                    );
                  })()}
                </SelectValue>
              </SelectTrigger>

              <SelectContent>
                {isOpen ? (
                  <>
                    <SelectGroup>
                      {filteredCountries.map((country) => (
                        <SelectItem
                          key={`${country.code}-${country.dial_code}`}
                          value={`${normalizeDial(country.dial_code)}-${country.code}`}
                        >
                          <span className="flex items-center gap-2">
                            <span>{countryCodeToFlagEmoji(country.code)}</span>
                            <span>{country.name} ({normalizeDial(country.dial_code)})</span>
                          </span>
                        </SelectItem>
                      ))}
                    </SelectGroup>
                    <SelectSeparator />
                    <SelectGroup>
                      {otherCountriesOptions.map((country) => (
                        <SelectItem
                          key={`${country.code}-${country.dial_code}`}
                          value={`${normalizeDial(country.dial_code)}-${country.code}`}
                        >
                          <span className="flex items-center gap-2">
                            <span>{countryCodeToFlagEmoji(country.code)}</span>
                            <span>{country.name} ({normalizeDial(country.dial_code)})</span>
                          </span>
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </>
                ) : null}
              </SelectContent>
            </Select>
          </InputGroupAddon>

          <InputGroupInput
            ref={ref}
            id={id}
            name={name}
            type="tel"
            autoComplete="tel-national"
            value={phoneNumber}
            onChange={(event) => {
              const next = event.target.value;
              setPhoneNumber(next);
              emitChange(countryCode, next);
            }}
            disabled={disabled}
            className="h-full rounded-r-2xl px-4 text-base"
            {...props}
          />
        </InputGroup>
      </div>
    );
  },
);

InputPhone.displayName = "InputPhone";

export { InputPhone };

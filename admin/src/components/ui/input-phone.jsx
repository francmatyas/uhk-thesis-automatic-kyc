import { useState, forwardRef, useEffect, useMemo } from "react";
import { cn } from "@/lib/utils";
import { Input } from "./input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectSeparator,
} from "./select";
import ReactCountryFlag from "react-country-flag";

import countries from "@/assets/data/countries.json";

const InputPhone = forwardRef(
  (
    {
      className,
      preferredCountries,
      label,
      value,
      onChange,
      disabled,
      ...props
    },
    ref
  ) => {
    const initialDial = Array.isArray(value) && value[0] ? value[0] : "+420";
    const initialPhone = Array.isArray(value) && value[1] ? value[1] : "";

    const [countryCode, setCountryCode] = useState(initialDial);
    const [phoneNumber, setPhoneNumber] = useState(initialPhone);
    const [isOpen, setIsOpen] = useState(false);

    useEffect(() => {
      if (Array.isArray(value)) {
        const [dial, phone] = value;
        if (dial !== countryCode) setCountryCode(dial ?? "+420");
        if (phone !== phoneNumber) setPhoneNumber(phone ?? "");
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [value]);

    const countryByDial = countries.find((c) => c.dial_code === countryCode);
    const selectedCountry = `${countryCode}-${countryByDial?.code ?? "CZ"}`;

    const { filteredCountries, otherCountriesOptions } = useMemo(() => {
      const pref = new Set(
        (preferredCountries ?? []).map((c) => c.toLowerCase())
      );
      const filtered = [];
      const others = [];
      for (const country of countries) {
        if (pref.has(country.code.toLowerCase())) filtered.push(country);
        else others.push(country);
      }
      return { filteredCountries: filtered, otherCountriesOptions: others };
    }, [preferredCountries]);

    const extractDialCode = (val) => val.split("-")[0];

    const emitChange = (dial, phone) => {
      if (typeof onChange === "function") {
        onChange([dial, phone]);
      }
    };

    return (
      <div
        className={cn("relative w-full flex flex-col gap-1.5", label && "pt-1")}
      >
        {label && (
          <Label
            htmlFor={props.id}
            className={cn(
              "ml-0.5 text-xs font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
            )}
          >
            {label}
          </Label>
        )}
        <div className={cn("w-full flex items-center gap-1", className)}>
          <Select
            value={selectedCountry}
            onValueChange={(val) => {
              const dialCode = extractDialCode(val);
              setCountryCode(dialCode);
              emitChange(dialCode, phoneNumber);
            }}
            onOpenChange={(open) => setIsOpen(open)}
            name="country_code"
            autoComplete="tel-country-code"
            disabled={disabled}
          >
            <SelectTrigger className="min-w-32">
              <SelectValue>
                {selectedCountry &&
                  (() => {
                    const [dial, code] = selectedCountry.split("-");
                    return (
                      <div className="flex items-center gap-2">
                        <ReactCountryFlag countryCode={code} svg /> {dial}
                      </div>
                    );
                  })()}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              {isOpen && (
                <>
                  <SelectGroup>
                    {filteredCountries.map((country) => (
                      <SelectItem
                        key={`${country.code}-${country.dial_code}`}
                        value={`${country.dial_code}-${country.code}`}
                        className="flex items-center gap-2"
                      >
                        {country.name} ({country.dial_code})
                      </SelectItem>
                    ))}
                  </SelectGroup>
                  <SelectSeparator />
                  <SelectGroup>
                    {otherCountriesOptions.map((country) => (
                      <SelectItem
                        key={`${country.code}-${country.dial_code}`}
                        value={`${country.dial_code}-${country.code}`}
                        className="flex items-center gap-2"
                      >
                        {country.name} ({country.dial_code})
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </>
              )}
            </SelectContent>
          </Select>
          <Input
            ref={ref}
            type="tel"
            id={props.id ?? "phoneNumber"}
            name={props.name ?? "phone_number"}
            autoComplete="tel-national"
            value={phoneNumber}
            onChange={(e) => {
              const next = e.target.value;
              setPhoneNumber(next);
              emitChange(countryCode, next);
            }}
            disabled={disabled}
            className={cn(
              "w-full border-input placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground dark:bg-input/30",
              props.className
            )}
          />
        </div>
      </div>
    );
  }
);

InputPhone.displayName = "InputPhone";

export { InputPhone };

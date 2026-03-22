"use client";

import * as React from "react";
import { ChevronDownIcon } from "lucide-react";
import { cn } from "@/lib/utils";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

export function DatePicker({
  value,
  onChange,
  disabled,
  label,
  readOnly = false,
  ...props
}) {
  const [open, setOpen] = React.useState(false);

  const handleOpenChange = (open) => {
    if (!readOnly) {
      setOpen(open);
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
      <Popover open={open} onOpenChange={handleOpenChange}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            id="date"
            className="w-full justify-between font-normal"
          >
            {value ? value.toLocaleDateString("cs-CZ") : "Select date"}
            <ChevronDownIcon />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto overflow-hidden p-0" align="start">
          <Calendar
            mode="single"
            selected={value}
            captionLayout="dropdown"
            disabled={disabled}
            {...props}
            onSelect={(date) => {
              setOpen(false);
              // Ensure the selected date is treated as local timezone
              if (date) {
                const localDate = new Date(
                  date.getFullYear(),
                  date.getMonth(),
                  date.getDate()
                );
                onChange(localDate);
              } else {
                onChange(date);
              }
            }}
          />
        </PopoverContent>
      </Popover>
    </div>
  );
}

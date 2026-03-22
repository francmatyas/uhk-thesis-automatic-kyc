import * as React from "react";
import { ChevronDownIcon } from "lucide-react";
import { cn } from "@/lib/utils";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

export function TimeSelect({
  value,
  minuteStep = 15,
  onChange,
  disabled,
  label,
  readOnly = false,
  ...props
}) {
  const [open, setOpen] = React.useState(false);
  const [selectedHour, selectedMinute] = value?.split(":")?.map(Number) || [
    0, 0,
  ];

  const handleChange = (newHour, newMinute) => {
    newHour = newHour?.toString().padStart(2, "0") ?? "00";
    newMinute = newMinute?.toString().padStart(2, "0") ?? "00";
    onChange(`${newHour}:${newMinute}`);
  };

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
      <div
        className={cn(
          "w-full flex items-center gap-1",
          "border-input dark:bg-input/30 border bg-transparent shadow-xs",
          "data-[placeholder]:text-muted-foreground [&_svg:not([class*='text-'])]:text-muted-foreground focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive flex items-center gap-1 rounded-md text-sm whitespace-nowrap transition-[color,box-shadow] outline-none focus-visible:ring-[3px] disabled:cursor-not-allowed disabled:opacity-50 data-[size=default]:h-9 data-[size=sm]:h-8 *:data-[slot=select-value]:line-clamp-1 *:data-[slot=select-value]:flex *:data-[slot=select-value]:items-center *:data-[slot=select-value]:gap-2 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4"
        )}
      >
        <Popover open={open} onOpenChange={handleOpenChange}>
          <PopoverTrigger
            asChild
            className={cn(disabled && "cursor-not-allowed")}
          >
            <Button
              variant="outline"
              id="time-select"
              className={cn(
                "w-full justify-between font-normal cursor-pointer",
                disabled && "cursor-not-allowed",
                readOnly && "cursor-default"
              )}
              disabled={disabled}
            >
              {value ? value : "Select time (HH:MM)"}
              <ChevronDownIcon />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-auto overflow-hidden p-0" align="start">
            <div className="flex p-2">
              <ScrollArea className="h-40 w-20">
                <div className="flex flex-col">
                  {Array.from({ length: 24 }, (_, i) => {
                    const hour = i.toString().padStart(2, "0");
                    return (
                      <div key={hour} className="flex items-center">
                        <Button
                          variant="ghost"
                          className={cn(
                            "w-full justify-between",
                            hour == selectedHour && "bg-muted"
                          )}
                          onClick={() => handleChange(hour, selectedMinute)}
                        >
                          {hour}
                        </Button>
                      </div>
                    );
                  })}
                </div>
              </ScrollArea>
              <ScrollArea className="h-40 w-20">
                <div className="flex flex-col">
                  {Array.from({ length: 60 / minuteStep }, (_, i) => {
                    const minute = (i * minuteStep).toString().padStart(2, "0");
                    return (
                      <div key={minute} className="flex items-center">
                        <Button
                          variant="ghost"
                          className={cn(
                            "w-full justify-between",
                            minute == selectedMinute && "bg-muted"
                          )}
                          onClick={() => handleChange(selectedHour, minute)}
                        >
                          {minute}
                        </Button>
                      </div>
                    );
                  })}
                </div>
              </ScrollArea>
            </div>
          </PopoverContent>
        </Popover>
      </div>
    </div>
  );
}

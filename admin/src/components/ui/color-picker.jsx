import * as React from "react";
import { cn } from "@/lib/utils";
import { HexColorPicker } from "react-colorful";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

export function ColorPicker({
  value,
  onChange,
  disabled,
  label,
  variant = "color",
  readOnly = false,
  className,
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
            id="color"
            className={cn(variant === "color" && "px-2 w-fit", className)}
          >
            <div
              className="w-6 h-6 rounded border"
              style={{ backgroundColor: value }}
            ></div>
            {variant !== "color" && (
              <span className="truncate font-mono">{value}</span>
            )}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto overflow-hidden p-0" align="start">
          <HexColorPicker color={value} onChange={onChange} />
        </PopoverContent>
      </Popover>
    </div>
  );
}

import * as React from "react";
import { cn } from "@/lib/utils";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

import { DynamicIcon } from "lucide-react/dynamic";
import { Landmark } from "lucide-react";
const icons = [
  "folder",
  "tag",
  "house",
  "building",
  "factory",
  "landmark",
  "store",
  "warehouse",
  "palette",
  "user",
  "users",
  "truck",
  "shopping-cart",
  "credit-card",
  "dollar-sign",
  "bar-chart-2",
  "pie-chart",
  "line-chart",
  "activity",
  "settings",
  "wrench",
  "hammer",
  "search",
  "bell",
  "calendar",
  "clock",
  "map-pin",
  "globe",
  "heart",
  "star",
  "camera",
  "music",
  "film",
  "book",
  "code",
  "terminal",
  "cloud",
  "sun",
  "moon",
  "coffee",
  "gift",
  "anchor",
  "battery",
  "bolt",
  "briefcase",
  "bug",
  "crown",
  "diamond",
  "feather",
  "flag",
  "flame",
  "flower",
  "gamepad",
  "headphones",
  "key",
  "leaf",
  "lightbulb",
  "lock",
  "magnet",
  "microscope",
  "paperclip",
  "pencil",
  "phone",
  "rocket",
  "scissors",
  "shield",
];

export function IconSelect({
  value,
  onChange,
  disabled,
  label,
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
          <Button variant="outline" id="color" className={cn(className)}>
            <span className="truncate font-mono">
              {value ? <DynamicIcon name={value} size={16} /> : "Select icon"}
            </span>
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto overflow-hidden p-2">
          <div className="grid grid-cols-6 gap-1">
            {icons.map((iconName, index) => (
              <div
                variant="ghost"
                title={iconName.charAt(0).toUpperCase() + iconName.slice(1)}
                key={index}
                className={cn(
                  "flex items-center gap-2 p-2 cursor-pointer hover:bg-accent hover:text-accent-foreground rounded",
                  value === iconName && "bg-accent text-accent-foreground"
                )}
                onClick={() => {
                  onChange(iconName);
                  setOpen(false);
                }}
              >
                <DynamicIcon name={iconName} size={16} />
              </div>
            ))}
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}

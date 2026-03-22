import * as React from "react";

import { Label } from "@/components/ui/label";

import { cn } from "@/lib/utils";

function Textarea({ className, label, ...props }) {
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
      <textarea
        data-slot="textarea"
        className={cn(
          "border-input placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:bg-input/30 flex field-sizing-content min-h-16 w-full rounded-md border bg-transparent px-3 py-2 text-base shadow-xs transition-[color,box-shadow] outline-none focus-visible:ring-[3px] disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
          className
        )}
        {...props}
      />
    </div>
  );
}

export { Textarea };
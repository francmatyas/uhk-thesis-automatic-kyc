import * as React from "react";
import { cn } from "@/lib/utils";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

const Input = React.forwardRef(
  (
    {
      className,
      type,
      prepend,
      append,
      label,
      clickToCopy = false,
      copyMessage = "Copied to clipboard.",
      ...props
    },
    ref,
  ) => {
    return (
      <div
        className={cn("relative w-full flex flex-col gap-1.5", label && "pt-1")}
      >
        {label && (
          <Label
            htmlFor={props.id}
            className={cn(
              "ml-0.5 text-xs font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 gap-0.5",
            )}
          >
            {label}
            {props.required ? <span className="text-destructive">*</span> : ""}
          </Label>
        )}
        <PrimitiveInput
          ref={ref}
          type={type}
          className={cn(
            "border-input placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground dark:bg-input/30",
            className,
          )}
          prepend={prepend}
          append={append}
          clickToCopy={clickToCopy}
          copyMessage={copyMessage}
          {...props}
        />
      </div>
    );
  },
);

Input.displayName = "Input";

export { Input };

const PrimitiveInput = React.forwardRef(
  (
    {
      className,
      type,
      prepend,
      append,
      clickToCopy = false,
      copyMessage = "Copied to clipboard.",
      onClick,
      ...props
    },
    ref,
  ) => {
    const handleClick = async (event) => {
      onClick?.(event);
      if (!clickToCopy || event.defaultPrevented || props.disabled) return;

      const valueToCopy =
        props.value ?? props.defaultValue ?? event.currentTarget.value;
      if (valueToCopy === null || valueToCopy === undefined || valueToCopy === "") {
        toast.error("Nothing to copy.");
        return;
      }

      try {
        await navigator.clipboard.writeText(String(valueToCopy));
        toast.success(copyMessage);
      } catch {
        toast.error("Failed to copy.");
      }
    };

    return (
      <div
        className={cn(
          "flex items-center gap-2 rounded-md border bg-transparent px-3 shadow-xs transition-[color,box-shadow] dark:bg-input/30 focus-within:border-ring focus-within:ring-ring/50 focus-within:ring-[3px]",
          className,
        )}
      >
        {prepend && (
          <div className="flex items-center">
            {prepend}
          </div>
        )}
        <input
          ref={ref}
          type={type}
          data-slot="input"
          className={cn(
            "file:text-foreground placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground border-input flex h-9 w-full min-w-0 rounded-md bg-transparent py-1 text-base outline-none disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
            "aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive",
            clickToCopy && "cursor-copy",
          )}
          title={clickToCopy ? "Click to copy" : props.title}
          onClick={handleClick}
          {...props}
        />
        {append && (
          <div className="flex items-center">
            {append}
          </div>
        )}
      </div>
    );
  },
);
PrimitiveInput.displayName = "PrimitiveInput";
export { PrimitiveInput };

import * as React from "react";
import { CheckIcon, ChevronsUpDownIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Label } from "@/components/ui/label";

export function Combobox({
  value,
  onChange,
  options = [],
  placeholder,
  label,
  multiple = false,
  readOnly = false,
  ...props
}) {
  const [open, setOpen] = React.useState(false);
  const [visibleChipCount, setVisibleChipCount] = React.useState(0);
  const triggerRef = React.useRef(null);
  const measureRef = React.useRef(null);
  const selectedValues = React.useMemo(() => {
    if (!multiple) {
      return value === undefined || value === null || value === "" ? [] : [value];
    }
    return Array.isArray(value) ? value : [];
  }, [multiple, value]);

  const selectedKeySet = React.useMemo(
    () => new Set(selectedValues.map((selectedValue) => String(selectedValue))),
    [selectedValues],
  );

  const resolveOptionLabel = React.useCallback((option) => {
    if (!option) return "";
    if (typeof option.label === "string" || typeof option.label === "number") {
      return String(option.label);
    }
    return String(option.value ?? "");
  }, []);

  const selectedOptions = React.useMemo(
    () =>
      selectedValues.map((selectedValue) => {
        const selected = options.find(
          (option) => String(option.value) === String(selectedValue),
        );
        return {
          value: selected?.value ?? selectedValue,
          label: resolveOptionLabel(selected ?? { value: selectedValue }),
        };
      }),
    [options, resolveOptionLabel, selectedValues],
  );

  const displayValue = React.useMemo(() => {
    if (multiple) {
      return "";
    }
    const selected = options.find((option) => String(option.value) === String(value));
    return resolveOptionLabel(selected);
  }, [multiple, options, resolveOptionLabel, value]);

  const recomputeVisibleChipCount = React.useCallback(() => {
    if (!multiple) return;
    const triggerNode = triggerRef.current;
    if (!triggerNode) return;

    const selectedCount = selectedOptions.length;
    if (selectedCount === 0) {
      setVisibleChipCount(0);
      return;
    }

    const availableWidth = triggerNode.clientWidth - 44;
    if (availableWidth <= 0) {
      setVisibleChipCount(0);
      return;
    }

    const measureNode = measureRef.current;
    const chipNodes = measureNode?.querySelectorAll("[data-chip-width]") || [];
    const moreNode = measureNode?.querySelector("[data-more-width]");
    if (chipNodes.length === 0) {
      setVisibleChipCount(0);
      return;
    }

    const chipWidths = Array.from(chipNodes).map((node) => node.offsetWidth);
    const moreWidth = moreNode?.offsetWidth ?? 24;
    const chipGap = 4;

    let usedWidth = 0;
    let fitCount = 0;
    for (let index = 0; index < chipWidths.length; index += 1) {
      const chipWidth = chipWidths[index];
      const nextChipWidth = fitCount > 0 ? chipGap + chipWidth : chipWidth;
      const remainingAfter = chipWidths.length - (index + 1);
      const needsMoreChip = remainingAfter > 0;
      const moreChipReserve = needsMoreChip
        ? (fitCount > 0 || nextChipWidth > 0 ? chipGap : 0) + moreWidth
        : 0;

      if (usedWidth + nextChipWidth + moreChipReserve <= availableWidth) {
        usedWidth += nextChipWidth;
        fitCount += 1;
      } else {
        break;
      }
    }

    setVisibleChipCount(fitCount);
  }, [multiple, selectedOptions.length]);

  React.useLayoutEffect(() => {
    if (!multiple) return;
    recomputeVisibleChipCount();
  }, [multiple, recomputeVisibleChipCount, selectedOptions]);

  React.useLayoutEffect(() => {
    if (!multiple || !triggerRef.current || typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(() => {
      recomputeVisibleChipCount();
    });
    observer.observe(triggerRef.current);
    return () => observer.disconnect();
  }, [multiple, recomputeVisibleChipCount]);

  const handleOpenChange = (open) => {
    if (!readOnly) {
      setOpen(open);
    }
  };

  const overflowCount = Math.max(selectedOptions.length - visibleChipCount, 0);
  const visibleChips = selectedOptions.slice(0, visibleChipCount);

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
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
        <PopoverTrigger asChild>
          <div className="w-full">
            {multiple ? (
              <div
                ref={triggerRef}
                className={cn(
                  "flex h-9 w-full items-center gap-2 rounded-md border bg-transparent px-3 shadow-xs transition-[color,box-shadow]",
                  "dark:bg-input/30",
                  open && "border-ring ring-ring/50 ring-[3px]",
                  "cursor-pointer",
                  readOnly && "cursor-default",
                )}
              >
                <div className="min-w-0 flex-1 overflow-hidden">
                  {selectedOptions.length === 0 ? (
                    <span className="text-sm text-muted-foreground">
                      {placeholder ?? "Select values"}
                    </span>
                  ) : (
                    <div className="flex items-center gap-1 overflow-hidden">
                      {visibleChips.map((chip) => (
                        <span
                          key={chip.value}
                          className="inline-flex max-w-full items-center rounded bg-muted px-2 py-0.5 text-xs truncate"
                          title={chip.label}
                        >
                          {chip.label}
                        </span>
                      ))}
                      {overflowCount > 0 && (
                        <span className="inline-flex items-center rounded bg-muted px-2 py-0.5 text-xs">
                          +{overflowCount}
                        </span>
                      )}
                    </div>
                  )}
                </div>
                <ChevronsUpDownIcon className="h-4 w-4 shrink-0 opacity-50" />
              </div>
            ) : (
              <Input
                type="text"
                readOnly
                value={displayValue}
                placeholder={placeholder ?? "Select value"}
                append={<ChevronsUpDownIcon className="h-4 w-4 opacity-50" />}
                className={cn("cursor-pointer", readOnly && "cursor-default")}
              />
            )}
          </div>
        </PopoverTrigger>
        {multiple && (
          <div
            ref={measureRef}
            className="pointer-events-none absolute left-0 top-0 -z-10 invisible flex items-center gap-1 whitespace-nowrap"
          >
            {selectedOptions.map((chip) => (
              <span
                key={`measure-${chip.value}`}
                data-chip-width
                className="inline-flex items-center rounded bg-muted px-2 py-0.5 text-xs"
              >
                {chip.label}
              </span>
            ))}
            <span
              data-more-width
              className="inline-flex items-center rounded bg-muted px-2 py-0.5 text-xs"
            >
              +{Math.max(selectedOptions.length, 1)}
            </span>
          </div>
        )}
      </div>
      <PopoverContent className="w-[240px] p-0" align="start">
        <Command>
          <CommandInput placeholder="Search value..." />
          <CommandList>
            <CommandEmpty>No result found.</CommandEmpty>
            <CommandGroup>
              {options.map((option) => (
                <CommandItem
                  key={option.value}
                  value={String(option.value)}
                  onSelect={(currentValue) => {
                    if (multiple) {
                      const nextValues = selectedKeySet.has(currentValue)
                        ? selectedValues.filter(
                            (selectedValue) => String(selectedValue) !== currentValue,
                          )
                        : [
                            ...selectedValues,
                            options.find(
                              (candidate) => String(candidate.value) === currentValue,
                            )?.value ?? currentValue,
                          ];
                      onChange(nextValues);
                      return;
                    }
                    onChange(
                      options.find(
                        (candidate) => String(candidate.value) === currentValue,
                      )?.value ?? currentValue,
                    );
                    setOpen(false);
                  }}
                >
                  <CheckIcon
                    className={cn(
                      "mr-2 h-4 w-4",
                      selectedKeySet.has(String(option.value))
                        ? "opacity-100"
                        : "opacity-0",
                    )}
                  />
                  {option.label}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

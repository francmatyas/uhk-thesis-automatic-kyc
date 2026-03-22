import { useEffect, useMemo, useRef, useState } from "react";
import axiosInstance from "@/api/axiosInstance";
import { Input } from "@/components/ui/input";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { XIcon } from "lucide-react";

export function SearchSelect({
  endpoint,
  timeout = 300,
  queryMinLength = 2,
  formatter,
  onSelect,
  onClear,
  params,
  limit = 20,
  label,
  placeholder = "Search...",
  emptyMessage = "Type to search",
  closeOnBlur = true,
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const lastRequestId = useRef(0);
  const suppressNextOpen = useRef(false);
  const hasInteracted = useRef(false);
  const isFocused = useRef(false);

  const handleClear = () => {
    setQuery("");
    setResults([]);
    setOpen(false);
    onClear?.();
  };

  const formattedResults = useMemo(() => {
    if (!formatter) {
      return results.map((item) => ({
        label: item?.label || item?.name || item?.email || item?.id || "Item",
        sublabel: item?.email || "",
        value: item?.id || item,
        raw: item,
      }));
    }
    return results.map((item) => ({
      ...formatter(item),
      raw: item,
    }));
  }, [results, formatter]);

  useEffect(() => {
    if (!endpoint) return;

    const trimmed = query.trim();
    if (trimmed.length < queryMinLength) {
      setResults([]);
      setLoading(false);
      return;
    }

    const requestId = ++lastRequestId.current;
    setLoading(true);

    const handle = setTimeout(async () => {
      try {
        const extraParams =
          typeof params === "function" ? params(trimmed) : params || {};
        const response = await axiosInstance.get(endpoint, {
          params: { q: trimmed, limit, ...extraParams },
        });
        if (requestId !== lastRequestId.current) return;

        const data = response?.data;
        const items = Array.isArray(data)
          ? data
          : Array.isArray(data?.items)
            ? data.items
            : Array.isArray(data?.results)
              ? data.results
              : Array.isArray(data?.data)
                ? data.data
                : Array.isArray(data?.data?.items)
                  ? data.data.items
                  : [];

        setResults(items);
        if (!hasInteracted.current || !isFocused.current) {
          return;
        }
        if (suppressNextOpen.current) {
          suppressNextOpen.current = false;
          setOpen(false);
          return;
        }
        if (items.length > 0 || trimmed.length >= queryMinLength) {
          setOpen(true);
        }
      } catch (e) {
        if (requestId !== lastRequestId.current) return;
        setResults([]);
        if (!hasInteracted.current || !isFocused.current) {
          return;
        }
        if (suppressNextOpen.current) {
          suppressNextOpen.current = false;
          setOpen(false);
          return;
        }
        setOpen(true);
      } finally {
        if (requestId === lastRequestId.current) {
          setLoading(false);
        }
      }
    }, timeout);

    return () => clearTimeout(handle);
  }, [endpoint, query, queryMinLength, timeout, limit, params]);

  return (
    <Popover open={open}>
      <PopoverTrigger asChild>
        <div className="w-full">
          <Input
            label={label}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={placeholder}
            onFocus={() => {
              hasInteracted.current = true;
              isFocused.current = true;
              if (formattedResults.length > 0) setOpen(true);
            }}
            onBlur={() => {
              if (!closeOnBlur) return;
              isFocused.current = false;
              setTimeout(() => setOpen(false), 150);
            }}
            append={
              query.length > 0 ? (
                <button
                  type="button"
                  onClick={handleClear}
                  className="text-muted-foreground"
                  aria-label="Clear search"
                >
                  <XIcon className="h-4 w-4" />
                </button>
              ) : null
            }
          />
        </div>
      </PopoverTrigger>
      <PopoverContent
        className="w-[320px] p-0"
        align="start"
        onOpenAutoFocus={(event) => event.preventDefault()}
        onCloseAutoFocus={(event) => event.preventDefault()}
      >
        <div className="border rounded-md max-h-52 overflow-auto">
          {loading && (
            <div className="p-2 text-sm text-muted-foreground">
              Searching...
            </div>
          )}
          {!loading && formattedResults.length === 0 && (
            <div className="p-2 text-sm text-muted-foreground">
              {query.trim().length < queryMinLength
                ? emptyMessage
                : "No users found"}
            </div>
          )}
          {!loading &&
            formattedResults.map((item) => (
              <button
                type="button"
                key={item.value}
                className="w-full text-left p-2 hover:bg-muted/30"
                onClick={() => {
                  onSelect?.(item.raw, item);
                  suppressNextOpen.current = true;
                  setQuery(item.label || "");
                  setOpen(false);
                }}
              >
                <div className="text-sm font-medium">{item.label}</div>
                {item.sublabel && (
                  <div className="text-xs text-muted-foreground">
                    {item.sublabel}
                  </div>
                )}
              </button>
            ))}
        </div>
      </PopoverContent>
    </Popover>
  );
}

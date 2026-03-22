import { Link } from "react-router";
import { useLocation } from "react-router";
import { cn } from "@/lib/utils";

// Sticky sidebar nav for settings pages. Accepts optional className so callers can tweak offset.
export function SettingsPageNav({ children, className }) {
  return (
    <div
      className={cn("sticky top-0 self-start flex flex-col gap-1", className)}
    >
      {children}
    </div>
  );
}

export function SettingsPageNavItem({ children, to }) {
  const { hash } = useLocation();
  const isActive = to.endsWith(hash);

  return (
    <Link to={to}>
      <div
        className={cn(
          // base styles
          "relative px-2 py-1 rounded font-normal text-sm text-neutral-400 hover:text-white cursor-pointer pl-4",
          // pseudo element for the active line (hidden by default)
          "before:content-[''] before:absolute before:left-0 before:top-1 before:bottom-1 before:w-[3px] before:rounded-full before:bg-transparent before:transition-colors before:duration-200",
          // active state styles
          {
            "text-white before:bg-white": isActive,
          }
        )}
      >
        {children}
      </div>
    </Link>
  );
}

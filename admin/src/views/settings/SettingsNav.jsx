import { Link } from "react-router";
import { useLocation } from "react-router";
import { cn } from "@/lib/utils";

export function SettingsNav({ children }) {
  return <div className="flex items-center gap-1">{children}</div>;
}

export function SettingsNavItem({ children, to }) {
  const location = useLocation();
  const isActive = location.pathname.endsWith(to);

  return (
    <Link to={to}>
      <div
        className={cn(
          "px-2 py-1 rounded font-normal text-base hover:bg-neutral-800 hover:text-white cursor-pointer",
          {
            "bg-neutral-800 text-white": isActive,
          }
        )}
      >
        {children}
      </div>
    </Link>
  );
}

import { cn } from "@/lib/utils";
export default function Card({ children, fullPage = false, className }) {
  return (
    <div
      className={cn(
        "rounded-md border bg-card",
        fullPage && "min-h-full",
        className
      )}
    >
      {children}
    </div>
  );
}

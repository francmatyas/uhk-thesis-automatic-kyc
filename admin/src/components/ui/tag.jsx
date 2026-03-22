import { DynamicIcon } from "lucide-react/dynamic";
import { cn } from "@/lib/utils";
import { cva } from "class-variance-authority";

const Tag = ({ label, className, icon, color, ...props }) => {
  const tagVariants = cva(
    "inline-flex gap-1 items-center rounded-md px-2 py-1 text-xs font-medium",
    {
      variants: {
        color: {
          neutral:
            "bg-gray-200 text-gray-800 border-gray-300 dark:bg-gray-950 dark:text-gray-200",
          red: "bg-red-100 text-red-800 border-red-300 dark:bg-red-950 dark:text-red-200",
          green:
            "bg-green-100 text-green-800 border-green-300 dark:bg-green-950 dark:text-green-200",
          blue: "bg-blue-100 text-blue-800 border-blue-300 dark:bg-blue-950 dark:text-blue-200",
          yellow:
            "bg-yellow-100 text-yellow-800 border-yellow-300 dark:bg-yellow-950 dark:text-yellow-200",
          purple:
            "bg-purple-100 text-purple-800 border-purple-300 dark:bg-purple-950 dark:text-purple-200",
          pink: "bg-pink-100 text-pink-800 border-pink-300 dark:bg-pink-950 dark:text-pink-200",
          orange:
            "bg-orange-100 text-orange-800 border-orange-300 dark:bg-orange-950 dark:text-orange-200",
        },
      },
      defaultVariants: {
        color: "neutral",
      },
    }
  );

  return (
    <span className={cn(tagVariants({ color }), className)} {...props}>
      {icon && <DynamicIcon name={icon} size={16} />}
      {label}
    </span>
  );
};
const TagContainer = ({ children, className, ...props }) => {
  return (
    <div
      className={cn("flex flex-wrap items-center gap-2", className)}
      {...props}
    >
      {children}
    </div>
  );
};
export { Tag, TagContainer };

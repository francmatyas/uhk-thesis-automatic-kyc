import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva } from "class-variance-authority";

import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center justify-center rounded-md border px-2 py-0.5 text-xs font-medium w-fit whitespace-nowrap shrink-0 [&>svg]:size-3 gap-1 [&>svg]:pointer-events-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive transition-[color,box-shadow] overflow-hidden",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-primary text-primary-foreground [a&]:hover:bg-primary/90",
        secondary:
          "border-transparent bg-secondary text-secondary-foreground [a&]:hover:bg-secondary/90",
        destructive:
          "border-transparent bg-destructive text-white [a&]:hover:bg-destructive/90 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 dark:bg-destructive/60",
        outline:
          "text-foreground [a&]:hover:bg-accent [a&]:hover:text-accent-foreground",
      },
      status: {
        new: "bg-blue-600 text-blue-50 border-blue-200 dark:bg-blue-600 dark:border-blue-500",
        running:
          "bg-fuchsia-700 text-fuchsia-900 border-fuchsia-200 dark:bg-fuchsia-600 dark:text-fuchsia-50 dark:border-fuchsia-500",
        completed:
          "bg-teal-700 text-teal-900 border-teal-200 dark:bg-teal-700 dark:text-teal-50 dark:border-teal-500",
        cancelled:
          "bg-gray-600 text-gray-50 border-gray-300 dark:bg-gray-700 dark:border-gray-500",
        failed:
          "bg-red-700 text-red-900 border-red-200 dark:bg-red-700 dark:text-red-50 dark:border-red-500",
        queued:
          "bg-yellow-600 text-yellow-900 border-yellow-200 dark:bg-yellow-500 dark:text-yellow-50 dark:border-yellow-500",
        success:
          "bg-green-700 text-green-900 border-green-200 dark:bg-green-700 dark:text-green-50 dark:border-green-500",
        warning:
          "bg-orange-600 text-orange-900 border-orange-200 dark:bg-orange-500 dark:text-orange-50 dark:border-orange-500",
        default:
          "bg-gray-600 text-gray-50 border-gray-300 dark:bg-gray-700 dark:border-gray-500",
        error:
          "bg-red-700 text-red-900 border-red-200 dark:bg-red-700 dark:text-red-50 dark:border-red-500",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  },
);

function Badge({ className, variant, status, asChild = false, ...props }) {
  const Comp = asChild ? Slot : "span";
  status = status?.toLowerCase();

  return (
    <Comp
      data-slot="badge"
      data-status={status}
      className={cn(badgeVariants({ variant, status }), className)}
      {...props}
    />
  );
}

export { Badge, badgeVariants };

import * as React from "react";
import { Link as LinkCore } from "react-router";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";

import { cn } from "@/lib/utils";

function Link({ className, tooltip, tooltipProps, ...props }) {
  if (!tooltip) {
    return <LinkCore data-slot="label" className={cn(className)} {...props} />;
  }
  return (
    <Tooltip>
      <TooltipTrigger className="w-full">
        <LinkCore data-slot="label" className={cn(className)} {...props} />
      </TooltipTrigger>
      <TooltipContent {...tooltipProps}>{tooltip}</TooltipContent>
    </Tooltip>
  );
}

export { Link };

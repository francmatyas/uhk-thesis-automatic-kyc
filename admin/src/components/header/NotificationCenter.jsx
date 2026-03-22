import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Bell } from "lucide-react";

export function NotificationCenter({}) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" className="text-muted-foreground px-3">
          <div className="relative">
            <Bell />
            <div className="absolute top-0 right-0 w-1.5 h-1.5 bg-red-600 rounded-full" />
          </div>
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 sm:w-96" align="end">
        <div className="">
          <h3 className="text-lg font-semibold mb-2">Notifications</h3>
          <p className="text-sm text-muted-foreground">
            You have no new notifications.
          </p>
        </div>
      </PopoverContent>
    </Popover>
  );
}

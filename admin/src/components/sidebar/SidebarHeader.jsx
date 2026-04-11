import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  SidebarMenu,
  SidebarMenuItem,
  SidebarTrigger,
  useSidebar,
} from "@/components/ui/sidebar";
import { cn } from "@/lib/utils";

export default function SidebarHeader({ className, ...props }) {
  const { open } = useSidebar();
  return (
    <SidebarMenu className="flex justify-center" {...props}>
      <SidebarMenuItem className="h-16">
        <div
          className={cn(
            "h-full flex items-center gap-2 p-2",
            open ? "justify-start" : "justify-center"
          )}
        >
          {open && (
            <>
              <Avatar className="h-8 w-8 rounded-lg">
                <AvatarImage src={"/automatic-kyc.png"} alt={"kyc"} />
                <AvatarFallback className="rounded-lg">K</AvatarFallback>
              </Avatar>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-semibold">KYC</span>
                <span className="truncate text-xs">Knowledge Management</span>
              </div>
            </>
          )}
          <SidebarTrigger className="cursor-pointer" />
        </div>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}

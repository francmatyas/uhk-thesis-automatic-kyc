import { useMemo, useState } from "react";
import { toast } from "sonner";
import { Check, ChevronsUpDown } from "lucide-react";
import { useLocation, useNavigate } from "react-router";
import { useAuth } from "@/contexts/AuthContext";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import { cn } from "@/lib/utils";
import { getSwitchTargetPath } from "@/router/scope";
export default function SidebarTenantSwitch({ className }) {
  const { user, tenants, activeTenantId, activeScope, routeTenantSlug } =
    useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { isMobile } = useSidebar();
  const [isSwitching, setIsSwitching] = useState(false);

  const hasProviderOption = Boolean(user?.isProviderUser);
  const tenantList = Array.isArray(tenants) ? tenants : [];
  const shouldRender =
    (hasProviderOption && tenantList.length > 0) || tenantList.length > 1;
  const isProviderActive = activeScope === "provider";
  const activeTenant = useMemo(
    () =>
      tenantList.find(
        (tenant) =>
          tenant?.slug === routeTenantSlug || tenant?.id === activeTenantId,
      ) || null,
    [tenantList, routeTenantSlug, activeTenantId],
  );
  const activeLabel =
    activeTenant?.name || (hasProviderOption ? "Provider" : "");
  const activeInitials = getInitials(activeLabel || "T");

  if (shouldRender) {
    const handleSwitch = async ({ scope, tenantSlug }) => {
      const targetPath = getSwitchTargetPath({
        currentPath: location.pathname,
        targetScope: scope,
        targetTenantSlug: tenantSlug,
      });
      const nextPath = `${targetPath}${location.search || ""}${location.hash || ""}`;

      setIsSwitching(true);
      try {
        navigate(nextPath);
      } catch {
        toast.error("Failed to switch scope. Please try again.");
      } finally {
        setIsSwitching(false);
      }
    };

    return (
      <SidebarMenu className={cn("px-2", className)}>
        <SidebarMenuItem>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <SidebarMenuButton
                size="lg"
                className={cn(
                  "data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground",
                  {
                    "gap-2 bg-diagonal border rounded-md border-orange-800":
                      isProviderActive,
                  },
                )}
                disabled={isSwitching}
                style={
                  isProviderActive
                    ? {
                        "--diag-color": "255 79 0",
                        "--diag-opacity": "0.2",
                        "--diag-gap": "10px",
                      }
                    : undefined
                }
              >
                <Avatar className="h-8 w-8 rounded-lg">
                  <AvatarImage
                    src={activeTenant?.avatarUrl || null}
                    alt={activeLabel}
                  />
                  <AvatarFallback className="rounded-lg">
                    {activeInitials}
                  </AvatarFallback>
                </Avatar>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-semibold">
                    {activeLabel || "Tenant"}
                  </span>
                  <span className="truncate text-xs text-muted-foreground">
                    {activeTenant ? "Tenant Workspace" : "Provider Workspace"}
                  </span>
                </div>
                <ChevronsUpDown className="ml-auto size-4" />
              </SidebarMenuButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              className="w-[--radix-dropdown-menu-trigger-width] min-w-56 rounded-lg"
              side={isMobile ? "bottom" : "right"}
              align="start"
              sideOffset={4}
            >
              <DropdownMenuLabel>Switch Workspace</DropdownMenuLabel>
              <DropdownMenuSeparator />
              {hasProviderOption && (
                <>
                  <DropdownMenuItem
                    onClick={() => handleSwitch({ scope: "provider" })}
                    disabled={isSwitching}
                    className="gap-2 bg-diagonal border rounded-md border-orange-800"
                    style={{
                      "--diag-color": "255 79 0", // RGB, space-separated
                      "--diag-opacity": "0.2",
                      "--diag-gap": "10px",
                    }}
                  >
                    <Avatar className="h-6 w-6 rounded-md">
                      <AvatarImage src={null} alt="Provider" />
                      <AvatarFallback className="rounded-md">
                        {getInitials("Provider")}
                      </AvatarFallback>
                    </Avatar>
                    <span className="flex-1">Provider</span>
                    {isProviderActive && (
                      <Check className="size-4 text-muted-foreground" />
                    )}
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                </>
              )}
              {tenantList.map((tenant) => {
                const isActive = tenant?.id === activeTenantId;
                const tenantLabel = tenant?.name || "Tenant";
                return (
                  <DropdownMenuItem
                    key={tenant.id}
                    onClick={() =>
                      handleSwitch({
                        scope: "tenant",
                        tenantSlug: tenant.slug || routeTenantSlug,
                      })
                    }
                    disabled={isSwitching || !(tenant.slug || routeTenantSlug)}
                    className="gap-2"
                  >
                    <Avatar className="h-6 w-6 rounded-md">
                      <AvatarImage
                        src={tenant?.avatarUrl || null}
                        alt={tenantLabel}
                      />
                      <AvatarFallback className="rounded-md">
                        {getInitials(tenantLabel)}
                      </AvatarFallback>
                    </Avatar>
                    <span className="flex-1 truncate">{tenantLabel}</span>
                    {isActive && activeScope === "tenant" && (
                      <Check className="size-4 text-muted-foreground" />
                    )}
                  </DropdownMenuItem>
                );
              })}
            </DropdownMenuContent>
          </DropdownMenu>
        </SidebarMenuItem>
      </SidebarMenu>
    );
  }
  return null;
}

function getInitials(value) {
  if (!value) return "T";
  const parts = value.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "T";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

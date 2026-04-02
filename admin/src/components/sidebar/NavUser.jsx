import {
  ChevronsUpDown,
  LogOut,
  User,
  Sparkles,
  Settings,
} from "lucide-react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
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

import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";

import { useAuth } from "@/contexts/AuthContext";
import * as authApi from "@/api/auth";
import { toProviderPath, toTenantPath } from "@/router/scope";
import { queryClient } from "@/main";

export default function NavUser() {
  const { t } = useTranslation();
  const { isMobile } = useSidebar();
  const navigate = useNavigate();

  const { user, setProfile, activeScope, routeTenantSlug } = useAuth();
  const settingsBasePath =
    activeScope === "tenant" && routeTenantSlug
      ? toTenantPath(routeTenantSlug, "/settings")
      : toProviderPath("/settings");
  const profilePath = `${settingsBasePath}/profile`;
  return (
    <>
      <SidebarMenu>
        <SidebarMenuItem>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <SidebarMenuButton
                size="lg"
                className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground cursor-pointer"
              >
                <Avatar className="h-8 w-8 rounded-lg">
                  <AvatarImage src={user?.avatarUrl} alt={user?.fullName} />
                  <AvatarFallback className="rounded-lg">
                    {user?.givenName?.charAt(0)}
                    {user?.familyName?.charAt(0)}
                  </AvatarFallback>
                </Avatar>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-semibold">
                    {user?.fullName}
                  </span>
                  <span className="truncate text-xs">{user?.email}</span>
                </div>
                <ChevronsUpDown className="ml-auto size-4" />
              </SidebarMenuButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              className="w-[--radix-dropdown-menu-trigger-width] min-w-56 rounded-lg"
              side={isMobile ? "bottom" : "right"}
              align="end"
              sideOffset={4}
            >
              <DropdownMenuLabel className="p-0 font-normal">
                <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                  <Avatar className="h-8 w-8 rounded-lg">
                    <AvatarImage src={user?.avatarUrl} alt={user?.fullName} />
                    <AvatarFallback className="rounded-lg">
                      {user?.givenName?.charAt(0)}
                      {user?.familyName?.charAt(0)}
                    </AvatarFallback>
                  </Avatar>
                  <div className="grid flex-1 text-left text-sm leading-tight">
                    <span className="truncate font-semibold">
                      {user?.fullName}
                    </span>
                    <span className="truncate text-xs">{user?.email}</span>
                  </div>
                </div>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <Link to={profilePath}>
                  <DropdownMenuItem>
                    <User />
                    {t("sidebar.userMenu.profile")}
                  </DropdownMenuItem>
                </Link>
                <Link to={settingsBasePath}>
                  <DropdownMenuItem>
                    <Settings />
                    {t("sidebar.userMenu.settings")}
                  </DropdownMenuItem>
                </Link>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={async () => {
                  try {
                    await authApi.logout();
                  } catch {}
                  queryClient.clear();
                  setProfile(null);
                  navigate("/login");
                }}
              >
                <LogOut />
                {t("sidebar.userMenu.logout")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </SidebarMenuItem>
      </SidebarMenu>
    </>
  );
}

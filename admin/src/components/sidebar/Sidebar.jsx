import * as React from "react";
import SidebarHeader from "@/components/sidebar/SidebarHeader";
import Nav from "@/components/sidebar/Nav";
import NavUser from "@/components/sidebar/NavUser";
import { useAuth } from "@/contexts/AuthContext";

import {
  Sidebar as SidebarRoot,
  SidebarContent,
  SidebarFooter,
  SidebarHeader as SidebarHeaderRoot,
  SidebarRail,
} from "@/components/ui/sidebar";
import SidebarTenantSwitch from "./SidebarTenantSwitch";
import _menuConfig from "./_menuConfig";
function buildMenuSections(config, hasPermission) {
  const sections = Object.values(config);

  return sections
    .map((section) => {
      const items = section.items
        .filter((item) => {
          if (!item.requires || item.requires.length === 0) return true;
          return hasPermission(item.requires, { mode: "all" });
        })
        .filter((item) => Boolean(item.path))
        .map((item) => ({
          title: item.label,
          url: item.path,
          icon: item.icon,
          action: item.action,
        }));

      if (items.length === 0) return null;
      
      return {
        label: section.label,
        admin: section.admin || false,
        items,
      };
    })
    .filter(Boolean);
}

export default function Sidebar({ ...props }) {
  const { hasPermission, activeScope, routeTenantSlug } = useAuth();
  const sections = React.useMemo(
    () =>
      buildMenuSections(
        _menuConfig({ scope: activeScope, tenantSlug: routeTenantSlug }),
        hasPermission,
      ),
    [hasPermission, activeScope, routeTenantSlug],
  );
  const clientSections = sections.filter((section) => !section.admin);
  const adminSections = sections.filter((section) => section.admin);
  return (
    <SidebarRoot collapsible="icon" {...props}>
      <SidebarHeaderRoot className="p-0">
        <SidebarHeader className="" />
        <SidebarTenantSwitch className="mb-4" />
      </SidebarHeaderRoot>
      <SidebarContent>
        {clientSections.map((section, index) => (
          <Nav key={index} conf={section} />
        ))}
        {adminSections.length > 0 && (
          <div
            className="bg-diagonal border rounded-md border-orange-800"
            style={{
              "--diag-color": "255 79 0", // RGB, space-separated
              "--diag-opacity": "0.2",
              "--diag-gap": "10px",
            }}
          >
            {adminSections.map((section, index) => (
              <Nav key={index} conf={section} admin />
            ))}
          </div>
        )}
      </SidebarContent>
      <SidebarFooter className={"mb-2"}>
        <NavUser />
      </SidebarFooter>
      <SidebarRail />
    </SidebarRoot>
  );
}

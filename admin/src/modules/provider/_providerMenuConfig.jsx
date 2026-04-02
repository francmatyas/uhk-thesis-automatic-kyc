import {
  LayoutDashboard,
  Users,
  ShieldUser,
  KeyRound,
  Building,
  ScrollText,
  ShieldCheck,
  FileCode2,
} from "lucide-react";
import { toProviderPath } from "@/router/scope";

const providerPath = (path = "/") => toProviderPath(path);

export default function providerMenuConfig() {
  return {
    _: {
      items: [
        {
          label: "Dashboard",
          labelKey: "menu.provider.dashboard",
          path: providerPath("/"),
          icon: LayoutDashboard,
        },
        {
          label: "Verifications",
          labelKey: "menu.provider.verifications",
          path: providerPath("/verifications"),
          icon: ShieldCheck,
          requires: ["provider.verifications:read"],
        },
        {
          label: "Journey Templates",
          labelKey: "menu.provider.journeyTemplates",
          path: providerPath("/journey-templates"),
          icon: FileCode2,
          requires: ["provider.journey-templates:read"],
        },
      ],
    },
    management: {
      label: "Management",
      labelKey: "menu.provider.management",
      items: [
        {
          label: "Users",
          labelKey: "menu.provider.users",
          path: providerPath("/users"),
          icon: Users,
          requires: ["provider.users:read"],
        },
        {
          label: "Tenants",
          labelKey: "menu.provider.tenants",
          path: providerPath("/tenants"),
          icon: Building,
          requires: ["provider.tenants:read"],
        },
        {
          label: "Roles",
          labelKey: "menu.provider.roles",
          path: providerPath("/roles"),
          icon: ShieldUser,
          requires: ["provider.roles:read"],
        },
        {
          label: "Permissions",
          labelKey: "menu.provider.permissions",
          path: providerPath("/permissions"),
          icon: KeyRound,
          requires: ["provider.permissions:read"],
        },
        {
          label: "Audit Logs",
          labelKey: "menu.provider.auditLogs",
          path: providerPath("/audit-logs"),
          icon: ScrollText,
          requires: ["provider.audit-logs:read"],
        },
      ],
    },
  };
}

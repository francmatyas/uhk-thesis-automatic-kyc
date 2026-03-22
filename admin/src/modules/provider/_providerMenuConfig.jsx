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
          path: providerPath("/"),
          icon: LayoutDashboard,
        },
        {
          label: "Verifications",
          path: providerPath("/verifications"),
          icon: ShieldCheck,
          requires: ["provider.verifications:read"],
        },
        {
          label: "Journey Templates",
          path: providerPath("/journey-templates"),
          icon: FileCode2,
          requires: ["provider.journey-templates:read"],
        },
      ],
    },
    management: {
      label: "Management",
      items: [
        {
          label: "Users",
          path: providerPath("/users"),
          icon: Users,
          requires: ["provider.users:read"],
        },
        {
          label: "Tenants",
          path: providerPath("/tenants"),
          icon: Building,
          requires: ["provider.tenants:read"],
        },
        {
          label: "Roles",
          path: providerPath("/roles"),
          icon: ShieldUser,
          requires: ["provider.roles:read"],
        },
        {
          label: "Permissions",
          path: providerPath("/permissions"),
          icon: KeyRound,
          requires: ["provider.permissions:read"],
        },
        {
          label: "Audit Logs",
          path: providerPath("/audit-logs"),
          icon: ScrollText,
          requires: ["provider.audit-logs:read"],
        },
      ],
    },
  };
}

import {
  LayoutDashboard,
  KeyRound,
  Webhook,
  Building,
  Users,
  ScrollText,
  ShieldCheck,
  FileCode2,
} from "lucide-react";
import { toTenantPath } from "@/router/scope";

export default function tenantMenuConfig({ tenantSlug }) {
  const tenantPath = (path = "/") =>
    tenantSlug ? toTenantPath(tenantSlug, path) : null;

  return {
    _: {
      items: [
        {
          label: "Dashboard",
          labelKey: "menu.tenant.dashboard",
          path: tenantPath("/"),
          icon: LayoutDashboard,
        },
        {
          label: "Verifications",
          labelKey: "menu.tenant.verifications",
          path: tenantPath("/verifications"),
          icon: ShieldCheck,
          requires: ["tenant.verifications:read"],
        },
        {
          label: "Journey Templates",
          labelKey: "menu.tenant.journeyTemplates",
          path: tenantPath("/journey-templates"),
          icon: FileCode2,
          requires: ["tenant.journey-templates:read"],
        },
      ],
    },
    administration: {
      label: "Administration",
      labelKey: "menu.tenant.administration",
      admin: true,
      items: [
        {
          label: "Team Settings",
          labelKey: "menu.tenant.teamSettings",
          path: tenantPath("/team-settings"),
          icon: Building,
          requires: ["tenant.tenants:read"],
        },
        {
          label: "Members",
          labelKey: "menu.tenant.members",
          path: tenantPath("/members"),
          icon: Users,
          requires: ["tenant.members:read"],
        },
        {
          label: "API Keys",
          labelKey: "menu.tenant.apiKeys",
          path: tenantPath("/api-keys"),
          icon: KeyRound,
          requires: ["tenant.api-keys:read"],
        },
        {
          label: "Webhooks",
          labelKey: "menu.tenant.webhooks",
          path: tenantPath("/webhooks"),
          icon: Webhook,
          requires: ["tenant.webhooks:read"],
        },
        {
          label: "Audit Logs",
          labelKey: "menu.tenant.auditLogs",
          path: tenantPath("/audit-logs"),
          icon: ScrollText,
          requires: ["tenant.audit-logs:read"],
        },
      ],
    },
  };
}

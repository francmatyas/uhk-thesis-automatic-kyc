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
          path: tenantPath("/"),
          icon: LayoutDashboard,
        },
        {
          label: "Verifications",
          path: tenantPath("/verifications"),
          icon: ShieldCheck,
          requires: ["tenant.verifications:read"],
        },
        {
          label: "Journey Templates",
          path: tenantPath("/journey-templates"),
          icon: FileCode2,
          requires: ["tenant.journey-templates:read"],
        },
      ],
    },
    administration: {
      label: "Administration",
      admin: true,
      items: [
        {
          label: "Team Settings",
          path: tenantPath("/team-settings"),
          icon: Building,
          requires: ["tenant.tenants:read"],
        },
        {
          label: "Members",
          path: tenantPath("/members"),
          icon: Users,
          requires: ["tenant.members:read"],
        },
        {
          label: "API Keys",
          path: tenantPath("/api-keys"),
          icon: KeyRound,
          requires: ["tenant.api-keys:read"],
        },
        {
          label: "Webhooks",
          path: tenantPath("/webhooks"),
          icon: Webhook,
          requires: ["tenant.webhooks:read"],
        },
        {
          label: "Audit Logs",
          path: tenantPath("/audit-logs"),
          icon: ScrollText,
          requires: ["tenant.audit-logs:read"],
        },
      ],
    },
  };
}

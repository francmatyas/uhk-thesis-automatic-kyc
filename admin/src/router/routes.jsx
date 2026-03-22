import Layout from "@/components/layout/Layout";
import AuthLayout from "@/components/layout/AuthLayout";

import LoginForm from "@/components/auth/LoginForm";
import RegisterForm from "@/components/auth/RegisterForm";
import ForgotPasswordForm from "@/components/auth/ForgotPasswordForm";

import Table from "@/components/table/Table";

import Dashboard from "@/views/dashboard/Dashboard";
import UserDetail from "@/views/users/UserDetail";
import UserTable from "@/views/users/UserTable";

import SettingsLayout from "@/views/settings/SettingsLayout";
import SettingsGeneral from "@/views/settings/SettingsGeneral";
import SettingsProfile from "@/views/settings/SettingsProfile";
import SettingsSecurity from "@/views/settings/SettingsSecurity";
import { SettingsNav, SettingsNavItem } from "@/views/settings/SettingsNav";
import RoleDetail from "@/views/roles/RoleDetail";
import RoleTable from "@/views/roles/RoleTable";
import PermissionDetail from "@/views/permissions/PermissionDetail";
import PermissionTable from "@/views/permissions/PermissionTable";
import TenantDetail from "@/views/tenants/provider/TenantDetail";
import TenantTable from "@/views/tenants/provider/TenantTable";
import ApiKeysDetail from "@/views/apiKeys/ApiKeysDetail";
import ApiKeysTable from "@/views/apiKeys/ApiKeysTable";
import WebhooksDetail from "@/views/webhooks/WebhooksDetail";
import WebhooksTable from "@/views/webhooks/WebhooksTable";
import TenantMeDetail from "@/views/tenants/TenantMeDetail";
import MemberTable from "@/views/users/MemberTable";
import MemberDetail from "@/views/users/MemberDetail";
import ProviderAuditLogTable from "@/views/auditLogs/ProviderAuditLogTable";
import ProviderAuditLogDetail from "@/views/auditLogs/ProviderAuditLogDetail";
import TenantAuditLogTable from "@/views/auditLogs/TenantAuditLogTable";
import TenantAuditLogDetail from "@/views/auditLogs/TenantAuditLogDetail";
import ProviderVerificationTable from "@/views/verifications/ProviderVerificationTable";
import ProviderVerificationDetail from "@/views/verifications/ProviderVerificationDetail";
import TenantVerificationTable from "@/views/verifications/TenantVerificationTable";
import TenantVerificationDetail from "@/views/verifications/TenantVerificationDetail";
import ProviderJourneyTemplateTable from "@/views/journeyTemplates/ProviderJourneyTemplateTable";
import ProviderJourneyTemplateDetail from "@/views/journeyTemplates/ProviderJourneyTemplateDetail";
import TenantJourneyTemplateTable from "@/views/journeyTemplates/TenantJourneyTemplateTable";
import TenantJourneyTemplateDetail from "@/views/journeyTemplates/TenantJourneyTemplateDetail";

import { Navigate } from "react-router";
import {
  ProviderScopeGuard,
  ScopeLandingRedirect,
  TenantScopeGuard,
} from "@/router/ScopeGuards";

const sharedSettingsRoute = {
  path: "settings",
  element: <SettingsLayout />,
  headerControls: (
    <SettingsNav>
      <SettingsNavItem to="settings/general">General</SettingsNavItem>
      <SettingsNavItem to="settings/profile">Profile</SettingsNavItem>
      <SettingsNavItem to="settings/security">Security</SettingsNavItem>
    </SettingsNav>
  ),
  breadcrumb: "Settings",
  redirect: "general",
  children: [
    {
      path: "general",
      element: <SettingsGeneral />,
      breadcrumb: "General",
    },
    {
      path: "profile",
      element: <SettingsProfile />,
      breadcrumb: "Profile",
    },
    {
      path: "security",
      element: <SettingsSecurity />,
      breadcrumb: "Security",
    },
  ],
};

const tenantScopedRoutes = [
  { index: true, element: <Dashboard />, breadcrumb: "Dashboard" },
  sharedSettingsRoute,
  {
    path: "api-keys",
    breadcrumb: "API Keys",
    children: [
      {
        index: true,
        element: <ApiKeysTable />,
      },
      {
        path: "new",
        element: <ApiKeysDetail mode="create" />,
        breadcrumb: "New API Key",
      },
      {
        path: ":apiKeyId",
        element: <ApiKeysDetail mode="edit" />,
        breadcrumb: {
          key: "apiKeys",
          param: "apiKeyId",
        },
      },
    ],
  },
  {
    path: "webhooks",
    breadcrumb: "Webhooks",
    children: [
      {
        index: true,
        element: <WebhooksTable />,
      },
      {
        path: "new",
        element: <WebhooksDetail mode="create" />,
        breadcrumb: "New Webhook",
      },
      {
        path: ":webhookId",
        element: <WebhooksDetail mode="edit" />,
        breadcrumb: {
          key: "webhooks",
          param: "webhookId",
        },
      },
    ],
  },
  {
    path: "team-settings",
    breadcrumb: "Team Settings",
    element: <TenantMeDetail mode="edit" />,
  },
  {
    path: "audit-logs",
    breadcrumb: "Audit Logs",
    children: [
      {
        index: true,
        element: <TenantAuditLogTable />,
      },
      {
        path: ":auditLogId",
        element: <TenantAuditLogDetail mode="edit" />,
        breadcrumb: {
          key: "auditLogs",
          param: "auditLogId",
        },
      },
    ],
  },
  {
    path: "members",
    breadcrumb: "Members",
    children: [
      {
        index: true,
        element: <MemberTable />,
      },
      {
        path: ":memberId",
        element: <MemberDetail mode="edit" />,
        breadcrumb: {
          key: "members",
          param: "memberId",
        },
      },
    ],
  },
  {
    path: "verifications",
    breadcrumb: "Verifications",
    children: [
      {
        index: true,
        element: <TenantVerificationTable />,
      },
      {
        path: ":verificationId",
        element: <TenantVerificationDetail mode="edit" />,
        breadcrumb: {
          key: "verifications",
          param: "verificationId",
        },
      },
    ],
  },
  {
    path: "journey-templates",
    breadcrumb: "Journey Templates",
    children: [
      {
        index: true,
        element: <TenantJourneyTemplateTable />,
      },
      {
        path: "new",
        element: <TenantJourneyTemplateDetail mode="create" />,
        breadcrumb: "New Template",
      },
      {
        path: ":journeyTemplateId",
        element: <TenantJourneyTemplateDetail mode="edit" />,
        breadcrumb: {
          key: "journeyTemplates",
          param: "journeyTemplateId",
        },
      },
    ],
  },
];

const providerScopedRoutes = [
  { index: true, element: <Dashboard />, breadcrumb: "Dashboard" },
  sharedSettingsRoute,
  {
    path: "tenants",
    breadcrumb: "Tenants",
    children: [
      {
        index: true,
        element: <TenantTable />,
      },
      {
        path: "new",
        element: <TenantDetail mode="create" />,
        breadcrumb: "New Tenant",
      },
      {
        path: ":tenantId",
        element: <TenantDetail mode="edit" />,
        breadcrumb: {
          key: "tenants",
          param: "tenantId",
        },
      },
    ],
  },
  {
    path: "roles",
    breadcrumb: "Roles",
    children: [
      {
        index: true,
        element: <RoleTable />,
      },
      {
        path: "new",
        element: <RoleDetail mode="create" />,
        breadcrumb: "New Role",
      },
      {
        path: ":roleId",
        element: <RoleDetail mode="edit" />,
        breadcrumb: {
          key: "roles",
          param: "roleId",
        },
      },
    ],
  },
  {
    path: "permissions",
    breadcrumb: "Permissions",
    children: [
      {
        index: true,
        element: <PermissionTable />,
      },
      {
        path: "new",
        element: <PermissionDetail mode="create" />,
        breadcrumb: "New Permission",
      },
      {
        path: ":permissionId",
        element: <PermissionDetail mode="edit" />,
        breadcrumb: {
          key: "permissions",
          param: "permissionId",
        },
      },
    ],
  },
  {
    path: "audit-logs",
    breadcrumb: "Audit Logs",
    children: [
      {
        index: true,
        element: <ProviderAuditLogTable />,
      },
      {
        path: ":auditLogId",
        element: <ProviderAuditLogDetail mode="edit" />,
        breadcrumb: {
          key: "auditLogs",
          param: "auditLogId",
        },
      },
    ],
  },
  {
    path: "users",
    breadcrumb: "Users",
    children: [
      {
        index: true,
        element: <UserTable />,
      },
      {
        path: "new",
        element: <UserDetail mode="create" />,
        breadcrumb: "New User",
      },
      {
        path: ":userId",
        element: <UserDetail mode="edit" />,
        breadcrumb: {
          key: "users",
          param: "userId",
        },
      },
    ],
  },
  {
    path: "verifications",
    breadcrumb: "Verifications",
    children: [
      {
        index: true,
        element: <ProviderVerificationTable />,
      },
      {
        path: ":verificationId",
        element: <ProviderVerificationDetail mode="edit" />,
        breadcrumb: {
          key: "verifications",
          param: "verificationId",
        },
      },
    ],
  },
  {
    path: "journey-templates",
    breadcrumb: "Journey Templates",
    children: [
      {
        index: true,
        element: <ProviderJourneyTemplateTable />,
      },
      {
        path: "new",
        element: <ProviderJourneyTemplateDetail mode="create" />,
        breadcrumb: "New Template",
      },
      {
        path: ":journeyTemplateId",
        element: <ProviderJourneyTemplateDetail mode="edit" />,
        breadcrumb: {
          key: "journeyTemplates",
          param: "journeyTemplateId",
        },
      },
    ],
  },
];

// Base route definitions (allow custom `redirect` property on any route)
const baseRoutes = [
  {
    path: "/",
    children: [
      {
        path: "login",
        element: <AuthLayout />,
        children: [{ index: true, element: <LoginForm /> }],
      },
      {
        path: "register",
        element: <AuthLayout />,
        children: [{ index: true, element: <RegisterForm /> }],
      },
      {
        path: "forgot-password",
        element: <AuthLayout />,
        children: [{ index: true, element: <ForgotPasswordForm /> }],
      },
      {
        element: <Layout />,
        children: [
          {
            index: true,
            element: <ScopeLandingRedirect />,
          },
        ],
      },
      {
        path: "p",
        element: <ProviderScopeGuard />,
        children: [
          {
            element: <Layout />,
            children: providerScopedRoutes,
          },
        ],
      },
      {
        path: "t/:tenantSlug",
        element: <TenantScopeGuard />,
        children: [
          {
            element: <Layout />,
            children: tenantScopedRoutes,
          },
        ],
      },
      { path: "*", element: <div>404 Not Found</div> },
    ],
  },
];

function applyRedirects(routeList) {
  return routeList.map((r) => {
    const route = { ...r };
    if (route.redirect) {
      route.children = route.children || [];
      const hasIndex = route.children.some((c) => c.index);
      if (!hasIndex) {
        route.children.unshift({
          index: true,
          element: <Navigate to={route.redirect} replace />,
        });
      }
    }
    if (route.children) {
      route.children = applyRedirects(route.children);
    }
    return route;
  });
}

export const routes = applyRedirects(baseRoutes);

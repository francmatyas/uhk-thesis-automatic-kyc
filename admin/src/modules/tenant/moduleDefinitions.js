import { ConfigJsonEditor } from "@/components/ConfigJsonEditor";
import { fetchAuditLog as fetchTenantAuditLog } from "@/api/tenant/auditLogs";
import {
  createApiKey,
  deleteApiKey,
  fetchApiKey,
  updateApiKey,
} from "@/api/tenant/apiKeys";
import {
  createWebhook,
  deleteWebhook,
  fetchWebhook,
  updateWebhook,
} from "@/api/tenant/webhooks";
import { fetchTenantMe, updateTenantMe } from "@/api/tenant/tenantMe";
import { fetchUser, updateUser } from "@/api/tenant/users";
import { fetchVerification } from "@/api/tenant/verifications";
import {
  createJourneyTemplate,
  deleteJourneyTemplate,
  fetchJourneyTemplate,
  updateJourneyTemplate,
} from "@/api/tenant/journeyTemplates";
import { queryKeys } from "@/modules/queryKeys";
import { toTenantPath } from "@/router/scope";
import { createElement } from "react";
import WebhookEventTypesField from "@/views/webhooks/WebhookEventTypesField";

export const tenantModuleDefinitions = {
  auditLogs: {
    key: "auditLogs",
    queryKeys: queryKeys.auditLogs,
    api: {
      fetchOne: fetchTenantAuditLog,
      createOne: async () => {
        throw new Error("Creating audit logs is not supported.");
      },
      updateOne: async () => {
        throw new Error("Updating audit logs is not supported.");
      },
      deleteOne: async () => {
        throw new Error("Deleting audit logs is not supported.");
      },
    },
    table: {
      module: "auditLogs",
      basePath: "/tenants/audit-logs",
      showCreateButton: false,
      searchPlaceholder: "Search Audit Logs",
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/audit-logs"),
      detail: (tenantSlug, id) => toTenantPath(tenantSlug, `/audit-logs/${id}`),
    },
    detail: {
      idParam: "auditLogId",
      breadcrumb: {
        key: "auditLogs",
        labelField: "action",
      },
      defaultValues: {},
      showDelete: false,
      showCreate: false,
      readOnly: true,
      fields: [
        { name: "action", type: "text", label: "Action" },
        { name: "actorType", type: "text", label: "Actor Type" },
        { name: "entityType", type: "text", label: "Entity Type" },
        { name: "result", type: "text", label: "Status" },
        { name: "errorCode", type: "text", label: "Error Code" },
        {
          name: "createdAt",
          type: "datetime",
          label: "Timestamp",
          fullWidth: true,
        },
      ],
    },
  },
  tenantMe: {
    key: "tenantMe",
    queryKeys: queryKeys.tenants,
    api: {
      fetchOne: () => fetchTenantMe(),
      createOne: (payload) => updateTenantMe(payload),
      updateOne: (_id, payload) => updateTenantMe(payload),
      deleteOne: async () => {
        throw new Error("Tenant self deletion is not supported.");
      },
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/team-settings"),
      detail: (tenantSlug) => toTenantPath(tenantSlug, "/team-settings"),
    },
    messages: {
      confirmDelete: "Are you sure you want to delete this tenant?",
      success: {
        create: "Tenant saved successfully!",
        update: "Tenant saved successfully!",
        remove: "Tenant deleted successfully.",
      },
      error: {
        save: "Failed to save tenant. Please try again.",
        remove: "Failed to delete tenant. Please try again.",
      },
    },
    detail: {
      idParam: "tenantSlug",
      breadcrumb: {
        key: "tenants",
        labelField: "name",
      },
      sectionTitle: "Tenant",
      defaultValues: {
        name: "",
        slug: "",
        active: "ACTIVE",
      },
      permissions: {
        update: "tenant.tenants:update",
      },
      actionLabels: {
        save: "Save Changes",
      },
      showDelete: false,
      showCreate: false,
      fields: [
        {
          name: "name",
          type: "text",
          label: "Name",
          props: {
            readOnly: true,
          },
        },
        {
          name: "slug",
          type: "text",
          label: "Slug",
          props: {
            readOnly: true,
          },
        },
      ],
    },
  },
  apiKeys: {
    key: "apiKeys",
    queryKeys: queryKeys.apiKeys,
    api: {
      fetchOne: fetchApiKey,
      createOne: createApiKey,
      updateOne: updateApiKey,
      deleteOne: deleteApiKey,
    },
    table: {
      module: "apiKeys",
      basePath: "/integrations/api-keys",
      createPath: (tenantSlug) => toTenantPath(tenantSlug, "/api-keys/new"),
      createLabel: "New API Key",
      searchPlaceholder: "Search API Keys",
      createPermission: "tenant.api-keys:create",
      enumConfig: {
        status: {
          type: "ENUM",
          displayMode: "badge", // or "text"
          enumValues: [
            { value: "ACTIVE", label: "Active", status: "new" },
            { value: "REVOKED", label: "Revoked", status: "failed" },
          ],
        },
      },
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/api-keys"),
      detail: (tenantSlug, id) => toTenantPath(tenantSlug, `/api-keys/${id}`),
    },
    messages: {
      confirmDelete: "Are you sure you want to delete this api key?",
      success: {
        create: "API key created successfully!",
        update: "API key saved successfully!",
        remove: "API key deleted successfully.",
      },
      error: {
        save: "Failed to save api key. Please try again.",
        remove: "Failed to delete api key. Please try again.",
      },
    },
    detail: {
      idParam: "apiKeyId",
      breadcrumb: {
        key: "apiKeys",
        labelField: "name",
      },
      defaultValues: {
        name: "",
        description: "",
        scope: "TENANT",
        priority: 0,
      },
      permissions: {
        create: "tenant.api-keys:create",
        update: "tenant.api-keys:update",
        delete: "tenant.api-keys:delete",
      },
      actionLabels: {
        create: "Create API Key",
        save: "Save Changes",
        delete: "Delete",
      },
      fields: [
        {
          name: "name",
          type: "text",
          label: "Name",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "status",
          type: "enum",
          label: "Status",
          options: [
            { value: "ACTIVE", label: "Active" },
            { value: "REVOKED", label: "Revoked" },
          ],
        },
        {
          name: "publicKey",
          type: "text",
          label: "Public Key",
          fullWidth: true,
          props: {
            readOnly: true,
            clickToCopy: true,
          },
        },
        {
          name: "lastUsedAt",
          type: "datetime",
          label: "Last Used At",
          props: {
            readOnly: true,
          },
        },
        {
          name: "createdAt",
          type: "datetime",
          label: "Created At",
          props: {
            readOnly: true,
          },
        },
      ],
    },
  },
  webhooks: {
    key: "webhooks",
    queryKeys: queryKeys.webhooks,
    api: {
      fetchOne: fetchWebhook,
      createOne: createWebhook,
      updateOne: updateWebhook,
      deleteOne: deleteWebhook,
    },
    table: {
      module: "webhooks",
      basePath: "/integrations/webhooks",
      createPath: (tenantSlug) => toTenantPath(tenantSlug, "/webhooks/new"),
      createLabel: "New Webhook",
      searchPlaceholder: "Search Webhooks",
      createPermission: "tenant.webhooks:create",
      enumConfig: {
        status: {
          type: "ENUM",
          displayMode: "badge", // or "text"
          enumValues: [
            { value: "ACTIVE", label: "Active", status: "new" },
            { value: "DISABLED", label: "Disabled", status: "failed" },
          ],
        },
      },
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/webhooks"),
      detail: (tenantSlug, id) => toTenantPath(tenantSlug, `/webhooks/${id}`),
    },
    messages: {
      confirmDelete: "Are you sure you want to delete this webhook?",
      success: {
        create: "Webhook created successfully!",
        update: "Webhook saved successfully!",
        remove: "Webhook deleted successfully.",
      },
      error: {
        save: "Failed to save webhook. Please try again.",
        remove: "Failed to delete webhook. Please try again.",
      },
    },
    detail: {
      idParam: "webhookId",
      breadcrumb: {
        key: "webhooks",
        labelField: "url",
      },
      defaultValues: {
        url: "",
        status: "ACTIVE",
        eventTypes: [],
        secretPreview: "",
      },
      permissions: {
        create: "tenant.webhooks:create",
        update: "tenant.webhooks:update",
        delete: "tenant.webhooks:delete",
      },
      actionLabels: {
        create: "Create Webhook",
        save: "Save Changes",
        delete: "Delete",
      },
      transformEntityForForm: (entity) => ({
        ...entity,
        eventTypes: Array.isArray(entity?.eventTypes) ? entity.eventTypes : [],
      }),
      transformSubmit: (data) => ({
        ...data,
        eventTypes: Array.isArray(data?.eventTypes) ? data.eventTypes : [],
      }),
      fields: ({ control, entity }) => [
        {
          name: "url",
          type: "text",
          label: "URL",
          props: {
            readOnly: true,
            clickToCopy: true,
          },
        },
        {
          name: "status",
          type: "enum",
          label: "Status",
          options: [
            { value: "ACTIVE", label: "Active" },
            { value: "DISABLED", label: "Disabled" },
          ],
        },
        {
          name: "eventTypes",
          fullWidth: true,
          render: ({ readOnly }) =>
            createElement(WebhookEventTypesField, {
              control,
              readOnly,
              options: entity?.eventTypeOptions || [],
            }),
        },
        {
          name: "lastDeliveryAt",
          type: "datetime",
          label: "Last Delivery At",
          props: {
            readOnly: true,
          },
        },
        {
          name: "createdAt",
          type: "datetime",
          label: "Created At",
          props: {
            readOnly: true,
          },
        },
      ],
    },
  },
  members: {
    key: "members",
    queryKeys: queryKeys.members,
    api: {
      fetchOne: fetchUser,
      updateOne: updateUser,
      createOne: async () => {
        throw new Error("Creating members is not supported.");
      },
      deleteOne: async () => {
        throw new Error("Deleting members is not supported.");
      },
    },
    table: {
      module: "users",
      basePath: "/users",
      showCreateButton: false,
      searchPlaceholder: "Search Members",
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/members"),
      detail: (tenantSlug, id) => toTenantPath(tenantSlug, `/members/${id}`),
    },
    messages: {
      confirmDelete: "Are you sure you want to delete this member?",
      success: {
        update: "Member saved successfully!",
      },
      error: {
        save: "Failed to save member. Please try again.",
      },
    },
    detail: {
      idParam: "memberId",
      breadcrumb: {
        key: "members",
        labelField: "fullName",
      },
      defaultValues: {
        email: "",
        role: "",
      },
      permissions: {
        update: "tenant.members:update",
      },
      actionLabels: {
        save: "Save Changes",
      },
      showDelete: false,
      showCreate: false,
      fields: [
        {
          name: "givenName",
          type: "text",
          label: "Given Name",
          props: {
            readOnly: true,
          },
        },
        {
          name: "familyName",
          type: "text",
          label: "Family Name",
          props: {
            readOnly: true,
          },
        },
        {
          name: "email",
          type: "text",
          label: "Email",
          props: {
            readOnly: true,
          },
          fullWidth: true,
        },
      ],
    },
  },
  verifications: {
    key: "verifications",
    queryKeys: queryKeys.verifications,
    api: {
      fetchOne: fetchVerification,
      createOne: async () => {
        throw new Error("Creating verifications from admin is not supported.");
      },
      updateOne: async () => {
        throw new Error("Updating verifications from admin is not supported.");
      },
      deleteOne: async () => {
        throw new Error("Deleting verifications from admin is not supported.");
      },
    },
    table: {
      module: "verifications",
      basePath: "/verifications",
      showCreateButton: false,
      searchPlaceholder: "Search Verifications",
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/verifications"),
      detail: (tenantSlug, id) =>
        toTenantPath(tenantSlug, `/verifications/${id}`),
    },
    detail: {
      idParam: "verificationId",
      breadcrumb: {
        key: "verifications",
        labelField: "status",
      },
      defaultValues: {},
      showDelete: false,
      showCreate: false,
      readOnly: true,
      fields: [
        { name: "status", type: "text", label: "Status" },
        {
          name: "journeyTemplateId",
          type: "text",
          label: "Journey Template ID",
        },
        { name: "clientIdentityId", type: "text", label: "Client Identity ID" },
        { name: "expiresAt", type: "datetime", label: "Expires At" },
        { name: "completedAt", type: "datetime", label: "Completed At" },
        {
          name: "createdAt",
          type: "datetime",
          label: "Created At",
          fullWidth: true,
        },
      ],
    },
  },
  journeyTemplates: {
    key: "journeyTemplates",
    queryKeys: queryKeys.journeyTemplates,
    api: {
      fetchOne: fetchJourneyTemplate,
      createOne: createJourneyTemplate,
      updateOne: updateJourneyTemplate,
      deleteOne: deleteJourneyTemplate,
    },
    table: {
      module: "journeyTemplates",
      basePath: "/journey-templates",
      createPath: (tenantSlug) =>
        toTenantPath(tenantSlug, "/journey-templates/new"),
      createLabel: "New Template",
      searchPlaceholder: "Search Journey Templates",
      createPermission: "tenant.journey-templates:create",
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/journey-templates"),
      detail: (tenantSlug, id) =>
        toTenantPath(tenantSlug, `/journey-templates/${id}`),
    },
    messages: {
      confirmDelete: "Are you sure you want to archive this journey template?",
      success: {
        create: "Journey template created successfully!",
        update: "Journey template saved successfully!",
        remove: "Journey template archived successfully.",
      },
      error: {
        save: "Failed to save journey template. Please try again.",
        remove: "Failed to archive journey template. Please try again.",
      },
    },
    detail: {
      idParam: "journeyTemplateId",
      breadcrumb: {
        key: "journeyTemplates",
        labelField: "name",
      },
      defaultValues: {
        name: "",
        description: "",
        configJson: null,
      },
      permissions: {
        create: "tenant.journey-templates:create",
        update: "tenant.journey-templates:update",
        delete: "tenant.journey-templates:delete",
      },
      actionLabels: {
        create: "Create Template",
        save: "Save Changes",
        delete: "Archive",
      },
      fields: [
        {
          name: "name",
          type: "text",
          label: "Name",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "status",
          type: "enum",
          label: "Status",
          options: [
            { value: "ACTIVE", label: "Active" },
            { value: "ARCHIVED", label: "Archived" },
          ],
        },
        {
          name: "description",
          type: "textarea",
          label: "Description",
          fullWidth: true,
        },
        {
          name: "configJson",
          label: "Config JSON",
          fullWidth: true,
          render: ({ control, readOnly }) =>
            createElement(ConfigJsonEditor, { control, readOnly }),
        },
      ],
    },
  },
};

export function getTenantModuleDefinition(moduleKey) {
  return tenantModuleDefinitions[moduleKey];
}

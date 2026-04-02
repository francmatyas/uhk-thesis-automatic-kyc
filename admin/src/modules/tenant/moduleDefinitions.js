import { ConfigJsonEditor } from "@/views/journeyTemplates/ConfigJsonEditor";
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
      searchPlaceholder: "moduleDefinitions.auditLogs.search",
      enumConfig: {
        result: {
          type: "ENUM",
          displayMode: "badge", // or "text"
          enumValues: [
            {
              value: "SUCCESS",
              label: "moduleDefinitions.auditLogs.enums.result.success",
              status: "success",
            },
            {
              value: "FAILURE",
              label: "moduleDefinitions.auditLogs.enums.result.failure",
              status: "failed",
            },
          ],
        },
        actorType: {
          type: "ENUM",
          displayMode: "text",
          enumValues: [
            {
              value: "USER",
              label: "moduleDefinitions.auditLogs.enums.actor.user",
            },
            {
              value: "SYSTEM",
              label: "moduleDefinitions.auditLogs.enums.actor.system",
            },
            {
              value: "API_KEY",
              label: "moduleDefinitions.auditLogs.enums.actor.api_key",
            },
            {
              value: "SERVICE",
              label: "moduleDefinitions.auditLogs.enums.actor.service",
            },
          ],
        },
      },
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
      showSave: false,
      readOnly: true,
      fields: [
        {
          name: "action",
          type: "text",
          label: "moduleDefinitions.auditLogs.detailFields.action",
        },
        {
          name: "actorType",
          type: "enum",
          label: "moduleDefinitions.auditLogs.detailFields.actorType",
          options: [
            {
              value: "USER",
              label: "moduleDefinitions.auditLogs.enums.actor.user",
            },
            {
              value: "SYSTEM",
              label: "moduleDefinitions.auditLogs.enums.actor.system",
            },
            {
              value: "API_KEY",
              label: "moduleDefinitions.auditLogs.enums.actor.api_key",
            },
            {
              value: "SERVICE",
              label: "moduleDefinitions.auditLogs.enums.actor.service",
            },
          ],
        },
        {
          name: "entityType",
          type: "text",
          label: "moduleDefinitions.auditLogs.detailFields.entityType",
        },
        {
          name: "result",
          type: "enum",
          label: "moduleDefinitions.auditLogs.detailFields.result",
          options: [
            {
              value: "SUCCESS",
              label: "moduleDefinitions.auditLogs.enums.result.success",
            },
            {
              value: "FAILURE",
              label: "moduleDefinitions.auditLogs.enums.result.failure",
            },
          ],
        },
        {
          name: "errorCode",
          type: "text",
          label: "moduleDefinitions.auditLogs.detailFields.errorCode",
        },
        {
          name: "createdAt",
          type: "datetime",
          label: "moduleDefinitions.auditLogs.detailFields.timestamp",
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
      confirmDelete: "moduleDefinitions.tenantMe.messages.confirmDelete",
      success: {
        create: "moduleDefinitions.tenantMe.messages.success.create",
        update: "moduleDefinitions.tenantMe.messages.success.update",
        remove: "moduleDefinitions.tenantMe.messages.success.remove",
      },
      error: {
        save: "moduleDefinitions.tenantMe.messages.error.save",
        remove: "moduleDefinitions.tenantMe.messages.error.remove",
      },
    },
    detail: {
      idParam: "tenantSlug",
      breadcrumb: {
        key: "tenants",
        labelField: "name",
      },
      sectionTitle: "moduleDefinitions.tenantMe.sectionTitle",
      defaultValues: {
        name: "",
        slug: "",
        active: "ACTIVE",
      },
      permissions: {
        update: "tenant.tenants:update",
      },
      actionLabels: {
        save: "moduleDefinitions.tenantMe.actions.save",
      },
      showDelete: false,
      showCreate: false,
      fields: [
        {
          name: "name",
          type: "text",
          label: "moduleDefinitions.tenantMe.detailFields.name",
          props: {
            readOnly: true,
          },
        },
        {
          name: "slug",
          type: "text",
          label: "moduleDefinitions.tenantMe.detailFields.slug",
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
      createLabel: "moduleDefinitions.apiKeys.createLabel",
      searchPlaceholder: "moduleDefinitions.apiKeys.search",
      createPermission: "tenant.api-keys:create",
      enumConfig: {
        status: {
          type: "ENUM",
          displayMode: "badge", // or "text"
          enumValues: [
            {
              value: "ACTIVE",
              label: "moduleDefinitions.apiKeys.enums.status.active",
              status: "new",
            },
            {
              value: "REVOKED",
              label: "moduleDefinitions.apiKeys.enums.status.revoked",
              status: "failed",
            },
          ],
        },
      },
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/api-keys"),
      detail: (tenantSlug, id) => toTenantPath(tenantSlug, `/api-keys/${id}`),
    },
    messages: {
      confirmDelete: "moduleDefinitions.apiKeys.messages.confirmDelete",
      success: {
        create: "moduleDefinitions.apiKeys.messages.success.create",
        update: "moduleDefinitions.apiKeys.messages.success.update",
        remove: "moduleDefinitions.apiKeys.messages.success.remove",
      },
      error: {
        save: "moduleDefinitions.apiKeys.messages.error.save",
        remove: "moduleDefinitions.apiKeys.messages.error.remove",
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
        create: "moduleDefinitions.apiKeys.actions.create",
        save: "moduleDefinitions.apiKeys.actions.save",
        delete: "moduleDefinitions.apiKeys.actions.delete",
      },
      fields: [
        {
          name: "name",
          type: "text",
          label: "moduleDefinitions.apiKeys.detailFields.name",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "status",
          type: "enum",
          label: "moduleDefinitions.apiKeys.detailFields.status",
          options: [
            {
              value: "ACTIVE",
              label: "moduleDefinitions.apiKeys.enums.status.active",
            },
            {
              value: "REVOKED",
              label: "moduleDefinitions.apiKeys.enums.status.revoked",
            },
          ],
        },
        {
          name: "publicKey",
          type: "text",
          label: "moduleDefinitions.apiKeys.detailFields.publicKey",
          fullWidth: true,
          props: {
            readOnly: true,
            clickToCopy: true,
          },
        },
        {
          name: "lastUsedAt",
          type: "datetime",
          label: "moduleDefinitions.apiKeys.detailFields.lastUsedAt",
          props: {
            readOnly: true,
          },
        },
        {
          name: "createdAt",
          type: "datetime",
          label: "moduleDefinitions.apiKeys.detailFields.createdAt",
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
      createLabel: "moduleDefinitions.webhooks.createLabel",
      searchPlaceholder: "moduleDefinitions.webhooks.search",
      createPermission: "tenant.webhooks:create",
      enumConfig: {
        status: {
          type: "ENUM",
          displayMode: "badge", // or "text"
          enumValues: [
            {
              value: "ACTIVE",
              label: "moduleDefinitions.webhooks.enums.status.active",
              status: "new",
            },
            {
              value: "DISABLED",
              label: "moduleDefinitions.webhooks.enums.status.disabled",
              status: "failed",
            },
          ],
        },
      },
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/webhooks"),
      detail: (tenantSlug, id) => toTenantPath(tenantSlug, `/webhooks/${id}`),
    },
    messages: {
      confirmDelete: "moduleDefinitions.webhooks.messages.confirmDelete",
      success: {
        create: "moduleDefinitions.webhooks.messages.success.create",
        update: "moduleDefinitions.webhooks.messages.success.update",
        remove: "moduleDefinitions.webhooks.messages.success.remove",
      },
      error: {
        save: "moduleDefinitions.webhooks.messages.error.save",
        remove: "moduleDefinitions.webhooks.messages.error.remove",
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
        create: "moduleDefinitions.webhooks.actions.create",
        save: "moduleDefinitions.webhooks.actions.save",
        delete: "moduleDefinitions.webhooks.actions.delete",
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
          label: "moduleDefinitions.webhooks.detailFields.urls",
          props: {
            readOnly: true,
            clickToCopy: true,
          },
        },
        {
          name: "status",
          type: "enum",
          label: "moduleDefinitions.webhooks.detailFields.status",
          options: [
            {
              value: "ACTIVE",
              label: "moduleDefinitions.webhooks.enums.status.active",
            },
            {
              value: "DISABLED",
              label: "moduleDefinitions.webhooks.enums.status.disabled",
            },
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
          label: "moduleDefinitions.webhooks.detailFields.lastDeliveryAt",
          props: {
            readOnly: true,
          },
        },
        {
          name: "createdAt",
          type: "datetime",
          label: "moduleDefinitions.webhooks.detailFields.createdAt",
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
      searchPlaceholder: "moduleDefinitions.members.search",
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/members"),
      detail: (tenantSlug, id) => toTenantPath(tenantSlug, `/members/${id}`),
    },
    messages: {
      confirmDelete: "moduleDefinitions.members.messages.confirmDelete",
      success: {
        update: "moduleDefinitions.members.messages.success.update",
      },
      error: {
        save: "moduleDefinitions.members.messages.error.save",
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
        save: "moduleDefinitions.members.actions.save",
      },
      showDelete: false,
      showCreate: false,
      fields: [
        {
          name: "givenName",
          type: "text",
          label: "moduleDefinitions.members.detailFields.givenName",
          props: {
            readOnly: true,
          },
        },
        {
          name: "familyName",
          type: "text",
          label: "moduleDefinitions.members.detailFields.familyName",
          props: {
            readOnly: true,
          },
        },
        {
          name: "email",
          type: "text",
          label: "moduleDefinitions.members.detailFields.email",
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
      searchPlaceholder: "moduleDefinitions.verifications.search",
      enumConfig: {
        status: {
          type: "ENUM",
          displayMode: "badge", // or "text"
          enumValues: [
            {
              value: "INITIATED",
              label: "moduleDefinitions.verifications.enums.status.initiated",
              status: "new",
            },
            {
              value: "IN_PROGRESS",
              label: "moduleDefinitions.verifications.enums.status.inProgress",
              status: "new",
            },
            {
              value: "READY_FOR_AUTOCHECK",
              label:
                "moduleDefinitions.verifications.enums.status.readyForAutoCheck",
              status: "new",
            },
            {
              value: "AUTO_PASSED",
              label: "moduleDefinitions.verifications.enums.status.autoPassed",
              status: "success",
            },
            {
              value: "AUTO_FAILED",
              label: "moduleDefinitions.verifications.enums.status.autoFailed",
              status: "failed",
            },
            {
              value: "REQUIRES_REVIEW",
              label:
                "moduleDefinitions.verifications.enums.status.requiresReview",
              status: "warning",
            },
            {
              value: "APPROVED",
              label: "moduleDefinitions.verifications.enums.status.approved",
              status: "success",
            },
            {
              value: "REJECTED",
              label: "moduleDefinitions.verifications.enums.status.rejected",
              status: "failed",
            },
            {
              value: "EXPIRED",
              label: "moduleDefinitions.verifications.enums.status.expired",
              status: "default",
            },
          ],
        },
      },
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
        labelField: "clientName",
      },
      defaultValues: {},
      showDelete: false,
      showCreate: false,
      showSave: false,
      readOnly: true,
      fields: [
        {
          type: "relation",
          label: "moduleDefinitions.verifications.detailFields.journeyTemplate",
          idField: "journeyTemplateId",
          nameField: "journeyTemplateName",
          endpoint: "/journey-templates",
          formatter: (item) => ({
            label: item.name,
            sublabel: item.id,
            value: item.id,
          }),
          getDetailPath: (id, params) =>
            toTenantPath(params.tenantSlug, `/journey-templates/${id}`),
          fullWidth: true,
        },
        {
          name: "expiresAt",
          type: "datetime",
          label: "moduleDefinitions.verifications.detailFields.expiresAt",
        },
        {
          name: "completedAt",
          type: "datetime",
          label: "moduleDefinitions.verifications.detailFields.completedAt",
        },
        {
          name: "createdAt",
          type: "datetime",
          label: "moduleDefinitions.verifications.detailFields.createdAt",
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
      createLabel: "moduleDefinitions.journeyTemplates.createLabel",
      searchPlaceholder: "moduleDefinitions.journeyTemplates.search",
      createPermission: "tenant.journey-templates:create",
      enumConfig: {
        status: {
          type: "ENUM",
          displayMode: "badge", // or "text"
          enumValues: [
            {
              value: "ACTIVE",
              label: "moduleDefinitions.journeyTemplates.enums.status.active",
              status: "new",
            },
            {
              value: "ARCHIVED",
              label: "moduleDefinitions.journeyTemplates.enums.status.archived",
              status: "default",
            },
          ],
        },
      },
    },
    routes: {
      list: (tenantSlug) => toTenantPath(tenantSlug, "/journey-templates"),
      detail: (tenantSlug, id) =>
        toTenantPath(tenantSlug, `/journey-templates/${id}`),
    },
    messages: {
      confirmDelete:
        "moduleDefinitions.journeyTemplates.messages.confirmDelete",
      success: {
        create: "moduleDefinitions.journeyTemplates.messages.success.create",
        update: "moduleDefinitions.journeyTemplates.messages.success.update",
        remove: "moduleDefinitions.journeyTemplates.messages.success.remove",
      },
      error: {
        save: "moduleDefinitions.journeyTemplates.messages.error.save",
        remove: "moduleDefinitions.journeyTemplates.messages.error.remove",
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
        create: "moduleDefinitions.journeyTemplates.actions.create",
        save: "moduleDefinitions.journeyTemplates.actions.save",
        delete: "moduleDefinitions.journeyTemplates.actions.delete",
      },
      fields: [
        {
          name: "id",
          type: "copy",
          label: "moduleDefinitions.journeyTemplates.detailFields.id",
          fullWidth: true,
        },
        {
          name: "name",
          type: "text",
          label: "moduleDefinitions.journeyTemplates.detailFields.name",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "status",
          type: "enum",
          label: "moduleDefinitions.journeyTemplates.detailFields.status",
          options: [
            {
              value: "ACTIVE",
              label: "moduleDefinitions.journeyTemplates.enums.status.active",
            },
            {
              value: "ARCHIVED",
              label: "moduleDefinitions.journeyTemplates.enums.status.archived",
            },
          ],
        },
        {
          name: "description",
          type: "textarea",
          label: "moduleDefinitions.journeyTemplates.detailFields.description",
          fullWidth: true,
        },
        {
          name: "configJson",
          label: "moduleDefinitions.journeyTemplates.detailFields.configJson",
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

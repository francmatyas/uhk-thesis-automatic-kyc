import { ConfigJsonEditor } from "@/views/journeyTemplates/ConfigJsonEditor";
import { fetchAuditLog as fetchProviderAuditLog } from "@/api/provider/auditLogs";
import {
  createPermission,
  deletePermission,
  fetchPermission,
  updatePermission,
} from "@/api/provider/permissions";
import {
  createRole,
  deleteRole,
  fetchRole,
  updateRole,
} from "@/api/provider/roles";
import {
  createTenantProvider,
  deleteTenantProvider,
  fetchTenantProvider,
  updateTenantProvider,
} from "@/api/provider/tenants";
import {
  createUser,
  deleteUser,
  fetchUser,
  updateUser,
} from "@/api/provider/users";
import { fetchVerification } from "@/api/provider/verifications";
import {
  createJourneyTemplate,
  deleteJourneyTemplate,
  fetchJourneyTemplate,
  updateJourneyTemplate,
} from "@/api/provider/journeyTemplates";
import { queryKeys } from "@/modules/queryKeys";
import { toProviderPath } from "@/router/scope";
import { createElement } from "react";
import RolePermissionsField from "@/views/roles/RolePermissionsField";

export const providerModuleDefinitions = {
  auditLogs: {
    key: "auditLogs",
    queryKeys: queryKeys.providerAuditLogs,
    api: {
      fetchOne: fetchProviderAuditLog,
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
      basePath: "/provider/audit-logs",
      showCreateButton: false,
      searchPlaceholder: "moduleDefinitions.auditLogs.search",
      enumConfig: {
        result: {
          type: "ENUM",
          displayMode: "badge",
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
      list: () => toProviderPath("/audit-logs"),
      detail: (id) => toProviderPath(`/audit-logs/${id}`),
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
  roles: {
    key: "roles",
    queryKeys: queryKeys.roles,
    api: {
      fetchOne: fetchRole,
      createOne: createRole,
      updateOne: updateRole,
      deleteOne: deleteRole,
    },
    table: {
      module: "roles",
      basePath: "/provider/roles",
      createPath: toProviderPath("/roles/new"),
      createLabel: "moduleDefinitions.roles.createLabel",
      searchPlaceholder: "moduleDefinitions.roles.search",
      createPermission: "provider.roles:create",
    },
    routes: {
      list: () => toProviderPath("/roles"),
      detail: (id) => toProviderPath(`/roles/${id}`),
    },
    messages: {
      confirmDelete: "moduleDefinitions.roles.messages.confirmDelete",
      success: {
        create: "moduleDefinitions.roles.messages.success.create",
        update: "moduleDefinitions.roles.messages.success.update",
        remove: "moduleDefinitions.roles.messages.success.remove",
      },
      error: {
        save: "moduleDefinitions.roles.messages.error.save",
        remove: "moduleDefinitions.roles.messages.error.remove",
      },
    },
    detail: {
      idParam: "roleId",
      breadcrumb: {
        key: "roles",
        labelField: "slug",
      },
      defaultValues: {
        name: "",
        description: "",
        scope: "TENANT",
        priority: 0,
        permissions: [],
      },
      permissions: {
        create: "provider.roles:create",
        update: "provider.roles:update",
        delete: "provider.roles:delete",
      },
      actionLabels: {
        create: "moduleDefinitions.roles.actions.create",
        save: "moduleDefinitions.roles.actions.save",
        delete: "moduleDefinitions.roles.actions.delete",
      },
      transformEntityForForm: (entity) => ({
        ...entity,
        permissions: Array.isArray(entity?.permissionIds)
          ? entity.permissionIds
          : Array.isArray(entity?.permissions)
            ? entity.permissions
            : [],
      }),
      transformSubmit: (data, { inEditMode }) => {
        const permissionIds = Array.isArray(data.permissions)
          ? data.permissions
          : Array.isArray(data.permissionIds)
            ? data.permissionIds
            : [];

        const payload = {
          ...data,
          permissionIds,
        };

        if (inEditMode) {
          delete payload.permissions;
        }

        return payload;
      },
      fields: ({ watch, control }) => [
        {
          name: "scope",
          type: "enum",
          label: "moduleDefinitions.roles.detailFields.scope",
          required: true,
          options: [
            {
              value: "TENANT",
              label: "moduleDefinitions.roles.enums.scope.tenant",
            },
            {
              value: "PROVIDER",
              label: "moduleDefinitions.roles.enums.scope.provider",
            },
          ],
        },
        {
          name: "name",
          type: "text",
          label: "moduleDefinitions.roles.detailFields.name",
          required: true,
          registerOptions: { required: true },
          props: {
            prepend: watch("scope") + "_",
          },
        },
        {
          name: "description",
          type: "textarea",
          label: "moduleDefinitions.roles.detailFields.description",
          fullWidth: true,
        },
        {
          name: "permissions",
          fullWidth: true,
          render: ({ readOnly }) =>
            createElement(RolePermissionsField, {
              control,
              readOnly,
              roleScope: watch("scope"),
            }),
        },
      ],
    },
  },
  permissions: {
    key: "permissions",
    queryKeys: queryKeys.permissions,
    api: {
      fetchOne: fetchPermission,
      createOne: createPermission,
      updateOne: updatePermission,
      deleteOne: deletePermission,
    },
    table: {
      module: "permissions",
      basePath: "/provider/permissions",
      searchPlaceholder: "moduleDefinitions.permissions.search",
      showCreateButton: false,
    },
    routes: {
      list: () => toProviderPath("/permissions"),
      detail: (id) => toProviderPath(`/permissions/${id}`),
    },
    messages: {
      confirmDelete: "moduleDefinitions.permissions.messages.confirmDelete",
      success: {
        create: "moduleDefinitions.permissions.messages.success.create",
        update: "moduleDefinitions.permissions.messages.success.update",
        remove: "moduleDefinitions.permissions.messages.success.remove",
      },
      error: {
        save: "moduleDefinitions.permissions.messages.error.save",
        remove: "moduleDefinitions.permissions.messages.error.remove",
      },
    },
    detail: {
      showDelete: false,
      showCreate: false,
      showSave: false,
      readOnly: true,
      idParam: "permissionId",
      breadcrumb: {
        key: "permissions",
        labelField: "label",
      },
      defaultValues: {
        resource: "",
        action: "",
        description: "",
      },
      actionLabels: {
        create: "moduleDefinitions.permissions.actions.create",
        save: "moduleDefinitions.permissions.actions.save",
        delete: "moduleDefinitions.permissions.actions.delete",
      },
      fields: [
        {
          name: "resource",
          type: "text",
          label: "moduleDefinitions.permissions.detailFields.resource",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "action",
          type: "enum",
          label: "moduleDefinitions.permissions.detailFields.action",
          required: true,
          options: [
            {
              value: "create",
              label: "moduleDefinitions.permissions.enums.action.create",
            },
            {
              value: "read",
              label: "moduleDefinitions.permissions.enums.action.read",
            },
            {
              value: "update",
              label: "moduleDefinitions.permissions.enums.action.update",
            },
            {
              value: "delete",
              label: "moduleDefinitions.permissions.enums.action.delete",
            },
          ],
        },
        {
          name: "description",
          type: "textarea",
          label: "moduleDefinitions.permissions.detailFields.description",
          fullWidth: true,
        },
      ],
    },
  },
  users: {
    key: "users",
    queryKeys: queryKeys.users,
    api: {
      fetchOne: fetchUser,
      createOne: createUser,
      updateOne: updateUser,
      deleteOne: deleteUser,
    },
    table: {
      module: "users",
      basePath: "/provider/users",
      showCreateButton: false,
      searchPlaceholder: "moduleDefinitions.users.search",
    },
    routes: {
      list: () => toProviderPath("/users"),
      detail: (id) => toProviderPath(`/users/${id}`),
    },
    messages: {
      confirmDelete: "moduleDefinitions.users.messages.confirmDelete",
      success: {
        create: "moduleDefinitions.users.messages.success.create",
        update: "moduleDefinitions.users.messages.success.update",
        remove: "moduleDefinitions.users.messages.success.remove",
      },
      error: {
        save: "moduleDefinitions.users.messages.error.save",
        remove: "moduleDefinitions.users.messages.error.remove",
      },
    },
    detail: {
      idParam: "userId",
      breadcrumb: {
        key: "users",
        labelField: "fullName",
      },
      defaultValues: {
        email: "",
        givenName: "",
        familyName: "",
      },
      permissions: {
        update: "provider.users:update",
        delete: "provider.users:delete",
      },
      actionLabels: {
        create: "moduleDefinitions.users.actions.create",
        save: "moduleDefinitions.users.actions.save",
        delete: "moduleDefinitions.users.actions.delete",
      },
      readOnly: ({ inEditMode }) => !inEditMode,
      fields: [
        {
          name: "givenName",
          type: "text",
          label: "moduleDefinitions.users.detailFields.givenName",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "familyName",
          type: "text",
          label: "moduleDefinitions.users.detailFields.familyName",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "email",
          type: "text",
          label: "moduleDefinitions.users.detailFields.email",
          required: true,
          registerOptions: { required: true },
          fullWidth: true,
        },
        {
          name: "gender",
          type: "enum",
          label: "moduleDefinitions.users.detailFields.gender",
          options: [
            {
              value: "MALE",
              label: "moduleDefinitions.users.detailFields.male",
            },
            {
              value: "FEMALE",
              label: "moduleDefinitions.users.detailFields.female",
            },
            {
              value: "OTHER",
              label: "moduleDefinitions.users.detailFields.other",
            },
          ],
          fullWidth: true,
        },
      ],
    },
  },
  tenants: {
    key: "tenants",
    queryKeys: queryKeys.tenants,
    api: {
      fetchOne: fetchTenantProvider,
      createOne: createTenantProvider,
      updateOne: updateTenantProvider,
      deleteOne: deleteTenantProvider,
    },
    table: {
      module: "tenants",
      basePath: "/provider/tenants",
      createPath: toProviderPath("/tenants/new"),
      createLabel: "moduleDefinitions.tenants.createLabel",
      searchPlaceholder: "moduleDefinitions.tenants.search",
      createPermission: "provider.tenants:create",
      enumConfig: {
        status: {
          type: "ENUM",
          displayMode: "badge",
          enumValues: [
            {
              value: "ACTIVE",
              label: "moduleDefinitions.tenants.enums.status.active",
              status: "success",
            },
            {
              value: "SUSPENDED",
              label: "moduleDefinitions.tenants.enums.status.suspended",
              status: "warning",
            },
          ],
        },
      },
    },
    routes: {
      list: () => toProviderPath("/tenants"),
      detail: (id) => toProviderPath(`/tenants/${id}`),
    },
    messages: {
      confirmDelete: "moduleDefinitions.tenants.messages.confirmDelete",
      success: {
        create: "moduleDefinitions.tenants.messages.success.create",
        update: "moduleDefinitions.tenants.messages.success.update",
        remove: "moduleDefinitions.tenants.messages.success.remove",
      },
      error: {
        save: "moduleDefinitions.tenants.messages.error.save",
        remove: "moduleDefinitions.tenants.messages.error.remove",
      },
    },
    detail: {
      idParam: "tenantId",
      breadcrumb: {
        key: "tenants",
        labelField: "name",
      },
      sectionTitle: "moduleDefinitions.sectionTitle.tenant",
      defaultValues: {
        name: "",
        slug: "",
        active: "ACTIVE",
      },
      permissions: {
        create: "provider.tenants:create",
        update: "provider.tenants:update",
        delete: "provider.tenants:delete",
      },
      actionLabels: {
        create: "moduleDefinitions.tenants.actions.create",
        save: "moduleDefinitions.tenants.actions.save",
        delete: "moduleDefinitions.tenants.actions.delete",
      },
      fields: [
        {
          name: "name",
          type: "text",
          label: "moduleDefinitions.tenants.detailFields.name",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "slug",
          type: "text",
          label: "moduleDefinitions.tenants.detailFields.slug",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "status",
          type: "enum",
          label: "moduleDefinitions.tenants.detailFields.status",
          required: true,
          options: [
            {
              value: "ACTIVE",
              label: "moduleDefinitions.tenants.enums.status.active",
            },
            {
              value: "SUSPENDED",
              label: "moduleDefinitions.tenants.enums.status.suspended",
            },
          ],
        },
      ],
    },
  },
  verifications: {
    key: "verifications",
    queryKeys: queryKeys.providerVerifications,
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
      basePath: "/provider/verifications",
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
      list: () => toProviderPath("/verifications"),
      detail: (id) => toProviderPath(`/verifications/${id}`),
    },
    detail: {
      idParam: "verificationId",
      breadcrumb: {
        key: "verifications",
        labelField: "id",
      },
      defaultValues: {},
      showDelete: false,
      showCreate: false,
      showSave: false,
      readOnly: true,
      fields: [
        {
          type: "relation",
          label: "moduleDefinitions.verifications.detailFields.tenant",
          idField: "tenantId",
          nameField: "tenantName",
          endpoint: "/tenants",
          formatter: (item) => ({ label: item.name, value: item.id }),
          getDetailPath: (id) => toProviderPath(`/tenants/${id}`),
          fullWidth: true,
          readOnly: true,
        },
        {
          type: "relation",
          label: "moduleDefinitions.verifications.detailFields.journeyTemplate",
          idField: "journeyTemplateId",
          nameField: "journeyTemplateName",
          endpoint: "/journey-templates",
          formatter: (item) => ({ label: item.name, value: item.id }),
          getDetailPath: (id) => toProviderPath(`/journey-templates/${id}`),
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
    queryKeys: queryKeys.providerJourneyTemplates,
    api: {
      fetchOne: fetchJourneyTemplate,
      createOne: createJourneyTemplate,
      updateOne: updateJourneyTemplate,
      deleteOne: deleteJourneyTemplate,
    },
    table: {
      module: "journeyTemplates",
      basePath: "/provider/journey-templates",
      searchPlaceholder: "moduleDefinitions.journeyTemplates.search",
      showCreateButton: false,
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
      list: () => toProviderPath("/journey-templates"),
      detail: (id) => toProviderPath(`/journey-templates/${id}`),
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
        tenantId: "",
        name: "",
        description: "",
        configJson: null,
      },
      permissions: {
        create: "provider.journey-templates:create",
        update: "provider.journey-templates:edit",
        delete: "provider.journey-templates:delete",
      },
      actionLabels: {
        create: "moduleDefinitions.journeyTemplates.actions.create",
        save: "moduleDefinitions.journeyTemplates.actions.save",
        delete: "moduleDefinitions.journeyTemplates.actions.delete",
      },
      showDelete: false,
      fields: ({ inEditMode }) => [
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

export function getProviderModuleDefinition(moduleKey) {
  return providerModuleDefinitions[moduleKey];
}

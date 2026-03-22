import { ConfigJsonEditor } from "@/components/ConfigJsonEditor";
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
      searchPlaceholder: "Search Audit Logs",
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
      createLabel: "New Role",
      searchPlaceholder: "Search Roles",
      createPermission: "provider.roles:create",
    },
    routes: {
      list: () => toProviderPath("/roles"),
      detail: (id) => toProviderPath(`/roles/${id}`),
    },
    messages: {
      confirmDelete: "Are you sure you want to delete this role?",
      success: {
        create: "Role created successfully!",
        update: "Role saved successfully!",
        remove: "Role deleted successfully.",
      },
      error: {
        save: "Failed to save role. Please try again.",
        remove: "Failed to delete role. Please try again.",
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
        create: "Create Role",
        save: "Save Changes",
        delete: "Delete",
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
          label: "Scope",
          required: true,
          options: [
            { value: "TENANT", label: "Tenant" },
            { value: "PROVIDER", label: "Provider" },
          ],
        },
        {
          name: "name",
          type: "text",
          label: "Name",
          required: true,
          registerOptions: { required: true },
          props: {
            prepend: watch("scope") + "_",
          },
        },
        {
          name: "description",
          type: "textarea",
          label: "Description",
          fullWidth: true,
        },
        {
          name: "permissions",
          fullWidth: true,
          render: ({ readOnly }) =>
            createElement(RolePermissionsField, {
              control,
              readOnly,
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
      createPath: toProviderPath("/permissions/new"),
      createLabel: "New Permission",
      searchPlaceholder: "Search Permissions",
      createPermission: "provider.permissions:create",
    },
    routes: {
      list: () => toProviderPath("/permissions"),
      detail: (id) => toProviderPath(`/permissions/${id}`),
    },
    messages: {
      confirmDelete: "Are you sure you want to delete this permission?",
      success: {
        create: "Permission created successfully!",
        update: "Permission saved successfully!",
        remove: "Permission deleted successfully.",
      },
      error: {
        save: "Failed to save permission. Please try again.",
        remove: "Failed to delete permission. Please try again.",
      },
    },
    detail: {
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
      permissions: {
        create: "provider.permissions:create",
        update: "provider.permissions:update",
        delete: "provider.permissions:delete",
      },
      actionLabels: {
        create: "Create Permission",
        save: "Save Changes",
        delete: "Delete",
      },
      fields: [
        {
          name: "resource",
          type: "text",
          label: "Resource",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "action",
          type: "enum",
          label: "Action",
          required: true,
          options: [
            { value: "create", label: "create" },
            { value: "read", label: "read" },
            { value: "update", label: "update" },
            { value: "delete", label: "delete" },
          ],
        },
        {
          name: "description",
          type: "textarea",
          label: "Description",
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
      searchPlaceholder: "Search Users",
    },
    routes: {
      list: () => toProviderPath("/users"),
      detail: (id) => toProviderPath(`/users/${id}`),
    },
    messages: {
      confirmDelete: "Are you sure you want to delete this user?",
      success: {
        create: "User created successfully!",
        update: "User saved successfully!",
        remove: "User deleted successfully.",
      },
      error: {
        save: "Failed to save user. Please try again.",
        remove: "Failed to delete user. Please try again.",
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
        create: "Create User",
        save: "Save Changes",
        delete: "Delete",
      },
      readOnly: ({ inEditMode }) => !inEditMode,
      fields: [
        {
          name: "givenName",
          type: "text",
          label: "Given Name",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "familyName",
          type: "text",
          label: "Family Name",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "email",
          type: "text",
          label: "Email",
          required: true,
          registerOptions: { required: true },
          fullWidth: true,
        },
        {
          name: "gender",
          type: "enum",
          label: "Gender",
          options: [
            { value: "MALE", label: "Male" },
            { value: "FEMALE", label: "Female" },
            { value: "OTHER", label: "Other" },
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
      createLabel: "New Tenant",
      searchPlaceholder: "Search Tenants",
      createPermission: "provider.tenants:create",
    },
    routes: {
      list: () => toProviderPath("/tenants"),
      detail: (id) => toProviderPath(`/tenants/${id}`),
    },
    messages: {
      confirmDelete: "Are you sure you want to delete this tenant?",
      success: {
        create: "Tenant created successfully!",
        update: "Tenant saved successfully!",
        remove: "Tenant deleted successfully.",
      },
      error: {
        save: "Failed to save tenant. Please try again.",
        remove: "Failed to delete tenant. Please try again.",
      },
    },
    detail: {
      idParam: "tenantId",
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
        create: "provider.tenants:create",
        update: "provider.tenants:update",
        delete: "provider.tenants:delete",
      },
      actionLabels: {
        create: "Create Tenant",
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
          name: "slug",
          type: "text",
          label: "Slug",
          required: true,
          registerOptions: { required: true },
        },
        {
          name: "active",
          type: "enum",
          label: "Active",
          required: true,
          options: [
            { value: "ACTIVE", label: "Active" },
            { value: "SUSPENDED", label: "Suspended" },
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
      searchPlaceholder: "Search Verifications",
    },
    routes: {
      list: () => toProviderPath("/verifications"),
      detail: (id) => toProviderPath(`/verifications/${id}`),
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
        { name: "tenantId", type: "text", label: "Tenant ID" },
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
      createPath: toProviderPath("/journey-templates/new"),
      createLabel: "New Template",
      searchPlaceholder: "Search Journey Templates",
      createPermission: "provider.journey-templates:manage",
    },
    routes: {
      list: () => toProviderPath("/journey-templates"),
      detail: (id) => toProviderPath(`/journey-templates/${id}`),
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
        tenantId: "",
        name: "",
        description: "",
        configJson: null,
      },
      permissions: {
        create: "provider.journey-templates:manage",
        update: "provider.journey-templates:manage",
        delete: "provider.journey-templates:manage",
      },
      actionLabels: {
        create: "Create Template",
        save: "Save Changes",
        delete: "Archive",
      },
      fields: ({ inEditMode }) => [
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

export function getProviderModuleDefinition(moduleKey) {
  return providerModuleDefinitions[moduleKey];
}

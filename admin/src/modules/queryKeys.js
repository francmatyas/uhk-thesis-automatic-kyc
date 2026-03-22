export function createResourceQueryKeys(resource) {
  return {
    all: [resource],
    list: () => [resource],
    detail: (id) => [resource, id],
  };
}

export const queryKeys = {
  roles: createResourceQueryKeys("roles"),
  permissions: createResourceQueryKeys("permissions"),
  users: createResourceQueryKeys("users"),
  tenants: createResourceQueryKeys("tenants"),
  apiKeys: createResourceQueryKeys("apiKeys"),
  webhooks: createResourceQueryKeys("webhooks"),
  strategies: createResourceQueryKeys("strategies"),
  simulations: createResourceQueryKeys("simulations"),
  members: createResourceQueryKeys("members"),
  providerAuditLogs: createResourceQueryKeys("providerAuditLogs"),
  auditLogs: createResourceQueryKeys("auditLogs"),
  providerVerifications: createResourceQueryKeys("providerVerifications"),
  verifications: createResourceQueryKeys("verifications"),
  providerJourneyTemplates: createResourceQueryKeys("providerJourneyTemplates"),
  journeyTemplates: createResourceQueryKeys("journeyTemplates"),
};

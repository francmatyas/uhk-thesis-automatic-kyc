import { makeCrudApi } from "@/api/makeCrudApi";

const auditLogsApi = makeCrudApi("/tenants/audit-logs");

export const fetchAuditLog = auditLogsApi.fetchOne;

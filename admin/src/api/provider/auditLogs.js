import { makeCrudApi } from "@/api/makeCrudApi";

const auditLogsApi = makeCrudApi("/provider/audit-logs");

export const fetchAuditLog = auditLogsApi.fetchOne;

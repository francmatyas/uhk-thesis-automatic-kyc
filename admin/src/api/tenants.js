import { axiosInstance } from "@/api/axiosInstance";

const inFlightScopeSwitches = new Map();
const inFlightTenantResolutions = new Map();

export async function resolveTenantBySlug(slug) {
  const key = slug || "";
  const inFlight = inFlightTenantResolutions.get(key);
  if (inFlight) return inFlight;

  const request = axiosInstance
    .get(`/tenants/resolve/${slug}`)
    .then(({ data }) => data)
    .finally(() => {
      inFlightTenantResolutions.delete(key);
    });

  inFlightTenantResolutions.set(key, request);
  return request;
}

export async function switchTenantScope(payload) {
  const key = JSON.stringify(payload || {});
  const inFlight = inFlightScopeSwitches.get(key);
  if (inFlight) return inFlight;

  const request = axiosInstance
    .post("/tenants/switch", payload)
    .then(({ data }) => data)
    .finally(() => {
      inFlightScopeSwitches.delete(key);
    });

  inFlightScopeSwitches.set(key, request);
  return request;
}

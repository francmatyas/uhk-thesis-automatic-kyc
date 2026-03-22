const TENANT_SCOPE_PREFIX = "/t";
const PROVIDER_SCOPE_PREFIX = "/p";

const TENANT_ROUTE_PREFIXES = [
  "/",
  "/settings",
  "/market-tickers",
  "/strategies",
  "/simulations",
  "/bots",
  "/team-settings",
  "/api-keys",
  "/webhooks",
  "/members",
];

const PROVIDER_ROUTE_PREFIXES = [
  "/",
  "/settings",
  "/tenants",
  "/roles",
  "/permissions",
  "/users",
];

function normalizeInnerPath(path = "/") {
  if (!path || path === "/") return "/";
  return path.startsWith("/") ? path : `/${path}`;
}

export function getScopeFromPath(pathname = "") {
  if (pathname === PROVIDER_SCOPE_PREFIX || pathname.startsWith(`${PROVIDER_SCOPE_PREFIX}/`)) {
    return "provider";
  }

  if (pathname === TENANT_SCOPE_PREFIX || pathname.startsWith(`${TENANT_SCOPE_PREFIX}/`)) {
    return "tenant";
  }

  return "global";
}

export function getTenantSlugFromPath(pathname = "") {
  const match = pathname.match(/^\/t\/([^/]+)/);
  return match?.[1] || null;
}

export function stripScopePrefix(pathname = "") {
  if (pathname === PROVIDER_SCOPE_PREFIX) return "/";
  if (pathname.startsWith(`${PROVIDER_SCOPE_PREFIX}/`)) {
    return pathname.slice(PROVIDER_SCOPE_PREFIX.length) || "/";
  }

  const tenantMatch = pathname.match(/^\/t\/[^/]+(\/.*)?$/);
  if (tenantMatch) {
    return tenantMatch[1] || "/";
  }

  return pathname || "/";
}

export function toProviderPath(innerPath = "/") {
  const inner = normalizeInnerPath(innerPath);
  return inner === "/" ? PROVIDER_SCOPE_PREFIX : `${PROVIDER_SCOPE_PREFIX}${inner}`;
}

export function toTenantPath(tenantSlug, innerPath = "/") {
  if (!tenantSlug) return "/";
  const inner = normalizeInnerPath(innerPath);
  return inner === "/" ? `${TENANT_SCOPE_PREFIX}/${tenantSlug}` : `${TENANT_SCOPE_PREFIX}/${tenantSlug}${inner}`;
}

function isAllowedInScope(innerPath, allowedPrefixes) {
  const inner = normalizeInnerPath(innerPath);
  return allowedPrefixes.some((prefix) => {
    if (prefix === "/") return inner === "/";
    return inner === prefix || inner.startsWith(`${prefix}/`);
  });
}

export function isTenantInnerPath(innerPath) {
  return isAllowedInScope(innerPath, TENANT_ROUTE_PREFIXES);
}

export function isProviderInnerPath(innerPath) {
  return isAllowedInScope(innerPath, PROVIDER_ROUTE_PREFIXES);
}

export function getScopeHomePath(scope, tenantSlug) {
  if (scope === "tenant") return toTenantPath(tenantSlug);
  if (scope === "provider") return toProviderPath("/");
  return "/";
}

export function getScopedPath({ scope, tenantSlug, innerPath = "/" }) {
  if (scope === "tenant") {
    return toTenantPath(tenantSlug, innerPath);
  }
  if (scope === "provider") {
    return toProviderPath(innerPath);
  }
  return normalizeInnerPath(innerPath);
}

export function getSwitchTargetPath({
  currentPath,
  targetScope,
  targetTenantSlug,
}) {
  const innerPath = stripScopePrefix(currentPath);

  if (targetScope === "provider") {
    const nextInner = isProviderInnerPath(innerPath) ? innerPath : "/";
    return toProviderPath(nextInner);
  }

  if (targetScope === "tenant") {
    const nextInner = isTenantInnerPath(innerPath) ? innerPath : "/";
    return toTenantPath(targetTenantSlug, nextInner);
  }

  return "/";
}

export {
  TENANT_SCOPE_PREFIX,
  PROVIDER_SCOPE_PREFIX,
  TENANT_ROUTE_PREFIXES,
  PROVIDER_ROUTE_PREFIXES,
};

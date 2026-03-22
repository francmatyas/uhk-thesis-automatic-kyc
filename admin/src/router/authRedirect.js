export function getCurrentRelativeUrl() {
  const { pathname = "/", search = "", hash = "" } = window.location || {};
  return `${pathname}${search}${hash}`;
}

export function buildLoginUrl(redirectTo) {
  if (!redirectTo) return "/login";
  const params = new URLSearchParams({ redirect: redirectTo });
  return `/login?${params.toString()}`;
}

export function getPostLoginRedirect(searchParams) {
  const redirect = searchParams.get("redirect");
  if (!redirect) return null;
  if (!redirect.startsWith("/")) return null;
  if (redirect.startsWith("//")) return null;
  return redirect;
}

export function isRedirectAllowedForProfile(redirect, profile) {
  if (!redirect) return false;
  // Determine scope from path prefix
  if (redirect === "/p" || redirect.startsWith("/p/")) {
    return Boolean(profile?.user?.isProviderUser ?? profile?.isProviderUser);
  }
  if (redirect.startsWith("/t/")) {
    const match = redirect.match(/^\/t\/([^/]+)/);
    const slug = match?.[1];
    if (!slug) return false;
    const tenants = Array.isArray(profile?.tenants) ? profile.tenants : [];
    return tenants.some((t) => t?.slug === slug);
  }
  return true;
}

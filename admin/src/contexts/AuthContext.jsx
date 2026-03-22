import React, {
  createContext,
  useState,
  useEffect,
  useContext,
  useMemo,
  useCallback,
} from "react";
import { useLocation } from "react-router";
import * as auth from "@/api/auth";
import { getScopeFromPath, getTenantSlugFromPath } from "@/router/scope";

const AuthContext = createContext();

function extractPermissions(source) {
  if (!source) return [];
  if (Array.isArray(source)) {
    return source.filter(Boolean);
  }
  return [];
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [preferences, setPreferences] = useState(null);

  const [tenants, setTenants] = useState([]);
  const [activeTenantId, setActiveTenantId] = useState(null);

  const [initialized, setInitialized] = useState(false);
  const [permissions, setPermissions] = useState(new Set());
  const [roles, setRoles] = useState(new Set());
  const { pathname } = useLocation();
  const activeScope = useMemo(() => getScopeFromPath(pathname), [pathname]);
  const routeTenantSlug = useMemo(
    () => getTenantSlugFromPath(pathname),
    [pathname],
  );

  const permissionSet = useMemo(() => permissions, [permissions]);

  const hasRole = useCallback(
    (role, options = {}) => {
      if (!role) return false;
      const { mode = "any" } = options;
      const required = Array.isArray(role) ? role : [role];

      if (!required.length) return false;

      const checks = required.map((r) => roles.has(r));
      return mode === "all" ? checks.every(Boolean) : checks.some(Boolean);
    },
    [roles],
  );

  const hasPermission = useCallback(
    (permission, options = {}) => {
      if (!permission) return false;

      const { mode = "any" } = options;
      const required = Array.isArray(permission) ? permission : [permission];

      if (!required.length) return false;

      const checks = required.map((perm) => permissionSet.has(perm));

      return mode === "all" ? checks.every(Boolean) : checks.some(Boolean);
    },
    [permissionSet],
  );

  const pathnamesWithoutAuth = ["/login", "/register", "/forgot-password"];

  useEffect(() => {
    let cancelled = false;
    const onProtectedRoute = !pathnamesWithoutAuth.includes(pathname);

    async function ensureProfile() {
      // If we're on a public route, mark initialized and skip fetching
      if (!onProtectedRoute) {
        setInitialized(true);
        return;
      }
      // If we already have a user, we're good
      if (user) {
        setInitialized(true);
        return;
      }
      try {
        const data = await auth.fetchProfile();
        if (!cancelled) {
          setProfile(data);
          setTenants(data?.tenants || []);
          setActiveTenantId(data?.activeTenantId || null);
        }
      } catch {
        // Let the axios interceptor handle 401 → redirect; we only finalize init here
      } finally {
        if (!cancelled) setInitialized(true);
      }
    }

    ensureProfile();
    return () => {
      cancelled = true;
    };
  }, [pathname, user]);

  function setProfile(profile) {
    if (!profile?.user) {
      setUser(null);
      setPermissions(new Set());
      setRoles(new Set());
      setTenants([]);
      setActiveTenantId(null);
      return;
    }

    setUser(profile.user);
    setPreferences(profile?.preferences || null);
    setPermissions(new Set(extractPermissions(profile?.permissions)));
    setRoles(new Set(extractPermissions(profile?.roles)));
    setTenants(profile?.tenants || []);
    setActiveTenantId(profile?.activeTenantId ?? null);
  }

  const applyTenantSwitch = useCallback(
    (payload) => {
      setActiveTenantId(payload?.activeTenantId ?? null);
      if (payload?.permissions) {
        setPermissions(new Set(extractPermissions(payload.permissions)));
      }
      if (payload?.roles) {
        setRoles(new Set(extractPermissions(payload.roles)));
      }
      if (payload?.tenant) {
        setTenants((current) => {
          const list = Array.isArray(current) ? current : [];
          const exists = list.some((item) => item?.id === payload.tenant?.id);
          return exists ? list : [...list, payload.tenant];
        });
      }
    },
    [setActiveTenantId, setPermissions, setRoles, setTenants],
  );

  if (!initialized) return null;

  return (
    <AuthContext.Provider
      value={{
        user,
        setUser,
        preferences,
        setPreferences,
        setProfile,
        tenants,
        activeTenantId,
        activeScope,
        routeTenantSlug,
        isProvider: activeScope === "provider",
        applyTenantSwitch,
        permissions,
        roles,
        hasPermission,
        hasRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}

import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation, useParams } from "react-router";
import { useAuth } from "@/contexts/AuthContext";
import { Loader } from "@/components/ui/loader";
import { resolveTenantBySlug, switchTenantScope } from "@/api/tenants";
import {
  getScopeFromPath,
  getTenantSlugFromPath,
  toProviderPath,
  toTenantPath,
} from "@/router/scope";
import { buildLoginUrl } from "@/router/authRedirect";

function ScopeMessage({ title, description }) {
  return (
    <div className="h-full min-h-[50vh] w-full flex items-center justify-center p-6">
      <div className="max-w-md text-center">
        <h1 className="text-2xl font-semibold mb-2">{title}</h1>
        <p className="text-muted-foreground">{description}</p>
      </div>
    </div>
  );
}

export function ScopeLandingRedirect() {
  const { user, activeTenantId, tenants } = useAuth();
  const tenantList = Array.isArray(tenants) ? tenants : [];

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (activeTenantId) {
    const activeTenant = tenantList.find(
      (tenant) => tenant?.id === activeTenantId,
    );

    if (activeTenant?.slug) {
      return <Navigate to={toTenantPath(activeTenant.slug)} replace />;
    }
  }

  const firstTenant = tenantList.find((tenant) => Boolean(tenant?.slug));
  if (firstTenant?.slug) {
    return <Navigate to={toTenantPath(firstTenant.slug)} replace />;
  }

  return <Navigate to={toProviderPath("/")} replace />;
}

export function ProviderScopeGuard() {
  const location = useLocation();
  const { user, activeTenantId, applyTenantSwitch } = useAuth();
  const [state, setState] = useState({ status: "idle", errorCode: null });

  useEffect(() => {
    let cancelled = false;

    async function syncProviderScope() {
      if (!user) return;
      if (!activeTenantId) {
        if (!cancelled) setState({ status: "ready", errorCode: null });
        return;
      }

      if (!cancelled) setState({ status: "loading", errorCode: null });

      try {
        const payload = await switchTenantScope({ tenantId: null });
        if (cancelled) return;
        applyTenantSwitch(payload);
        setState({ status: "ready", errorCode: null });
      } catch (error) {
        if (cancelled) return;
        const status = error?.response?.status;
        if (status === 401) {
          const currentUrl = `${window.location.pathname}${window.location.search}${window.location.hash}`;
          window.location.href = buildLoginUrl(currentUrl);
          return;
        }

        if (status === 403) {
          setState({ status: "error", errorCode: 403 });
          return;
        }

        setState({ status: "error", errorCode: status || 500 });
      }
    }

    syncProviderScope();

    return () => {
      cancelled = true;
    };
  }, [user, activeTenantId, applyTenantSwitch]);

  if (!user) {
    return (
      <Navigate
        to={buildLoginUrl(
          `${location.pathname}${location.search}${location.hash}`,
        )}
        replace
      />
    );
  }

  if (state.status === "loading" || state.status === "idle") {
    return (
      <div className="h-screen w-screen flex items-center justify-center">
        <Loader fullPage />
      </div>
    );
  }

  if (state.status === "error") {
    if (state.errorCode === 403) {
      return (
        <ScopeMessage
          title="No Access"
          description="You do not have access to provider scope."
        />
      );
    }
    return (
      <ScopeMessage
        title="Scope Error"
        description="Provider scope could not be initialized."
      />
    );
  }

  return <Outlet />;
}

export function TenantScopeGuard() {
  const location = useLocation();
  const { tenantSlug } = useParams();
  const { user, activeTenantId, applyTenantSwitch } = useAuth();
  const [state, setState] = useState({ status: "idle", errorCode: null });

  useEffect(() => {
    let cancelled = false;

    async function syncTenantScope() {
      if (!user || !tenantSlug) return;

      if (!cancelled) setState({ status: "loading", errorCode: null });

      try {
        const resolved = await resolveTenantBySlug(tenantSlug);
        if (cancelled) return;

        const resolvedTenant = resolved?.tenant || resolved;
        const resolvedId = resolved?.activeTenantId || resolvedTenant?.id;

        if (!resolvedTenant?.id || !resolvedId) {
          setState({ status: "error", errorCode: 500 });
          return;
        }

        if (activeTenantId !== resolvedId) {
          const payload = await switchTenantScope({ tenantSlug });
          if (cancelled) return;
          applyTenantSwitch({
            ...payload,
            tenant: payload?.tenant || resolvedTenant,
          });
        } else {
          applyTenantSwitch({
            activeTenantId: resolvedId,
            tenant: resolvedTenant,
          });
        }

        setState({ status: "ready", errorCode: null });
      } catch (error) {
        if (cancelled) return;

        const status = error?.response?.status;
        if (status === 401) {
          const currentUrl = `${window.location.pathname}${window.location.search}${window.location.hash}`;
          window.location.href = buildLoginUrl(currentUrl);
          return;
        }

        if (status === 403 || status === 404) {
          setState({ status: "error", errorCode: status });
          return;
        }

        setState({ status: "error", errorCode: status || 500 });
      }
    }

    syncTenantScope();

    return () => {
      cancelled = true;
    };
  }, [user, tenantSlug, applyTenantSwitch]);

  if (!user) {
    return (
      <Navigate
        to={buildLoginUrl(
          `${location.pathname}${location.search}${location.hash}`,
        )}
        replace
      />
    );
  }

  if (!tenantSlug) {
    return <Navigate to={toProviderPath("/")} replace />;
  }

  if (state.status === "loading" || state.status === "idle") {
    return (
      <div className="h-screen w-screen flex items-center justify-center">
        <Loader fullPage />
      </div>
    );
  }

  if (state.status === "error") {
    /* if (state.errorCode === 404) {
      return (
        <ScopeMessage
          title="Tenant Not Found"
          description="The requested tenant does not exist."
        />
      );
    } */

    if (state.errorCode === 403 || state.errorCode === 404) {
      return (
        <ScopeMessage
          title="No Access"
          description="You do not have access to this tenant."
        />
      );
    }

    return (
      <ScopeMessage
        title="Scope Error"
        description="Tenant scope could not be initialized."
      />
    );
  }

  return <Outlet />;
}

export function ScopeRouteAliasRedirect({ innerPath }) {
  const location = useLocation();
  const currentScope = getScopeFromPath(location.pathname);
  const tenantSlug = getTenantSlugFromPath(location.pathname);

  if (currentScope === "tenant" && tenantSlug) {
    return <Navigate to={toTenantPath(tenantSlug, innerPath)} replace />;
  }

  return <Navigate to={toProviderPath(innerPath)} replace />;
}

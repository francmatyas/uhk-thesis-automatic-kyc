import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Link } from "react-router";
import { toast } from "sonner";
import { InputPassword } from "../ui/input-password";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

import { login } from "@/api/auth";
import { sha256 } from "js-sha256";
import { useAuth } from "@/contexts/AuthContext";
import {
  getPostLoginRedirect,
  isRedirectAllowedForProfile,
} from "@/router/authRedirect";
import { toProviderPath, toTenantPath } from "@/router/scope";

export default function LoginForm({ className, ...props }) {
  const [form, setForm] = useState({});
  const [loading, setLoading] = useState(false);
  const [selectionState, setSelectionState] = useState(null);
  const [selectedTenantId, setSelectedTenantId] = useState(undefined);
  const [switching, setSwitching] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { setProfile } = useAuth();
  const redirectTo = getPostLoginRedirect(searchParams);

  function handleChange(name, value) {
    setForm({ ...form, [name]: value });
  }

  const workspaceOptions = useMemo(() => {
    if (!selectionState) return null;
    const tenantList = Array.isArray(selectionState.tenants)
      ? selectionState.tenants
      : [];
    const canProvider = Boolean(selectionState.isProviderUser);
    return { tenantList, canProvider };
  }, [selectionState]);

  function handleLogin(e) {
    e.preventDefault();
    if (loading || !form.email || !form.password) return;

    setLoading(true);
    login(form.email, sha256(form.password), form.rememberMe)
      .then((res) => {
        const profile = res.data;
        setProfile(profile);
        if (redirectTo && isRedirectAllowedForProfile(redirectTo, profile)) {
          navigate(redirectTo, { replace: true });
          return;
        }

        const tenantList = Array.isArray(profile?.tenants)
          ? profile.tenants
          : [];
        const canProvider = Boolean(
          profile?.user?.isProviderUser ?? profile?.isProviderUser,
        );
        const shouldSelect =
          (canProvider && tenantList.length > 0) || tenantList.length > 1;

        if (!shouldSelect) {
          navigate(getDefaultPostLoginPath(profile), { replace: true });
          return;
        }

        setSelectionState({
          tenants: tenantList,
          isProviderUser: canProvider,
        });

        if (profile?.activeTenantId != null) {
          setSelectedTenantId(profile.activeTenantId);
        } else if (canProvider) {
          setSelectedTenantId(null);
        } else if (tenantList.length > 0) {
          setSelectedTenantId(tenantList[0].id);
        } else {
          setSelectedTenantId(undefined);
        }
      })
      .catch((err) => {
        toast.error(
          err.response?.data?.error || "Login failed. Please try again.",
        );
      })
      .finally(() => setLoading(false));
  }

  async function handleWorkspaceContinue() {
    if (!selectionState) return;
    if (switching || typeof selectedTenantId === "undefined") return;

    setSwitching(true);
    try {
      const selectedTenant = workspaceOptions?.tenantList?.find(
        (tenant) => tenant?.id === selectedTenantId,
      );
      if (selectedTenantId === null) {
        setSelectionState(null);
        navigate(toProviderPath("/"), { replace: true });
        return;
      }

      if (!selectedTenant?.slug) {
        toast.error("Selected tenant is missing a slug.");
        return;
      }

      setSelectionState(null);
      navigate(toTenantPath(selectedTenant.slug), { replace: true });
    } catch {
      toast.error("Failed to continue. Please try again.");
    } finally {
      setSwitching(false);
    }
  }

  const showingWorkspaceSelect = Boolean(selectionState && workspaceOptions);

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card className="overflow-hidden p-0">
        <CardContent className="grid p-0 md:grid-cols-2">
          {showingWorkspaceSelect ? (
            <div className="p-6 md:p-8">
              <div className="flex flex-col gap-6">
                <div className="flex flex-col items-center text-center">
                  <h1 className="text-2xl font-bold">Select workspace</h1>
                  <p className="text-balance text-muted-foreground">
                    Choose where you want to start.
                  </p>
                </div>
                <div className="grid gap-2">
                  {workspaceOptions?.canProvider && (
                    <button
                      type="button"
                      onClick={() => setSelectedTenantId(null)}
                      className={cn(
                        "flex items-center gap-3 rounded-md border p-2 text-left transition hover:bg-accent bg-diagonal border-orange-800",
                        selectedTenantId === null && "ring-2 ring-primary",
                      )}
                      style={{
                        "--diag-color": "255 79 0", // RGB, space-separated
                        "--diag-opacity": "0.2",
                        "--diag-gap": "10px",
                      }}
                    >
                      <Avatar className="h-8 w-8 rounded-md">
                        <AvatarImage src={null} alt="Provider" />
                        <AvatarFallback className="rounded-md">
                          {getInitials("Provider")}
                        </AvatarFallback>
                      </Avatar>
                      <div className="grid">
                        <span className="font-medium">Provider</span>
                        <span className="text-xs text-muted-foreground">
                          Provider workspace
                        </span>
                      </div>
                    </button>
                  )}
                  {workspaceOptions?.tenantList?.map((tenant) => (
                    <button
                      key={tenant.id}
                      type="button"
                      onClick={() => setSelectedTenantId(tenant.id)}
                      className={cn(
                        "flex items-center gap-3 rounded-md border p-2 text-left transition hover:bg-accent",
                        selectedTenantId === tenant.id && "ring-2 ring-primary",
                      )}
                    >
                      <Avatar className="h-8 w-8 rounded-md">
                        <AvatarImage
                          src={tenant?.avatarUrl || null}
                          alt={tenant?.name}
                        />
                        <AvatarFallback className="rounded-md">
                          {getInitials(tenant?.name || "T")}
                        </AvatarFallback>
                      </Avatar>
                      <div className="grid">
                        <span className="font-medium">
                          {tenant?.name || "Tenant"}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          Tenant workspace
                        </span>
                      </div>
                    </button>
                  ))}
                </div>
                <Button
                  type="button"
                  className="w-full cursor-pointer"
                  onClick={handleWorkspaceContinue}
                  disabled={
                    switching || typeof selectedTenantId === "undefined"
                  }
                  loading={switching}
                >
                  Continue
                </Button>
              </div>
            </div>
          ) : (
            <form className="p-6 md:p-8" onSubmit={handleLogin}>
              <div className="flex flex-col gap-6">
                <div className="flex flex-col items-center text-center">
                  <h1 className="text-2xl font-bold">Welcome back</h1>
                  <p className="text-balance text-muted-foreground">
                    Login to your Acme Inc account
                  </p>
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="username">Email</Label>
                  <Input
                    id="username"
                    type="email"
                    name="username"
                    placeholder="johndoe@example.com"
                    required
                    onChange={(e) => handleChange("email", e.target.value)}
                    value={form.email}
                    autoComplete="username"
                  />
                </div>
                <div className="grid gap-2">
                  <div className="flex items-center">
                    <Label htmlFor="password">Password</Label>
                  </div>
                  <InputPassword
                    id="password"
                    name="password"
                    required
                    onChange={(e) => handleChange("password", e.target.value)}
                    value={form.password}
                    autoComplete="current-password"
                  />
                  <div className="flex items-center justify-between">
                    <Label className="flex items-center cursor-pointer">
                      <Checkbox
                        onCheckedChange={(value) =>
                          handleChange("rememberMe", value)
                        }
                        checked={form.rememberMe}
                      />
                      <span className="text-xs">Remember me</span>
                    </Label>
                    <Link
                      to="/forgot-password"
                      className="ml-auto text-xs underline-offset-2 hover:underline"
                    >
                      Forgot your password?
                    </Link>
                  </div>
                </div>
                <Button
                  type="submit"
                  className="w-full cursor-pointer"
                  loading={loading}
                >
                  Login
                </Button>
              </div>
            </form>
          )}
          <div className="relative hidden bg-muted md:block">
            <img
              src="/placeholder.svg"
              alt="Image"
              className="absolute inset-0 h-full w-full object-cover dark:brightness-[0.2] dark:grayscale"
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function getInitials(value) {
  if (!value) return "T";
  const parts = value.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "T";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

function getDefaultPostLoginPath(profile) {
  const tenantList = Array.isArray(profile?.tenants) ? profile.tenants : [];
  const activeTenant = tenantList.find(
    (tenant) => tenant?.id === profile?.activeTenantId,
  );

  if (activeTenant?.slug) {
    return toTenantPath(activeTenant.slug);
  }

  if (profile?.user?.isProviderUser ?? profile?.isProviderUser) {
    return toProviderPath("/");
  }

  const firstTenantWithSlug = tenantList.find((tenant) =>
    Boolean(tenant?.slug),
  );
  if (firstTenantWithSlug?.slug) {
    return toTenantPath(firstTenantWithSlug.slug);
  }

  return toProviderPath("/");
}

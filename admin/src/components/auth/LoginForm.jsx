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
                <div className="relative text-center text-sm after:absolute after:inset-0 after:top-1/2 after:z-0 after:flex after:items-center after:border-t after:border-border">
                  <span className="relative z-10 bg-card px-2 text-muted-foreground text-xs">
                    OR
                  </span>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <Button variant="outline" className="w-full cursor-pointer">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                      <path
                        d="M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701"
                        fill="currentColor"
                      />
                    </svg>
                    <span className="sr-only">Login with Apple</span>
                  </Button>
                  <Button variant="outline" className="w-full cursor-pointer">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                      <path
                        d="M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z"
                        fill="currentColor"
                      />
                    </svg>
                    <span className="sr-only">Login with Google</span>
                  </Button>
                  <Button variant="outline" className="w-full cursor-pointer">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                      <path
                        d="M6.915 4.03c-1.968 0-3.683 1.28-4.871 3.113C.704 9.208 0 11.883 0 14.449c0 .706.07 1.369.21 1.973a6.624 6.624 0 0 0 .265.86 5.297 5.297 0 0 0 .371.761c.696 1.159 1.818 1.927 3.593 1.927 1.497 0 2.633-.671 3.965-2.444.76-1.012 1.144-1.626 2.663-4.32l.756-1.339.186-.325c.061.1.121.196.183.3l2.152 3.595c.724 1.21 1.665 2.556 2.47 3.314 1.046.987 1.992 1.22 3.06 1.22 1.075 0 1.876-.355 2.455-.843a3.743 3.743 0 0 0 .81-.973c.542-.939.861-2.127.861-3.745 0-2.72-.681-5.357-2.084-7.45-1.282-1.912-2.957-2.93-4.716-2.93-1.047 0-2.088.467-3.053 1.308-.652.57-1.257 1.29-1.82 2.05-.69-.875-1.335-1.547-1.958-2.056-1.182-.966-2.315-1.303-3.454-1.303zm10.16 2.053c1.147 0 2.188.758 2.992 1.999 1.132 1.748 1.647 4.195 1.647 6.4 0 1.548-.368 2.9-1.839 2.9-.58 0-1.027-.23-1.664-1.004-.496-.601-1.343-1.878-2.832-4.358l-.617-1.028a44.908 44.908 0 0 0-1.255-1.98c.07-.109.141-.224.211-.327 1.12-1.667 2.118-2.602 3.358-2.602zm-10.201.553c1.265 0 2.058.791 2.675 1.446.307.327.737.871 1.234 1.579l-1.02 1.566c-.757 1.163-1.882 3.017-2.837 4.338-1.191 1.649-1.81 1.817-2.486 1.817-.524 0-1.038-.237-1.383-.794-.263-.426-.464-1.13-.464-2.046 0-2.221.63-4.535 1.66-6.088.454-.687.964-1.226 1.533-1.533a2.264 2.264 0 0 1 1.088-.285z"
                        fill="currentColor"
                      />
                    </svg>
                    <span className="sr-only">Login with Meta</span>
                  </Button>
                </div>
                <div className="text-center text-sm">
                  Don&apos;t have an account?{" "}
                  <Link to="/register" className="underline underline-offset-4">
                    Sign up
                  </Link>
                </div>
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

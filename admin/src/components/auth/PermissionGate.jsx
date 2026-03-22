import React from "react";
import { useAuth } from "@/contexts/AuthContext";

export default function PermissionGate({
  permission,
  children,
  fallback = null,
  mode = "any",
}) {
  const { hasPermission } = useAuth();

  if (!hasPermission(permission, { mode })) {
    return fallback;
  }

  return <>{children}</>;
}

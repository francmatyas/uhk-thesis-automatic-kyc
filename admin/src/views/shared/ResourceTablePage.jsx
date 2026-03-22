import { useMemo } from "react";
import { Link, useParams } from "react-router";
import { PlusCircle } from "lucide-react";
import Table from "@/components/table/Table";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/AuthContext";

export default function ResourceTablePage({
  module,
  basePath,
  createPath,
  createLabel = "New",
  searchPlaceholder,
  enumConfig,
  showCreateButton = true,
  createPermission,
  buttons = [],
}) {
  const { tenantSlug } = useParams();
  const { hasPermission } = useAuth();
  const resolvedCreatePath =
    typeof createPath === "function" ? createPath(tenantSlug) : createPath;

  const canCreate = !createPermission || hasPermission(createPermission);

  const headerButtons = useMemo(() => {
    const normalizedButtons = Array.isArray(buttons) ? buttons : [];

    if (!showCreateButton || !resolvedCreatePath || !canCreate) {
      return normalizedButtons;
    }

    return [
      <Link to={resolvedCreatePath} key={`${module}-create`}>
        <Button size="default">
          <PlusCircle />
          {createLabel}
        </Button>
      </Link>,
      ...normalizedButtons,
    ];
  }, [buttons, showCreateButton, resolvedCreatePath, canCreate, module, createLabel]);

  return (
    <div className="flex flex-col gap-2 p-2">
      <div className="h-full">
        <Table
          module={module}
          basePath={basePath}
          enumConfig={enumConfig}
          buttons={headerButtons}
          translations={{
            SEARCH_PLACEHOLDER: searchPlaceholder || "Search",
          }}
        />
      </div>
    </div>
  );
}

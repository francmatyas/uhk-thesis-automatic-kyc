import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router";
import { PlusCircle } from "lucide-react";
import { toast } from "sonner";
import { createApiKey } from "@/api/tenant/apiKeys";
import { queryKeys } from "@/modules/queryKeys";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAuth } from "@/contexts/AuthContext";

function mapCreatedCredentials(result) {
  return {
    id: result?.id,
    publicKey: result?.publicKey ?? result?.public_key ?? "",
    secret: result?.secret ?? result?.secretKey ?? result?.secret_key ?? "",
  };
}

export default function ApiKeyCreateWizardDialog() {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [created, setCreated] = useState(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { tenantSlug } = useParams();
  const { hasPermission } = useAuth();

  const resetWizard = () => {
    setName("");
    setCreated(null);
    createMutation.reset();
  };

  const createMutation = useMutation({
    mutationFn: (payload) => createApiKey(payload),
    onSuccess: async (result) => {
      setCreated(mapCreatedCredentials(result));
      await queryClient.invalidateQueries({ queryKey: queryKeys.apiKeys.list() });
      toast.success("API key created successfully.");
    },
    onError: () => {
      toast.error("Failed to create API key. Please try again.");
    },
  });

  const handleOpenChange = (nextOpen) => {
    setOpen(nextOpen);
    if (!nextOpen) {
      resetWizard();
    }
  };

  const handleCreate = () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      toast.error("API key name is required.");
      return;
    }

    createMutation.mutate({ name: trimmedName });
  };

  const handleOpenDetail = () => {
    if (!created?.id) return;
    navigate(tenantModuleDefinitions.apiKeys.routes.detail(tenantSlug, created.id));
    handleOpenChange(false);
  };

  if (!hasPermission("tenant.api-keys:create")) return null;

  return (
    <>
      <Button size="default" onClick={() => setOpen(true)}>
        <PlusCircle />
        New API Key
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent showCloseButton={!createMutation.isPending}>
          {!created && (
            <>
              <DialogHeader>
                <DialogTitle>Create API Key</DialogTitle>
                <DialogDescription>
                  Enter a name. You will see the secret exactly once after creation.
                </DialogDescription>
              </DialogHeader>

              <div className="py-2">
                <Input
                  label="Name"
                  required
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Trading bot key"
                  disabled={createMutation.isPending}
                />
              </div>

              <DialogFooter>
                <Button
                  variant="ghost"
                  onClick={() => handleOpenChange(false)}
                  disabled={createMutation.isPending}
                >
                  Cancel
                </Button>
                <Button onClick={handleCreate} loading={createMutation.isPending}>
                  Create
                </Button>
              </DialogFooter>
            </>
          )}

          {created && (
            <>
              <DialogHeader>
                <DialogTitle>Store Your API Secret Now</DialogTitle>
                <DialogDescription>
                  This secret is shown only once and will not be visible again.
                </DialogDescription>
              </DialogHeader>

              <div className="flex flex-col gap-3 py-1">
                <div className="rounded-md border p-3">
                  <div className="text-xs text-muted-foreground">Public Key</div>
                  <code className="block break-all pt-1 text-sm">
                    {created.publicKey || "Not returned by API"}
                  </code>
                </div>

                <div className="rounded-md border p-3">
                  <div className="text-xs text-muted-foreground">Secret</div>
                  <code className="block break-all pt-1 text-sm">
                    {created.secret || "Not returned by API"}
                  </code>
                </div>
              </div>

              <DialogFooter>
                <Button variant="ghost" onClick={() => handleOpenChange(false)}>
                  Close
                </Button>
                <Button onClick={handleOpenDetail} disabled={!created.id}>
                  Open Detail
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

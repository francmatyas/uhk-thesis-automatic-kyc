import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();
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
      toast.success(t("moduleDefinitions.apiKeys.messages.success.create"));
    },
    onError: () => {
      toast.error(t("moduleDefinitions.apiKeys.createWizard.toasts.errorCreate"));
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
      toast.error(t("moduleDefinitions.apiKeys.createWizard.validation.nameRequired"));
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
        {t("moduleDefinitions.apiKeys.createWizard.trigger")}
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent showCloseButton={!createMutation.isPending}>
          {!created && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {t("moduleDefinitions.apiKeys.createWizard.dialog.title")}
                </DialogTitle>
                <DialogDescription>
                  {t("moduleDefinitions.apiKeys.createWizard.dialog.description")}
                </DialogDescription>
              </DialogHeader>

              <div className="py-2">
                <Input
                  label={t("moduleDefinitions.apiKeys.createWizard.fields.name")}
                  required
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder={t(
                    "moduleDefinitions.apiKeys.createWizard.fields.namePlaceholder",
                  )}
                  disabled={createMutation.isPending}
                />
              </div>

              <DialogFooter>
                <Button
                  variant="ghost"
                  onClick={() => handleOpenChange(false)}
                  disabled={createMutation.isPending}
                >
                  {t("moduleDefinitions.apiKeys.createWizard.actions.cancel")}
                </Button>
                <Button onClick={handleCreate} loading={createMutation.isPending}>
                  {t("moduleDefinitions.apiKeys.createWizard.actions.create")}
                </Button>
              </DialogFooter>
            </>
          )}

          {created && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {t("moduleDefinitions.apiKeys.createWizard.created.title")}
                </DialogTitle>
                <DialogDescription>
                  {t("moduleDefinitions.apiKeys.createWizard.created.description")}
                </DialogDescription>
              </DialogHeader>

              <div className="flex flex-col gap-3 py-1">
                <Input
                  label={t("moduleDefinitions.apiKeys.createWizard.created.publicKey")}
                  readOnly
                  clickToCopy
                  value={created.publicKey || t("moduleDefinitions.apiKeys.createWizard.created.notReturned")}
                />
                <Input
                  label={t("moduleDefinitions.apiKeys.createWizard.created.secret")}
                  readOnly
                  clickToCopy
                  value={created.secret || t("moduleDefinitions.apiKeys.createWizard.created.notReturned")}
                />
              </div>

              <DialogFooter>
                <Button variant="ghost" onClick={() => handleOpenChange(false)}>
                  {t("moduleDefinitions.apiKeys.createWizard.actions.close")}
                </Button>
                <Button onClick={handleOpenDetail} disabled={!created.id}>
                  {t("moduleDefinitions.apiKeys.createWizard.actions.openDetail")}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

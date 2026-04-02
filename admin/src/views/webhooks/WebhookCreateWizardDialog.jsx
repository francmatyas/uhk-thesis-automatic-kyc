import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router";
import { useTranslation } from "react-i18next";
import { PlusCircle } from "lucide-react";
import { toast } from "sonner";
import { createWebhook, fetchWebhookOptions } from "@/api/tenant/webhooks";
import { queryKeys } from "@/modules/queryKeys";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAuth } from "@/contexts/AuthContext";

export default function WebhookCreateWizardDialog() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [url, setUrl] = useState("");
  const [secret, setSecret] = useState("");
  const [eventTypes, setEventTypes] = useState([]);
  const [createdId, setCreatedId] = useState(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { tenantSlug } = useParams();
  const { hasPermission } = useAuth();
  const { data: webhookOptions } = useQuery({
    queryKey: ["webhooks", "options"],
    queryFn: fetchWebhookOptions,
    staleTime: 5 * 60 * 1000,
  });
  const availableEventTypes = Array.isArray(webhookOptions?.eventTypes)
    ? webhookOptions.eventTypes
    : [];

  const createMutation = useMutation({
    mutationFn: (payload) => createWebhook(payload),
    onSuccess: async (result) => {
      setCreatedId(result?.id || null);
      await queryClient.invalidateQueries({ queryKey: queryKeys.webhooks.list() });
      toast.success(t("moduleDefinitions.webhooks.messages.success.create"));
    },
    onError: () => {
      toast.error(t("moduleDefinitions.webhooks.createWizard.toasts.errorCreate"));
    },
  });

  const resetWizard = () => {
    setUrl("");
    setSecret("");
    setEventTypes([]);
    setCreatedId(null);
    createMutation.reset();
  };

  const handleOpenChange = (nextOpen) => {
    setOpen(nextOpen);
    if (!nextOpen) {
      resetWizard();
    }
  };

  const handleCreate = () => {
    const trimmedUrl = url.trim();
    const trimmedSecret = secret.trim();

    if (!trimmedUrl) {
      toast.error(t("moduleDefinitions.webhooks.createWizard.validation.urlRequired"));
      return;
    }
    if (!trimmedSecret) {
      toast.error(
        t("moduleDefinitions.webhooks.createWizard.validation.secretRequired"),
      );
      return;
    }
    if (trimmedSecret.length < 16 || trimmedSecret.length > 255) {
      toast.error(
        t("moduleDefinitions.webhooks.createWizard.validation.secretLength"),
      );
      return;
    }
    if (eventTypes.length === 0) {
      toast.error(
        t("moduleDefinitions.webhooks.createWizard.validation.eventTypeRequired"),
      );
      return;
    }

    createMutation.mutate({
      url: trimmedUrl,
      secret: trimmedSecret,
      eventTypes,
    });
  };

  const toggleEventType = (eventType) => {
    setEventTypes((prev) =>
      prev.includes(eventType)
        ? prev.filter((value) => value !== eventType)
        : [...prev, eventType],
    );
  };

  const handleOpenDetail = () => {
    if (!createdId) return;
    navigate(tenantModuleDefinitions.webhooks.routes.detail(tenantSlug, createdId));
    handleOpenChange(false);
  };

  if (!hasPermission("tenant.webhooks:create")) return null;

  return (
    <>
      <Button size="default" onClick={() => setOpen(true)}>
        <PlusCircle />
        {t("moduleDefinitions.webhooks.createWizard.trigger")}
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent showCloseButton={!createMutation.isPending}>
          {!createdId && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {t("moduleDefinitions.webhooks.createWizard.dialog.title")}
                </DialogTitle>
                <DialogDescription>
                  {t("moduleDefinitions.webhooks.createWizard.dialog.description")}
                </DialogDescription>
              </DialogHeader>

              <div className="flex flex-col gap-3 py-2">
                <Input
                  label={t("moduleDefinitions.webhooks.createWizard.fields.url")}
                  required
                  value={url}
                  onChange={(event) => setUrl(event.target.value)}
                  placeholder={t(
                    "moduleDefinitions.webhooks.createWizard.fields.urlPlaceholder",
                  )}
                  disabled={createMutation.isPending}
                />
                <Input
                  label={t("moduleDefinitions.webhooks.createWizard.fields.secret")}
                  required
                  value={secret}
                  onChange={(event) => setSecret(event.target.value)}
                  placeholder={t(
                    "moduleDefinitions.webhooks.createWizard.fields.secretPlaceholder",
                  )}
                  minLength={16}
                  maxLength={255}
                  disabled={createMutation.isPending}
                />
                <p className="text-xs text-muted-foreground">
                  {t("moduleDefinitions.webhooks.createWizard.fields.secretHint")}
                </p>
                <div className="flex flex-col gap-2">
                  <span className="text-xs font-medium">
                    {t("moduleDefinitions.webhooks.createWizard.fields.eventTypes")}
                  </span>
                  <div className="max-h-40 overflow-y-auto rounded-md border p-2">
                    {availableEventTypes.length === 0 && (
                      <div className="text-sm text-muted-foreground">
                        {t(
                          "moduleDefinitions.webhooks.createWizard.fields.noEventTypes",
                        )}
                      </div>
                    )}
                    {availableEventTypes.length > 0 && (
                      <div className="flex flex-col gap-2">
                        {availableEventTypes.map((eventType) => (
                          <label
                            key={eventType}
                            className="flex items-center gap-2 text-sm"
                          >
                            <Checkbox
                              checked={eventTypes.includes(eventType)}
                              onCheckedChange={() => toggleEventType(eventType)}
                              disabled={createMutation.isPending}
                            />
                            <span>{eventType}</span>
                          </label>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </div>

              <DialogFooter>
                <Button
                  variant="ghost"
                  onClick={() => handleOpenChange(false)}
                  disabled={createMutation.isPending}
                >
                  {t("moduleDefinitions.webhooks.createWizard.actions.cancel")}
                </Button>
                <Button onClick={handleCreate} loading={createMutation.isPending}>
                  {t("moduleDefinitions.webhooks.createWizard.actions.create")}
                </Button>
              </DialogFooter>
            </>
          )}

          {createdId && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {t("moduleDefinitions.webhooks.createWizard.created.title")}
                </DialogTitle>
                <DialogDescription>
                  {t("moduleDefinitions.webhooks.createWizard.created.description")}
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="ghost" onClick={() => handleOpenChange(false)}>
                  {t("moduleDefinitions.webhooks.createWizard.actions.close")}
                </Button>
                <Button onClick={handleOpenDetail}>
                  {t("moduleDefinitions.webhooks.createWizard.actions.openDetail")}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

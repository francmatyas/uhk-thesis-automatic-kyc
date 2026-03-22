import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router";
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
      toast.success("Webhook created successfully.");
    },
    onError: () => {
      toast.error("Failed to create webhook. Please try again.");
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
      toast.error("Webhook URL is required.");
      return;
    }
    if (!trimmedSecret) {
      toast.error("Webhook secret is required.");
      return;
    }
    if (trimmedSecret.length < 16 || trimmedSecret.length > 255) {
      toast.error("Webhook secret must be between 16 and 255 characters.");
      return;
    }
    if (eventTypes.length === 0) {
      toast.error("Select at least one event type.");
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
        New Webhook
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent showCloseButton={!createMutation.isPending}>
          {!createdId && (
            <>
              <DialogHeader>
                <DialogTitle>Create Webhook</DialogTitle>
                <DialogDescription>
                  Enter the target URL and signing secret.
                </DialogDescription>
              </DialogHeader>

              <div className="flex flex-col gap-3 py-2">
                <Input
                  label="URL"
                  required
                  value={url}
                  onChange={(event) => setUrl(event.target.value)}
                  placeholder="https://example.com/webhooks/endpoint"
                  disabled={createMutation.isPending}
                />
                <Input
                  label="Secret"
                  required
                  value={secret}
                  onChange={(event) => setSecret(event.target.value)}
                  placeholder="whsec_..."
                  minLength={16}
                  maxLength={255}
                  disabled={createMutation.isPending}
                />
                <p className="text-xs text-muted-foreground">
                  Secret length must be between 16 and 255 characters.
                </p>
                <div className="flex flex-col gap-2">
                  <span className="text-xs font-medium">Event types</span>
                  <div className="max-h-40 overflow-y-auto rounded-md border p-2">
                    {availableEventTypes.length === 0 && (
                      <div className="text-sm text-muted-foreground">
                        No event types available.
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
                  Cancel
                </Button>
                <Button onClick={handleCreate} loading={createMutation.isPending}>
                  Create
                </Button>
              </DialogFooter>
            </>
          )}

          {createdId && (
            <>
              <DialogHeader>
                <DialogTitle>Webhook Created</DialogTitle>
                <DialogDescription>
                  Your webhook has been created successfully.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="ghost" onClick={() => handleOpenChange(false)}>
                  Close
                </Button>
                <Button onClick={handleOpenDetail}>Open Detail</Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

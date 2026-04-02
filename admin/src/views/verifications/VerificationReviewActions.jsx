import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { CheckCircle, XCircle } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/AuthContext";
import { queryKeys } from "@/modules/queryKeys";

export function VerificationReviewActions({
  entity,
  id,
  permission,
  approveApi,
  rejectApi,
}) {
  const { t } = useTranslation();
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();

  const approveMutation = useMutation({
    mutationFn: () => approveApi(id),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.verifications.detail(id),
      });
      toast.success(
        t("moduleDefinitions.verifications.review.approveSuccess"),
      );
    },
    onError: () =>
      toast.error(t("moduleDefinitions.verifications.review.approveError")),
  });

  const rejectMutation = useMutation({
    mutationFn: () => rejectApi(id),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.verifications.detail(id),
      });
      toast.success(t("moduleDefinitions.verifications.review.rejectSuccess"));
    },
    onError: () =>
      toast.error(t("moduleDefinitions.verifications.review.rejectError")),
  });

  if (entity?.status !== "REQUIRES_REVIEW" || !hasPermission(permission)) {
    return null;
  }

  const isPending = approveMutation.isPending || rejectMutation.isPending;

  return (
    <>
      <Button
        type="button"
        onClick={() => approveMutation.mutate()}
        disabled={isPending}
        className="cursor-pointer"
      >
        <CheckCircle />
        {t("moduleDefinitions.verifications.review.approve")}
      </Button>
      <Button
        type="button"
        variant="destructive"
        onClick={() => rejectMutation.mutate()}
        disabled={isPending}
        className="cursor-pointer"
      >
        <XCircle />
        {t("moduleDefinitions.verifications.review.reject")}
      </Button>
    </>
  );
}

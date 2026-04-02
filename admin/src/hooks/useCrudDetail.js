import { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { toast } from "sonner";

export function useCrudDetail({
  mode,
  id,
  queryKeys,
  fetchOne,
  createOne,
  updateOne,
  deleteOne,
  reset,
  getCreateRedirectPath,
  getDeleteRedirectPath,
  confirmDeleteMessage = "Are you sure you want to delete this item?",
  successMessages = {
    create: "Created successfully.",
    update: "Saved successfully.",
    remove: "Deleted successfully.",
  },
  errorMessages = {
    save: "Failed to save item. Please try again.",
    remove: "Failed to delete item. Please try again.",
  },
  onEntityLoaded,
  transformEntityForForm,
}) {
  const { t } = useTranslation();
  const tr = (value) =>
    typeof value === "string" ? t(value, { defaultValue: value }) : value;
  const inEditMode = mode === "edit";
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const {
    data: entity,
    isLoading,
    error,
  } = useQuery({
    queryKey: queryKeys.detail(id),
    queryFn: () => fetchOne(id),
    enabled: inEditMode && Boolean(id),
  });

  useEffect(() => {
    if (!inEditMode || !entity) return;
    const entityForForm = transformEntityForForm
      ? transformEntityForForm(entity)
      : entity;
    reset?.(entityForForm);
    onEntityLoaded?.(entity);
  }, [entity, inEditMode, reset, onEntityLoaded, transformEntityForForm]);

  const saveMutation = useMutation({
    mutationFn: (payload) =>
      inEditMode ? updateOne(id, payload) : createOne(payload),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.list() });
      const message = inEditMode
        ? successMessages.update
        : successMessages.create;
      toast.success(tr(message));

      if (!inEditMode && result?.id && getCreateRedirectPath) {
        navigate(getCreateRedirectPath(result));
      }
    },
    onError: () => {
      toast.error(tr(errorMessages.save));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteOne(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.list() });
      toast.success(tr(successMessages.remove));
      if (getDeleteRedirectPath) {
        navigate(getDeleteRedirectPath());
      }
    },
    onError: () => {
      toast.error(tr(errorMessages.remove));
    },
  });

  const submit = (payload) => {
    saveMutation.mutate(payload);
  };

  const remove = () => {
    if (!confirm(tr(confirmDeleteMessage))) return;
    deleteMutation.mutate();
  };

  return useMemo(
    () => ({
      inEditMode,
      entity,
      isLoading,
      error,
      submit,
      remove,
      saveMutation,
      deleteMutation,
    }),
    [
      inEditMode,
      entity,
      isLoading,
      error,
      submit,
      remove,
      saveMutation,
      deleteMutation,
    ],
  );
}

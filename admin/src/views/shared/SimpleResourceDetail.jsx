import { useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import { useParams } from "react-router";
import { useTranslation } from "react-i18next";
import { Save, Trash } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Loader } from "@/components/ui/loader";
import { ControlBar } from "@/views/ControlBar";
import DetailFieldsSection from "@/views/shared/DetailFieldsSection";
import { useCrudDetail } from "@/hooks/useCrudDetail";
import { useBreadcrumb } from "@/contexts/BreadcrumbContext";
import { useAuth } from "@/contexts/AuthContext";

export default function SimpleResourceDetail({ moduleDef, mode }) {
  const { t } = useTranslation();
  const hasTranslation = (key) =>
    typeof key === "string" &&
    t(key, { defaultValue: "__MISSING_TRANSLATION__" }) !==
      "__MISSING_TRANSLATION__";
  const tr = (value) => {
    if (typeof value !== "string") return value;
    const moduleKey = moduleDef?.key;
    if (moduleKey && value.startsWith("moduleDefinitions.labels.")) {
      const token = value.slice("moduleDefinitions.labels.".length);
      const detailKey = `moduleDefinitions.${moduleKey}.detailFields.${token}`;
      if (hasTranslation(detailKey)) return t(detailKey);
    }
    return t(value, { defaultValue: value });
  };
  const { setLabel } = useBreadcrumb();
  const { hasPermission } = useAuth();
  const params = useParams();
  const id = params[moduleDef.detail.idParam];
  const { tenantSlug } = params;

  const form = useForm({
    defaultValues: moduleDef.detail.defaultValues,
    shouldUnregister: false,
  });

  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    getValues,
    setValue,
    formState: { isDirty },
  } = form;

  const { inEditMode, entity, isLoading, error, submit, remove } =
    useCrudDetail({
      mode,
      id,
      queryKeys: moduleDef.queryKeys,
      ...moduleDef.api,
      reset,
      transformEntityForForm: moduleDef.detail.transformEntityForForm,
      getCreateRedirectPath: (result) =>
        tenantSlug
          ? moduleDef.routes.detail(tenantSlug, result.id)
          : moduleDef.routes.detail(result.id),
      getDeleteRedirectPath: () =>
        tenantSlug
          ? moduleDef.routes.list(tenantSlug)
          : moduleDef.routes.list(),
      confirmDeleteMessage: moduleDef.messages?.confirmDelete,
      successMessages: moduleDef.messages?.success,
      errorMessages: moduleDef.messages?.error,
    });

  useEffect(() => {
    if (!id || !entity || !moduleDef.detail?.breadcrumb) return;

    const { key, labelField } = moduleDef.detail.breadcrumb;
    if (!key || !labelField || !entity?.[labelField]) return;
    setLabel(`${key}:${id}`, entity[labelField]);
  }, [id, entity, moduleDef.detail, setLabel]);

  const perms = moduleDef.detail.permissions || {};
  const canCreate = !perms.create || hasPermission(perms.create);
  const canUpdate = !perms.update || hasPermission(perms.update);
  const canDelete = !perms.delete || hasPermission(perms.delete);

  const readOnly =
    (typeof moduleDef.detail.readOnly === "function"
      ? moduleDef.detail.readOnly({ inEditMode, entity, mode })
      : Boolean(moduleDef.detail.readOnly)) ||
    (inEditMode ? !canUpdate : !canCreate);

  const fields = useMemo(
    () =>
      typeof moduleDef.detail.fields === "function"
        ? moduleDef.detail.fields({
            register,
            control,
            watch,
            getValues,
            setValue,
            formState: form.formState,
            entity,
            inEditMode,
          })
        : moduleDef.detail.fields,
    [
      moduleDef.detail,
      register,
      control,
      watch,
      getValues,
      setValue,
      form.formState,
      entity,
      inEditMode,
    ],
  );

  const beforeFields = useMemo(
    () =>
      typeof moduleDef.detail.renderBeforeFields === "function"
        ? moduleDef.detail.renderBeforeFields({
            register,
            control,
            watch,
            getValues,
            setValue,
            formState: form.formState,
            entity,
            inEditMode,
            id,
          })
        : null,
    [
      moduleDef.detail,
      register,
      control,
      watch,
      getValues,
      setValue,
      form.formState,
      entity,
      inEditMode,
      id,
    ],
  );

  const afterFields = useMemo(
    () =>
      typeof moduleDef.detail.renderAfterFields === "function"
        ? moduleDef.detail.renderAfterFields({
            register,
            control,
            watch,
            getValues,
            setValue,
            formState: form.formState,
            entity,
            inEditMode,
            id,
          })
        : null,
    [
      moduleDef.detail,
      register,
      control,
      watch,
      getValues,
      setValue,
      form.formState,
      entity,
      inEditMode,
      id,
    ],
  );

  const onSubmit = (data) => {
    const payload = moduleDef.detail.transformSubmit
      ? moduleDef.detail.transformSubmit(data, { entity, inEditMode })
      : data;
    submit(payload);
  };

  const labels = moduleDef.detail.actionLabels || {};

  const showCreate =
    typeof moduleDef.detail.showCreate === "boolean"
      ? moduleDef.detail.showCreate
      : !inEditMode;
  const showSave =
    typeof moduleDef.detail.showSave === "boolean"
      ? moduleDef.detail.showSave
      : inEditMode;
  const showDelete =
    typeof moduleDef.detail.showDelete === "boolean"
      ? moduleDef.detail.showDelete
      : inEditMode;

  if (isLoading && inEditMode) return <Loader screen />;
  if (error)
    return (
      <div>
        {t("shared.resourceDetail.errorPrefix")}: {error.message}
      </div>
    );

  return (
    <form className="flex flex-col gap-2 p-2" onSubmit={handleSubmit(onSubmit)}>
      <ControlBar>
        <ControlBar.Section align="start" priority={1}>
          {!inEditMode && showCreate && canCreate && (
            <Button
              type="submit"
              disabled={!isDirty}
              className="cursor-pointer"
            >
              <Save />{" "}
              {tr(labels.create || "shared.resourceDetail.actions.create")}
            </Button>
          )}
          {inEditMode && (
            <>
              {showSave && canUpdate && (
                <Button
                  type="submit"
                  disabled={!isDirty}
                  className="cursor-pointer"
                >
                  <Save />{" "}
                  {tr(
                    labels.save || "shared.resourceDetail.actions.saveChanges",
                  )}
                </Button>
              )}
              {showDelete && canDelete && (
                <Button
                  type="button"
                  onClick={remove}
                  className="cursor-pointer"
                >
                  <Trash />{" "}
                  {tr(labels.delete || "shared.resourceDetail.actions.delete")}
                </Button>
              )}
            </>
          )}
          {moduleDef.detail.renderControlBarActions?.({ entity, id })}
        </ControlBar.Section>
      </ControlBar>

      {beforeFields}

      <DetailFieldsSection
        title={tr(moduleDef.detail.sectionTitle)}
        description={tr(moduleDef.detail.sectionDescription)}
        columns={moduleDef.detail.columns || 2}
        fields={fields}
        register={register}
        control={control}
        setValue={setValue}
        readOnly={readOnly}
        translateValue={tr}
      />

      {afterFields}
    </form>
  );
}

import { useEffect, useState, useRef } from "react";
import { useForm, useFieldArray, Controller, get } from "react-hook-form";
import { useParams, useNavigate } from "react-router";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Input } from "@/components/ui/input";
import { InputPhone } from "@/components/ui/input-phone";
import { Textarea } from "@/components/ui/textarea";
import { DatePicker } from "@/components/ui/date-picker";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Loader } from "@/components/ui/loader";
import { Combobox } from "@/components/ui/combobox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Play,
  PlusCircle,
  Save,
  Trash,
  CloudDownload,
  Copy,
  Ban,
  Folder,
  Plus,
} from "lucide-react";
import ReactCountryFlag from "react-country-flag";
import Card from "../Card";
import { toast } from "sonner";
import {
  fetchSettingsGeneral,
  updateSettingsGeneral,
} from "@/api/settings";

export default function SettingsGeneral() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const readOnly = false;

  const { data, isLoading, error } = useQuery({
    queryKey: ["settingsGeneral"],
    queryFn: () => fetchSettingsGeneral(),
    enabled: true,
  });

  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    setValue,
    getValues,
    formState: { isDirty },
  } = useForm({
    defaultValues: {
      language: "en",
      currency: "USD",
      dateFormat: "Y_M_D",
      use24HourTime: true,
      timezone: "UTC",
      numberFormat: "COMMA_DOT",
    },
    shouldUnregister: false,
  });

  const { userPreferences, preferencesOptions } = data || {};

  // Reset form values when user data is fetched
  useEffect(() => {
    if (!data || !userPreferences) return;
    reset({
      ...userPreferences,
    });
  }, [data, userPreferences, reset]);

  // Mutation for saving other user fields
  const saveMutation = useMutation({
    mutationFn: (payload) => updateSettingsGeneral(payload),
    onSuccess: () => {
      queryClient.invalidateQueries(["settingsGeneral"]);
      toast.success("Preferences saved successfully!");
    },
    onError: () => {
      toast.error("Failed to save preferences. Please try again.");
    },
  });

  const onSubmit = (data) => {
    console.log("Form data to submit:", data);
    const payload = {
      ...data,
    };
    saveMutation.mutate(payload);
  };

  // onSubmit remains for non-avatar changes

  if (isLoading) return <Loader screen />;
  if (error) return <div>Error: {error.message}</div>;

  return (
    <form className="flex flex-col gap-2" onSubmit={handleSubmit(onSubmit)}>
      {/* Action buttons */}
      <div className="flex justify-between items-center">
        <div className="flex gap-2">
          <>
            <Button
              type="submit"
              disabled={!isDirty}
              className={"cursor-pointer"}
            >
              <Save className="" /> Save Changes
            </Button>
          </>
        </div>
        <div className="flex gap-2">
          <></>
        </div>
      </div>

      <Card>
        <div className="space-y-4 p-4">
          {/* User fields */}
          <div>
            <Controller
              name="language"
              control={control}
              render={({ field }) => {
                return (
                  <Combobox
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    options={
                      preferencesOptions?.languages.map((option) => ({
                        value: option.code,
                        label: (
                          <span className="flex items-center gap-1">
                            <ReactCountryFlag
                              countryCode={option.flagUnicode}
                            />
                            {option.label}
                          </span>
                        ),
                      })) || []
                    }
                    placeholder="Select a language"
                    label="Language"
                    readOnly={readOnly}
                  />
                );
              }}
            />
          </div>
          <div>
            <Controller
              name="currency"
              control={control}
              render={({ field }) => {
                return (
                  <Combobox
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    options={
                      preferencesOptions?.currencies.map((option) => ({
                        value: option.code,
                        label: (
                          <span className="flex items-center gap-1">
                            <ReactCountryFlag
                              countryCode={option.flagUnicode}
                            />
                            {option.label} ({option.code})
                          </span>
                        ),
                      })) || []
                    }
                    placeholder="Select a currency"
                    label="Currency"
                    readOnly={readOnly}
                  />
                );
              }}
            />
          </div>
          <div>
            <Controller
              name="displayMode"
              control={control}
              render={({ field }) => {
                return (
                  <Combobox
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    options={preferencesOptions?.displayModes || []}
                    placeholder="Select a display mode"
                    label="Display Mode"
                    readOnly={readOnly}
                  />
                );
              }}
            />
          </div>
          <div>
            <Controller
              name="numberFormat"
              control={control}
              render={({ field }) => {
                return (
                  <Combobox
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    options={preferencesOptions?.numberFormats || []}
                    placeholder="Select a number format"
                    label="Number Format"
                    readOnly={readOnly}
                  />
                );
              }}
            />
          </div>
          <div>
            <Controller
              name="dateFormat"
              control={control}
              render={({ field }) => {
                return (
                  <Combobox
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    options={preferencesOptions?.dateFormats || []}
                    placeholder="Select a date format"
                    label="Date Format"
                    readOnly={readOnly}
                  />
                );
              }}
            />
          </div>
          <div>
            <Controller
              name="timezone"
              control={control}
              render={({ field }) => {
                return (
                  <Combobox
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    options={
                      preferencesOptions?.timezones.map((option) => ({
                        value: option.value,
                        label: `${option.value}`,
                      })) || []
                    }
                    placeholder="Select a timezone"
                    label="Timezone"
                    readOnly={readOnly}
                  />
                );
              }}
            />
          </div>
        </div>
      </Card>
    </form>
  );
}

import { useEffect } from "react";
import { useForm, Controller, get } from "react-hook-form";
import { useNavigate } from "react-router";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Input } from "@/components/ui/input";
import { InputPhone } from "@/components/ui/input-phone";
import { DatePicker } from "@/components/ui/date-picker";
import { Button } from "@/components/ui/button";
import { Loader } from "@/components/ui/loader";
import { Combobox } from "@/components/ui/combobox";
import { Save } from "lucide-react";
import Card from "../Card";
import {
  presignDocumentUpload,
  putDocumentToStorage,
  completeDocumentUpload,
} from "@/api/documents";
import { toast } from "sonner";
import { fetchProfile, updateProfile } from "@/api/profile";
import { useAuth } from "@/contexts/AuthContext";
import { UserAvatarEditorDialog } from "../users/UserAvatarEditorDialog";

export default function SettingsProfile() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const readOnly = false;

  if (!user) {
    return <div>User not found</div>;
  }

  const {
    data: userData,
    isLoading,
    error,
  } = useQuery({
    queryKey: ["profile", user.id],
    queryFn: () => fetchProfile(),
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
      email: "",
      givenName: "",
      familyName: "",
    },
    shouldUnregister: false,
  });

  // Reset form values when user data is fetched
  useEffect(() => {
    if (!userData) return;
    reset({
      ...userData,
      dateOfBirth: userData.dateOfBirth ? new Date(userData.dateOfBirth) : null,
    });
  }, [userData, reset]);

  // Mutation for saving other user fields
  const saveMutation = useMutation({
    mutationFn: (payload) => updateProfile(payload),
    onSuccess: () => {
      queryClient.invalidateQueries(["profile", userData.id]);
      toast.success("Profile saved successfully!");
    },
    onError: () => {
      toast.error("Failed to save profile. Please try again.");
    },
  });

  // Direct avatar upload helper returning final URL
  const uploadAvatar = async (file) => {
    if (!userData) throw new Error("User not loaded yet");
    const presignData = await presignDocumentUpload({
      ownerType: "USER",
      ownerId: userData.id,
      tenantId: null,
      category: "AVATAR",
      filename: file.name,
      contentType: file.type,
    });
    const { documentId, uploadUrl, publicUrl } = presignData;

    await putDocumentToStorage({
      uploadUrl,
      file,
      contentType: file.type,
    });

    const completeData = await completeDocumentUpload(documentId, {
      sizeBytes: file.size,
    });
    const documents = Array.isArray(completeData?.documents)
      ? completeData.documents
      : [];
    const completedAvatar = documents.find((doc) => doc?.id === documentId);
    const finalAvatarUrl =
      completedAvatar?.publicUrl || completedAvatar?.url || publicUrl;

    // Update form state immediately so UI reflects new avatar
    setValue("avatarUrl", finalAvatarUrl, { shouldDirty: true });

    // Persist avatar to backend (send minimal payload)
    try {
      const payload = getValues();
      console.log("uploadAvatar -> updateProfile payload:", payload);
      await updateProfile(payload);
      queryClient.invalidateQueries(["profile", userData.id]);
      toast.success("Avatar updated");
    } catch (e) {
      toast.error("Failed to persist avatar");
    }
    return finalAvatarUrl; // required by uploader for preview
  };

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
          <div className="flex items-center gap-8 w-full">
            <UserAvatarEditorDialog
              userName={userData.fullName}
              avatarUrl={watch("avatarUrl") || userData?.avatarUrl}
              onUpload={uploadAvatar}
            />
            <div className="flex-1 space-y-4">
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <Input
                    label="Given Name"
                    type="string"
                    readOnly={readOnly}
                    {...register("givenName", {
                      required: true,
                    })}
                  />
                </div>
                <div>
                  <Input
                    label="Family Name"
                    type="string"
                    readOnly={readOnly}
                    {...register("familyName", {
                      required: true,
                    })}
                  />
                </div>
              </div>
              <div>
                <Input
                  label="Email"
                  type="string"
                  readOnly={readOnly}
                  {...register("email", {
                    required: true,
                  })}
                />
              </div>
            </div>
          </div>

          <div>
            <Controller
              name="dateOfBirth"
              control={control}
              rules={{ required: "Date of Birth is required" }}
              render={({ field, fieldState }) => (
                <DatePicker
                  id="dateOfBirth"
                  label="Date of Birth"
                  value={field.value}
                  onChange={field.onChange}
                  error={fieldState.error?.message}
                  readOnly={readOnly}
                />
              )}
            />
          </div>
          <div>
            <Controller
              name="gender"
              control={control}
              render={({ field }) => {
                return (
                  <Combobox
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    options={[
                      { value: "male", label: "Male" },
                      { value: "female", label: "Female" },
                      { value: "other", label: "Other" },
                    ]}
                    placeholder="Select a gender"
                    label="Gender"
                    readOnly={readOnly}
                  />
                );
              }}
            />
          </div>
          <div>
            {/* Controlled composite phone input bound to dialCode + phoneNumber */}
            <Controller
              name="phoneComposite" // virtual field to drive the component
              control={control}
              render={() => {
                const dialCode = watch("dialCode") ?? "+420";
                const phoneNumber = watch("phoneNumber") ?? "";
                return (
                  <InputPhone
                    label="Phone Number"
                    value={[dialCode, phoneNumber]}
                    onChange={([nextDial, nextPhone]) => {
                      setValue("dialCode", nextDial, { shouldDirty: true });
                      setValue("phoneNumber", nextPhone, { shouldDirty: true });
                    }}
                    disabled={readOnly}
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

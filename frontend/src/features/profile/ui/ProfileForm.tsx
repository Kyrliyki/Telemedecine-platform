import { zodResolver } from "@hookform/resolvers/zod";
import { AlertCircle, Loader2 } from "lucide-react";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { updateMyProfile } from "@/entities/user/api/userApi";
import type { UserProfile } from "@/entities/user/model/types";
import { formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { useToast } from "@/shared/ui/toast";

const profileSchema = z.object({
  firstName: z.string().trim().max(100, "Не больше 100 символов"),
  lastName: z.string().trim().max(100, "Не больше 100 символов"),
});

type ProfileValues = z.infer<typeof profileSchema>;

interface ProfileFormProps {
  user: UserProfile;
  onUpdated: (user: UserProfile) => void;
}

export function ProfileForm({ user, onUpdated }: ProfileFormProps) {
  const { showToast } = useToast();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      firstName: user.firstName ?? "",
      lastName: user.lastName ?? "",
    },
  });

  useEffect(() => {
    reset({
      firstName: user.firstName ?? "",
      lastName: user.lastName ?? "",
    });
  }, [reset, user]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      const updated = await updateMyProfile({
        firstName: values.firstName.trim() || null,
        lastName: values.lastName.trim() || null,
      });
      onUpdated(updated);
      showToast({ description: "Личные данные сохранены.", variant: "success" });
    } catch (error) {
      setError("root", { message: formatApiError(error) });
    }
  });

  return (
    <form className="space-y-4" onSubmit={onSubmit} noValidate>
      {errors.root?.message ? (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{errors.root.message}</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="profile-first-name">Имя</Label>
          <Input
            id="profile-first-name"
            autoComplete="given-name"
            placeholder="..."
            {...register("firstName")}
          />
          {errors.firstName?.message ? (
            <p className="text-sm text-destructive">{errors.firstName.message}</p>
          ) : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="profile-last-name">Фамилия</Label>
          <Input
            id="profile-last-name"
            autoComplete="family-name"
            placeholder="..."
            {...register("lastName")}
          />
          {errors.lastName?.message ? (
            <p className="text-sm text-destructive">{errors.lastName.message}</p>
          ) : null}
        </div>
      </div>

      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        Сохранить
      </Button>
    </form>
  );
}

import { zodResolver } from "@hookform/resolvers/zod";
import { AlertCircle, Loader2 } from "lucide-react";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { upsertMyDoctorProfile } from "@/entities/user/api/userApi";
import type { DoctorProfile } from "@/entities/user/model/types";
import { formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { useToast } from "@/shared/ui/toast";

const doctorProfileSchema = z.object({
  specialization: z
    .string()
    .trim()
    .min(1, "Укажите специализацию")
    .max(120, "Не больше 120 символов"),
  bio: z.string().max(4000, "Не больше 4000 символов"),
  yearsOfExperience: z.string().refine((value) => {
    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed >= 0 && parsed <= 80;
  }, "Стаж должен быть целым числом от 0 до 80"),
  licenseNumber: z.string().trim().max(64, "Не больше 64 символов"),
});

type DoctorProfileValues = z.infer<typeof doctorProfileSchema>;

interface DoctorProfileFormProps {
  profile: DoctorProfile | null;
  onSaved: (profile: DoctorProfile) => void;
}

export function DoctorProfileForm({ profile, onSaved }: DoctorProfileFormProps) {
  const { showToast } = useToast();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DoctorProfileValues>({
    resolver: zodResolver(doctorProfileSchema),
    defaultValues: toFormValues(profile),
  });

  useEffect(() => {
    reset(toFormValues(profile));
  }, [profile, reset]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      const result = await upsertMyDoctorProfile({
        specialization: values.specialization.trim(),
        bio: values.bio.trim() || null,
        yearsOfExperience: Number(values.yearsOfExperience),
        licenseNumber: values.licenseNumber.trim() || null,
        
        acceptingPatients: true,
      });
      onSaved(result);
      showToast({ description: "Профиль врача сохранён.", variant: "success" });
    } catch (error) {
      setError("root", { message: formatApiError(error) });
    }
  });

  return (
    <form className="space-y-5" onSubmit={onSubmit} noValidate>
      {errors.root?.message ? (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{errors.root.message}</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="doctor-specialization">Специализация</Label>
          <Input
            id="doctor-specialization"
            placeholder="Кардиолог"
            {...register("specialization")}
          />
          {errors.specialization?.message ? (
            <p className="text-sm text-destructive">{errors.specialization.message}</p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="doctor-years">Стаж, лет</Label>
          <Input
            id="doctor-years"
            type="number"
            min={0}
            max={80}
            step={1}
            {...register("yearsOfExperience")}
          />
          {errors.yearsOfExperience?.message ? (
            <p className="text-sm text-destructive">{errors.yearsOfExperience.message}</p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="doctor-license">Номер лицензии</Label>
          <Input
            id="doctor-license"
            placeholder="ЛП-001234"
            {...register("licenseNumber")}
          />
          {errors.licenseNumber?.message ? (
            <p className="text-sm text-destructive">{errors.licenseNumber.message}</p>
          ) : null}
        </div>

        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="doctor-bio">О себе</Label>
          <textarea
            id="doctor-bio"
            rows={6}
            className="flex min-h-[120px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            placeholder="Опыт, направления работы, подход к консультациям"
            {...register("bio")}
          />
          {errors.bio?.message ? (
            <p className="text-sm text-destructive">{errors.bio.message}</p>
          ) : null}
        </div>
      </div>

      <p className="text-xs text-muted-foreground">
        Сохранение обновляет профиль. Профиль со статусом «Отклонён» будет отправлен на
        повторную проверку.
      </p>

      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        Сохранить профиль
      </Button>
    </form>
  );
}

function toFormValues(profile: DoctorProfile | null): DoctorProfileValues {
  return {
    specialization: profile?.specialization ?? "",
    bio: profile?.bio ?? "",
    yearsOfExperience: String(profile?.yearsOfExperience ?? 0),
    licenseNumber: profile?.licenseNumber ?? "",
  };
}

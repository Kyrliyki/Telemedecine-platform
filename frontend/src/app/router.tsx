import { createBrowserRouter, Navigate } from "react-router-dom";

import { RequireAuth } from "@/app/routes/RequireAuth";
import { RequireRole } from "@/app/routes/RequireRole";
import { AcceptInvitationPage } from "@/pages/accept-invitation/AcceptInvitationPage";
import { AdminAuditPage } from "@/pages/admin-audit/AdminAuditPage";
import { AdminDoctorsPage } from "@/pages/admin-doctors/AdminDoctorsPage";
import { AdminUsersPage } from "@/pages/admin-users/AdminUsersPage";
import { DoctorDetailPage } from "@/pages/doctor-detail/DoctorDetailPage";
import { DoctorProfilePage } from "@/pages/doctor-profile/DoctorProfilePage";
import { DoctorsPage } from "@/pages/doctors/DoctorsPage";
import { CalendarPage } from "@/pages/calendar/CalendarPage";
import { HistoryPage } from "@/pages/history/HistoryPage";
import { HomePage } from "@/pages/home/HomePage";
import { LoginPage } from "@/pages/login/LoginPage";
import { MedicalRecordsPage } from "@/pages/medical-records/MedicalRecordsPage";
import { OAuthCallbackPage } from "@/pages/oauth-callback/OAuthCallbackPage";
import { OAuthConfirmPage } from "@/pages/oauth-confirm/OAuthConfirmPage";
import { ProfilePage } from "@/pages/profile/ProfilePage";
import { RegisterPage } from "@/pages/register/RegisterPage";
import { VerifyEmailPage } from "@/pages/verify-email/VerifyEmailPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: (
      <RequireAuth>
        <HomePage />
      </RequireAuth>
    ),
  },
  {
    path: "/history",
    element: (
      <RequireAuth>
        <HistoryPage />
      </RequireAuth>
    ),
  },
  {
    path: "/calendar",
    element: (
      <RequireAuth>
        <CalendarPage />
      </RequireAuth>
    ),
  },
  {
    path: "/medical-records",
    element: (
      <RequireAuth>
        <MedicalRecordsPage />
      </RequireAuth>
    ),
  },
  {
    path: "/login",
    element: <LoginPage />,
  },
  {
    path: "/register",
    element: <RegisterPage />,
  },
  {
    path: "/verify-email",
    element: <VerifyEmailPage />,
  },
  {
    path: "/oauth/callback",
    element: <OAuthCallbackPage />,
  },
  {
    path: "/oauth/confirm",
    element: <OAuthConfirmPage />,
  },
  {
    path: "/accept-invitation",
    element: <AcceptInvitationPage />,
  },
  {
    path: "/profile",
    element: (
      <RequireAuth>
        <ProfilePage />
      </RequireAuth>
    ),
  },
  {
    path: "/doctors",
    element: (
      <RequireAuth>
        <DoctorsPage />
      </RequireAuth>
    ),
  },
  {
    path: "/doctors/:userId",
    element: (
      <RequireAuth>
        <DoctorDetailPage />
      </RequireAuth>
    ),
  },
  {
    path: "/doctor/profile",
    element: (
      <RequireRole role="DOCTOR">
        <DoctorProfilePage />
      </RequireRole>
    ),
  },
  {
    path: "/admin/users",
    element: (
      <RequireRole role="ADMIN">
        <AdminUsersPage />
      </RequireRole>
    ),
  },
  {
    path: "/admin/doctors",
    element: (
      <RequireRole role="ADMIN">
        <AdminDoctorsPage />
      </RequireRole>
    ),
  },
  {
    path: "/admin/audit",
    element: (
      <RequireRole role="ADMIN">
        <AdminAuditPage />
      </RequireRole>
    ),
  },
  {
    path: "*",
    element: <Navigate to="/" replace />,
  },
]);

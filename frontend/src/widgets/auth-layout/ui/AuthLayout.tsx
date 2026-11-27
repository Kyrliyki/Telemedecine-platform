import { CalendarCheck, HeartPulse, Video } from "lucide-react";
import type { ReactNode } from "react";

interface AuthLayoutProps {
  children: ReactNode;
}

const features = [
  { icon: CalendarCheck, text: "Расписание и запись на приём" },
  { icon: Video, text: "Видеоконсультации с врачом" },
];

export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="relative min-h-dvh w-full overflow-hidden bg-[#052624] text-slate-50 lg:grid lg:h-dvh lg:grid-cols-[1.05fr_1fr]">
      <div
        aria-hidden
        className="pointer-events-none absolute -left-32 -top-32 h-[28rem] w-[28rem] rounded-full bg-primary/40 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-40 right-[-10%] h-[32rem] w-[32rem] rounded-full bg-cyan-400/15 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(13,122,107,0.35),transparent_55%)]"
      />

      <aside className="relative hidden h-full flex-col px-10 py-10 lg:flex">
        <div className="ml-auto flex w-full max-w-md items-center gap-3 text-lg font-semibold">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <HeartPulse className="h-5 w-5" />
          </span>
          Telemed
        </div>

        <div className="flex flex-1 flex-col justify-center">
          <div className="ml-auto flex w-full max-w-md flex-col gap-8">
            <div>
              <h1 className="max-w-md text-3xl font-semibold leading-tight">
                Медицина рядом, даже когда вы далеко
              </h1>
              <p className="mt-3 max-w-md text-slate-300">
                Онлайн-консультации и видеозвонки с врачом в одном защищённом пространстве.
              </p>
            </div>

            <ul className="space-y-3">
              {features.map(({ icon: Icon, text }) => (
                <li key={text} className="flex items-center gap-3 text-slate-200">
                  <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10">
                    <Icon className="h-4 w-4" />
                  </span>
                  {text}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </aside>

      <main className="relative flex min-h-dvh items-center justify-center p-6 lg:h-full lg:min-h-0 lg:justify-start lg:px-10">
        <div className="w-full max-w-md">
          <div className="mb-6 flex items-center gap-3 text-lg font-semibold lg:hidden">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <HeartPulse className="h-5 w-5" />
            </span>
            Telemed
          </div>
          {children}
        </div>
      </main>
    </div>
  );
}

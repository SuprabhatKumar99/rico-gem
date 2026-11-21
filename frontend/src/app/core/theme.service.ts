import { Injectable } from "@angular/core";

export type ThemePreference = "light" | "dark" | "system";
export type ResolvedTheme = "light" | "dark";

const THEME_STORAGE_KEY = "rk_theme_mode";

@Injectable({ providedIn: "root" })
export class ThemeService {
  private preference: ThemePreference = "light";
  private resolvedTheme: ResolvedTheme = "light";
  private mediaQuery?: MediaQueryList;

  constructor() {
    this.init();
  }

  private init(): void {
    const saved = this.readSavedPreference();
    this.preference = saved;

    if (typeof window !== "undefined" && typeof window.matchMedia === "function") {
      this.mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
      this.mediaQuery.addEventListener("change", () => {
        if (this.preference === "system") {
          this.apply();
        }
      });
    }

    this.apply();
  }

  get mode(): ThemePreference {
    return this.preference;
  }

  get resolved(): ResolvedTheme {
    return this.resolvedTheme;
  }

  get isDark(): boolean {
    return this.resolvedTheme === "dark";
  }

  toggle(): void {
    const next: ResolvedTheme = this.isDark ? "light" : "dark";
    this.setMode(next);
  }

  setMode(mode: ThemePreference): void {
    this.preference = mode;
    try {
      localStorage.setItem(THEME_STORAGE_KEY, mode);
    } catch {
      // Ignore storage failures in restricted contexts
    }
    this.apply();
  }

  private readSavedPreference(): ThemePreference {
    try {
      const raw = localStorage.getItem(THEME_STORAGE_KEY)?.trim().toLowerCase();
      if (raw === "dark" || raw === "light" || raw === "system") {
        return raw;
      }
    } catch {
      // Ignore storage errors
    }
    return "system";
  }

  private apply(): void {
    const systemDark = this.mediaQuery?.matches ?? false;
    this.resolvedTheme =
      this.preference === "dark"
        ? "dark"
        : this.preference === "light"
          ? "light"
          : systemDark
            ? "dark"
            : "light";

    if (typeof document === "undefined") return;
    const root = document.documentElement;
    const dark = this.resolvedTheme === "dark";
    root.classList.toggle("dark", dark);
    root.setAttribute("data-theme", this.resolvedTheme);

    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) {
      meta.setAttribute("content", dark ? "#0b1120" : "#f8fafc");
    }
  }
}

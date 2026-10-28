import { computed, inject, Injectable, signal } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { catchError, map, of, switchMap, tap } from "rxjs";
import { CsrfService } from "./csrf.service";
export interface SessionUser {
  id: number;
  name: string;
  email: string;
  roles: string[];
  team: string | null;
  referralCode: string | null;
}
@Injectable({ providedIn: "root" })
export class AuthService {
  private http = inject(HttpClient);
  private tokens = inject(CsrfService);
  readonly user = signal<SessionUser | null>(null);
  readonly isSuperAdmin = computed(
    () => this.user()?.roles.includes("ROLE_SUPER_ADMIN") ?? false,
  );
  readonly isDirector = computed(
    () =>
      this.isSuperAdmin() ||
      (this.user()?.roles.includes("ROLE_DIRECTEUR_COMMERCIAL") ?? false),
  );
  readonly isSupervisor = computed(
    () => this.user()?.roles.includes("ROLE_SUPERVISEUR") ?? false,
  );
  readonly isManagement = computed(
    () => this.isDirector() || this.isSupervisor(),
  );
  readonly isCommercial = computed(
    () => !!this.user() && !this.isManagement() && !this.isAdmin(),
  );
  readonly isAdmin = computed(
    () =>
      this.user()?.roles.some(
        (r) => r === "ROLE_ADMIN" || r === "ROLE_SUPER_ADMIN",
      ) ?? false,
  );
  csrf() {
    return this.tokens.refresh();
  }
  restore() {
    return this.http
      .get<SessionUser>("/api/auth/me")
      .pipe(tap((user) => this.user.set(user)));
  }
  check() {
    return this.restore().pipe(
      map(() => true),
      catchError(() => {
        this.user.set(null);
        return of(false);
      }),
    );
  }
  login(email: string, password: string) {
    return this.csrf().pipe(
      switchMap(() =>
        this.http.post(
          "/api/auth/login",
          new HttpParams().set("email", email).set("password", password),
        ),
      ),
      switchMap(() => this.csrf()),
      switchMap(() => this.restore()),
    );
  }
  logout() {
    return this.csrf().pipe(
      switchMap(() => this.http.post("/api/auth/logout", {})),
      tap(() => {
        this.user.set(null);
        this.tokens.clear();
      }),
    );
  }
}

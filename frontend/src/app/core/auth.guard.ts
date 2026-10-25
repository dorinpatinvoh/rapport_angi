import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { map } from "rxjs";
import { AuthService } from "./auth.service";
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthService)
    .check()
    .pipe(map((ok) => ok || router.createUrlTree(["/connexion"])));
};

export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService),
    router = inject(Router);
  return auth
    .check()
    .pipe(
      map(
        (ok) =>
          (ok && auth.isSuperAdmin()) || router.createUrlTree(["/connexion"]),
      ),
    );
};

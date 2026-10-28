import { inject } from "@angular/core";
import { HttpErrorResponse, HttpInterceptorFn } from "@angular/common/http";
import { Router } from "@angular/router";
import { catchError, throwError } from "rxjs";
import { AuthService } from "./auth.service";
import { isApiRequest } from "./api-url";
export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService),
    router = inject(Router);
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (
        error.status === 401 &&
        isApiRequest(request.url) &&
        !request.url.includes("/api/auth/")
      ) {
        auth.user.set(null);
        void router.navigateByUrl("/connexion");
      }
      return throwError(() => error);
    }),
  );
};

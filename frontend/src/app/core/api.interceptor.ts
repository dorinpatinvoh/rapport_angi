import { inject } from "@angular/core";
import { HttpInterceptorFn } from "@angular/common/http";
import { switchMap } from "rxjs";
import { apiUrl, isApiRequest } from "./api-url";
import { CsrfService } from "./csrf.service";

export const apiInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApiRequest(request.url)) return next(request);
  const csrf = inject(CsrfService);
  const target = request.clone({
    url: request.url.startsWith("/api/") ? apiUrl(request.url) : request.url,
    withCredentials: true,
  });
  if (["GET", "HEAD", "OPTIONS"].includes(request.method)) return next(target);
  return csrf.ensure().pipe(
    switchMap((token) =>
      next(
        target.clone({
          setHeaders: { "X-XSRF-TOKEN": token },
        }),
      ),
    ),
  );
};

import { environment } from "../../environments/environment";

export function apiUrl(path: string): string {
  return environment.apiOrigin + path;
}

export function isApiRequest(url: string): boolean {
  return (
    url.startsWith("/api/") ||
    (!!environment.apiOrigin && url.startsWith(environment.apiOrigin + "/api/"))
  );
}

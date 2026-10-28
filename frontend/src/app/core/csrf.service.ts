import { inject, Injectable } from "@angular/core";
import { HttpBackend, HttpClient } from "@angular/common/http";
import { finalize, map, Observable, of, shareReplay, tap } from "rxjs";
import { apiUrl } from "./api-url";

@Injectable({ providedIn: "root" })
export class CsrfService {
  // Bypass interceptors to avoid recursion when acquiring a token.
  private http = new HttpClient(inject(HttpBackend));
  private token: string | null = null;
  private pending?: Observable<string>;

  refresh(): Observable<string> {
    if (!this.pending) {
      this.token = null;
      this.pending = this.http
        .get<{ token: string }>(apiUrl("/api/auth/csrf"), {
          withCredentials: true,
        })
        .pipe(
          map((response) => response.token),
          tap((token) => (this.token = token)),
          finalize(() => (this.pending = undefined)),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
    }
    return this.pending;
  }

  ensure(): Observable<string> {
    return this.token ? of(this.token) : this.refresh();
  }

  clear(): void {
    this.token = null;
  }
}

import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, map, of, switchMap, tap } from 'rxjs';
export interface SessionUser { id: number; name: string; email: string; roles: string[]; }
@Injectable({providedIn:'root'})
export class AuthService {
  private http = inject(HttpClient);
  readonly user = signal<SessionUser | null>(null);
  csrf() { return this.http.get('/api/auth/csrf'); }
  restore() {
    return this.http.get<SessionUser>('/api/auth/me').pipe(tap(user => this.user.set(user)));
  }
  check() { return this.restore().pipe(map(() => true), catchError(() => { this.user.set(null); return of(false); })); }
  login(email: string, password: string) {
    return this.csrf().pipe(switchMap(() => this.http.post('/api/auth/login',
      new HttpParams().set('email',email).set('password',password))),
      switchMap(() => this.csrf()), switchMap(() => this.restore()));
  }
  logout() {
    return this.csrf().pipe(switchMap(() => this.http.post('/api/auth/logout',{})),
      tap(() => this.user.set(null)));
  }
}

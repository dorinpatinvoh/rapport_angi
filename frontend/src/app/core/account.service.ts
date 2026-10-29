import { inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
export interface Commercial {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  team: string;
  referralCode: string | null;
  enabled: boolean;
  country: string;
}
export interface AccountInput {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  team: string;
  referralCode?: string;
  country?: string;
}
@Injectable({ providedIn: "root" })
export class AccountService {
  private http = inject(HttpClient);
  list() {
    return this.http.get<Commercial[]>("/api/commercials");
  }
  create(data: AccountInput) {
    return this.http.post<Commercial>("/api/commercials", data);
  }
  update(id: number, team: string, referralCode: string, enabled: boolean) {
    return this.http.put<Commercial>(`/api/commercials/${id}`, {
      team,
      referralCode,
      enabled,
    });
  }
}

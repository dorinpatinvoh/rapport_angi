import { inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
export interface Member {
  id: number;
  name: string;
  email: string;
  role: string;
  team: string | null;
  supervisorId: number | null;
  zone: string | null;
  enabled: boolean;
}
export interface Goal {
  code: string;
  label: string;
  target: number;
}
export interface Progress extends Goal {
  achieved: number;
  remaining: number;
  percent: number;
}
export interface Monthly {
  commercialId: number;
  name: string;
  team: string;
  zone: string | null;
  goals: Progress[];
  pendingReports: number;
  unquantifiedReports: number;
}
export interface Summary {
  id: number;
  supervisorId: number;
  supervisorName: string;
  reportDate: string;
  team: string;
  zone: string;
  content: string;
  createdAt: string;
  reportIds: number[];
  commercialCount: number;
  totals: Record<string, number>;
}
export const goalLabels: Record<string, string> = {
  AGENCES_FLOTTE: "Agences de flotte",
  SPEEDERS_INDEPENDANTS: "Speeders indépendants",
  CLIENTS: "Clients",
  PROFESSIONNELS: "Professionnels",
  UTILISATEURS: "Utilisateurs",
};
export function localDay() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Africa/Lagos",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}
@Injectable({ providedIn: "root" })
export class SalesService {
  private http = inject(HttpClient);
  members() {
    return this.http.get<Member[]>("/api/sales/members");
  }
  assign(
    id: number,
    data: {
      role: string;
      team: string;
      supervisorId: number | null;
      zone: string;
    },
  ) {
    return this.http.put<Member>(`/api/sales/members/${id}`, data);
  }
  goals(team: string) {
    return this.http.get<Goal[]>("/api/sales/goals", { params: { team } });
  }
  objectives(month: string) {
    return this.http.get<Monthly[]>("/api/sales/objectives", {
      params: { month },
    });
  }
  summaries(date: string) {
    return this.http.get<Summary[]>("/api/sales/summaries", {
      params: { date },
    });
  }
  send(date: string, content: string) {
    return this.http.post<Summary>("/api/sales/summaries", { date, content });
  }
}

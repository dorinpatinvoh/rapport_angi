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
  country: string;
  projects: string[];
  zoneLevel: string | null;
  configured: boolean;
  zoneConfigId: number | null;
  revision: number;
}
export interface ZoneGrid {
  id: number;
  country: string;
  project: string;
  level: string;
  name: string;
  parentId: number | null;
  plannedStaff: number;
  assignedStaff: number;
  inheritGoals: boolean;
  goals: Goal[];
  effectiveGoals: Goal[];
  version: number;
}
export interface ZoneInput {
  country: string;
  project: string;
  level: string;
  name: string;
  parentId: number | null;
  plannedStaff: number;
  inheritGoals: boolean;
  goals: Goal[];
  version: number | null;
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
  zoneId: number | null;
  gridSource: string;
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
  goalLabels: Record<string, string>;
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
  countries() {
    return this.http.get<{ code: string; name: string }[]>(
      "/api/sales/countries",
    );
  }
  addCountry(code: string, name: string) {
    return this.http.post<{ code: string; name: string }>(
      "/api/sales/countries",
      { code, name },
    );
  }
  zones() {
    return this.http.get<ZoneGrid[]>("/api/sales/zones");
  }
  saveZone(id: number | null, data: ZoneInput) {
    return id === null
      ? this.http.post<ZoneGrid>("/api/sales/zones", data)
      : this.http.put<ZoneGrid>(`/api/sales/zones/${id}`, data);
  }
  status(member: Member, enabled: boolean) {
    return this.http.put<Member>(`/api/sales/members/${member.id}/status`, {
      enabled,
      revision: member.revision,
    });
  }
  replaceDirector(current: Member, candidateId: number) {
    return this.http.post<Member>("/api/sales/directors/replace", {
      country: current.country,
      expectedDirectorId: current.id,
      candidateId,
    });
  }
  members() {
    return this.http.get<Member[]>("/api/sales/members");
  }
  createMember(data: {
    account: {
      firstName: string;
      lastName: string;
      email: string;
      password: string;
      team: string;
      country: string;
    };
    role: string;
    zone: string;
    projects: string[];
    zoneLevel: string;
    zoneConfigId?: number | null;
  }) {
    return this.http.post<Member>("/api/sales/members", data);
  }
  assign(
    id: number,
    data: {
      role: string;
      team: string;
      supervisorId: number | null;
      zone: string;
      country: string;
      projects: string[];
      zoneLevel: string;
      zoneConfigId?: number | null;
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

import { inject, Injectable } from "@angular/core";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
export interface Review {
  action: string;
  comment: string;
  reviewerName: string;
  createdAt: string;
}
export interface AzoProfile {
  code: string;
  label: string;
  group: string;
}
export interface AzoRegistration {
  firstName: string;
  lastName: string;
  profileCode: string;
}
export interface Report {
  azoRegistrations: AzoRegistration[];
  goalCounts: Partial<Record<string, number>>;
  goalLabels: Record<string, string>;
  supervisorId: number | null;
  zone: string | null;
  profileCounts: Record<string, number>;
  id: number;
  title: string;
  content: string;
  status: string;
  authorId: number;
  authorName: string;
  createdAt: string;
  updatedAt: string | null;
  team: string | null;
  reportDate: string;
  mode: string;
  prospectsContacted: number;
  registrations: number;
  statsSource: string;
  statsFetchedAt: string | null;
  referralCode: string | null;
  version: number;
  reviews: Review[];
}
export interface ReportInput {
  azoRegistrations: AzoRegistration[];
  goalCounts: Record<string, number>;
  profileCounts: Record<string, number>;
  title: string;
  content: string;
  status: string;
  team: string;
  reportDate: string;
  mode: string;
  prospectsContacted: number;
  registrations: number;
  syncStats: boolean;
  version: number | null;
}
export function statusLabel(status: string) {
  return (
    (
      {
        DRAFT: "Brouillon",
        SUBMITTED: "Soumis",
        RETURNED: "À confirmer",
        CONFIRMED: "Confirmé",
      } as Record<string, string>
    )[status] || status
  );
}
export function apiError(error: HttpErrorResponse): string {
  if (error.status === 0)
    return "Serveur inaccessible. Vérifiez votre connexion et réessayez.";
  if (error.status === 401) return "Votre session a expiré. Reconnectez-vous.";
  if (error.status === 403)
    return "Action non autorisée. Vérifiez vos droits ou rechargez la page.";
  return error.error?.message || "Une erreur est survenue. Réessayez.";
}
@Injectable({ providedIn: "root" })
export class ReportService {
  private http = inject(HttpClient);
  list() {
    return this.http.get<Report[]>("/api/reports");
  }
  create(data: ReportInput) {
    return this.http.post<Report>("/api/reports", data);
  }
  update(id: number, data: ReportInput) {
    return this.http.put<Report>(`/api/reports/${id}`, data);
  }
  delete(id: number) {
    return this.http.delete<void>(`/api/reports/${id}`);
  }
  review(r: Report, action: string, comment: string, location: string) {
    return this.http.post<Report>(`/api/reports/${r.id}/review`, {
      action,
      comment,
      location,
      version: r.version,
    });
  }
  pdf(id: number) {
    return this.http.get(`/api/reports/${id}/pdf`, { responseType: "blob" });
  }
}

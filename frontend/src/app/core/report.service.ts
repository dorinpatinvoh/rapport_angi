import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
export interface Report {id:number;title:string;content:string;status:'DRAFT'|'SUBMITTED';authorId:number;authorName:string;createdAt:string;updatedAt:string|null;}
export interface ReportInput {title:string;content:string;status:string;}
export function apiError(error: HttpErrorResponse): string {
  if (error.status === 0) return 'Serveur inaccessible. Vérifiez votre connexion et réessayez.';
  if (error.status === 401) return 'Votre session a expiré. Reconnectez-vous.';
  if (error.status === 403) return 'Action refusée. Rechargez la page puis réessayez.';
  return error.error?.message || 'Une erreur est survenue. Réessayez.';
}
@Injectable({providedIn:'root'})
export class ReportService {
  private http = inject(HttpClient);
  list() { return this.http.get<Report[]>('/api/reports'); }
  create(data: ReportInput) { return this.http.post<Report>('/api/reports',data); }
  update(id: number, data: ReportInput) { return this.http.put<Report>(`/api/reports/${id}`,data); }
  delete(id: number) { return this.http.delete<void>(`/api/reports/${id}`); }
}

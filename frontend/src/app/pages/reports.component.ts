import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
interface Report {id:number;title:string;status:string;authorName?:string;createdAt?:string;}
@Component({selector:'app-reports',standalone:true,imports:[DatePipe,ReactiveFormsModule],template:`
<div class="d-flex flex-wrap justify-content-between gap-3 mb-4"><div><h1 class="h2 fw-bold">Rapports</h1><p class="text-secondary">Consulter et préparer les comptes rendus.</p></div><button class="btn btn-primary" (click)="formVisible.set(!formVisible())"><i class="bi bi-plus-lg me-2"></i>Rédiger</button></div>
@if(formVisible()){<section class="panel mb-4"><h2 class="h5 mb-3">Nouveau rapport</h2><form [formGroup]="form">
<div class="mb-3"><label for="title" class="form-label">Titre</label><input id="title" class="form-control" formControlName="title"></div>
<div class="mb-3"><label for="body" class="form-label">Description</label><textarea id="body" rows="6" class="form-control" formControlName="content"></textarea></div>
<div class="mb-3"><label for="status" class="form-label">Statut</label><select id="status" class="form-select" formControlName="status"><option value="DRAFT">Brouillon</option><option value="SUBMITTED">Soumis</option></select></div>
<div class="alert alert-warning">Enregistrement temporairement désactivé : l'API doit identifier l'auteur via une session sécurisée.</div><button type="button" class="btn btn-primary" disabled>Enregistrer</button>
</form></section>}
<section class="panel"><div class="d-flex justify-content-between align-items-center mb-3"><h2 class="h5 mb-0">Historique des rapports</h2><button class="btn btn-outline-primary btn-sm" (click)="load()">Actualiser</button></div>
@if(error()){<div class="alert alert-warning" role="alert">{{error()}}</div>}
@if(loading()){<p class="text-secondary">Chargement…</p>}
@else if(reports().length===0){<div class="empty"><i class="bi bi-file-earmark-text fs-1"></i><p class="mt-2">Aucun rapport disponible.</p></div>}
@else{<div class="table-responsive"><table class="table"><thead><tr><th>Titre</th><th>Auteur</th><th>Statut</th><th>Date</th></tr></thead><tbody>@for(r of reports();track r.id){<tr><td>{{r.title}}</td><td>{{r.authorName || '—'}}</td><td>{{r.status}}</td><td>{{r.createdAt ? (r.createdAt | date:'dd/MM/yyyy') : '—'}}</td></tr>}</tbody></table></div>}
</section>`})
export class ReportsComponent {
private http=inject(HttpClient);private fb=inject(FormBuilder);
readonly reports=signal<Report[]>([]);readonly error=signal('');readonly loading=signal(false);readonly formVisible=signal(false);
readonly form=this.fb.nonNullable.group({title:['',[Validators.required,Validators.maxLength(200)]],content:['',Validators.required],status:['DRAFT',Validators.required]});
constructor(){this.load();}
load(){this.loading.set(true);this.error.set('');this.http.get<Report[]>('/api/reports').subscribe({next:r=>{this.reports.set(r);this.loading.set(false);},error:()=>{this.error.set('API indisponible ou route non configurée. Vérifiez Spring Boot et le proxy.');this.loading.set(false);}});}
}

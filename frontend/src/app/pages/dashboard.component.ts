import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { apiError, Report, ReportService } from '../core/report.service';
@Component({selector:'app-dashboard',standalone:true,imports:[RouterLink,DatePipe],template:`
<div class="d-flex flex-wrap justify-content-between gap-3 mb-4"><div><h1 class="h2 fw-bold">Tableau de bord</h1><p class="text-secondary">Synthèse des rapports accessibles à votre compte.</p></div><a routerLink="/rapports" class="btn btn-primary align-self-start">Gérer les rapports</a></div>
@if(error()){<div class="alert alert-danger" role="alert">{{error()}} <button class="btn btn-sm btn-outline-danger" (click)="load()">Réessayer</button></div>}
@if(loading()){<p role="status">Chargement…</p>}
@else{<div class="row g-3 mb-4">@for(item of metrics();track item.label){<div class="col-sm-4"><div class="panel"><div class="metric">{{item.value}}</div><span class="text-secondary">{{item.label}}</span></div></div>}</div>
<section class="panel"><h2 class="h5">Rapports récents</h2>@for(r of reports().slice(0,5);track r.id){<div class="border-bottom py-3"><a routerLink="/rapports">{{r.title}}</a><div class="small text-secondary">{{r.authorName}} · {{r.createdAt | date:'dd/MM/yyyy HH:mm'}} · {{r.status==='DRAFT' ? 'Brouillon' : 'Soumis'}}</div></div>}@empty{<div class="empty">Aucun rapport pour le moment.</div>}</section>}
`})
export class DashboardComponent {
  private api=inject(ReportService);readonly reports=signal<Report[]>([]);readonly error=signal('');readonly loading=signal(false);
  readonly metrics=computed(()=>[{label:'Rapports',value:this.reports().length},{label:'Brouillons',value:this.reports().filter(r=>r.status==='DRAFT').length},{label:'Rapports soumis',value:this.reports().filter(r=>r.status==='SUBMITTED').length}]);
  constructor(){this.load();}
  load(){this.loading.set(true);this.error.set('');this.api.list().pipe(finalize(()=>this.loading.set(false))).subscribe({next:r=>this.reports.set(r),error:e=>this.error.set(apiError(e))});}
}

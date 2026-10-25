import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, switchMap } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { apiError, Report, ReportService } from '../core/report.service';
@Component({selector:'app-reports',standalone:true,imports:[DatePipe,ReactiveFormsModule],template:`
<div class="d-flex flex-wrap justify-content-between gap-3 mb-4"><div><h1 class="h2 fw-bold">Rapports</h1><p class="text-secondary">Rédigez et suivez vos comptes rendus.</p></div><button class="btn btn-primary" [disabled]="busy()" (click)="edit()">Rédiger</button></div>
@if(error()){<div class="alert alert-danger" role="alert">{{error()}}</div>}
@if(success()){<div class="alert alert-success" role="status">{{success()}}</div>}
@if(formVisible()){<section class="panel mb-4"><h2 class="h5">{{editingId() ? 'Modifier le rapport' : 'Nouveau rapport'}}</h2>
<form [formGroup]="form" (ngSubmit)="save()">
<div class="mb-3"><label for="title" class="form-label">Titre</label><input id="title" class="form-control" formControlName="title" maxlength="200" required></div>
<div class="mb-3"><label for="body" class="form-label">Contenu</label><textarea id="body" rows="6" class="form-control" formControlName="content" maxlength="50000" required></textarea></div>
<div class="mb-3"><label for="status" class="form-label">Statut</label><select id="status" class="form-select" formControlName="status"><option value="DRAFT">Brouillon</option><option value="SUBMITTED">Soumis</option></select></div>
<p class="small text-secondary">Le titre et le contenu sont obligatoires. Un rapport soumis reste modifiable.</p>
<div class="d-flex gap-2"><button type="submit" class="btn btn-primary" [disabled]="form.invalid || busy()">{{busy() ? 'Enregistrement…' : 'Enregistrer'}}</button><button type="button" class="btn btn-outline-secondary" [disabled]="busy()" (click)="formVisible.set(false)">Annuler</button></div>
</form></section>}
@if(detail(); as r){<section class="panel mb-4"><div class="d-flex justify-content-between gap-3"><h2 class="h5">{{r.title}}</h2><button class="btn btn-sm btn-outline-secondary" (click)="detail.set(null)">Fermer</button></div><p class="text-secondary">{{r.authorName}} · {{r.createdAt | date:'dd/MM/yyyy HH:mm'}}</p><p class="report-body">{{r.content}}</p></section>}
<section class="panel"><div class="d-flex flex-wrap justify-content-between gap-3 mb-3"><h2 class="h5 mb-0">Historique des rapports</h2><button class="btn btn-outline-primary btn-sm" [disabled]="loading() || busy()" (click)="load()">Actualiser</button></div>
<div class="row g-2 mb-3"><div class="col-sm-8"><label for="search" class="form-label">Rechercher</label><input #search id="search" type="search" class="form-control" placeholder="Titre, contenu, auteur" (input)="query.set(search.value)"></div><div class="col-sm-4"><label for="filter" class="form-label">Statut</label><select #filter id="filter" class="form-select" (change)="statusFilter.set(filter.value)"><option value="">Tous</option><option value="DRAFT">Brouillon</option><option value="SUBMITTED">Soumis</option></select></div></div>
@if(loading()){<p role="status">Chargement…</p>}
@else if(filtered().length===0){<div class="empty">Aucun rapport à afficher.</div>}
@else{<div class="table-responsive"><table class="table align-middle"><thead><tr><th>Titre</th><th>Auteur</th><th>Statut</th><th>Date</th><th>Actions</th></tr></thead><tbody>
@for(r of filtered();track r.id){<tr><td><button class="btn btn-link text-start p-0" (click)="detail.set(r)">{{r.title}}</button></td><td>{{r.authorName}}</td><td><span class="badge" [class.text-bg-secondary]="r.status==='DRAFT'" [class.text-bg-primary]="r.status==='SUBMITTED'">{{r.status==='DRAFT' ? 'Brouillon' : 'Soumis'}}</span></td><td>{{r.createdAt | date:'dd/MM/yyyy'}}</td><td><div class="d-flex gap-2"><button class="btn btn-sm btn-outline-primary" [disabled]="busy()" (click)="edit(r)">Modifier</button><button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="pendingDelete.set(r)">Supprimer</button></div></td></tr>}
</tbody></table></div>}
@if(pendingDelete(); as r){<div class="alert alert-warning mt-3" role="alert"><p>Supprimer définitivement « {{r.title}} » ?</p><button class="btn btn-danger me-2" [disabled]="busy()" (click)="remove(r)">Confirmer la suppression</button><button class="btn btn-outline-secondary" [disabled]="busy()" (click)="pendingDelete.set(null)">Annuler</button></div>}
</section>`})
export class ReportsComponent {
  private api=inject(ReportService); private auth=inject(AuthService); private fb=inject(FormBuilder);
  readonly reports=signal<Report[]>([]); readonly error=signal(''); readonly success=signal('');
  readonly loading=signal(false); readonly busy=signal(false); readonly formVisible=signal(false);
  readonly editingId=signal<number|null>(null); readonly detail=signal<Report|null>(null); readonly pendingDelete=signal<Report|null>(null);
  readonly query=signal(''); readonly statusFilter=signal('');
  readonly filtered=computed(()=>this.reports().filter(r=>(!this.statusFilter() || r.status===this.statusFilter()) &&
    `${r.title} ${r.content} ${r.authorName}`.toLocaleLowerCase().includes(this.query().toLocaleLowerCase())));
  readonly form=this.fb.nonNullable.group({title:['',[Validators.required,Validators.pattern(/\S/),Validators.maxLength(200)]],content:['',[Validators.required,Validators.pattern(/\S/),Validators.maxLength(50000)]],status:['DRAFT',Validators.required]});
  constructor(){this.load();}
  load(){
    this.loading.set(true);this.error.set('');
    this.api.list().pipe(finalize(()=>this.loading.set(false))).subscribe({next:r=>this.reports.set(r),error:e=>this.error.set(apiError(e))});
  }
  edit(report?:Report){
    this.editingId.set(report?.id ?? null); this.form.reset({title:report?.title ?? '',content:report?.content ?? '',status:report?.status ?? 'DRAFT'});
    this.formVisible.set(true); this.error.set(''); this.success.set('');
  }
  save(){
    if(this.form.invalid || this.busy()) return;
    this.busy.set(true);this.error.set('');this.success.set('');
    const id=this.editingId(), data=this.form.getRawValue();
    this.auth.csrf().pipe(switchMap(()=>id===null ? this.api.create(data) : this.api.update(id,data)),finalize(()=>this.busy.set(false))).subscribe({
      next:r=>{this.formVisible.set(false);this.detail.set(r);this.success.set('Rapport enregistré.');this.load();},error:e=>this.error.set(apiError(e))
    });
  }
  remove(report:Report){
    if(this.busy()) return;
    this.busy.set(true);this.error.set('');this.success.set('');
    this.auth.csrf().pipe(switchMap(()=>this.api.delete(report.id)),finalize(()=>this.busy.set(false))).subscribe({
      next:()=>{this.pendingDelete.set(null);if(this.detail()?.id===report.id)this.detail.set(null);if(this.editingId()===report.id)this.formVisible.set(false);this.success.set('Rapport supprimé.');this.load();},error:e=>this.error.set(apiError(e))
    });
  }
}

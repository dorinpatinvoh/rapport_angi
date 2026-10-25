import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({selector:'app-dashboard',standalone:true,imports:[RouterLink],template:`
<div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4"><div><h1 class="h2 fw-bold">Tableau de bord</h1><p class="text-secondary">Bienvenue dans votre espace professionnel.</p></div><a routerLink="/rapports" class="btn btn-primary"><i class="bi bi-plus-lg me-2"></i>Nouveau rapport</a></div>
<div class="alert alert-info" role="status">Statistiques en attente de connexion au backend.</div>
<div class="row g-3 mb-4">@for (item of items; track item) {<div class="col-sm-6 col-xl-3"><div class="panel"><i class="bi bi-bar-chart text-primary"></i><div class="metric">—</div><span class="text-secondary">{{item}}</span></div></div>}</div>
<section class="panel"><h2 class="h5">Activité récente</h2><div class="empty"><i class="bi bi-inbox fs-1"></i><p class="mt-2 mb-0">Aucune activité chargée pour le moment.</p></div></section>
`})
export class DashboardComponent {readonly items=['Rapports créés','Brouillons','Rapports soumis','Collaborateurs'];}

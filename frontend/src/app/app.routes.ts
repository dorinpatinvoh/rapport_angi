import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard.component';
import { ReportsComponent } from './pages/reports.component';
import { LoginComponent } from './pages/login.component';
export const routes: Routes = [{path:'',redirectTo:'dashboard',pathMatch:'full'},{path:'dashboard',component:DashboardComponent},{path:'rapports',component:ReportsComponent},{path:'connexion',component:LoginComponent},{path:'**',redirectTo:'dashboard'}];

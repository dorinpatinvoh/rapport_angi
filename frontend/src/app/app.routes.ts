import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard.component';
import { ReportsComponent } from './pages/reports.component';
import { LoginComponent } from './pages/login.component';
import { authGuard } from './core/auth.guard';
export const routes: Routes = [
  {path:'',redirectTo:'dashboard',pathMatch:'full'},
  {path:'dashboard',component:DashboardComponent,canActivate:[authGuard]},
  {path:'rapports',component:ReportsComponent,canActivate:[authGuard]},
  {path:'connexion',component:LoginComponent},
  {path:'**',redirectTo:'dashboard'}
];

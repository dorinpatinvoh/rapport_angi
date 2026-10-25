import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
@Component({selector:'app-login',standalone:true,imports:[ReactiveFormsModule],template:`
<div class="panel mx-auto" style="max-width:460px"><h1 class="h3 fw-bold mb-3">Connexion</h1><p class="text-secondary">Accédez à votre espace ANGI DSN PRO.</p>
<form [formGroup]="form"><div class="mb-3"><label for="email" class="form-label">E-mail</label><input id="email" class="form-control" type="email" autocomplete="username" formControlName="email"></div>
<div class="mb-3"><label for="password" class="form-label">Mot de passe</label><input id="password" class="form-control" type="password" autocomplete="current-password" formControlName="password"></div>
<div class="alert alert-warning">Connexion désactivée jusqu'à l'intégration d'une API d'authentification sécurisée.</div><button class="btn btn-primary w-100" disabled type="button">Se connecter</button></form></div>`})
export class LoginComponent {private fb=inject(FormBuilder);form=this.fb.nonNullable.group({email:['',[Validators.required,Validators.email]],password:['',Validators.required]});}

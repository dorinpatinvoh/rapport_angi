import { Component, inject, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { finalize, switchMap } from "rxjs";
import { AuthService } from "../core/auth.service";
import { apiError } from "../core/report.service";
@Component({
  selector: "app-password-reset",
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: ` <section class="panel auth-panel">
    <p class="eyebrow">Accès au compte</p>
    <h1>
      {{
        resetMode ? "Choisir un nouveau mot de passe" : "Mot de passe oublié"
      }}
    </h1>
    @if (success()) {
      <div class="alert alert-success" role="status">{{ success() }}</div>
      <a routerLink="/connexion" class="btn btn-primary"
        >Retour à la connexion</a
      >
    } @else {
      <form [formGroup]="form" (ngSubmit)="submit()">
        @if (resetMode) {
          <label for="newPassword" class="form-label"
            >Nouveau mot de passe</label
          ><input
            id="newPassword"
            type="password"
            autocomplete="new-password"
            class="form-control"
            formControlName="password"
            minlength="12"
            maxlength="72"
            required
          />
          <p class="form-text">
            12 à 72 caractères. Le lien expire après 30 minutes et ne fonctionne
            qu'une fois.
          </p>
          <label for="confirmPassword" class="form-label"
            >Confirmer le mot de passe</label
          ><input
            id="confirmPassword"
            type="password"
            autocomplete="new-password"
            class="form-control"
            formControlName="confirmation"
            required
          />
        } @else {
          <p>
            Renseignez l'email de votre compte pour recevoir un lien temporaire.
          </p>
          <label for="recoveryEmail" class="form-label">Email du compte</label
          ><input
            id="recoveryEmail"
            type="email"
            autocomplete="username"
            class="form-control"
            formControlName="email"
            required
          />
        }
        @if (error()) {
          <p class="alert alert-danger mt-3" role="alert">{{ error() }}</p>
        }
        <button
          class="btn btn-primary w-100 mt-4"
          [disabled]="busy() || form.invalid"
          type="submit"
        >
          {{
            busy()
              ? "Traitement…"
              : resetMode
                ? "Réinitialiser mon mot de passe"
                : "Recevoir le lien"
          }}</button
        ><a routerLink="/connexion" class="d-block mt-3"
          >Retour à la connexion</a
        >
        @if (resetMode) {
          <a routerLink="/mot-de-passe-oublie" class="d-block mt-2"
            >Demander un nouveau lien</a
          >
        }
      </form>
    }
  </section>`,
})
export class PasswordResetComponent {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly resetMode = this.router.url.startsWith(
    "/reinitialiser-mot-de-passe",
  );
  private token =
    new URLSearchParams(location.hash.slice(1)).get("token") || "";
  readonly busy = signal(false);
  readonly success = signal("");
  readonly error = signal("");
  readonly form = this.fb.nonNullable.group({
    email: [""],
    password: [""],
    confirmation: [""],
  });
  constructor() {
    if (this.resetMode) {
      this.form.controls.password.setValidators([
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(72),
      ]);
      this.form.controls.confirmation.setValidators([Validators.required]);
      if (!this.token)
        this.error.set("Lien incomplet. Demandez un nouveau lien.");
      history.replaceState(null, "", location.pathname);
    } else
      this.form.controls.email.setValidators([
        Validators.required,
        Validators.email,
        Validators.maxLength(150),
      ]);
    Object.values(this.form.controls).forEach((control) =>
      control.updateValueAndValidity(),
    );
    this.form.updateValueAndValidity();
  }
  submit() {
    if (this.busy() || this.form.invalid) return;
    const data = this.form.getRawValue();
    if (this.resetMode && data.password !== data.confirmation) {
      this.error.set("Les deux mots de passe doivent être identiques.");
      return;
    }
    if (this.resetMode && !this.token) {
      this.error.set("Lien incomplet. Demandez un nouveau lien.");
      return;
    }
    this.busy.set(true);
    this.error.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() =>
          this.http.post<{ message: string }>(
            this.resetMode
              ? "/api/auth/reset-password"
              : "/api/auth/forgot-password",
            this.resetMode
              ? { token: this.token, password: data.password }
              : { email: data.email },
          ),
        ),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (r) => {
          this.success.set(r.message);
          this.form.reset();
          if (this.resetMode) this.auth.user.set(null);
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
}

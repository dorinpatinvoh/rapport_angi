import { Component, inject, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { finalize, switchMap } from "rxjs";
import { AccountService } from "../core/account.service";
import { AuthService } from "../core/auth.service";
import { apiError } from "../core/report.service";
@Component({
  selector: "app-register",
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: "./register.component.html",
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private accounts = inject(AccountService);
  private auth = inject(AuthService);
  readonly busy = signal(false);
  readonly error = signal("");
  readonly success = signal("");
  readonly form = this.fb.nonNullable.group({
    firstName: [
      "",
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(100),
      ],
    ],
    lastName: [
      "",
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(100),
      ],
    ],
    email: [
      "",
      [Validators.required, Validators.email, Validators.maxLength(150)],
    ],
    password: [
      "",
      [Validators.required, Validators.minLength(12), Validators.maxLength(72)],
    ],
    team: ["AZO", Validators.required],
  });
  submit() {
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() => this.accounts.register(this.form.getRawValue())),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (r) => {
          this.success.set(r.message);
          this.form.reset();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
}

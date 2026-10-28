import { apiUrl } from "../core/api-url";
import { Component, computed, inject, signal } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { Goal, SalesService, goalLabels } from "../core/sales.service";
import { DatePipe } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from "@angular/forms";
import { finalize, switchMap } from "rxjs";
import { AuthService } from "../core/auth.service";
import {
  AzoProfile,
  AzoRegistration,
  apiError,
  Report,
  ReportService,
  statusLabel,
} from "../core/report.service";
@Component({
  selector: "app-reports",
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: "./reports.component.html",
})
export class ReportsComponent {
  readonly apiUrl = apiUrl;
  private api = inject(ReportService);
  private sales = inject(SalesService);
  private route = inject(ActivatedRoute);
  readonly goalLabels = goalLabels;
  readonly goals = signal<Record<string, Goal[]>>({});
  readonly goalForm = new FormGroup<Record<string, FormControl<number>>>({});
  readonly dateFilter = signal("");
  readonly correctionLocation = signal("");
  goalDefs(team?: string | null) {
    return (
      this.goals()[
        team ??
          this.editing()?.team ??
          this.auth.user()?.team ??
          this.form.controls.team.value
      ] || []
    );
  }
  readonly auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  readonly reports = signal<Report[]>([]);
  readonly error = signal("");
  readonly success = signal("");
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly formVisible = signal(false);
  readonly editing = signal<Report | null>(null);
  readonly detail = signal<Report | null>(null);
  readonly pendingDelete = signal<Report | null>(null);
  readonly query = signal("");
  readonly statusFilter = signal("");
  readonly teamFilter = signal("");
  readonly integrationReady = signal(false);
  readonly label = statusLabel;
  readonly today = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Africa/Lagos",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  readonly filtered = computed(() =>
    this.reports().filter(
      (r) =>
        (!this.dateFilter() || r.reportDate === this.dateFilter()) &&
        (!this.statusFilter() || r.status === this.statusFilter()) &&
        (!this.teamFilter() || r.team === this.teamFilter()) &&
        `${r.title} ${r.content} ${r.authorName}`
          .toLocaleLowerCase()
          .includes(this.query().toLocaleLowerCase()),
    ),
  );
  readonly form = this.fb.nonNullable.group({
    title: [
      "",
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(200),
      ],
    ],
    content: ["", Validators.maxLength(50000)],
    status: ["DRAFT", Validators.required],
    team: ["AZO", Validators.required],
    reportDate: [this.today, Validators.required],
    mode: ["WRITTEN", Validators.required],
    prospectsContacted: [
      0,
      [
        Validators.required,
        Validators.min(0),
        Validators.max(1000000),
        Validators.pattern(/^\d+$/),
      ],
    ],
    registrations: [
      0,
      [
        Validators.required,
        Validators.min(0),
        Validators.max(1000000),
        Validators.pattern(/^\d+$/),
      ],
    ],
    detailProfiles: [true],
    syncStats: [false],
  });
  readonly profileDefs = signal<AzoProfile[]>([]);
  readonly profileGroups = computed(() => [
    ...new Set(this.profileDefs().map((p) => p.group)),
  ]);
  readonly registrationForm = new FormArray<
    FormGroup<{
      firstName: FormControl<string>;
      lastName: FormControl<string>;
      profileCode: FormControl<string>;
    }>
  >([]);
  addRegistration(entry?: AzoRegistration) {
    if (this.registrationForm.length >= 500 || this.busy()) return;
    this.registrationForm.push(
      this.fb.nonNullable.group({
        firstName: [
          entry?.firstName ?? "",
          [
            Validators.required,
            Validators.pattern(/\S/),
            Validators.maxLength(100),
          ],
        ],
        lastName: [
          entry?.lastName ?? "",
          [
            Validators.required,
            Validators.pattern(/\S/),
            Validators.maxLength(100),
          ],
        ],
        profileCode: [entry?.profileCode ?? "", Validators.required],
      }),
    );
  }
  removeRegistration(index: number) {
    if (!this.busy()) this.registrationForm.removeAt(index);
  }
  profileLabel(code: string) {
    return this.profileDefs().find((p) => p.code === code)?.label ?? code;
  }
  readonly profileForm = new FormGroup<Record<string, FormControl<number>>>({});
  isAzo() {
    return (
      (this.editing()?.team ??
        this.auth.user()?.team ??
        this.form.controls.team.value) === "AZO"
    );
  }
  groupProfiles(group: string) {
    return this.profileDefs().filter((p) => p.group === group);
  }
  hasProfiles(r: Report) {
    return Object.keys(r.profileCounts || {}).length > 0;
  }
  profileTotal(r: Report) {
    return Object.values(r.profileCounts || {}).reduce((a, b) => a + b, 0);
  }
  constructor() {
    for (const team of ["AZO", "ZEHAMI"])
      this.sales.goals(team).subscribe({
        next: (defs) => {
          this.goals.update((g) => ({ ...g, [team]: defs }));
          for (const d of defs)
            this.goalForm.addControl(
              d.code,
              new FormControl(0, {
                nonNullable: true,
                validators: [
                  Validators.required,
                  Validators.min(0),
                  Validators.max(1000000),
                  Validators.pattern(/^\d+$/),
                ],
              }),
            );
          if (this.editing()?.goalCounts)
            this.goalForm.patchValue(this.editing()!.goalCounts);
        },
        error: (e) => this.error.set(apiError(e)),
      });
    this.http.get<AzoProfile[]>("/api/report-profiles").subscribe({
      next: (defs) => {
        this.profileDefs.set(defs);
        for (const p of defs)
          this.profileForm.addControl(
            p.code,
            new FormControl(0, {
              nonNullable: true,
              validators: [
                Validators.required,
                Validators.min(0),
                Validators.max(1000000),
                Validators.pattern(/^\d+$/),
              ],
            }),
          );
        const selected = this.editing();
        if (selected?.profileCounts)
          this.profileForm.patchValue(selected.profileCounts);
      },
      error: () =>
        this.error.set(
          "Impossible de charger les profils AZO. Actualisez avant de détailler les profils.",
        ),
    });
    this.load();
    this.http
      .get<{ configured: boolean }>("/api/integrations/status")
      .subscribe({
        next: (r) => this.integrationReady.set(r.configured),
        error: () => this.integrationReady.set(false),
      });
  }
  load(clearNotice = false) {
    if (clearNotice) this.success.set("");
    this.loading.set(true);
    this.error.set("");
    this.api
      .list()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (r) => {
          this.reports.set(r);
          const requested = this.route.snapshot.queryParamMap.get("id");
          if (requested && !this.detail())
            this.detail.set(
              r.find((item) => item.id === Number(requested)) ?? null,
            );
          const opened = this.detail();
          if (opened)
            this.detail.set(r.find((item) => item.id === opened.id) ?? null);
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  canEdit(r: Report) {
    return (
      r.authorId === this.auth.user()?.id &&
      ["DRAFT", "RETURNED"].includes(r.status)
    );
  }
  canReview(r: Report) {
    return (
      ((this.auth.isSupervisor() && r.supervisorId === this.auth.user()?.id) ||
        (this.auth.isSuperAdmin() && !r.supervisorId)) &&
      r.status === "SUBMITTED" &&
      r.authorId !== this.auth.user()?.id
    );
  }
  read(r: Report) {
    this.correctionLocation.set("");
    this.detail.set(r);
    this.formVisible.set(false);
    this.error.set("");
    this.jump("report-detail");
  }
  edit(r?: Report) {
    this.registrationForm.clear();
    for (const entry of r?.azoRegistrations ?? []) this.addRegistration(entry);
    this.goalForm.reset();
    if (r?.goalCounts) this.goalForm.patchValue(r.goalCounts);
    this.editing.set(r ?? null);
    this.form.reset({
      title: r?.title ?? "",
      content: r?.content ?? "",
      status: r?.status === "SUBMITTED" ? "SUBMITTED" : "DRAFT",
      team: r?.team ?? this.auth.user()?.team ?? "AZO",
      reportDate: r?.reportDate ?? this.today,
      mode: r?.mode ?? "WRITTEN",
      prospectsContacted: r?.prospectsContacted ?? 0,
      registrations: r?.registrations ?? 0,
      detailProfiles: !r || Object.keys(r.profileCounts || {}).length > 0,
      syncStats: false,
    });
    this.profileForm.reset();
    if (r?.profileCounts) this.profileForm.patchValue(r.profileCounts);
    this.formVisible.set(true);
    this.detail.set(null);
    this.error.set("");
    this.success.set("");
    this.jump("report-form");
  }
  save() {
    if (this.isAzo() && this.registrationForm.invalid) {
      this.registrationForm.markAllAsTouched();
      this.error.set(
        "Complétez le nom, le prénom et le profil de chaque personne inscrite, ou retirez la ligne incomplète.",
      );
      return;
    }

    if (
      this.form.invalid ||
      this.goalForm.invalid ||
      this.busy() ||
      (this.isAzo() &&
        this.form.controls.detailProfiles.value &&
        this.profileForm.invalid)
    )
      return;
    if (!this.goalDefs().length) {
      this.error.set(
        "Impossible de charger les objectifs. Actualisez avant de sauvegarder le rapport.",
      );
      return;
    }
    const data = {
      ...this.form.getRawValue(),
      azoRegistrations: this.isAzo() ? this.registrationForm.getRawValue() : [],
      goalCounts: Object.fromEntries(
        this.goalDefs().map((g) => [
          g.code,
          this.goalForm.controls[g.code]?.value ?? 0,
        ]),
      ),
      profileCounts:
        this.isAzo() &&
        this.form.controls.mode.value === "STATS" &&
        this.form.controls.detailProfiles.value
          ? this.profileForm.getRawValue()
          : {},
      version: this.editing()?.version ?? null,
    };
    if (data.mode === "WRITTEN" && !data.content.trim()) {
      this.error.set("Décrivez votre journée avant d’enregistrer.");
      return;
    }
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    const r = this.editing();
    this.auth
      .csrf()
      .pipe(
        switchMap(() =>
          r ? this.api.update(r.id, data) : this.api.create(data),
        ),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (report) => {
          this.formVisible.set(false);
          this.detail.set(report);
          this.success.set(
            report.status === "SUBMITTED"
              ? "Rapport soumis au superviseur."
              : "Brouillon enregistré.",
          );
          this.load();
          this.jump("report-detail");
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  previewStats() {
    if (this.busy() || !this.form.controls.reportDate.value) return;
    this.busy.set(true);
    this.error.set("");
    this.http
      .get<{
        prospectsContacted: number;
        registrations: number;
        profileCounts?: Record<string, number>;
        goalCounts?: Record<string, number>;
      }>("/api/integrations/my-stats", {
        params: { date: this.form.controls.reportDate.value },
      })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (r) => {
          this.goalForm.reset();
          if (r.goalCounts) this.goalForm.patchValue(r.goalCounts);
          this.profileForm.reset();
          if (r.profileCounts) this.profileForm.patchValue(r.profileCounts);
          this.form.patchValue({
            detailProfiles:
              !!r.profileCounts && Object.keys(r.profileCounts).length > 0,
            prospectsContacted: r.prospectsContacted,
            registrations: r.registrations,
          });
          this.success.set(
            "Aperçu chargé. Les statistiques seront relues côté serveur à l’enregistrement.",
          );
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  review(r: Report, action: string, comment: string) {
    if (this.busy()) return;
    if (
      action === "RETURNED" &&
      (!comment.trim() || !this.correctionLocation().trim())
    ) {
      this.error.set("Indiquez la rubrique exacte et la correction attendue.");
      return;
    }
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() =>
          this.api.review(r, action, comment, this.correctionLocation()),
        ),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (value) => {
          this.correctionLocation.set("");
          this.detail.set(value);
          this.success.set(
            action === "RETURNED"
              ? "Rapport renvoyé au commercial."
              : "Rapport confirmé.",
          );
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  remove(r: Report) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() => this.api.delete(r.id)),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.pendingDelete.set(null);
          if (this.detail()?.id === r.id) this.detail.set(null);
          this.success.set("Brouillon supprimé.");
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  download(r: Report) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.api
      .pdf(r.id)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob),
            a = document.createElement("a");
          a.href = url;
          a.download = `rapport-${r.team ?? "angi"}-${r.reportDate}-${r.id}.pdf`;
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 30000);
        },
        error: () =>
          this.error.set(
            "Téléchargement impossible. Vérifiez votre session et réessayez.",
          ),
      });
  }
  private jump(id: string) {
    setTimeout(() =>
      document
        .getElementById(id)
        ?.scrollIntoView({ behavior: "smooth", block: "start" }),
    );
  }
}

import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { AppComponent } from './app/app.component';
import { sessionInterceptor } from './app/core/session.interceptor';
import { routes } from './app/app.routes';
bootstrapApplication(AppComponent, {providers:[provideRouter(routes),provideHttpClient(withInterceptors([sessionInterceptor]))]}).catch(console.error);

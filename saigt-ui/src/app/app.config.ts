import { ApplicationConfig, inject, provideAppInitializer } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { errorEnvelopeInterceptor } from './core/interceptors/error-envelope.interceptor';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([errorEnvelopeInterceptor, authInterceptor])),
    // Antes da primeira navegação, senão o `authGuard` manda para `/login` sem esperar pela sessão restaurada.
    provideAppInitializer(() => inject(AuthService).restaurarSessao()),
  ]
};

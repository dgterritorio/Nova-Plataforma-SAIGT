import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ehPerfilBackoffice } from './perfis-backoffice';

/** Usar depois de `authGuard` (`canActivate: [authGuard, adminGuard]`), que já trata a falta de sessão. */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (ehPerfilBackoffice(authService.perfilInterno())) {
    return true;
  }

  return router.createUrlTree(['/painel']);
};

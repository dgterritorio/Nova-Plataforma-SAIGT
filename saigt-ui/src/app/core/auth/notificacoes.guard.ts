import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ROUTES } from '../../shared/routes';
import { AuthService } from './auth.service';
import { temCentroNotificacoes } from './perfis-backoffice';

export const notificacoesGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (temCentroNotificacoes(authService.perfilInterno())) {
    return true;
  }

  return router.createUrlTree([ROUTES.PAINEL]);
};

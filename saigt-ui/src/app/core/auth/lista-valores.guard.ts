import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ROUTES } from '../../shared/routes';
import { AuthService } from './auth.service';
import { temListasValores } from './perfis-backoffice';

/** Usar depois de `authGuard`. Ao contrário do `adminGuard`, deixa entrar também o `TECNICO`, em leitura. */
export const listaValoresGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (temListasValores(authService.perfilInterno())) {
    return true;
  }

  return router.createUrlTree([ROUTES.PAINEL]);
};

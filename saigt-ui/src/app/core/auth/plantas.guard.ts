import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ROUTES } from '../../shared/routes';
import { AuthService } from './auth.service';
import { temPlantasSaigt } from './perfis-backoffice';

/** Usar depois de `authGuard`. Ao contrário do `adminGuard`, deixa entrar também o `TECNICO`. */
export const plantasGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (temPlantasSaigt(authService.perfilInterno())) {
    return true;
  }

  return router.createUrlTree([ROUTES.PAINEL]);
};

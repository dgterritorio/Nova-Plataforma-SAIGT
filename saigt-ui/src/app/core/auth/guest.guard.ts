import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';

/** "Recuar" até `/login` não limpa a sessão em memória: termina-a aqui, senão "avançar" reentrava sem credenciais. */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  if (authService.estaAutenticado()) {
    authService.logout().subscribe({ error: () => undefined });
  }
  return true;
};

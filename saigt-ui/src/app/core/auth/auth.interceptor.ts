import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Quem faz o pedido renova e repete ele próprio num 401 (carregamentos de ficheiros), para o envio não se repetir duas vezes. */
export const RENOVACAO_PELO_CHAMADOR = new HttpContextToken<boolean>(() => false);

/**
 * Num 401 renova **uma** vez pelo grant `refresh_token` (`AuthService.refresh()`) e repete o pedido. Se a sessão já foi
 * renovada por outro pedido entretanto, repete logo com o token novo, sem gastar outro refresh. Uma renovação recusada
 * (`invalid_grant`) ou sem resposta termina a sessão e manda para o login. Os pedidos ao servidor de autorização
 * (`/oauth2/*`) nunca levam o Bearer nem renovam num 401 (evita ciclos).
 * Registado depois do `errorEnvelopeInterceptor`: precisa do `HttpErrorResponse` em bruto (`status`).
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isAuthEndpoint = request.url.includes('/oauth2/');
  const accessToken = authService.accessToken();
  const authorizedRequest =
    accessToken && !isAuthEndpoint ? request.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } }) : request;

  return next(authorizedRequest).pipe(
    catchError((error: unknown) => {
      const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
      if (!isUnauthorized || isAuthEndpoint || request.context.get(RENOVACAO_PELO_CHAMADOR)) {
        return throwError(() => error);
      }

      const tokenAtual = authService.accessToken();
      if (tokenAtual && tokenAtual !== accessToken) {
        return next(request.clone({ setHeaders: { Authorization: `Bearer ${tokenAtual}` } }));
      }

      return authService.refresh().pipe(
        switchMap((sessao) =>
          next(request.clone({ setHeaders: { Authorization: `Bearer ${sessao.accessToken}` } })),
        ),
        catchError((erroDeRenovacao: unknown) => {
          authService.limparSessao();
          void router.navigate(['/login']);
          return throwError(() => erroDeRenovacao);
        }),
      );
    }),
  );
};

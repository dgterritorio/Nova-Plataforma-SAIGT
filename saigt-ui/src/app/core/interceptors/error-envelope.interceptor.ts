import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { ApiError, ErrorResponse, MENSAGEM_SEM_LIGACAO, semLigacao } from '../models/error-response.model';

const MENSAGEM_ERRO_INESPERADO = 'Não foi possível concluir a operação. Tente novamente.';

/** Converte qualquer erro HTTP em `ApiError`, sem nunca o engolir. */
export const errorEnvelopeInterceptor: HttpInterceptorFn = (request, next) => {
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
        // Num pedido de ficheiro (`responseType: 'blob'`) o envelope de erro também chega como Blob.
        return from(lerTexto(error.error)).pipe(switchMap((texto) => throwError(() => toApiError(error, lerJson(texto)))));
      }
      if (error instanceof HttpErrorResponse) {
        return throwError(() => toApiError(error));
      }
      return throwError(() => error);
    }),
  );
};

function lerTexto(blob: Blob): Promise<string> {
  return new Promise((resolve) => {
    const leitor = new FileReader();
    leitor.onload = () => resolve(String(leitor.result ?? ''));
    leitor.onerror = () => resolve('');
    leitor.readAsText(blob);
  });
}

function lerJson(texto: string): ErrorResponse | null {
  try {
    return JSON.parse(texto);
  } catch {
    return null;
  }
}

/** Erro do servidor de autorização (RFC 6749 §5.2), fora do envelope da API. */
interface ErroOAuth {
  error: string;
  error_description?: string;
}

function toApiError(error: HttpErrorResponse, body: ErrorResponse | ErroOAuth | null = error.error): ApiError {
  const estado = error.status;
  if (body && typeof body === 'object' && 'codigo' in body && typeof body.codigo === 'string') {
    return new ApiError(body.codigo, body.mensagem, body.traceId, body.campos ?? null, estado);
  }
  if (body && typeof body === 'object' && 'error' in body && typeof body.error === 'string') {
    return new ApiError(body.error, body.error_description ?? '', 'unknown', null, estado);
  }
  if (estado === 413) {
    // O nginx corta o upload acima do seu limite antes de a API responder, sem envelope.
    return new ApiError('DOC_TAMANHO_EXCEDIDO', 'O ficheiro excede o tamanho máximo permitido.', 'unknown', null, estado);
  }
  // O `message` do `HttpErrorResponse` («Http failure response for…») nunca chega ao ecrã.
  const semResposta = new ApiError('ERRO_DESCONHECIDO', MENSAGEM_SEM_LIGACAO, 'unknown', null, estado);
  return semLigacao(semResposta) ? semResposta : new ApiError('ERRO_DESCONHECIDO', MENSAGEM_ERRO_INESPERADO, 'unknown', null, estado);
}

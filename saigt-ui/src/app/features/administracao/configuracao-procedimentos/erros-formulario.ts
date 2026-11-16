import { AbstractControl, ValidationErrors } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';

export interface ErroServidorSeparado<C extends string> {
  porCampo: Partial<Record<C, string>>;
  /** 409 `EM_USO` ou outro erro sem campo do formulário a assinalar. */
  geral: ApiError | null;
}

export function separarErroServidor<C extends string>(erro: ApiError | null, campos: readonly C[]): ErroServidorSeparado<C> {
  if (!erro) {
    return { porCampo: {}, geral: null };
  }
  const porCampo: Partial<Record<C, string>> = {};
  for (const campo of erro.campos ?? []) {
    if ((campos as readonly string[]).includes(campo.path)) {
      porCampo[campo.path as C] = campo.erro;
    }
  }
  return { porCampo, geral: Object.keys(porCampo).length > 0 ? null : erro };
}

/** Os campos numéricos chegam como texto do `app-campo-texto`; vazio fica para o `required`. */
export function inteiroPositivo(controlo: AbstractControl<string | null>): ValidationErrors | null {
  const valor = (controlo.value ?? '').trim();
  if (valor === '') {
    return null;
  }
  return /^\d+$/.test(valor) && Number(valor) >= 1 ? null : { inteiroPositivo: true };
}

export function numeroOuNulo(valor: string | null): number | null {
  const texto = (valor ?? '').trim();
  return texto === '' ? null : Number(texto);
}

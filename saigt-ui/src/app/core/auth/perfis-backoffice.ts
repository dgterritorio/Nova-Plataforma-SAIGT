import type { PerfilInterno } from './auth.models';

/** Igual aos serviços de administração do backend: só `MASTER` lê e escreve no backoffice. */
export const PERFIS_BACKOFFICE: readonly PerfilInterno[] = ['MASTER'];

export function ehPerfilBackoffice(perfil: PerfilInterno | null): boolean {
  return perfil !== null && PERFIS_BACKOFFICE.includes(perfil);
}

/** Plantas SAIGT/SNIT: o Técnico também as corrige, por isso não é o conjunto do `adminGuard`. */
export const PERFIS_PLANTAS_SAIGT: readonly PerfilInterno[] = ['MASTER', 'TECNICO'];

export function temPlantasSaigt(perfil: PerfilInterno | null): boolean {
  return perfil !== null && PERFIS_PLANTAS_SAIGT.includes(perfil);
}

/** Listas de Valores: o Técnico lê-as (escolhe delas os motivos ao rejeitar); só o `MASTER` as altera, na API. */
export const PERFIS_LISTAS_VALORES: readonly PerfilInterno[] = ['MASTER', 'TECNICO'];

export function temListasValores(perfil: PerfilInterno | null): boolean {
  return perfil !== null && PERFIS_LISTAS_VALORES.includes(perfil);
}

export function temCentroNotificacoes(perfil: PerfilInterno | null): boolean {
  return perfil === null || perfil === 'MASTER';
}

/** Qualquer perfil interno: vê o painel da DGT e nunca cria processos pelo perfil (usa o acesso INCM da DGT). */
export function ehUtilizadorDgt(perfil: PerfilInterno | null): boolean {
  return perfil !== null;
}

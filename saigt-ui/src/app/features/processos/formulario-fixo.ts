import { FormularioFixo } from '../../models/processo.model';


export const AVISO_SEM_FORMULARIO =
  'Este procedimento ainda não tem formulário na aplicação. Contacte a DGT através de um pedido de apoio.';

export const ESTADOS_COM_FORMULARIO_EDITAVEL: readonly string[] = ['EM_SUBMISSAO'];

const SEM_ELEMENTOS_AFETADOS: readonly FormularioFixo[] = [
  'SRUP',
  'RATIFICACAO',
  'SUSPENSAO_CCDR',
  'SUSPENSAO_MP_GOVERNO',
  'PRORROGACAO_GOVERNO',
];

/**
 * Os formulários das restantes servidões, da Ratificação, da Suspensão da CCDR e do Governo nunca perguntam pelos
 * elementos afetados.
 */
export function pedeElementosAfetadosNoFormulario(formulario: FormularioFixo | null, exige: boolean): boolean {
  return !(formulario && SEM_ELEMENTOS_AFETADOS.includes(formulario)) && exige;
}

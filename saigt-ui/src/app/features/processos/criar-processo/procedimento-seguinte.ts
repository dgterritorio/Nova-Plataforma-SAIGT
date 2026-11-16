import { ProcedimentoDisponivel } from '../../../models/instrumento.model';

/**
 * A API só oferece um Procedimento com os anteriores obrigatórios publicados e nenhum outro processo da
 * Dinâmica em curso; por isso o de maior `ordem` é o seguinte, e uma Discussão Pública repetida fica como
 * alternativa. Um rascunho por acabar ganha a todos; os atos pontuais ficam fora do sequenciamento.
 */
export function procedimentoSeguinte(procedimentos: readonly ProcedimentoDisponivel[]): ProcedimentoDisponivel | null {
  const sequenciados = procedimentos.filter((procedimento) => !procedimento.atoPontual);
  const comTentativa = sequenciados.find((procedimento) => procedimento.tentativaEmCurso);
  if (comTentativa) {
    return comTentativa;
  }
  return sequenciados.reduce<ProcedimentoDisponivel | null>(
    (seguinte, procedimento) => (seguinte === null || procedimento.ordem > seguinte.ordem ? procedimento : seguinte),
    null,
  );
}

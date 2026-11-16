import { ApiError, CampoErro } from '../../core/models/error-response.model';
import { InformacaoBase, InformacaoBaseTextoAto, RespostaSimultaneoPmot } from '../../models/processo.model';

export const MENSAGEM_CAMPO_BLOQUEADO = 'Bloqueado depois da primeira submissão';
/** 409 de um campo do formulário inicial alterado depois da primeira submissão; a UI já os desativa, o servidor decide. */
export const CODIGO_CAMPO_BLOQUEADO = 'CAMPO_BLOQUEADO';

const CAMINHO_INFORMACAO_BASE = 'informacaoBase';
const CAMINHO_DATA_ASSINATURA = 'dataAssinatura';
export const CAMINHO_ERRO_PRAZO = 'informacaoBase.textoAto.prazoAnos';
export const CAMINHO_ERRO_AVALIACAO_AMBIENTAL = 'informacaoBase.textoAto.avaliacaoAmbiental';
export const CAMINHO_ERRO_DATA_DELIBERACAO = 'informacaoBase.deliberacao.dataDeliberacao';
const CAMPOS_PRAZO = ['prazoAnos', 'prazoMeses', 'prazoDias'] as const;

/** O que o ecrã junta ao pedido de gravação: o componente de documentos não grava. `dataAssinatura` vai na coluna, fora da Informação Base. */
export interface InformacaoBaseAlterada {
  informacaoBase: InformacaoBase | null;
  dataAssinatura: string | null;
}

/** `dadosFormulario` chega como texto JSON: `{ "informacaoBase": { ... } }`, ou nulo num rascunho sem Informação Base. */
export function lerInformacaoBase(dadosFormulario: string | null): InformacaoBase | null {
  if (!dadosFormulario) {
    return null;
  }
  try {
    const dados = JSON.parse(dadosFormulario) as { informacaoBase?: InformacaoBase | null } | null;
    return dados?.informacaoBase ?? null;
  } catch {
    return null;
  }
}

/** A resposta «A alteração introduz alguma planta nova?» fica em `dadosFormulario`, ao lado da Informação Base; nulo se não houver booleano. */
export function lerIntroduzPlantaNova(dadosFormulario: string | null): boolean | null {
  if (!dadosFormulario) {
    return null;
  }
  try {
    const dados = JSON.parse(dadosFormulario) as { introduzPlantaNova?: unknown } | null;
    return typeof dados?.introduzPlantaNova === 'boolean' ? dados.introduzPlantaNova : null;
  } catch {
    return null;
  }
}

export function lerSimultaneoComPmot(dadosFormulario: string | null): RespostaSimultaneoPmot | null {
  if (!dadosFormulario) {
    return null;
  }
  try {
    const dados = JSON.parse(dadosFormulario) as { simultaneoComPmot?: unknown } | null;
    return dados?.simultaneoComPmot === 'SIM' || dados?.simultaneoComPmot === 'NAO' ? dados.simultaneoComPmot : null;
  } catch {
    return null;
  }
}

export function informacaoBaseDe(processo: { dadosFormulario: string | null; dataAssinatura: string | null }): InformacaoBaseAlterada {
  return { informacaoBase: lerInformacaoBase(processo.dadosFormulario), dataAssinatura: processo.dataAssinatura };
}

function temPrazo(dados: InformacaoBaseTextoAto | null | undefined): dados is InformacaoBaseTextoAto {
  return !!dados && CAMPOS_PRAZO.some((campo) => dados[campo] != null);
}

/**
 * As regras de coerência que o servidor aplica na submissão, para o erro aparecer no campo sem esperar por ela. Cada
 * regra só corre com os dois lados preenchidos; no prazo, vazio conta como 0 e os campos comparam-se um a um.
 */
export function errosCoerenciaInformacaoBase({ informacaoBase, dataAssinatura }: InformacaoBaseAlterada): Record<string, string> {
  const deliberacao = informacaoBase?.deliberacao;
  const textoAto = informacaoBase?.textoAto;
  const erros: Record<string, string> = {};
  if (temPrazo(deliberacao) && temPrazo(textoAto) && CAMPOS_PRAZO.some((campo) => (deliberacao[campo] ?? 0) !== (textoAto[campo] ?? 0))) {
    erros[CAMINHO_ERRO_PRAZO] = 'O prazo tem de ser o mesmo da Deliberação.';
  }
  const avaliacaoDeliberacao = deliberacao?.avaliacaoAmbiental ?? null;
  const avaliacaoTextoAto = textoAto?.avaliacaoAmbiental ?? null;
  if (avaliacaoDeliberacao !== null && avaliacaoTextoAto !== null && avaliacaoDeliberacao !== avaliacaoTextoAto) {
    erros[CAMINHO_ERRO_AVALIACAO_AMBIENTAL] = 'A avaliação ambiental tem de ser a mesma da Deliberação.';
  }
  const dataDeliberacao = deliberacao?.dataDeliberacao;
  if (dataDeliberacao && dataAssinatura && dataDeliberacao > dataAssinatura) {
    erros[CAMINHO_ERRO_DATA_DELIBERACAO] = 'A data da deliberação tem de ser igual ou anterior à data do texto do ato.';
  }
  return erros;
}

/** Dois erros no mesmo caminho (a data da deliberação depois do texto do ato e da data limite) ficam os dois no campo. */
export function errosPorCaminho(campos: CampoErro[]): Record<string, string> {
  const erros: Record<string, string> = {};
  for (const campo of campos) {
    erros[campo.path] = erros[campo.path] ? `${erros[campo.path]} ${campo.erro}` : campo.erro;
  }
  return erros;
}

function pertenceAoBloco(campo: CampoErro): boolean {
  return (
    campo.path === CAMINHO_DATA_ASSINATURA ||
    campo.path === CAMINHO_INFORMACAO_BASE ||
    campo.path.startsWith(`${CAMINHO_INFORMACAO_BASE}.`)
  );
}

/** Os erros do bloco aparecem junto do campo e não se repetem no banner nem no cartão INCM. */
export function separarErrosInformacaoBase(erro: ApiError): { doBloco: Record<string, string>; restantes: CampoErro[] } {
  const campos = erro.campos ?? [];
  return { doBloco: errosPorCaminho(campos.filter(pertenceAoBloco)), restantes: campos.filter((campo) => !pertenceAoBloco(campo)) };
}

export interface ListaValoresItem {
  id: number;
  tipo: string;
  codigo: string;
  descricao: string;
  ordem: number;
  ativo: boolean;
  /** Valor do nível acima (Classe → Domínio, Tipologia → Classe, CIM / AM → Região); nulo nos restantes tipos. */
  paiId?: number | null;
}

/** `tipo` tem de já existir: este ecrã nunca cria um `tipo` novo. */
export interface CriarListaValoresRequest {
  tipo: string;
  codigo: string;
  descricao: string;
  ordem: number;
  /** Obrigatório em `CLASSE_SRUP`, `TIPOLOGIA_SRUP` e `CIM_AM`; nos outros tipos não vai. */
  paiId?: number;
}

/** Sem `codigo`/`tipo` de propósito: imutáveis após criação. */
export interface AtualizarListaValoresRequest {
  descricao?: string;
  ordem?: number;
  ativo?: boolean;
}

/** Um tipo de lista de valores gerido pelo Master, com o rótulo em português. */
export interface TipoListaValores {
  tipo: string;
  descricao: string;
}

/**
 * Os tipos de `lista_valores` que o Master gere, enquanto a API não os devolve (`GET /api/v1/lista-valores/tipos`).
 * Um por constante do enum da API, também os que ainda não têm valores.
 */
export const TIPOS_LISTA_VALORES: readonly TipoListaValores[] = [
  { tipo: 'FREGUESIA', descricao: 'Freguesia' },
  { tipo: 'TOPONIMO', descricao: 'Topónimo' },
  { tipo: 'MOTIVO_ALTERACAO_LEI', descricao: 'Motivo da alteração — Lei' },
  { tipo: 'MOTIVO_ALTERACAO_PROGRAMA', descricao: 'Motivo da alteração — Programa' },
  { tipo: 'MOTIVO_ALTERACAO_PLANO', descricao: 'Motivo da alteração — Plano' },
  { tipo: 'ALTERACAO_PEOT', descricao: 'Alteração PEOT' },
  { tipo: 'CATEGORIA_REJEICAO_DGT', descricao: 'Categorias de recusa' },
  { tipo: 'MOTIVO_REJEICAO_DGT', descricao: 'Motivos de recusa' },
  { tipo: 'MOTIVO_PROCEDIMENTO_REN', descricao: 'Motivo do procedimento (REN)' },
  { tipo: 'DOMINIO_SRUP', descricao: 'Domínio SRUP' },
  { tipo: 'CLASSE_SRUP', descricao: 'Classe SRUP' },
  { tipo: 'TIPOLOGIA_SRUP', descricao: 'Tipologia SRUP' },
  { tipo: 'FUNDAMENTACAO_RJIGT', descricao: 'Fundamentação RJIGT' },
  { tipo: 'REGIAO', descricao: 'Região' },
  { tipo: 'CIM_AM', descricao: 'CIM / AM' },
];

/** Tipo dependente → tipo do valor de que depende (a classificação das SRUP; a CIM / AM do SGIFR depende da Região). */
export const TIPO_PAI_LISTA_VALORES: Readonly<Record<string, string>> = {
  CLASSE_SRUP: 'DOMINIO_SRUP',
  TIPOLOGIA_SRUP: 'CLASSE_SRUP',
  CIM_AM: 'REGIAO',
};

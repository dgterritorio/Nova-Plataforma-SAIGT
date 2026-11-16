import {
  Avaliador,
  EstadoDinamica,
  OperadorRegra,
  ParametrosCondicao,
  ROTULOS_ESTADO_DINAMICA,
} from '../../../models/configuracao.model';
import { rotuloEstado } from '../../../shared/components/estado-processo-badge.component';

export interface TipoNomeado {
  codigo: string;
  nome: string;
}

export interface TiposDaRegra {
  tiposDinamica: readonly TipoNomeado[];
  tiposProcedimento: readonly TipoNomeado[];
}

/** O avaliador pode faltar numa condição ainda por preencher no editor. */
export interface CondicaoADescrever {
  avaliador: Avaliador | null;
  parametros: ParametrosCondicao;
  grupo: number;
  ativo: boolean;
}

export interface RegraADescrever {
  operador: OperadorRegra;
  condicoes: readonly CondicaoADescrever[];
}

export const SEM_CONDICOES_ATIVAS = 'Sem condições ativas';

/** Valor ainda por escolher no editor. */
const POR_ESCOLHER = '…';

/**
 * Cada grupo entre parênteses, com as condições ativas unidas por "e"; os grupos unidos por "e" (AND) ou "ou" (OR).
 * Ex.: "(o Instrumento está em vigor e não existe Dinâmica Revisão em curso)".
 */
export function descreverRegra(regra: RegraADescrever, tipos: TiposDaRegra): string {
  const grupos = new Map<number, string[]>();
  for (const condicao of regra.condicoes) {
    if (!condicao.ativo || !condicao.avaliador) {
      continue;
    }
    const frases = grupos.get(condicao.grupo) ?? [];
    frases.push(descreverCondicao(condicao, tipos));
    grupos.set(condicao.grupo, frases);
  }
  if (grupos.size === 0) {
    return SEM_CONDICOES_ATIVAS;
  }
  return [...grupos.entries()]
    .sort(([a], [b]) => a - b)
    .map(([, frases]) => `(${frases.join(' e ')})`)
    .join(regra.operador === 'OR' ? ' ou ' : ' e ');
}

export function descreverCondicao(condicao: CondicaoADescrever, tipos: TiposDaRegra): string {
  const parametros = condicao.parametros;
  switch (condicao.avaliador) {
    case 'OCORRENCIA_EM_ESTADO':
      return `existe ${ocorrencia(parametros, tipos)} ${estado(parametros) ?? `em ${POR_ESCOLHER}`}`;
    case 'OCORRENCIA_NAO_EXISTE': {
      const quando = estado(parametros);
      const oQue =
        parametros.tipo_ocorrencia === 'DINAMICA' && !parametros.codigo ? 'nenhuma Dinâmica' : ocorrencia(parametros, tipos);
      return `não existe ${oQue}${quando ? ` ${quando}` : ''}`;
    }
    case 'INSTRUMENTO_EM_VIGOR':
      return 'o Instrumento está em vigor';
    case 'DINAMICA_BLOQUEADA_LEGADO':
      return `existe ${nome(parametros.codigo, tipos.tiposDinamica)} em curso no sistema anterior`;
    default:
      return POR_ESCOLHER;
  }
}

function ocorrencia(parametros: ParametrosCondicao, tipos: TiposDaRegra): string {
  switch (parametros.tipo_ocorrencia) {
    case 'DINAMICA':
      return `Dinâmica ${nome(parametros.codigo, tipos.tiposDinamica)}`;
    case 'PROCESSO':
      return `processo de ${nome(parametros.codigo, tipos.tiposProcedimento)}`;
    default:
      return POR_ESCOLHER;
  }
}

/** Os estados de Dinâmica já trazem a preposição quando a pedem ("em curso", "terminada"). */
function estado(parametros: ParametrosCondicao): string | null {
  if (!parametros.estado) {
    return null;
  }
  if (parametros.tipo_ocorrencia === 'DINAMICA') {
    return ROTULOS_ESTADO_DINAMICA[parametros.estado as EstadoDinamica] ?? parametros.estado;
  }
  return `em ${rotuloEstado(parametros.estado)}`;
}

/** Um código que já não está no catálogo mostra-se tal como está, nunca em branco. */
function nome(codigo: string | undefined, catalogo: readonly TipoNomeado[]): string {
  if (!codigo) {
    return POR_ESCOLHER;
  }
  return catalogo.find((tipo) => tipo.codigo === codigo)?.nome ?? codigo;
}

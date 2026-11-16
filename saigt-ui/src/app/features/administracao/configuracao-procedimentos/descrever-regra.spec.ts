import { describe, expect, it } from 'vitest';
import { CondicaoADescrever, SEM_CONDICOES_ATIVAS, TiposDaRegra, descreverCondicao, descreverRegra } from './descrever-regra';

const TIPOS: TiposDaRegra = {
  tiposDinamica: [
    { codigo: 'ELABORACAO', nome: 'Elaboração' },
    { codigo: 'REVISAO', nome: 'Revisão' },
  ],
  tiposProcedimento: [{ codigo: 'DECISAO_INICIO', nome: 'Decisão de início' }],
};

function condicao(parcial: Partial<CondicaoADescrever>): CondicaoADescrever {
  return { avaliador: 'INSTRUMENTO_EM_VIGOR', parametros: {}, grupo: 1, ativo: true, ...parcial };
}

describe('descreverCondicao', () => {
  it.each<[string, Partial<CondicaoADescrever>, string]>([
    [
      'existe Dinâmica em estado',
      { avaliador: 'OCORRENCIA_EM_ESTADO', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'REVISAO', estado: 'TERMINADA' } },
      'existe Dinâmica Revisão terminada',
    ],
    [
      'existe processo em estado (rótulo do badge)',
      { avaliador: 'OCORRENCIA_EM_ESTADO', parametros: { tipo_ocorrencia: 'PROCESSO', codigo: 'DECISAO_INICIO', estado: 'PUBLICADO' } },
      'existe processo de Decisão de início em Publicado',
    ],
    [
      'não existe Dinâmica sem estado',
      { avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'ELABORACAO' } },
      'não existe Dinâmica Elaboração',
    ],
    [
      'não existe Dinâmica em curso',
      { avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'REVISAO', estado: 'EM_CURSO' } },
      'não existe Dinâmica Revisão em curso',
    ],
    [
      'não existe nenhuma Dinâmica (sem código) em curso',
      { avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', estado: 'EM_CURSO' } },
      'não existe nenhuma Dinâmica em curso',
    ],
    [
      'não existe nenhuma Dinâmica (sem código nem estado)',
      { avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA' } },
      'não existe nenhuma Dinâmica',
    ],
    [
      'não existe processo em estado',
      { avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'PROCESSO', codigo: 'DECISAO_INICIO', estado: 'EM_SUBMISSAO' } },
      'não existe processo de Decisão de início em Em submissão',
    ],
    ['Instrumento em vigor', { avaliador: 'INSTRUMENTO_EM_VIGOR', parametros: {} }, 'o Instrumento está em vigor'],
    [
      'Dinâmica em curso no sistema anterior',
      { avaliador: 'DINAMICA_BLOQUEADA_LEGADO', parametros: { codigo: 'REVISAO' } },
      'existe Revisão em curso no sistema anterior',
    ],
    [
      'código fora do catálogo mostra-se tal como está',
      { avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'NOVA_DINAMICA' } },
      'não existe Dinâmica NOVA_DINAMICA',
    ],
    [
      'valores por escolher no editor',
      { avaliador: 'OCORRENCIA_EM_ESTADO', parametros: { tipo_ocorrencia: 'PROCESSO' } },
      'existe processo de … em …',
    ],
  ])('%s', (_, parcial, esperado) => {
    expect(descreverCondicao(condicao(parcial), TIPOS)).toBe(esperado);
  });
});

describe('descreverRegra', () => {
  it('um grupo fica entre parênteses, com as condições unidas por "e" (Revisão de PDM semeada)', () => {
    const texto = descreverRegra(
      {
        operador: 'AND',
        condicoes: [
          condicao({ avaliador: 'INSTRUMENTO_EM_VIGOR' }),
          condicao({ avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'REVISAO', estado: 'EM_CURSO' } }),
        ],
      },
      TIPOS,
    );
    expect(texto).toBe('(o Instrumento está em vigor e não existe Dinâmica Revisão em curso)');
  });

  it('a Elaboração única do PDM', () => {
    const texto = descreverRegra(
      {
        operador: 'AND',
        condicoes: [condicao({ avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'ELABORACAO' } })],
      },
      TIPOS,
    );
    expect(texto).toBe('(não existe Dinâmica Elaboração)');
  });

  it.each([
    ['AND', '(o Instrumento está em vigor) e (não existe Dinâmica Elaboração)'],
    ['OR', '(o Instrumento está em vigor) ou (não existe Dinâmica Elaboração)'],
  ] as const)('os grupos unem-se pelo operador %s, pela ordem do n.º do grupo', (operador, esperado) => {
    const texto = descreverRegra(
      {
        operador,
        condicoes: [
          condicao({ grupo: 3, avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'ELABORACAO' } }),
          condicao({ grupo: 1, avaliador: 'INSTRUMENTO_EM_VIGOR' }),
        ],
      },
      TIPOS,
    );
    expect(texto).toBe(esperado);
  });

  it('as condições inativas ficam de fora, e um grupo só com inativas desaparece', () => {
    const texto = descreverRegra(
      {
        operador: 'OR',
        condicoes: [
          condicao({ grupo: 1, avaliador: 'INSTRUMENTO_EM_VIGOR' }),
          condicao({ grupo: 1, avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA' }, ativo: false }),
          condicao({ grupo: 2, avaliador: 'DINAMICA_BLOQUEADA_LEGADO', parametros: { codigo: 'REVISAO' }, ativo: false }),
        ],
      },
      TIPOS,
    );
    expect(texto).toBe('(o Instrumento está em vigor)');
  });

  it('sem condições ativas, diz isso mesmo; condições ainda sem avaliador não contam', () => {
    expect(descreverRegra({ operador: 'AND', condicoes: [condicao({ ativo: false })] }, TIPOS)).toBe(SEM_CONDICOES_ATIVAS);
    expect(descreverRegra({ operador: 'AND', condicoes: [condicao({ avaliador: null })] }, TIPOS)).toBe(SEM_CONDICOES_ATIVAS);
    expect(descreverRegra({ operador: 'AND', condicoes: [] }, TIPOS)).toBe(SEM_CONDICOES_ATIVAS);
  });
});

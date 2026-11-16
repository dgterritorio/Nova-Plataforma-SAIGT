import { describe, expect, it } from 'vitest';
import { ApiError } from '../../core/models/error-response.model';
import { InformacaoBaseDeliberacao, InformacaoBaseTextoAto } from '../../models/processo.model';
import {
  errosCoerenciaInformacaoBase,
  errosPorCaminho,
  informacaoBaseDe,
  lerInformacaoBase,
  lerIntroduzPlantaNova,
  separarErrosInformacaoBase,
} from './informacao-base';

describe('lerIntroduzPlantaNova', () => {
  it('lê a resposta de dadosFormulario, ao lado da Informação Base', () => {
    expect(lerIntroduzPlantaNova('{"introduzPlantaNova":true}')).toBe(true);
    expect(lerIntroduzPlantaNova('{"informacaoBase":{"textoAto":null},"introduzPlantaNova":false}')).toBe(false);
  });

  it('devolve nulo sem dadosFormulario, com JSON ilegível, sem a chave ou sem um booleano', () => {
    expect(lerIntroduzPlantaNova(null)).toBeNull();
    expect(lerIntroduzPlantaNova('')).toBeNull();
    expect(lerIntroduzPlantaNova('{não é json')).toBeNull();
    expect(lerIntroduzPlantaNova('null')).toBeNull();
    expect(lerIntroduzPlantaNova('{"informacaoBase":{}}')).toBeNull();
    expect(lerIntroduzPlantaNova('{"introduzPlantaNova":"true"}')).toBeNull();
    expect(lerIntroduzPlantaNova('{"introduzPlantaNova":null}')).toBeNull();
  });
});

describe('lerInformacaoBase', () => {
  it('lê a Informação Base de dadosFormulario', () => {
    const informacaoBase = { deliberacao: { prazoAnos: 1 }, textoAto: null };

    expect(lerInformacaoBase(JSON.stringify({ informacaoBase }))).toEqual(informacaoBase);
  });

  it('devolve nulo sem dadosFormulario, com JSON ilegível ou sem o objeto', () => {
    expect(lerInformacaoBase(null)).toBeNull();
    expect(lerInformacaoBase('')).toBeNull();
    expect(lerInformacaoBase('{não é json')).toBeNull();
    expect(lerInformacaoBase('{"designacao":"x"}')).toBeNull();
    expect(lerInformacaoBase('null')).toBeNull();
  });

  it('junta a data de assinatura da coluna à Informação Base do JSON', () => {
    const processo = { dadosFormulario: '{"informacaoBase":{"textoAto":{"prazoAnos":2}}}', dataAssinatura: '2026-09-02' };

    expect(informacaoBaseDe(processo)).toEqual({ informacaoBase: { textoAto: { prazoAnos: 2 } }, dataAssinatura: '2026-09-02' });
  });
});

describe('separarErrosInformacaoBase', () => {
  const erro = (campos: { path: string; erro: string }[] | null) => new ApiError('VALIDACAO', 'Payload inválido.', 't1', campos);

  it('separa os caminhos da Informação Base e a data de assinatura dos restantes', () => {
    const resultado = separarErrosInformacaoBase(
      erro([
        { path: 'informacaoBase.deliberacao.prazoMeses', erro: 'Prazo inválido.' },
        { path: 'dataAssinatura', erro: 'Data inválida.' },
        { path: 'sumario', erro: 'Demasiado longo.' },
      ]),
    );

    expect(resultado.doBloco).toEqual({
      'informacaoBase.deliberacao.prazoMeses': 'Prazo inválido.',
      dataAssinatura: 'Data inválida.',
    });
    expect(resultado.restantes).toEqual([{ path: 'sumario', erro: 'Demasiado longo.' }]);
  });

  it('não confunde um campo que só começa por informacaoBase', () => {
    const resultado = separarErrosInformacaoBase(erro([{ path: 'informacaoBaseOutra', erro: 'x' }]));

    expect(resultado.doBloco).toEqual({});
    expect(resultado.restantes).toHaveLength(1);
  });

  it('sem campos não há nada do bloco nem restantes', () => {
    expect(separarErrosInformacaoBase(erro(null))).toEqual({ doBloco: {}, restantes: [] });
  });
});

describe('errosCoerenciaInformacaoBase', () => {
  const PRAZO = 'informacaoBase.textoAto.prazoAnos';
  const AVALIACAO = 'informacaoBase.textoAto.avaliacaoAmbiental';
  const DATA = 'informacaoBase.deliberacao.dataDeliberacao';
  const vazioTextoAto: InformacaoBaseTextoAto = { prazoAnos: null, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: null };
  const vazioDeliberacao: InformacaoBaseDeliberacao = { ...vazioTextoAto, dataDeliberacao: null, textoPublicar: null };

  function erros(
    deliberacao: Partial<InformacaoBaseDeliberacao> | null,
    textoAto: Partial<InformacaoBaseTextoAto> | null,
    dataAssinatura: string | null = '2027-03-31',
  ): Record<string, string> {
    return errosCoerenciaInformacaoBase({
      informacaoBase: {
        deliberacao: deliberacao ? { ...vazioDeliberacao, ...deliberacao } : null,
        textoAto: textoAto ? { ...vazioTextoAto, ...textoAto } : null,
      },
      dataAssinatura,
    });
  }

  it('prazos iguais campo a campo passam, com vazio igual a 0', () => {
    expect(erros({ prazoAnos: 1, prazoMeses: 2, prazoDias: 3 }, { prazoAnos: 1, prazoMeses: 2, prazoDias: 3 })).toEqual({});
    expect(erros({ prazoAnos: 1 }, { prazoAnos: 1, prazoMeses: 0, prazoDias: 0 })).toEqual({});
  });

  it('prazos diferentes, e 1 ano contra 12 meses, recusam o prazo do Texto do Ato', () => {
    expect(erros({ prazoAnos: 1 }, { prazoAnos: 2 })).toEqual({ [PRAZO]: 'O prazo tem de ser o mesmo da Deliberação.' });
    expect(Object.keys(erros({ prazoAnos: 1 }, { prazoMeses: 12 }))).toEqual([PRAZO]);
  });

  it('a avaliação ambiental diferente recusa a do Texto do Ato', () => {
    expect(erros({ avaliacaoAmbiental: true }, { avaliacaoAmbiental: false })).toEqual({
      [AVALIACAO]: 'A avaliação ambiental tem de ser a mesma da Deliberação.',
    });
    expect(erros({ avaliacaoAmbiental: false }, { avaliacaoAmbiental: false })).toEqual({});
  });

  it('a data da deliberação depois da data de assinatura é recusada; igual ou antes passa', () => {
    expect(erros({ dataDeliberacao: '2027-04-01' }, null)).toEqual({
      [DATA]: 'A data da deliberação tem de ser igual ou anterior à data do texto do ato.',
    });
    expect(erros({ dataDeliberacao: '2027-03-31' }, null)).toEqual({});
    expect(erros({ dataDeliberacao: '2027-03-01' }, null)).toEqual({});
  });

  it('com um lado vazio a regra não corre', () => {
    expect(erros({ prazoAnos: 1, avaliacaoAmbiental: true }, null)).toEqual({});
    expect(erros(null, { prazoAnos: 2, avaliacaoAmbiental: false })).toEqual({});
    expect(erros({ dataDeliberacao: '2030-01-01' }, null, null)).toEqual({});
    expect(errosCoerenciaInformacaoBase({ informacaoBase: null, dataAssinatura: null })).toEqual({});
  });

  it('vários desacordos saem todos', () => {
    expect(Object.keys(erros({ prazoAnos: 1, avaliacaoAmbiental: true, dataDeliberacao: '2027-04-01' }, { prazoAnos: 2, avaliacaoAmbiental: false }))).toEqual([
      PRAZO,
      AVALIACAO,
      DATA,
    ]);
  });
});

describe('errosPorCaminho', () => {
  it('junta as mensagens do mesmo caminho', () => {
    expect(
      errosPorCaminho([
        { path: 'informacaoBase.deliberacao.dataDeliberacao', erro: 'Depois do texto do ato.' },
        { path: 'informacaoBase.textoAto.prazoAnos', erro: 'Prazo diferente.' },
        { path: 'informacaoBase.deliberacao.dataDeliberacao', erro: 'Depois da data limite.' },
      ]),
    ).toEqual({
      'informacaoBase.deliberacao.dataDeliberacao': 'Depois do texto do ato. Depois da data limite.',
      'informacaoBase.textoAto.prazoAnos': 'Prazo diferente.',
    });
  });
});

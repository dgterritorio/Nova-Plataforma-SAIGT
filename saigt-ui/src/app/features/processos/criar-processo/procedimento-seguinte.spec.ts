import { describe, expect, it } from 'vitest';
import { ProcedimentoDisponivel } from '../../../models/instrumento.model';
import { procedimentoSeguinte } from './procedimento-seguinte';

function procedimento(overrides: Partial<ProcedimentoDisponivel> = {}): ProcedimentoDisponivel {
  return {
    instrumentoProcedimentoConfigId: 215,
    tipoProcedimento: 'ABERTURA_DISCUSSAO_PUBLICA',
    nomeProcedimento: 'Abertura do período de discussão pública',
    ordem: 2,
    obrigatorio: true,
    atoPontual: false,
    prazoDias: null,
    serieDr: null,
    formulario: null,
    documentosExigidos: [],
    tiposAtoDisponiveis: [],
    tentativaEmCurso: null,
    ...overrides,
  };
}

const discussaoPublica = procedimento();
const publicacao = procedimento({ instrumentoProcedimentoConfigId: 216, tipoProcedimento: 'PUBLICACAO_ALTERACAO', nomeProcedimento: 'Publicação da Alteração', ordem: 3 });
const retificacao = procedimento({ instrumentoProcedimentoConfigId: 230, tipoProcedimento: 'RETIFICACAO', nomeProcedimento: 'Retificação do ato e anexos', ordem: 9, atoPontual: true });

describe('procedimentoSeguinte', () => {
  it('sem Procedimentos, não há seguinte', () => {
    expect(procedimentoSeguinte([])).toBeNull();
  });

  it('só com atos pontuais, não há seguinte', () => {
    expect(procedimentoSeguinte([retificacao, { ...retificacao, instrumentoProcedimentoConfigId: 231, ordem: 10 }])).toBeNull();
  });

  it('sem tentativa em curso, é o de maior ordem, venha por que ordem vier', () => {
    expect(procedimentoSeguinte([publicacao, discussaoPublica])).toBe(publicacao);
    expect(procedimentoSeguinte([discussaoPublica, publicacao])).toBe(publicacao);
  });

  it('com um Procedimento com tentativa em curso, é esse, mesmo com ordem menor', () => {
    const comRascunho = { ...discussaoPublica, tentativaEmCurso: { processoId: 'processo-1', numeroTentativa: 2, estado: 'EM_SUBMISSAO' } };

    expect(procedimentoSeguinte([comRascunho, publicacao])).toBe(comRascunho);
  });

  it('um ato pontual de ordem maior nunca é o seguinte', () => {
    expect(procedimentoSeguinte([discussaoPublica, retificacao])).toBe(discussaoPublica);
  });

  it('um ato pontual com rascunho não passa à frente do procedimento seguinte', () => {
    const retificacaoComRascunho = { ...retificacao, tentativaEmCurso: { processoId: 'processo-9', numeroTentativa: 1, estado: 'EM_SUBMISSAO' } };

    expect(procedimentoSeguinte([discussaoPublica, retificacaoComRascunho])).toBe(discussaoPublica);
  });
});

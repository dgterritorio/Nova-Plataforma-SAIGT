import { describe, expect, it } from 'vitest';
import {
  AVALIADORES,
  BYTES_POR_MB,
  ESTADOS_DINAMICA,
  ROTULOS_AVALIADOR,
  ROTULOS_SERIE_DR,
  RegraConfig,
  TIPOS_ENTIDADE,
  formatarLimite,
  pedidoDaRegra,
} from './configuracao.model';

describe('configuracao.model', () => {
  it.each([
    [5368709120, '5 GB'],
    [1610612736, '1,5 GB'],
    [1073741824, '1 GB'],
    [500 * BYTES_POR_MB, '500 MB'],
    [Math.round(2.25 * BYTES_POR_MB), '2,3 MB'],
    [null, 'Sem limite'],
  ])('formatarLimite(%s) = "%s"', (bytes, esperado) => {
    expect(formatarLimite(bytes)).toBe(esperado);
  });

  it('5120 MB são 5368709120 bytes', () => {
    expect(5120 * BYTES_POR_MB).toBe(5368709120);
  });

  it('séries do DR por extenso e os 11 tipos de entidade do contrato', () => {
    expect(Object.values(ROTULOS_SERIE_DR)).toEqual(['1.ª série', '2.ª série — parte H', '2.ª série — parte C']);
    expect(TIPOS_ENTIDADE).toEqual(['CM', 'CCDR_DR', 'AM', 'CIM', 'PCM', 'SEC_ESTADO', 'DGT', 'ICNF', 'APA', 'DGPC', 'IP']);
  });
  it('os quatro avaliadores do contrato, com o rótulo do editor, e os três estados de Dinâmica', () => {
    expect(AVALIADORES).toEqual(['OCORRENCIA_EM_ESTADO', 'OCORRENCIA_NAO_EXISTE', 'INSTRUMENTO_EM_VIGOR', 'DINAMICA_BLOQUEADA_LEGADO']);
    expect(AVALIADORES.map((avaliador) => ROTULOS_AVALIADOR[avaliador])).toEqual([
      'Existe ocorrência num estado',
      'Não existe ocorrência',
      'O Instrumento está em vigor',
      'Dinâmica em curso no sistema anterior',
    ]);
    expect(ESTADOS_DINAMICA).toEqual(['EM_CURSO', 'TERMINADA', 'CADUCADA']);
  });

  it('pedidoDaRegra reenvia a regra gravada com todas as condições (ativas e inativas) e os seus ids', () => {
    const regra: RegraConfig = {
      id: 301,
      instrumentoDinamicaConfigId: 101,
      instrumentoProcedimentoConfigId: null,
      substituiSequenciamento: false,
      operador: 'AND',
      descricao: 'Elaboração é única',
      ativo: true,
      condicoes: [
        { id: 401, avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'ELABORACAO' }, grupo: 1, ativo: true },
        { id: 402, avaliador: 'INSTRUMENTO_EM_VIGOR', parametros: {}, grupo: 2, ativo: false },
      ],
    };
    const pedido = pedidoDaRegra(regra);
    expect(pedido).toEqual({
      instrumentoDinamicaConfigId: 101,
      instrumentoProcedimentoConfigId: null,
      substituiSequenciamento: false,
      operador: 'AND',
      descricao: 'Elaboração é única',
      ativo: true,
      condicoes: [
        { id: 401, avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'ELABORACAO' }, grupo: 1, ativo: true },
        { id: 402, avaliador: 'INSTRUMENTO_EM_VIGOR', parametros: {}, grupo: 2, ativo: false },
      ],
    });
    expect(pedido).not.toHaveProperty('id');
    expect(pedido.condicoes[0].parametros).not.toBe(regra.condicoes[0].parametros);
  });
});

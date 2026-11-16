import { describe, expect, it } from 'vitest';
import type { EstadoNotificacao, TipoNotificacao } from '../../models/notificacao.model';
import {
  apresentacaoTipo,
  ehNaoLida,
  formatarDataHora,
  formatarDataHoraTabela,
  formatarDia,
  normalizarPesquisa,
  remetente,
  seloEstado,
  tamanhoEmKb,
  temConversacao,
  tempoRelativo,
} from './apresentacao-notificacao';

const PROCESSO = '7d1c2c1e-0000-4000-8000-000000000001';

describe('apresentacaoTipo', () => {
  it.each([
    ['ALERTA', null, 'Alerta', 'erro', 'triangulo'],
    ['TEMPORARIA', null, 'Temporária', 'cinza', 'relogio'],
    ['ESTATICA', null, 'Estática', 'info', 'megafone'],
    ['RESPOSTA_OBRIGATORIA', null, 'Resp. Obrig.', 'violeta', 'balao'],
    ['PEDIDO_APOIO', PROCESSO, 'Apoio Proc.', 'ciano', 'documento'],
    ['PEDIDO_APOIO', null, 'Apoio Geral', 'sucesso', 'balao'],
    ['REGULARIZACAO_PROCEDIMENTO', null, 'Regularização', 'aviso', 'documento'],
  ] as const)('%s (processo %s) → «%s», tom %s, ícone %s', (tipo, processoId, rotulo, tom, icone) => {
    expect(apresentacaoTipo({ tipo, processoId })).toEqual({ rotulo, tom, icone });
  });
});

describe('seloEstado', () => {
  it.each<[TipoNotificacao, EstadoNotificacao, string | null, string | null]>([
    ['TEMPORARIA', 'ATIVA', 'Não lida', 'info'],
    ['ALERTA', 'ATIVA', 'Não lida', 'info'],
    ['RESPOSTA_OBRIGATORIA', 'ATIVA', 'Pend. Resposta', 'aviso'],
    ['REGULARIZACAO_PROCEDIMENTO', 'ATIVA', 'Pend. Resposta', 'aviso'],
    ['PEDIDO_APOIO', 'ATIVA', 'Pend. Resposta', 'aviso'],
    ['ESTATICA', 'ATIVA', null, null],
    ['TEMPORARIA', 'LIDA', 'Lida', 'cinza'],
    ['PEDIDO_APOIO', 'RESOLVIDA', 'Resolvida', 'sucesso'],
    ['ESTATICA', 'DESATIVADA', 'Desativada', 'cinza'],
    ['ESTATICA', 'REMOVIDA', 'Removida', 'cinza'],
  ])('%s em %s → %s', (tipo, estado, rotulo, tom) => {
    const selo = seloEstado({ tipo, estado, processoId: null });
    expect(selo).toEqual(rotulo === null ? null : { rotulo, tom });
  });
});

describe('ehNaoLida e temConversacao', () => {
  it('só TEMPORARIA e ALERTA ativas ficam por ler', () => {
    expect(ehNaoLida({ tipo: 'TEMPORARIA', estado: 'ATIVA' })).toBe(true);
    expect(ehNaoLida({ tipo: 'ALERTA', estado: 'ATIVA' })).toBe(true);
    expect(ehNaoLida({ tipo: 'ALERTA', estado: 'LIDA' })).toBe(false);
    expect(ehNaoLida({ tipo: 'PEDIDO_APOIO', estado: 'ATIVA' })).toBe(false);
  });

  it.each<[TipoNotificacao, boolean]>([
    ['RESPOSTA_OBRIGATORIA', true],
    ['PEDIDO_APOIO', true],
    ['REGULARIZACAO_PROCEDIMENTO', true],
    ['ESTATICA', false],
    ['TEMPORARIA', false],
    ['ALERTA', false],
  ])('%s tem conversação: %s', (tipo, esperado) => {
    expect(temConversacao(tipo)).toBe(esperado);
  });
});

describe('textos', () => {
  it('sem remetente é a plataforma', () => {
    expect(remetente(null)).toBe('SAIGT — Sistema');
    expect(remetente('Ana Teixeira')).toBe('Ana Teixeira');
  });

  it('formata na hora de Lisboa, também no horário de verão', () => {
    expect(formatarDataHora('2026-07-20T10:00:00Z')).toBe('2026-07-20 11:00');
    expect(formatarDataHora('2026-01-05T23:30:00Z')).toBe('2026-01-05 23:30');
    expect(formatarDia('2026-08-19T23:30:00Z')).toBe('2026-08-20');
  });

  it('formata para a tabela do backoffice em dd/MM/yyyy HH:mm na hora de Lisboa', () => {
    expect(formatarDataHoraTabela('2026-07-20T10:05:00Z')).toBe('20/07/2026 11:05');
    expect(formatarDataHoraTabela('2026-12-31T23:30:00Z')).toBe('31/12/2026 23:30');
  });

  it('tempo relativo: agora, minutos, horas e depois a data', () => {
    const agora = new Date('2026-11-11T12:00:00Z');
    expect(tempoRelativo('2026-11-11T11:59:40Z', agora)).toBe('agora');
    expect(tempoRelativo('2026-11-11T11:58:00Z', agora)).toBe('2 min atrás');
    expect(tempoRelativo('2026-11-11T09:00:00Z', agora)).toBe('3h atrás');
    expect(tempoRelativo('2026-11-09T09:00:00Z', agora)).toBe('2026-11-09');
  });

  it('tamanho em KB, nunca 0', () => {
    expect(tamanhoEmKb(911_360)).toBe('890 KB');
    expect(tamanhoEmKb(100)).toBe('1 KB');
  });

  it('pesquisa sem maiúsculas nem acentos', () => {
    expect(normalizarPesquisa('Câmara de SETÚBAL')).toBe('camara de setubal');
  });
});

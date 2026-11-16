import { describe, expect, it } from 'vitest';
import { formatarNomeConcelho } from './nome-concelho';

describe('formatarNomeConcelho', () => {
  it.each([
    ['SINTRA', 'Sintra'],
    ['VILA NOVA DE GAIA', 'Vila Nova de Gaia'],
    ['MONTEMOR-O-NOVO', 'Montemor-o-Novo'],
    ['PÓVOA DE VARZIM', 'Póvoa de Varzim'],
    ['ÉVORA', 'Évora'],
    ['A DOS CUNHADOS', 'A dos Cunhados'],
    ['Lisboa', 'Lisboa'],
    ['VILA REAL DE SANTO ANTÓNIO', 'Vila Real de Santo António'],
    ['CALHETA (MADEIRA)', 'Calheta (Madeira)'],
  ])('%s → %s', (doSnit, esperado) => {
    expect(formatarNomeConcelho(doSnit)).toBe(esperado);
  });

  it('sem nome devolve texto vazio', () => {
    expect(formatarNomeConcelho(null)).toBe('');
    expect(formatarNomeConcelho(undefined)).toBe('');
  });
});

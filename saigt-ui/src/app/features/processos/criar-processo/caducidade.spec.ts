import { describe, expect, it } from 'vitest';
import { diaEmLisboa, lerDataCaducidade, situacaoCaducidade, textoPrazo } from './caducidade';

const HOJE = new Date('2026-11-06T10:00:00Z');

describe('situacaoCaducidade', () => {
  it('CADUCADA com data devolve a data', () => {
    expect(situacaoCaducidade('2026-10-01', 'CADUCADA', HOJE)).toEqual({
      tipo: 'CADUCADA',
      data: { iso: '2026-10-01', texto: '01/10/2026' },
    });
  });

  it('CADUCADA sem data devolve data nula', () => {
    expect(situacaoCaducidade(null, 'CADUCADA', HOJE)).toEqual({ tipo: 'CADUCADA', data: null });
  });

  it('EM_CURSO sem data não tem nada a mostrar', () => {
    expect(situacaoCaducidade(null, 'EM_CURSO', HOJE)).toEqual({ tipo: 'SEM_DATA' });
  });

  it('EM_CURSO exatamente 1 mês antes da data já é PROXIMA', () => {
    expect(situacaoCaducidade('2026-12-06', 'EM_CURSO', HOJE)).toEqual({
      tipo: 'PROXIMA',
      data: { iso: '2026-12-06', texto: '06/12/2026' },
      dias: 30,
    });
  });

  it('EM_CURSO 1 dia antes da data é PROXIMA com 1 dia', () => {
    expect(situacaoCaducidade('2026-11-07', 'EM_CURSO', HOJE)).toMatchObject({ tipo: 'PROXIMA', dias: 1 });
  });

  it('EM_CURSO mais de 1 mês antes da data é NORMAL', () => {
    expect(situacaoCaducidade('2026-12-07', 'EM_CURSO', HOJE)).toEqual({
      tipo: 'NORMAL',
      data: { iso: '2026-12-07', texto: '07/12/2026' },
    });
  });

  it('EM_CURSO no próprio dia dá 0 dias', () => {
    expect(situacaoCaducidade('2026-11-06', 'EM_CURSO', HOJE)).toMatchObject({ tipo: 'PROXIMA', dias: 0 });
  });

  it('EM_CURSO com a data já passada (o job ainda não correu) fica em 0 dias', () => {
    expect(situacaoCaducidade('2026-11-01', 'EM_CURSO', HOJE)).toMatchObject({ tipo: 'PROXIMA', dias: 0 });
  });

  it.each([['TERMINADA'], [null]])('estado %s não tem nada a mostrar, mesmo com data', (estado) => {
    expect(situacaoCaducidade('2026-11-20', estado, HOJE)).toEqual({ tipo: 'SEM_DATA' });
  });

  it('ao subtrair 1 mês a 31/03 o limiar é 28/02', () => {
    expect(situacaoCaducidade('2027-03-31', 'EM_CURSO', new Date('2027-02-27T12:00:00Z')).tipo).toBe('NORMAL');
    expect(situacaoCaducidade('2027-03-31', 'EM_CURSO', new Date('2027-02-28T12:00:00Z'))).toMatchObject({ tipo: 'PROXIMA', dias: 31 });
  });

  it('em ano bissexto o limiar de 31/03 é 29/02', () => {
    expect(situacaoCaducidade('2028-03-31', 'EM_CURSO', new Date('2028-02-28T12:00:00Z')).tipo).toBe('NORMAL');
    expect(situacaoCaducidade('2028-03-31', 'EM_CURSO', new Date('2028-02-29T12:00:00Z')).tipo).toBe('PROXIMA');
  });

  it('o limiar de janeiro recua para dezembro do ano anterior', () => {
    expect(situacaoCaducidade('2027-01-15', 'EM_CURSO', new Date('2026-12-15T12:00:00Z')).tipo).toBe('PROXIMA');
    expect(situacaoCaducidade('2027-01-15', 'EM_CURSO', new Date('2026-12-14T12:00:00Z')).tipo).toBe('NORMAL');
  });

  it('hoje conta em Europe/Lisbon: às 23:30 UTC de um dia de verão já é o dia seguinte', () => {
    const noiteDeVerao = new Date('2026-07-14T23:30:00Z');

    expect(diaEmLisboa(noiteDeVerao)).toBe('2026-07-15');
    expect(situacaoCaducidade('2026-07-15', 'EM_CURSO', noiteDeVerao)).toMatchObject({ tipo: 'PROXIMA', dias: 0 });
    expect(situacaoCaducidade('2026-08-15', 'EM_CURSO', noiteDeVerao).tipo).toBe('PROXIMA');
  });

  it('a data não muda de dia por conversão UTC', () => {
    expect(lerDataCaducidade('2026-01-01')).toEqual({ iso: '2026-01-01', texto: '01/01/2026' });
    expect(lerDataCaducidade(null)).toBeNull();
  });
});

describe('textoPrazo', () => {
  it.each([
    [0, 'Caduca hoje'],
    [1, 'Falta 1 dia'],
    [2, 'Faltam 2 dias'],
    [10, 'Faltam 10 dias'],
  ])('%i dias → "%s"', (dias, texto) => {
    expect(textoPrazo(dias)).toBe(texto);
  });
});

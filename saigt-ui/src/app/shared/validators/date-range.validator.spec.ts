import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { dateRangeValidator } from './date-range.validator';

describe('dateRangeValidator', () => {
  const validador = dateRangeValidator('1900-01-01', '2099-12-31');

  it('aceita uma data dentro do intervalo', () => {
    expect(validador(new FormControl('1950-06-01'))).toBeNull();
  });

  it('rejeita uma data anterior ao mínimo', () => {
    expect(validador(new FormControl('1899-12-31'))).toEqual({
      dateRange: { min: '1900-01-01', max: '2099-12-31', valor: '1899-12-31' },
    });
  });

  it('rejeita uma data posterior ao máximo', () => {
    expect(validador(new FormControl('2100-01-01'))).toEqual({
      dateRange: { min: '1900-01-01', max: '2099-12-31', valor: '2100-01-01' },
    });
  });

  it('aceita as datas-limite do intervalo', () => {
    expect(validador(new FormControl('1900-01-01'))).toBeNull();
    expect(validador(new FormControl('2099-12-31'))).toBeNull();
  });

  it('aceita um valor vazio — campo de filtro opcional', () => {
    expect(validador(new FormControl(''))).toBeNull();
  });
});

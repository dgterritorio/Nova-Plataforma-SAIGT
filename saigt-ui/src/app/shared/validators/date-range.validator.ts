import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Compara strings ISO `YYYY-MM-DD` lexicograficamente: equivale à ordem cronológica, sem `Date` nem risco de fuso horário. */
export function dateRangeValidator(min: string, max: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const valor = control.value;
    if (!valor) {
      return null;
    }
    return valor < min || valor > max ? { dateRange: { min, max, valor } } : null;
  };
}

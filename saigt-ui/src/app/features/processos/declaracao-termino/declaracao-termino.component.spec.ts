import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { DinamicaATerminar } from '../../../models/processo.model';
import { DeclaracaoTerminoComponent } from './declaracao-termino.component';

describe('DeclaracaoTerminoComponent', () => {
  let fixture: ComponentFixture<DeclaracaoTerminoComponent>;

  const DINAMICAS: DinamicaATerminar[] = [
    { dinamicaId: 'd1', numeroDinamica: 'PDM-0004', tipoDinamica: 'Alteração', descritivo: 'Alteração ao regulamento' },
    { dinamicaId: 'd2', numeroDinamica: null, tipoDinamica: 'Revisão', descritivo: 'Revisão do PDM' },
  ];

  function criar(entradas: { declarado?: boolean; erro?: string | null } = {}): void {
    fixture = TestBed.createComponent(DeclaracaoTerminoComponent);
    fixture.componentRef.setInput('dinamicas', DINAMICAS);
    fixture.componentRef.setInput('declarado', entradas.declarado ?? false);
    fixture.componentRef.setInput('erro', entradas.erro ?? null);
    fixture.detectChanges();
  }

  function elemento(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function caixa(): HTMLInputElement {
    return elemento().querySelector('#declaracao-termino') as HTMLInputElement;
  }

  it('agrupa a declaração num fieldset com legenda e lista as Dinâmicas a terminar', () => {
    criar();

    const fieldset = elemento().querySelector('fieldset') as HTMLFieldSetElement;
    expect(fieldset.querySelector('legend')?.textContent?.trim()).toBe('Declaração de término');
    const lista = fieldset.querySelector('ul') as HTMLUListElement;
    expect(lista.getAttribute('aria-labelledby')).toBe('declaracao-termino-lista');
    expect(elemento().querySelector('#declaracao-termino-lista')).not.toBeNull();
    expect(Array.from(lista.querySelectorAll('li')).map((li) => li.textContent?.trim())).toEqual([
      'PDM-0004 · Alteração · Alteração ao regulamento',
      'Revisão · Revisão do PDM',
    ]);
  });

  it('tem uma caixa obrigatória, com rótulo, desmarcada por omissão', () => {
    criar();

    expect(caixa().type).toBe('checkbox');
    expect(caixa().required).toBe(true);
    expect(caixa().checked).toBe(false);
    expect(caixa().closest('label')?.textContent?.trim()).toBe(
      'Declaro que a revogação do plano termina também os procedimentos em curso acima indicados.',
    );
    expect(caixa().getAttribute('aria-invalid')).toBeNull();
    expect(caixa().getAttribute('aria-describedby')).toBeNull();
  });

  it('reflete o valor de `declarado`', () => {
    criar({ declarado: true });

    expect(caixa().checked).toBe(true);
  });

  it('emite `alterado` com o estado da caixa ao marcar e desmarcar', () => {
    criar();
    const emitidos: boolean[] = [];
    fixture.componentInstance.alterado.subscribe((valor) => emitidos.push(valor));

    caixa().click();
    caixa().click();

    expect(emitidos).toEqual([true, false]);
  });

  it('com erro, liga a mensagem à caixa, anuncia-a e põe-lhe o foco', () => {
    criar({ erro: 'Declare que a revogação termina os procedimentos em curso.' });

    const erro = elemento().querySelector('#erro-declaracao-termino') as HTMLElement;
    expect(erro.textContent?.trim()).toBe('Declare que a revogação termina os procedimentos em curso.');
    expect(erro.getAttribute('role')).toBe('alert');
    expect(caixa().getAttribute('aria-describedby')).toBe('erro-declaracao-termino');
    expect(caixa().getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(caixa());
  });
});

import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PassosComponent } from './passos.component';

const PASSOS = [{ etiqueta: 'Identificar' }, { etiqueta: 'Formulário' }, { etiqueta: 'Publicar' }, { etiqueta: 'Recibo' }];

function criar(atual: number): HTMLElement {
  const fixture = TestBed.createComponent(PassosComponent);
  fixture.componentRef.setInput('passos', PASSOS);
  fixture.componentRef.setInput('atual', atual);
  fixture.detectChanges();
  return fixture.nativeElement;
}

describe('PassosComponent', () => {
  it('com atual=2: os passos 0 e 1 concluídos com check, o 2 atual com aria-current="step", o resto pendente', () => {
    const el = criar(2);
    const passos = [...el.querySelectorAll('li.passo')];

    expect(passos.map((p) => p.className.match(/passo-(\w+)/)?.[1])).toEqual(['concluido', 'concluido', 'atual', 'pendente']);
    expect(passos[0].querySelector('svg')).not.toBeNull();
    expect(passos[1].querySelector('svg')).not.toBeNull();
    expect(passos.map((p) => p.getAttribute('aria-current'))).toEqual([null, null, 'step', null]);
    expect(passos[2].textContent).toContain('3');
    expect(passos[3].textContent).toContain('4');
  });

  it('é uma lista ordenada; os concluídos dizem-no ao leitor de ecrã', () => {
    const el = criar(1);

    expect(el.querySelector('ol')).not.toBeNull();
    expect(el.querySelector('li.passo-concluido .visualmente-oculto')?.textContent).toBe('(concluído)');
    expect(el.querySelectorAll('.visualmente-oculto')).toHaveLength(1);
  });

  it('os conectores depois de um passo concluído ficam marcados', () => {
    const el = criar(2);

    const conectores = [...el.querySelectorAll('li.conector')];
    expect(conectores).toHaveLength(3);
    expect(conectores.map((c) => c.classList.contains('conector-concluido'))).toEqual([true, true, false]);
    expect(conectores.every((c) => c.getAttribute('aria-hidden') === 'true')).toBe(true);
  });

  it('com atual=0 não há concluídos', () => {
    const el = criar(0);

    expect(el.querySelectorAll('li.passo-concluido')).toHaveLength(0);
    expect(el.querySelector('li.passo-atual')?.textContent).toContain('Identificar');
  });
});

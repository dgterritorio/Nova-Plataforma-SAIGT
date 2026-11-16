import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CampoPesquisaComponent, ESPERA_PESQUISA_MS } from './campo-pesquisa.component';

@Component({
  imports: [CampoPesquisaComponent, ReactiveFormsModule],
  template: `<app-campo-pesquisa placeholder="Pesquisar por designação…" [formControl]="controlo" (pesquisar)="termos.push($event)" />`,
})
class AnfitriaoComponent {
  readonly controlo = new FormControl('');
  readonly termos: string[] = [];
}

describe('CampoPesquisaComponent', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function criar() {
    const fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.detectChanges();
    const entrada: HTMLInputElement = fixture.nativeElement.querySelector('input');
    return { fixture, entrada, anfitriao: fixture.componentInstance };
  }

  function escrever(entrada: HTMLInputElement, texto: string): void {
    entrada.value = texto;
    entrada.dispatchEvent(new Event('input'));
  }

  it('é uma caixa de pesquisa com o placeholder como nome acessível', () => {
    const { entrada } = criar();
    expect(entrada.getAttribute('role')).toBe('searchbox');
    expect(entrada.getAttribute('aria-label')).toBe('Pesquisar por designação…');
    expect(entrada.placeholder).toBe('Pesquisar por designação…');
  });

  it('emite pesquisar 300ms depois da última tecla, uma só vez', () => {
    const { entrada, anfitriao } = criar();

    escrever(entrada, 'Sin');
    vi.advanceTimersByTime(ESPERA_PESQUISA_MS - 1);
    escrever(entrada, 'Sintra');
    vi.advanceTimersByTime(ESPERA_PESQUISA_MS - 1);
    expect(anfitriao.termos).toEqual([]);

    vi.advanceTimersByTime(1);
    expect(anfitriao.termos).toEqual(['Sintra']);
  });

  it('liga-se ao FormControl nos dois sentidos', () => {
    const { fixture, entrada, anfitriao } = criar();

    escrever(entrada, 'Cascais');
    expect(anfitriao.controlo.value).toBe('Cascais');

    anfitriao.controlo.setValue('Lisboa');
    fixture.detectChanges();
    expect(entrada.value).toBe('Lisboa');
  });

  it('fica desativado com o FormControl', () => {
    const { fixture, entrada, anfitriao } = criar();
    anfitriao.controlo.disable();
    fixture.detectChanges();
    expect(entrada.disabled).toBe(true);
  });
});

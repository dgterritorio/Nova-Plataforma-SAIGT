import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { InstrumentoAbrangido } from '../../../models/processo.model';
import { FormularioSuspensaoCcdrComponent } from './formulario-suspensao-ccdr.component';

@Component({
  standalone: true,
  imports: [FormularioSuspensaoCcdrComponent],
  template: `
    <app-formulario-suspensao-ccdr
      [tipoInstrumento]="tipoInstrumento()"
      [tipoProcedimento]="tipoProcedimento()"
      [designacao]="designacao()"
      [instrumentosAbrangidos]="instrumentos()"
      [nomesTipos]="nomesTipos()"
      [seccaoVisivel]="visivel()"
    >
      <p class="cabecalho-projetado">Informação Geral</p>
    </app-formulario-suspensao-ccdr>
  `,
})
class AnfitriaoComponent {
  readonly tipoInstrumento = signal<string | null>('Plano Diretor Municipal');
  readonly tipoProcedimento = signal<string | null>('Suspensão (CCDR)');
  readonly designacao = signal<string | null>('Suspensão dos PDM da Grande Lisboa');
  readonly instrumentos = signal<InstrumentoAbrangido[] | null>([
    { id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' },
    { id: 'i2', designacao: 'PDM de Cascais', tipoInstrumento: 'PDM' },
    { id: 'i3', designacao: 'PU da Ericeira', tipoInstrumento: 'PU' },
  ]);
  readonly nomesTipos = signal<Record<string, string>>({ PDM: 'Plano Diretor Municipal', PU: 'Plano de Urbanização' });
  readonly visivel = signal(true);
}

describe('FormularioSuspensaoCcdrComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  beforeEach(() => {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.detectChanges();
  });

  function valor(classe: string): string {
    return (fixture.nativeElement.querySelector(`dd.${classe}`) as HTMLElement).textContent!.trim();
  }

  it('mostra, pela ordem da EF, o Tipo de instrumento dos planos associados, o Tipo de procedimento e a Designação, só de leitura', () => {
    const titulos = Array.from(fixture.nativeElement.querySelectorAll('.cartao-suspensao dt') as NodeListOf<HTMLElement>).map((dt) =>
      dt.textContent!.trim(),
    );
    expect(titulos).toEqual(['Tipo de instrumento', 'Tipo de procedimento', 'Designação']);
    expect(valor('tipo-instrumento')).toBe('Plano Diretor Municipal, Plano de Urbanização');
    expect(valor('tipo-procedimento')).toBe('Suspensão (CCDR)');
    expect(valor('designacao')).toBe('Suspensão dos PDM da Grande Lisboa');
    expect(fixture.nativeElement.querySelector('.cartao-suspensao input, .cartao-suspensao select')).toBeNull();
  });

  it('sem planos associados mostra o tipo da Dinâmica; sem nome do tipo fica o código', () => {
    fixture.componentInstance.instrumentos.set([]);
    fixture.detectChanges();
    expect(valor('tipo-instrumento')).toBe('Plano Diretor Municipal');

    fixture.componentInstance.instrumentos.set([{ id: 'i4', designacao: 'PPIM do Oeste', tipoInstrumento: 'PPIM' }]);
    fixture.detectChanges();
    expect(valor('tipo-instrumento')).toBe('PPIM');
  });

  it('a secção tem o título como nome acessível e projeta o cabeçalho comum por baixo', () => {
    const seccao = fixture.nativeElement.querySelector('section.cartao-suspensao') as HTMLElement;
    const titulo = fixture.nativeElement.querySelector('#' + seccao.getAttribute('aria-labelledby')) as HTMLElement;
    expect(titulo.tagName).toBe('H2');
    expect(titulo.textContent!.trim()).toBe('Suspensão da iniciativa da CCDR');
    expect(fixture.nativeElement.querySelector('.cabecalho-projetado')).not.toBeNull();
  });

  it('no assistente fora da página do cabeçalho esconde a secção e mantém o cabeçalho projetado', () => {
    fixture.componentInstance.visivel.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('section.cartao-suspensao')).toBeNull();
    expect(fixture.nativeElement.querySelector('.cabecalho-projetado')).not.toBeNull();
  });
});

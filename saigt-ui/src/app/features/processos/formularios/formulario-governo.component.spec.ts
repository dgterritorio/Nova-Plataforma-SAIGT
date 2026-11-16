import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { InstrumentoAbrangido } from '../../../models/processo.model';
import { FormularioGovernoComponent, VarianteFormularioGoverno } from './formulario-governo.component';

@Component({
  standalone: true,
  imports: [FormularioGovernoComponent],
  template: `
    <app-formulario-governo
      [variante]="variante()"
      [tipoInstrumento]="tipoInstrumento()"
      [tipoProcedimento]="tipoProcedimento()"
      [designacao]="designacao()"
      [instrumentosAbrangidos]="instrumentos()"
      [nomesTipos]="nomesTipos()"
      [seccaoVisivel]="visivel()"
    >
      <p class="cabecalho-projetado">Informação Geral</p>
    </app-formulario-governo>
  `,
})
class AnfitriaoComponent {
  readonly variante = signal<VarianteFormularioGoverno>('PUBLICACAO');
  readonly tipoInstrumento = signal<string | null>('Plano Diretor Municipal');
  readonly tipoProcedimento = signal<string | null>('Publicação da suspensão (Governo)');
  readonly designacao = signal<string | null>('Suspensão dos PDM da Grande Lisboa');
  readonly instrumentos = signal<InstrumentoAbrangido[] | null>([
    { id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' },
    { id: 'i2', designacao: 'PDM de Cascais', tipoInstrumento: 'PDM' },
    { id: 'i3', designacao: 'PROT do Oeste', tipoInstrumento: 'PROT' },
  ]);
  readonly nomesTipos = signal<Record<string, string>>({ PDM: 'Plano Diretor Municipal', PROT: 'Programa Regional' });
  readonly visivel = signal(true);
}

describe('FormularioGovernoComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  beforeEach(() => {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.detectChanges();
  });

  function valor(classe: string): string {
    return (fixture.nativeElement.querySelector(`dd.${classe}`) as HTMLElement).textContent!.trim();
  }

  function titulo(): HTMLElement {
    const seccao = fixture.nativeElement.querySelector('section.cartao-governo') as HTMLElement;
    return fixture.nativeElement.querySelector('#' + seccao.getAttribute('aria-labelledby')) as HTMLElement;
  }

  it('mostra, pela ordem da EF, o Tipo de instrumento, o Tipo de procedimento, a Designação e os Instrumentos associados, só de leitura', () => {
    const titulos = Array.from(fixture.nativeElement.querySelectorAll('.cartao-governo dt') as NodeListOf<HTMLElement>).map((dt) =>
      dt.textContent!.trim(),
    );
    expect(titulos).toEqual(['Tipo de instrumento', 'Tipo de procedimento', 'Designação', 'Instrumentos associados']);
    expect(valor('tipo-instrumento')).toBe('Plano Diretor Municipal, Programa Regional');
    expect(valor('tipo-procedimento')).toBe('Publicação da suspensão (Governo)');
    expect(valor('designacao')).toBe('Suspensão dos PDM da Grande Lisboa');
    const itens = Array.from(fixture.nativeElement.querySelectorAll('.instrumentos-associados li') as NodeListOf<HTMLElement>).map(
      (li) => li.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(itens).toEqual(['PDM de Sintra (Plano Diretor Municipal)', 'PDM de Cascais (Plano Diretor Municipal)', 'PROT do Oeste (Programa Regional)']);
    expect(fixture.nativeElement.querySelector('.cartao-governo input, .cartao-governo select, .cartao-governo textarea')).toBeNull();
  });

  it('sem Instrumentos associados mostra o tipo da Dinâmica e o aviso; sem nome do tipo fica o código', () => {
    fixture.componentInstance.instrumentos.set([]);
    fixture.detectChanges();
    expect(valor('tipo-instrumento')).toBe('Plano Diretor Municipal');
    expect(valor('instrumentos-associados')).toBe('Sem instrumentos associados.');

    fixture.componentInstance.instrumentos.set([{ id: 'i4', designacao: 'PPIM do Oeste', tipoInstrumento: 'PPIM' }]);
    fixture.detectChanges();
    expect(valor('tipo-instrumento')).toBe('PPIM');
  });

  it('a secção tem o título como nome acessível, conforme a variante, e projeta o cabeçalho comum por baixo', () => {
    expect(titulo().tagName).toBe('H2');
    expect(titulo().textContent!.trim()).toBe('Suspensão / Medidas Preventivas (Governo)');
    expect(fixture.nativeElement.querySelector('.cabecalho-projetado')).not.toBeNull();

    fixture.componentInstance.variante.set('PRORROGACAO');
    fixture.componentInstance.tipoProcedimento.set('Prorrogação das medidas preventivas (Governo)');
    fixture.detectChanges();
    expect(titulo().textContent!.trim()).toBe('Prorrogação da Suspensão / Medidas Preventivas (Governo)');
    expect(valor('tipo-procedimento')).toBe('Prorrogação das medidas preventivas (Governo)');
  });

  it('no assistente fora da página do cabeçalho esconde a secção e mantém o cabeçalho projetado', () => {
    fixture.componentInstance.visivel.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('section.cartao-governo')).toBeNull();
    expect(fixture.nativeElement.querySelector('.cabecalho-projetado')).not.toBeNull();
  });
});

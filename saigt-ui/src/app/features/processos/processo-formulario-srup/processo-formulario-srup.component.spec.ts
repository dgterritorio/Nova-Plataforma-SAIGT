import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ProcessoFormularioSrupComponent } from './processo-formulario-srup.component';

@Component({
  standalone: true,
  imports: [ProcessoFormularioSrupComponent],
  template: `
    <app-processo-formulario-srup [tipoInstrumento]="tipoInstrumento()" [dinamica]="dinamica()" [servidaoVisivel]="visivel()">
      <p class="cabecalho-projetado">Informação Geral</p>
    </app-processo-formulario-srup>
  `,
})
class AnfitriaoComponent {
  readonly tipoInstrumento = signal<string | null>('Servidão de teste');
  readonly dinamica = signal<string | null>('Delimitação');
  readonly visivel = signal(true);
}

describe('ProcessoFormularioSrupComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  function criar(alterar: (anfitriao: AnfitriaoComponent) => void = () => undefined): void {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    alterar(fixture.componentInstance);
    fixture.detectChanges();
  }

  const seccao = (): HTMLElement | null => fixture.nativeElement.querySelector('section.cartao-servidao');
  const valores = (): string[] => Array.from(seccao()!.querySelectorAll('dd')).map((dd) => (dd as HTMLElement).textContent!.trim());

  it('mostra a secção Servidão com o tipo de instrumento e a Dinâmica como texto, sem campos', () => {
    criar();

    const titulo = seccao()!.querySelector('h2')!;
    expect(titulo.textContent!.trim()).toBe('Servidão');
    expect(seccao()!.getAttribute('aria-labelledby')).toBe(titulo.id);
    expect(Array.from(seccao()!.querySelectorAll('dt')).map((dt) => (dt as HTMLElement).textContent!.trim())).toEqual([
      'Tipo de instrumento',
      'Dinâmica',
    ]);
    expect(valores()).toEqual(['Servidão de teste', 'Delimitação']);
    expect(seccao()!.querySelector('input, select, textarea, [disabled], [readonly]')).toBeNull();
  });

  it('sem valores mostra um travessão', () => {
    criar((anfitriao) => {
      anfitriao.tipoInstrumento.set(null);
      anfitriao.dinamica.set('');
    });

    expect(valores()).toEqual(['—', '—']);
  });

  it('projeta o cabeçalho comum depois da secção Servidão', () => {
    criar();

    const anfitriao: HTMLElement = fixture.nativeElement.querySelector('app-processo-formulario-srup');
    const projetado = anfitriao.querySelector('.cabecalho-projetado')!;
    expect(projetado.textContent).toBe('Informação Geral');
    expect(seccao()!.compareDocumentPosition(projetado) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('com a secção escondida continua a mostrar o cabeçalho projetado', () => {
    criar((anfitriao) => anfitriao.visivel.set(false));

    expect(seccao()).toBeNull();
    expect(fixture.nativeElement.querySelector('.cabecalho-projetado')).not.toBeNull();
  });

  it('os identificadores do título não se repetem entre instâncias', () => {
    criar();
    const primeiro = seccao()!.querySelector('h2')!.id;
    criar();

    expect(seccao()!.querySelector('h2')!.id).not.toBe(primeiro);
  });
});

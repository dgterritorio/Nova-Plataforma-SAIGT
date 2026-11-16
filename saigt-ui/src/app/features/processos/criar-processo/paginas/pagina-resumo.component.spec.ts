import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PaginaResumoComponent } from './pagina-resumo.component';

describe('PaginaResumoComponent', () => {
  let fixture: ComponentFixture<PaginaResumoComponent>;

  function criar(entradas: Record<string, unknown>): void {
    fixture = TestBed.createComponent(PaginaResumoComponent);
    for (const [nome, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(nome, valor);
    }
    fixture.detectChanges();
  }

  function linhaDestino(): HTMLElement | null {
    return fixture.nativeElement.querySelector('.destino-processo');
  }

  it('mostra a linha do destino nos dados do processo', () => {
    criar({ destino: 'SO_DEPOSITO' });

    expect(linhaDestino()!.querySelector('dt')!.textContent).toBe('Destino');
    expect(linhaDestino()!.querySelector('dd')!.textContent).toBe('Só depósito');
  });

  it('sem destino (processo ainda não lido) não mostra a linha', () => {
    criar({ destino: null });

    expect(linhaDestino()).toBeNull();
  });

  it('a confirmação fala só de depósito quando o destino é «Só depósito»', () => {
    criar({ destino: 'SO_DEPOSITO' });

    const texto = (fixture.nativeElement.querySelector('.caixa-confirmacao') as HTMLElement).textContent!.trim();
    expect(texto).toBe('Confirmo o conteúdo do processo e submeto-o para depósito na DG Território.');
  });

  it('a confirmação mantém a publicação no Diário da República nos restantes destinos', () => {
    for (const destino of ['PUBLICACAO_DEPOSITO', null]) {
      criar({ destino });

      const texto = (fixture.nativeElement.querySelector('.caixa-confirmacao') as HTMLElement).textContent!.trim();
      expect(texto).toBe(
        'Confirmo o conteúdo do processo e submeto-o para publicação no Diário da República e depósito na DG Território.',
      );
    }
  });

  it('num ato pontual mostra a linha «Ato a que respeita»; fora deles não', () => {
    criar({ atoReferido: 'processo n.º 00005 (Decisão de início)' });

    const linha = fixture.nativeElement.querySelector('.ato-referido') as HTMLElement;
    expect(linha.querySelector('dt')!.textContent).toBe('Ato a que respeita');
    expect(linha.querySelector('dd')!.textContent).toBe('processo n.º 00005 (Decisão de início)');

    fixture.componentRef.setInput('atoReferido', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.ato-referido')).toBeNull();
  });
});

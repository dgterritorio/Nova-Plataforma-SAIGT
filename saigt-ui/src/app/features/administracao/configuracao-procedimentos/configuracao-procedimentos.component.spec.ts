import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfiguracaoProcedimentosComponent } from './configuracao-procedimentos.component';

const VAZIA = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe('ConfiguracaoProcedimentosComponent', () => {
  function criar() {
    const servico = {
      listarTiposInstrumento: vi.fn().mockReturnValue(of(VAZIA)),
      listarTiposDinamica: vi.fn().mockReturnValue(of(VAZIA)),
      listarDinamicasConfig: vi.fn().mockReturnValue(of(VAZIA)),
      listarTiposProcedimento: vi.fn().mockReturnValue(of(VAZIA)),
      listarProcedimentosConfig: vi.fn().mockReturnValue(of(VAZIA)),
      listarTiposAto: vi.fn().mockReturnValue(of(VAZIA)),
      listarRedesDefesa: vi.fn().mockReturnValue(of(VAZIA)),
      listarRegras: vi.fn().mockReturnValue(of(VAZIA)),
    };
    TestBed.configureTestingModule({
      imports: [ConfiguracaoProcedimentosComponent],
      providers: [
        provideRouter([]),
        { provide: ConfiguracaoService, useValue: servico },
        {
          provide: AuthService,
          useValue: {
            perfilInterno: vi.fn().mockReturnValue('MASTER'),
            nome: vi.fn().mockReturnValue('Master Fictícia'),
            origem: vi.fn().mockReturnValue('AD_DGT'),
            entidade: vi.fn().mockReturnValue(null),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ConfiguracaoProcedimentosComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, servico };
  }

  it.each([
    [2, 'app-configuracao-procedimentos-dinamica', 'listarTiposProcedimento'],
    [3, 'app-configuracao-regras', 'listarTiposDinamica'],
    [4, 'app-configuracao-itens-documentais', 'listarTiposInstrumento'],
    [5, 'app-configuracao-atos-redes', 'listarTiposAto'],
  ] as const)('o separador %s monta %s', (indice, seletor, metodo) => {
    const { fixture, el, servico } = criar();

    (el.querySelectorAll<HTMLButtonElement>('[role="tab"]')[indice]).click();
    fixture.detectChanges();

    expect(el.querySelector(seletor)).not.toBeNull();
    expect(el.querySelector('app-configuracao-tipos')).toBeNull();
    expect(servico[metodo]).toHaveBeenCalled();
  });

  it('tem um só h1, o subtítulo e os seis separadores, com Tipos ativo', () => {
    const { el } = criar();

    expect(el.querySelectorAll('h1')).toHaveLength(1);
    expect(el.querySelector('h1')?.textContent).toContain('Configuração de Procedimentos');
    expect(el.querySelector('.subtitulo')?.textContent).toContain('Tipos, Dinâmicas, Procedimentos, permissões e regras, sem desenvolvimento técnico');
    const separadores = [...el.querySelectorAll('[role="tab"]')];
    expect(separadores.map((s) => s.textContent?.trim())).toEqual(['Tipos', 'Dinâmicas', 'Procedimentos', 'Regras', 'Itens documentais', 'Tipos de ato e redes']);
    expect(separadores[0].getAttribute('aria-selected')).toBe('true');
    expect(el.querySelector('app-configuracao-tipos')).not.toBeNull();
    expect(el.querySelector('app-configuracao-dinamicas')).toBeNull();
    const painel = el.querySelector('[role="tabpanel"]')!;
    expect(painel.getAttribute('aria-labelledby')).toBe(separadores[0].id);
  });

  it('a ação Itens documentais de um Procedimento abre esse separador já com o Procedimento escolhido', () => {
    const { fixture, el, servico } = criar();
    (el.querySelectorAll<HTMLButtonElement>('[role="tab"]')[2]).click();
    fixture.detectChanges();

    fixture.componentInstance.abrirItensDocumentais({ tipoInstrumentoId: 1, dinamicaConfigId: 101, procedimentoConfigId: 502 });
    fixture.detectChanges();

    const separadores = el.querySelectorAll('[role="tab"]');
    expect(separadores[4].getAttribute('aria-selected')).toBe('true');
    expect(el.querySelector('app-configuracao-itens-documentais')).not.toBeNull();
    expect(el.querySelector('app-configuracao-procedimentos-dinamica')).toBeNull();
    expect(servico.listarTiposInstrumento).toHaveBeenCalled();
  });

  it('depois da ação Itens documentais o foco passa para o separador aberto; abri-lo depois à mão já não pré-escolhe', async () => {
    const { fixture, el } = criar();
    (el.querySelectorAll<HTMLButtonElement>('[role="tab"]')[2]).click();
    fixture.detectChanges();

    fixture.componentInstance.abrirItensDocumentais({ tipoInstrumentoId: 1, dinamicaConfigId: 101, procedimentoConfigId: 502 });
    fixture.detectChanges();
    await new Promise((resolver) => setTimeout(resolver));
    const separadores = el.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    expect(document.activeElement).toBe(separadores[4]);

    separadores[0].click();
    fixture.detectChanges();
    separadores[4].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selecaoItens()).toBeNull();
  });

  it('ao escolher Dinâmicas, monta esse separador e desmonta o dos tipos', () => {
    const { fixture, el, servico } = criar();

    (el.querySelectorAll<HTMLButtonElement>('[role="tab"]')[1]).click();
    fixture.detectChanges();

    expect(el.querySelector('app-configuracao-dinamicas')).not.toBeNull();
    expect(el.querySelector('app-configuracao-tipos')).toBeNull();
    expect(servico.listarTiposDinamica).toHaveBeenCalledWith({ ativo: true }, 0, 100);
    expect(el.querySelector('[role="tabpanel"]')?.getAttribute('aria-labelledby')).toBe(el.querySelectorAll('[role="tab"]')[1].id);
  });
});

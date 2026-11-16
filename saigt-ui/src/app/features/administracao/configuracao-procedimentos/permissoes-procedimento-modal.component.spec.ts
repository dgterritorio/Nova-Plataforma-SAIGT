import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { PermissaoConfig, ProcedimentoConfig, TipoEntidade } from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { PermissoesProcedimentoModalComponent } from './permissoes-procedimento-modal.component';

const DECISAO_ALTERACAO_PU: ProcedimentoConfig = {
  id: 3207,
  instrumentoDinamicaConfigId: 3103,
  tipoProcedimento: { id: 1, codigo: 'DECISAO_INICIO', nome: 'Decisão de início' },
  ordem: 1,
  obrigatorio: true,
  serieDr: 'SEGUNDA_H',
  tamanhoMaximoProcesso: 5368709120,
  repetivel: false,
  atoPontual: false,
  ativo: true,
  formulario: 'INFORMACAO_GERAL',
  emUso: false,
  visivelNaDinamica: false,
};

function permissao(id: number, tipoEntidade: TipoEntidade, ativo = true): PermissaoConfig {
  return { id, instrumentoProcedimentoConfigId: 3207, tipoProcedimento: DECISAO_ALTERACAO_PU.tipoProcedimento, tipoEntidade, ativo };
}

function pagina<T>(linhas: T[]): PageResponse<T> {
  return { content: linhas, page: 0, size: 100, totalElements: linhas.length, totalPages: 1 };
}

@Component({
  imports: [PermissoesProcedimentoModalComponent],
  template: `<app-permissoes-procedimento-modal [aberto]="procedimento() !== null" [procedimento]="procedimento()" (fechar)="fechou = true" />`,
})
class AnfitriaoComponent {
  readonly procedimento = signal<ProcedimentoConfig | null>(DECISAO_ALTERACAO_PU);
  fechou = false;
}

describe('PermissoesProcedimentoModalComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;
  let servico: Record<string, ReturnType<typeof vi.fn>>;

  function criar(linhas: PermissaoConfig[] = [permissao(3613, 'CM'), permissao(3614, 'DGT')]): void {
    servico = {
      listarPermissoes: vi.fn().mockReturnValue(of(pagina(linhas))),
      criarPermissao: vi.fn(),
      atualizarPermissao: vi.fn(),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ConfiguracaoService, useValue: servico }] });
    fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.detectChanges();
  }

  const dialogo = (): HTMLElement => document.querySelector('[role="dialog"]') as HTMLElement;
  const caixa = (tipo: string): HTMLInputElement =>
    [...dialogo().querySelectorAll('label')].find((l) => l.textContent?.trim() === tipo)!.querySelector('input')!;
  const marcadas = (): string[] =>
    [...dialogo().querySelectorAll<HTMLInputElement>('input[type="checkbox"]')]
      .filter((c) => c.checked)
      .map((c) => c.closest('label')!.textContent!.trim());
  const botao = (texto: string): HTMLButtonElement =>
    [...dialogo().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === texto)!;

  function clicar(alvo: HTMLElement): void {
    alvo.click();
    fixture.detectChanges();
  }

  it('abre com o título, lista as permissões do Procedimento e marca só CM e DGT, num fieldset com legend', () => {
    criar();

    expect(servico['listarPermissoes']).toHaveBeenCalledWith({ instrumentoProcedimentoConfigId: 3207 }, 0, 100);
    expect(dialogo().querySelector('h2')?.textContent).toContain('Permissões — Decisão de início');
    expect(dialogo().textContent).toContain('Tipos de entidade que podem criar, continuar e corrigir este Procedimento');
    const fieldset = dialogo().querySelector('fieldset')!;
    expect(fieldset.querySelector('legend')?.textContent?.trim()).toBe('Tipos de entidade');
    expect(fieldset.querySelectorAll('input[type="checkbox"]')).toHaveLength(11);
    for (const input of fieldset.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')) {
      expect(document.querySelector(`label[for="${input.id}"]`)).not.toBeNull();
    }
    expect(marcadas()).toEqual(['CM', 'DGT']);
    expect(dialogo().textContent).toContain('Os perfis internos Master e Técnico validam e enviam pelo perfil, não por esta lista.');
  });

  it('uma linha inativa fica desmarcada', () => {
    criar([permissao(3613, 'CM', false), permissao(3614, 'DGT')]);
    expect(marcadas()).toEqual(['DGT']);
  });

  it('marcar CCDR_DR e desmarcar CM: POST para CCDR_DR, depois PUT com ativo=false para CM, e mostra o estado novo', () => {
    criar();
    servico['criarPermissao'].mockReturnValue(of(permissao(9001, 'CCDR_DR')));
    servico['atualizarPermissao'].mockReturnValue(of(permissao(3613, 'CM', false)));
    servico['listarPermissoes'].mockReturnValue(of(pagina([permissao(3613, 'CM', false), permissao(3614, 'DGT'), permissao(9001, 'CCDR_DR')])));

    clicar(caixa('CCDR_DR'));
    clicar(caixa('CM'));
    clicar(botao('Guardar'));

    expect(servico['criarPermissao']).toHaveBeenCalledExactlyOnceWith({ instrumentoProcedimentoConfigId: 3207, tipoEntidade: 'CCDR_DR', ativo: true });
    expect(servico['atualizarPermissao']).toHaveBeenCalledExactlyOnceWith(3613, {
      instrumentoProcedimentoConfigId: 3207,
      tipoEntidade: 'CM',
      ativo: false,
    });
    expect(servico['criarPermissao'].mock.invocationCallOrder[0]).toBeLessThan(servico['atualizarPermissao'].mock.invocationCallOrder[0]);
    expect(servico['listarPermissoes']).toHaveBeenCalledTimes(2);
    expect(dialogo()).not.toBeNull();
    expect(marcadas()).toEqual(['CCDR_DR', 'DGT']);
    expect(dialogo().querySelector('app-alerta.alerta-sucesso')?.textContent).toContain('Permissões gravadas.');
    expect(fixture.componentInstance.fechou).toBe(false);
  });

  it('o segundo pedido falha: o modal fica aberto, diz que CM não foi gravado com a mensagem do servidor, e CCDR_DR fica gravado', () => {
    criar();
    servico['criarPermissao'].mockReturnValue(of(permissao(9001, 'CCDR_DR')));
    servico['atualizarPermissao'].mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'Sessão sem perfil Master.', 't', null)));
    servico['listarPermissoes'].mockReturnValue(of(pagina([permissao(3613, 'CM'), permissao(3614, 'DGT'), permissao(9001, 'CCDR_DR')])));

    clicar(caixa('CCDR_DR'));
    clicar(caixa('CM'));
    clicar(botao('Guardar'));

    const alerta = dialogo().querySelector('app-alerta[role="alert"]')!;
    expect(alerta.textContent).toContain('Não foi possível gravar: CM.');
    expect(alerta.textContent).toContain('Sessão sem perfil Master.');
    expect(alerta.textContent).not.toContain('CCDR_DR');
    expect(dialogo().querySelector('fieldset')?.getAttribute('aria-describedby')).toBe(alerta.id);
    expect(marcadas()).toEqual(['CCDR_DR', 'DGT']);
    expect(caixa('CM').checked).toBe(false);

    servico['atualizarPermissao'].mockReturnValue(of(permissao(3613, 'CM', false)));
    servico['listarPermissoes'].mockReturnValue(of(pagina([permissao(3613, 'CM', false), permissao(3614, 'DGT'), permissao(9001, 'CCDR_DR')])));
    clicar(botao('Guardar'));
    expect(servico['criarPermissao']).toHaveBeenCalledTimes(1);
    expect(servico['atualizarPermissao']).toHaveBeenLastCalledWith(3613, { instrumentoProcedimentoConfigId: 3207, tipoEntidade: 'CM', ativo: false });
    expect(dialogo().querySelector('app-alerta[role="alert"]')).toBeNull();
  });

  it('remarcar uma linha inativa faz PUT com ativo=true; sem alterações não há pedidos', () => {
    criar([permissao(3613, 'CM', false)]);
    clicar(botao('Guardar'));
    expect(servico['criarPermissao']).not.toHaveBeenCalled();
    expect(servico['atualizarPermissao']).not.toHaveBeenCalled();
    expect(dialogo().textContent).toContain('Sem alterações para gravar.');

    servico['atualizarPermissao'].mockReturnValue(of(permissao(3613, 'CM')));
    clicar(caixa('CM'));
    clicar(botao('Guardar'));
    expect(servico['atualizarPermissao']).toHaveBeenCalledWith(3613, { instrumentoProcedimentoConfigId: 3207, tipoEntidade: 'CM', ativo: true });
  });

  it('Fechar emite fechar', () => {
    criar();
    clicar(botao('Fechar'));
    expect(fixture.componentInstance.fechou).toBe(true);
  });
});

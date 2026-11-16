import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { WorkflowsComponent } from './workflows.component';
import { Workflow } from '../../../models/workflow.model';
import { WorkflowService } from '../../../services/workflow.service';

describe('WorkflowsComponent', () => {
  let fixture: ComponentFixture<WorkflowsComponent>;

  const pdm = { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO' as const, entidadeInstrutoraObrigatoria: true };
  const pu = { id: 3, codigo: 'PU', nome: 'Plano de Urbanização', categoria: 'PLANO' as const, entidadeInstrutoraObrigatoria: true };

  const workflows: Workflow[] = [
    {
      id: 101,
      titulo: 'PDM — Elaboração',
      tipoInstrumento: pdm,
      ativo: true,
      numeroPassos: 2,
      processosAtivos: 5,
      passos: [
        { nome: 'Decisão de Início', ordem: 1, obrigatorio: true, serieDr: 'SEGUNDA_H', prazoDias: null, tipoPrazo: null, atores: ['ENTIDADE', 'DGT'] },
        { nome: 'Discussão Pública', ordem: 2, obrigatorio: false, serieDr: 'SEGUNDA_H', prazoDias: 30, tipoPrazo: 'LEGAL', atores: ['DGT'] },
      ],
    },
    { id: 113, titulo: 'PDM — Suspensão', tipoInstrumento: pdm, ativo: false, numeroPassos: 0, processosAtivos: 0, passos: [] },
    {
      id: 3101,
      titulo: 'PU — Elaboração',
      tipoInstrumento: pu,
      ativo: true,
      numeroPassos: 1,
      processosAtivos: 0,
      passos: [{ nome: 'Decisão de elaborar', ordem: 1, obrigatorio: true, serieDr: 'SEGUNDA_H', prazoDias: null, tipoPrazo: null, atores: ['ENTIDADE', 'DGT'] }],
    },
  ];

  function textos(seletor: string): (string | undefined)[] {
    return [...fixture.nativeElement.querySelectorAll(seletor)].map((el: Element) => el.textContent?.replace(/\s+/g, ' ').trim());
  }

  function cabecalhoGrupo(codigo: string): HTMLButtonElement {
    return [...fixture.nativeElement.querySelectorAll('.cabecalho-grupo')].find(
      (el: Element) => el.querySelector('.codigo-grupo')?.textContent?.trim() === codigo,
    ) as HTMLButtonElement;
  }

  function criarComponente(workflowServiceMock: { listar: ReturnType<typeof vi.fn> }): void {
    TestBed.configureTestingModule({
      imports: [WorkflowsComponent],
      providers: [provideRouter([]), { provide: WorkflowService, useValue: workflowServiceMock }],
    });

    fixture = TestBed.createComponent(WorkflowsComponent);
    fixture.detectChanges();
  }

  it('mostra os workflows devolvidos por GET /api/v1/workflows, com estado e resumo', () => {
    criarComponente({ listar: vi.fn().mockReturnValue(of(workflows)) });

    const titulos = [...fixture.nativeElement.querySelectorAll('.titulo-workflow')].map((el: Element) => el.textContent?.trim());
    expect(titulos).toEqual(['PDM — Elaboração', 'PDM — Suspensão']);

    const badges = [...fixture.nativeElement.querySelectorAll('.badge')].map((el: Element) => el.textContent?.trim());
    expect(badges).toEqual(['Ativo', 'Inativo']);

    const resumos = [...fixture.nativeElement.querySelectorAll('.resumo-workflow')].map((el: Element) =>
      el.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(resumos[0]).toBe('2 passos · 5 processos ativos');
    expect(resumos[1]).toBe('Sem procedimentos configurados · 0 processos ativos');
  });

  it('agrupa os workflows por tipo de instrumento, com nome e contagem, e abre só o grupo do primeiro', () => {
    criarComponente({ listar: vi.fn().mockReturnValue(of(workflows)) });

    expect(textos('.codigo-grupo')).toEqual(['PDM', 'PU']);
    expect(textos('.nome-grupo')).toEqual(['Plano Diretor Municipal', 'Plano de Urbanização']);
    expect(textos('.contagem-grupo')).toEqual(['2', '1']);
    expect(cabecalhoGrupo('PDM').getAttribute('aria-expanded')).toBe('true');
    expect(cabecalhoGrupo('PU').getAttribute('aria-expanded')).toBe('false');
    expect(textos('.titulo-workflow')).toEqual(['PDM — Elaboração', 'PDM — Suspensão']);
  });

  it('abre e fecha um grupo ao carregar no cabeçalho, sem mudar o workflow selecionado', () => {
    criarComponente({ listar: vi.fn().mockReturnValue(of(workflows)) });

    cabecalhoGrupo('PU').click();
    fixture.detectChanges();
    expect(textos('.titulo-workflow')).toEqual(['PDM — Elaboração', 'PDM — Suspensão', 'PU — Elaboração']);
    expect(cabecalhoGrupo('PU').getAttribute('aria-expanded')).toBe('true');

    cabecalhoGrupo('PDM').click();
    fixture.detectChanges();
    expect(textos('.titulo-workflow')).toEqual(['PU — Elaboração']);
    expect(fixture.nativeElement.querySelector('.cabecalho-detalhe h2').textContent.trim()).toBe('PDM — Elaboração');
  });

  it('seleciona o primeiro workflow por omissão e mostra os seus passos, com os dois atores e o prazo quando existe', () => {
    criarComponente({ listar: vi.fn().mockReturnValue(of(workflows)) });

    expect(fixture.nativeElement.querySelector('.cabecalho-detalhe h2').textContent.trim()).toBe('PDM — Elaboração');

    const detalhes = [...fixture.nativeElement.querySelectorAll('.detalhe-passo')].map((el: Element) =>
      el.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(detalhes[0]).toBe('Atores: Entidade, DGT');
    expect(detalhes[1]).toBe('Atores: DGT · Prazo: 30d');
  });

  it('mostra "Sem procedimentos configurados." no detalhe de um workflow sem passos', () => {
    criarComponente({ listar: vi.fn().mockReturnValue(of(workflows)) });

    const itens: HTMLButtonElement[] = fixture.nativeElement.querySelectorAll('.item-workflow');
    itens[1].click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.cabecalho-detalhe h2').textContent.trim()).toBe('PDM — Suspensão');
    expect(fixture.nativeElement.querySelector('.passo-vazio').textContent.trim()).toBe('Sem procedimentos configurados.');
  });

  it('mostra o banner de erro quando o pedido falha', () => {
    criarComponente({ listar: vi.fn().mockReturnValue(throwError(() => ({ codigo: 'SEM_PERMISSAO', mensagem: 'erro' }))) });

    expect(fixture.nativeElement.querySelector('.error-banner')).not.toBeNull();
  });

  it('nunca mostra um botão de criar workflow, acrescentar passo ou guardar', () => {
    criarComponente({ listar: vi.fn().mockReturnValue(of(workflows)) });

    const conteudo = fixture.nativeElement.querySelector('.conteudo');
    expect(conteudo.textContent).not.toContain('Novo workflow');
    expect(conteudo.textContent).not.toContain('Adicionar passo');
    expect(conteudo.textContent).not.toContain('Guardar');
    // Os únicos botões abrem/fecham os grupos ou escolhem o workflow a ver.
    expect(conteudo.querySelector('button:not(.cabecalho-grupo):not(.item-workflow)')).toBeNull();
  });

  it('cada workflow é um botão dentro de um item da lista, e o escolhido tem aria-current', () => {
    criarComponente({ listar: vi.fn().mockReturnValue(of(workflows)) });

    const itens = [...fixture.nativeElement.querySelectorAll('.lista-workflows > li > button.item-workflow')] as HTMLButtonElement[];
    expect(itens.length).toBeGreaterThan(1);
    expect(itens.map((b) => b.getAttribute('aria-current'))).toEqual(['true', ...itens.slice(1).map(() => null)]);
    expect(fixture.nativeElement.querySelector('[role="button"]')).toBeNull();
  });
});

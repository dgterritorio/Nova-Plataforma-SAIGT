import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EstatisticasProcesso, Recusa } from '../../../models/processo.model';
import { DescargaService } from '../../../services/descarga.service';
import { ProcessoService } from '../../../services/processo.service';
import { ProcessoRecusasComponent } from './processo-recusas.component';

const RECUSA_DGT: Recusa = {
  sequencia: 1,
  origem: 'DGT',
  submissao: 1,
  data: '2026-06-25T15:51:05Z',
  notasGerais: 'Rever o regulamento.',
  motivoIncm: [],
  itens: [
    {
      tipo: 'DOCUMENTO',
      documentoId: 'd1',
      nomeFicheiro: 'alteracao-regulamento-v1.pdf',
      separador: 'Alteração ao Regulamento',
      elemento: null,
      categoria: { codigo: 'FORMATO', descricao: 'Formato' },
      motivo: { codigo: 'NAO_EDITAVEL', descricao: 'Ficheiro em formato não editável' },
      notas: 'Solicitar novo carregamento em PDF/A.',
    },
    {
      tipo: 'DOCUMENTO',
      documentoId: 'd2',
      nomeFicheiro: 'nota.pdf',
      separador: null,
      elemento: null,
      categoria: null,
      motivo: null,
      notas: 'Sem assinatura.',
    },
    {
      tipo: 'INFORMACAO_BASE',
      documentoId: null,
      nomeFicheiro: null,
      separador: null,
      elemento: 'DELIBERACAO',
      categoria: { codigo: 'CONTEUDO', descricao: 'Conteúdo' },
      motivo: { codigo: 'SEM_DATA', descricao: 'Falta a data' },
      notas: 'Indicar a data da deliberação.',
    },
  ],
};

const RECUSA_INCM: Recusa = {
  sequencia: 2,
  origem: 'INCM',
  submissao: 2,
  data: '2026-07-02T09:10:00Z',
  notasGerais: null,
  motivoIncm: ['ImageURLList cannot be empty.', 'ActDate inválida.'],
  itens: [],
};

describe('ProcessoRecusasComponent', () => {
  let fixture: ComponentFixture<ProcessoRecusasComponent>;
  let estatisticas: ReturnType<typeof vi.fn>;
  let descarregar: ReturnType<typeof vi.fn>;

  function abrir(resposta: EstatisticasProcesso | Subject<EstatisticasProcesso>): HTMLElement {
    estatisticas.mockReturnValue(resposta instanceof Subject ? resposta : of(resposta));
    fixture = TestBed.createComponent(ProcessoRecusasComponent);
    fixture.componentRef.setInput('processoId', 'p1');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    document.body.appendChild(el);
    return el;
  }

  const separadores = (el: HTMLElement) => [...el.querySelectorAll<HTMLButtonElement>('[role="tab"]')];
  const painel = (el: HTMLElement) => el.querySelector<HTMLElement>('.painel')!;

  beforeEach(() => {
    estatisticas = vi.fn();
    descarregar = vi.fn();
    TestBed.configureTestingModule({
      imports: [ProcessoRecusasComponent],
      providers: [
        { provide: ProcessoService, useValue: { estatisticas } },
        { provide: DescargaService, useValue: { descarregar, erro: () => null } },
      ],
    });
  });

  it('pede as recusas do processo recebido e mostra que está a carregar', () => {
    const pedido = new Subject<EstatisticasProcesso>();
    const el = abrir(pedido);

    expect(estatisticas).toHaveBeenCalledWith('p1');
    expect(el.querySelector('[role="status"]')?.textContent).toContain('A carregar as recusas');

    pedido.next({ numeroSubmissoes: 1, recusas: [RECUSA_DGT] });
    fixture.detectChanges();
    expect(el.textContent).not.toContain('A carregar as recusas');
  });

  it('sem recusas não mostra nada', () => {
    const el = abrir({ numeroSubmissoes: 1, recusas: [] });

    expect(el.textContent?.trim()).toBe('');
  });

  it('com erro mostra o alerta e «Tentar de novo» volta a pedir', () => {
    estatisticas.mockReturnValueOnce(throwError(() => ({ codigo: 'ERRO_INTERNO' })));
    fixture = TestBed.createComponent(ProcessoRecusasComponent);
    fixture.componentRef.setInput('processoId', 'p1');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível carregar as recusas');
    estatisticas.mockReturnValue(of({ numeroSubmissoes: 1, recusas: [RECUSA_DGT] }));
    const tentar = [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Tentar de novo')!;
    tentar.click();
    fixture.detectChanges();

    expect(estatisticas).toHaveBeenCalledTimes(2);
    expect(painel(el).querySelector('[data-campo="nome"]')?.textContent?.trim()).toBe('Recusa 1');
  });

  it('um separador por recusa, com a origem, e abre na mais recente', () => {
    const el = abrir({ numeroSubmissoes: 2, recusas: [RECUSA_DGT, RECUSA_INCM] });

    expect(separadores(el).map((s) => s.textContent?.trim())).toEqual(['Recusa 1 · DGT', 'Recusa 2 · INCM']);
    expect(separadores(el).map((s) => s.getAttribute('aria-selected'))).toEqual(['false', 'true']);
    expect(painel(el).getAttribute('role')).toBe('tabpanel');
    expect(painel(el).getAttribute('aria-labelledby')).toBe(separadores(el)[1].id);
    expect(painel(el).querySelector('[data-campo="origem"]')?.textContent?.trim()).toBe('INCM');
    expect(painel(el).querySelector('[data-campo="submissao"]')?.textContent).toContain('Submissão n.º 2');
  });

  it('na recusa INCM mostra as mensagens e não o quadro de itens', () => {
    const el = abrir({ numeroSubmissoes: 2, recusas: [RECUSA_DGT, RECUSA_INCM] });

    expect([...painel(el).querySelectorAll('.lista-mensagens li')].map((li) => li.textContent?.trim())).toEqual([
      'ImageURLList cannot be empty.',
      'ActDate inválida.',
    ]);
    expect(painel(el).textContent).not.toContain('Elementos Rejeitados');
    expect(painel(el).querySelector('table')).toBeNull();
  });

  it('ao escolher a recusa DGT mostra a data, as notas gerais e os elementos rejeitados', () => {
    const el = abrir({ numeroSubmissoes: 2, recusas: [RECUSA_DGT, RECUSA_INCM] });

    separadores(el)[0].click();
    fixture.detectChanges();

    expect(painel(el).querySelector('[data-campo="data"]')?.textContent).toMatch(/25\/06\/2026 \d{2}:\d{2}/);
    expect(painel(el).querySelector('[data-campo="submissao"]')?.textContent).toContain('Submissão n.º 1');
    expect(painel(el).textContent).toContain('Rever o regulamento.');
    expect(painel(el).querySelector('[data-campo="contagem"]')?.textContent?.trim()).toBe('3 elementos');
    const linhas = [...painel(el).querySelectorAll('tbody tr')].map((tr) =>
      [...tr.querySelectorAll('td')].map((td) => td.textContent?.trim()),
    );
    expect(linhas).toEqual([
      ['Alteração ao Regulamento', 'alteracao-regulamento-v1.pdf', 'Formato', 'Ficheiro em formato não editável', 'Solicitar novo carregamento em PDF/A.'],
      ['Outros', 'nota.pdf', '—', '—', 'Sem assinatura.'],
      ['Informação Base — Deliberação', '—', 'Conteúdo', 'Falta a data', 'Indicar a data da deliberação.'],
    ]);
  });

  it('as setas mudam o foco entre recusas e Enter abre a escolhida', () => {
    const el = abrir({ numeroSubmissoes: 2, recusas: [RECUSA_DGT, RECUSA_INCM] });
    separadores(el)[1].focus();

    separadores(el)[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(separadores(el)[0]);
    separadores(el)[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();

    expect(painel(el).querySelector('[data-campo="origem"]')?.textContent?.trim()).toBe('DGT');
  });

  it('com uma só recusa não mostra separadores e põe o nome da recusa no cabeçalho', () => {
    const el = abrir({ numeroSubmissoes: 1, recusas: [RECUSA_DGT] });

    expect(el.querySelector('[role="tablist"]')).toBeNull();
    expect(el.querySelector('[role="tabpanel"]')).toBeNull();
    const conteudo = el.querySelector<HTMLElement>('.painel')!;
    expect(conteudo.hasAttribute('id')).toBe(false);
    expect(conteudo.querySelector('h3[data-campo="nome"]')?.textContent?.trim()).toBe('Recusa 1');
    expect(conteudo.querySelector('[data-campo="origem"]')?.textContent?.trim()).toBe('DGT');
    expect(conteudo.querySelectorAll('tbody tr')).toHaveLength(3);
  });

  it('com várias recusas o nome fica só nos separadores', () => {
    const el = abrir({ numeroSubmissoes: 2, recusas: [RECUSA_DGT, RECUSA_INCM] });

    expect(painel(el).querySelector('[data-campo="nome"]')).toBeNull();
  });

  it('uma recusa DGT sem itens diz que não tem itens registados', () => {
    const el = abrir({ numeroSubmissoes: 1, recusas: [{ ...RECUSA_DGT, notasGerais: null, itens: [] }] });

    expect(painel(el).textContent).toContain('Esta recusa não tem itens registados.');
    expect(painel(el).querySelector('table')).toBeNull();
    expect(painel(el).textContent).not.toContain('Notas gerais');
  });

  it('o nome do ficheiro descarrega pelo ticket, com o nome no nome acessível', () => {
    const el = abrir({ numeroSubmissoes: 1, recusas: [RECUSA_DGT] });

    const ligacao = painel(el).querySelector<HTMLButtonElement>('button.ficheiro')!;
    expect(ligacao.getAttribute('aria-label')).toBe('Descarregar alteracao-regulamento-v1.pdf');
    ligacao.click();

    expect(descarregar).toHaveBeenCalledWith('p1', 'd1');
    expect(painel(el).querySelectorAll('button.ficheiro')).toHaveLength(2);
  });

  it('a tabela tem legenda e cabeçalhos de coluna', () => {
    const el = abrir({ numeroSubmissoes: 1, recusas: [RECUSA_DGT] });

    expect(painel(el).querySelector('caption')?.textContent?.trim()).toBe('Elementos rejeitados na recusa 1');
    expect([...painel(el).querySelectorAll('th')].map((th) => [th.getAttribute('scope'), th.textContent?.trim()])).toEqual([
      ['col', 'Separador'],
      ['col', 'Nome do Ficheiro'],
      ['col', 'Categoria'],
      ['col', 'Motivo'],
      ['col', 'Notas'],
    ]);
  });

  it('com várias recusas os separadores têm o rótulo da lista', () => {
    const el = abrir({ numeroSubmissoes: 2, recusas: [RECUSA_DGT, RECUSA_INCM] });

    expect(el.querySelector('[role="tablist"]')?.getAttribute('aria-label')).toBe('Recusas do processo');
  });
});

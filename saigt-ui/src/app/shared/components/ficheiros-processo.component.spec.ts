import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../core/models/error-response.model';
import { PageResponse } from '../../core/models/page-response.model';
import { CategoriaDocumento, DocumentoProcesso, EstruturaDocumental, ItemDocumental } from '../../models/documento.model';
import { DocumentoService } from '../../services/documento.service';
import { FicheirosProcessoComponent } from './ficheiros-processo.component';

function documento(parcial: Partial<DocumentoProcesso> = {}): DocumentoProcesso {
  return {
    id: 'd1',
    documentoConfigId: 1,
    nomeFicheiro: 'deliberacao.pdf',
    categoria: 'PUBLICACAO',
    extensao: 'pdf',
    origem: 'CARREGADO',
    bloqueadoEdicao: false,
    estadoValidacao: 'POR_VALIDAR',
    tamanhoBytes: 1024,
    criadoEm: '2026-10-20T09:30:00Z',
    criadoPorId: 'u1',
    criadoPorNome: 'Helena Marques',
    tags: [],
    justificacaoDesbloqueio: null,
    ...parcial,
  };
}

function pagina(content: DocumentoProcesso[], page = 0, totalPages = 1, totalElements = content.length): PageResponse<DocumentoProcesso> {
  return { content, page, size: 20, totalElements, totalPages };
}

function item(documentoConfigId: number, nome: string, categoria: CategoriaDocumento): ItemDocumental {
  return {
    documentoConfigId,
    codigo: `ITEM_${documentoConfigId}`,
    nome,
    categoria,
    tipoElemento: 'PECA_ESCRITA',
    formato: 'EDITAVEL',
    obrigatorio: false,
    extensoesAceites: ['pdf'],
    ficheiros: [],
    observacoes: null,
    pendente: false,
    obtidoDoGeoconform: false,
  };
}

const ESTRUTURA: EstruturaDocumental = {
  separadores: [
    {
      separador: 'Deliberação',
      elementoInformacaoBase: null,
      publicacao: [item(1, 'Deliberação', 'PUBLICACAO'), item(3, 'Texto do Ato', 'PUBLICACAO')],
      deposito: [item(2, 'Deliberação', 'DEPOSITO')],
    },
  ],
  outros: [],
  pendencias: [],
};

describe('FicheirosProcessoComponent', () => {
  let fixture: ComponentFixture<FicheirosProcessoComponent>;
  let componente: FicheirosProcessoComponent;
  let listar: ReturnType<typeof vi.fn>;
  let estrutura: ReturnType<typeof vi.fn>;

  function abrir(): void {
    TestBed.configureTestingModule({ providers: [{ provide: DocumentoService, useValue: { listar, estrutura } }] });
    fixture = TestBed.createComponent(FicheirosProcessoComponent);
    componente = fixture.componentInstance;
    fixture.componentRef.setInput('processoId', 'p1');
    fixture.detectChanges();
  }

  function celulas(linha: number): string[] {
    const linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    return Array.from((linhas[linha] as HTMLElement).querySelectorAll('td')).map((td) => (td as HTMLElement).textContent?.trim() ?? '');
  }

  function rotulosDosSeletores(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('app-seletor label')).map((l) => (l as HTMLElement).textContent?.trim() ?? '');
  }

  function escolher(rotulo: string, valor: string): void {
    const seletores = Array.from(fixture.nativeElement.querySelectorAll('app-seletor')) as HTMLElement[];
    const seletor = seletores.find((s) => s.querySelector('label')?.textContent?.trim() === rotulo) as HTMLElement;
    const select = seletor.querySelector('select') as HTMLSelectElement;
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function botao(texto: string): HTMLButtonElement {
    const botoes = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    return botoes.find((b) => b.textContent?.trim() === texto) as HTMLButtonElement;
  }

  beforeEach(() => {
    listar = vi.fn().mockReturnValue(of(pagina([])));
    estrutura = vi.fn().mockReturnValue(of(ESTRUTURA));
  });

  it('pede a primeira página de 20 sem filtros ao abrir', () => {
    abrir();

    expect(listar).toHaveBeenCalledTimes(1);
    expect(listar).toHaveBeenCalledWith('p1', {}, 0, 20);
    expect(estrutura).toHaveBeenCalledWith('p1');
  });

  it('mostra nome, tipo, caixa, item, data de envio e validação de cada ficheiro', () => {
    listar.mockReturnValue(
      of(
        pagina([
          documento(),
          documento({ id: 'd2', nomeFicheiro: 'planta.tif', extensao: 'tif', categoria: 'DEPOSITO', documentoConfigId: null, estadoValidacao: 'VALIDADO' }),
          documento({ id: 'd3', nomeFicheiro: 'ata.pdf', categoria: 'DEPOSITO', documentoConfigId: 99, estadoValidacao: 'REJEITADO' }),
        ]),
      ),
    );
    abrir();

    expect(celulas(0)).toEqual(['deliberacao.pdf', 'PDF', 'Publicação', 'Deliberação', expect.stringMatching(/^20\/10\/2026 \d{2}:\d{2}$/), 'Por validar']);
    expect(celulas(1)).toEqual(['planta.tif', 'TIF', 'Depósito', 'Outros', expect.stringMatching(/^20\/10\/2026 \d{2}:\d{2}$/), 'Validado']);
    expect(celulas(2)[3]).toBe('—');
    expect(celulas(2)[5]).toBe('Rejeitado');
  });

  it('mostra no cabeçalho o total vindo do servidor, não o da página', () => {
    listar.mockReturnValue(of(pagina([documento()], 0, 3, 47)));
    abrir();

    expect(fixture.nativeElement.querySelector('.cartao-cabecalho').textContent).toContain('47 ficheiros');
    expect(fixture.nativeElement.querySelector('app-paginacao').textContent).toContain('Mostrando 1 de 47 ficheiros');
  });

  it('muda de página mantendo os filtros', () => {
    listar.mockReturnValue(of(pagina([documento()], 0, 3, 47)));
    abrir();
    escolher('Caixa', 'PUBLICACAO');
    listar.mockClear();

    (fixture.nativeElement.querySelector('button[aria-label="Página 2"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(listar).toHaveBeenCalledWith('p1', { categoria: 'PUBLICACAO' }, 1, 20);
  });

  it('mudar um filtro volta à primeira página', () => {
    listar.mockReturnValue(of(pagina([documento()], 0, 3, 47)));
    abrir();
    (fixture.nativeElement.querySelector('button[aria-label="Página 3"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(listar).toHaveBeenLastCalledWith('p1', {}, 2, 20);

    escolher('Validação', 'VALIDADO');

    expect(listar).toHaveBeenLastCalledWith('p1', { estadoValidacao: 'VALIDADO' }, 0, 20);
  });

  it('combina os três filtros num só pedido', () => {
    abrir();

    escolher('Caixa', 'PUBLICACAO');
    escolher('Item', '1');
    escolher('Validação', 'POR_VALIDAR');

    expect(listar).toHaveBeenLastCalledWith('p1', { categoria: 'PUBLICACAO', documentoConfigId: 1, estadoValidacao: 'POR_VALIDAR' }, 0, 20);
  });

  it('o filtro Item só lista os itens da caixa escolhida', () => {
    abrir();
    const opcoes = () => componente.opcoesItem().map((opcao) => opcao.rotulo);

    expect(opcoes()).toEqual(['Todos', 'Deliberação — Publicação', 'Texto do Ato — Publicação', 'Deliberação — Depósito']);

    escolher('Caixa', 'DEPOSITO');

    expect(opcoes()).toEqual(['Todos', 'Deliberação — Depósito']);
  });

  it('trocar a caixa larga o item que é de outra caixa', () => {
    abrir();
    escolher('Item', '1');
    expect(listar).toHaveBeenLastCalledWith('p1', { documentoConfigId: 1 }, 0, 20);

    escolher('Caixa', 'DEPOSITO');

    expect(listar).toHaveBeenLastCalledWith('p1', { categoria: 'DEPOSITO' }, 0, 20);
  });

  it('esconde o filtro Item quando a estrutura não traz itens', () => {
    estrutura.mockReturnValue(of({ separadores: [], outros: [], pendencias: [] }));
    listar.mockReturnValue(of(pagina([documento()])));
    abrir();

    expect(rotulosDosSeletores()).toEqual(['Caixa', 'Validação']);
    expect(celulas(0)[3]).toBe('—');
  });

  it('esconde o filtro Item e lista na mesma quando a estrutura falha', () => {
    estrutura.mockReturnValue(throwError(() => new ApiError('ERRO', 'Erro.', 't1', null)));
    listar.mockReturnValue(of(pagina([documento()])));
    abrir();

    expect(rotulosDosSeletores()).toEqual(['Caixa', 'Validação']);
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('Limpar filtros só está ativo com filtros e repõe o pedido sem eles', () => {
    abrir();
    expect(botao('Limpar filtros').disabled).toBe(true);

    escolher('Caixa', 'DEPOSITO');
    escolher('Validação', 'REJEITADO');
    expect(botao('Limpar filtros').disabled).toBe(false);

    botao('Limpar filtros').click();
    fixture.detectChanges();

    expect(listar).toHaveBeenLastCalledWith('p1', {}, 0, 20);
    expect(botao('Limpar filtros').disabled).toBe(true);
    expect(componente.controloCaixa.value).toBe('TODOS');
    expect(componente.controloValidacao.value).toBe('TODOS');
  });

  it('sem resultados mostra a mensagem dos filtros, e outra quando não há filtros', () => {
    abrir();
    expect(fixture.nativeElement.textContent).toContain('Sem ficheiros remetidos.');
    expect(fixture.nativeElement.querySelector('app-paginacao')).toBeNull();

    escolher('Caixa', 'DEPOSITO');

    expect(fixture.nativeElement.textContent).toContain('Sem ficheiros para os filtros indicados.');
  });

  it('mostra o erro e nenhuma tabela quando a lista não carrega', () => {
    listar.mockReturnValue(throwError(() => new ApiError('NAO_ENCONTRADO', 'Não encontrado.', 't1', null)));
    abrir();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('troca o texto técnico de uma falha de ligação por uma mensagem legível', () => {
    listar.mockReturnValue(throwError(() => new ApiError('ERRO_DESCONHECIDO', 'Http failure response for /api: 0 undefined', 'unknown', null)));
    abrir();

    const alerta = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alerta.textContent).toContain('Não foi possível carregar os ficheiros. Tente novamente.');
    expect(alerta.textContent).not.toContain('Http failure');
  });

  it('mantém a mensagem que o backend já devolve legível', () => {
    listar.mockReturnValue(throwError(() => new ApiError('INTEGRACAO_INDISPONIVEL', 'Serviço indisponível.', 't1', null)));
    abrir();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Serviço indisponível.');
  });

  it('Tentar novamente repete o mesmo pedido, com a mesma página e os mesmos filtros', () => {
    listar.mockReturnValue(of(pagina([documento()], 0, 3, 47)));
    abrir();
    escolher('Caixa', 'PUBLICACAO');
    listar.mockClear();
    listar.mockReturnValueOnce(throwError(() => new ApiError('ERRO_DESCONHECIDO', 'x', 'unknown', null)));
    (fixture.nativeElement.querySelector('button[aria-label="Página 2"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    listar.mockReturnValueOnce(of(pagina([documento({ nomeFicheiro: 'recuperado.pdf' })], 1, 3, 47)));

    botao('Tentar novamente').click();
    fixture.detectChanges();

    expect(listar).toHaveBeenLastCalledWith('p1', { categoria: 'PUBLICACAO' }, 1, 20);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('recuperado.pdf');
  });

  it('descarta a resposta de um pedido que já foi substituído por outro', () => {
    const antiga = new Subject<PageResponse<DocumentoProcesso>>();
    const recente = new Subject<PageResponse<DocumentoProcesso>>();
    listar.mockReturnValueOnce(antiga).mockReturnValueOnce(recente);
    abrir();

    escolher('Caixa', 'DEPOSITO');
    recente.next(pagina([documento({ nomeFicheiro: 'recente.pdf' })]));
    antiga.next(pagina([documento({ nomeFicheiro: 'antiga.pdf' })]));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('recente.pdf');
    expect(fixture.nativeElement.textContent).not.toContain('antiga.pdf');
  });

  it('anuncia o total e a página numa região aria-live', () => {
    listar.mockReturnValue(of(pagina([documento()], 1, 3, 47)));
    abrir();

    const regiao = fixture.nativeElement.querySelector('p.so-leitor[aria-live="polite"]') as HTMLElement;
    expect(regiao.textContent?.trim()).toBe('47 ficheiros, página 1 de 3.');
  });
});

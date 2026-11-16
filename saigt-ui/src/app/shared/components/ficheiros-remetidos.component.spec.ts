import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../core/models/error-response.model';
import { PageResponse } from '../../core/models/page-response.model';
import { DocumentoProcesso } from '../../models/documento.model';
import { DocumentoService } from '../../services/documento.service';
import { FicheirosRemetidosComponent, textoTotalFicheiros } from './ficheiros-remetidos.component';

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
  return { content, page, size: 100, totalElements, totalPages };
}

describe('FicheirosRemetidosComponent', () => {
  let fixture: ComponentFixture<FicheirosRemetidosComponent>;
  let listar: ReturnType<typeof vi.fn>;

  function abrir(): void {
    TestBed.configureTestingModule({ providers: [{ provide: DocumentoService, useValue: { listar } }] });
    fixture = TestBed.createComponent(FicheirosRemetidosComponent);
    fixture.componentRef.setInput('processoId', 'p1');
    fixture.detectChanges();
  }

  function celulas(linha: number): string[] {
    const linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    return Array.from((linhas[linha] as HTMLElement).querySelectorAll('td')).map((td) => (td as HTMLElement).textContent?.trim() ?? '');
  }

  beforeEach(() => {
    listar = vi.fn().mockReturnValue(of(pagina([])));
  });

  it('pede os ficheiros do processo ao abrir', () => {
    abrir();

    expect(listar).toHaveBeenCalledWith('p1', {}, 0, 100);
  });

  it('mostra nome, tipo em maiúsculas, data de envio e estado de cada ficheiro', () => {
    listar.mockReturnValue(of(pagina([documento(), documento({ id: 'd2', nomeFicheiro: 'planta.dwg', extensao: 'dwg', criadoEm: '2026-10-21T12:05:00Z' })])));
    abrir();

    // A hora segue o fuso do navegador: fixa-se só o dia e o formato.
    expect(celulas(0)).toEqual(['deliberacao.pdf', 'PDF', expect.stringMatching(/^20\/10\/2026 \d{2}:\d{2}$/), 'Recebido']);
    expect(celulas(1)).toEqual(['planta.dwg', 'DWG', expect.stringMatching(/^21\/10\/2026 \d{2}:\d{2}$/), 'Recebido']);
  });

  it('mostra o total no cabeçalho', () => {
    listar.mockReturnValue(of(pagina([documento(), documento({ id: 'd2' }), documento({ id: 'd3' })])));
    abrir();

    expect(fixture.nativeElement.querySelector('.cartao-cabecalho').textContent).toContain('3 ficheiros');
  });

  it('emite os ficheiros carregados', () => {
    const lista = [documento()];
    listar.mockReturnValue(of(pagina(lista)));
    TestBed.configureTestingModule({ providers: [{ provide: DocumentoService, useValue: { listar } }] });
    fixture = TestBed.createComponent(FicheirosRemetidosComponent);
    fixture.componentRef.setInput('processoId', 'p1');
    let emitido: DocumentoProcesso[] | null = null;
    fixture.componentInstance.carregados.subscribe((valor) => (emitido = valor));

    fixture.detectChanges();

    expect(emitido).toEqual(lista);
  });

  it('deixa de fora da lista, do total e do que emite os ficheiros substituídos na correção', () => {
    const substituido = documento({ id: 'd1', nomeFicheiro: 'deliberacao.pdf', estadoValidacao: 'SUBSTITUIDO' as DocumentoProcesso['estadoValidacao'] });
    const novo = documento({ id: 'd2', nomeFicheiro: 'deliberacao-corrigida.pdf' });
    const outro = documento({ id: 'd3', nomeFicheiro: 'regulamento.pdf', estadoValidacao: 'VALIDADO' });
    listar.mockReturnValue(of(pagina([substituido, novo, outro])));
    TestBed.configureTestingModule({ providers: [{ provide: DocumentoService, useValue: { listar } }] });
    fixture = TestBed.createComponent(FicheirosRemetidosComponent);
    fixture.componentRef.setInput('processoId', 'p1');
    let emitido: DocumentoProcesso[] | null = null;
    fixture.componentInstance.carregados.subscribe((valor) => (emitido = valor));

    fixture.detectChanges();

    const nomes = Array.from(fixture.nativeElement.querySelectorAll('tbody tr')).map((linha) => (linha as HTMLElement).querySelector('td')!.textContent!.trim());
    expect(nomes).toEqual(['deliberacao-corrigida.pdf', 'regulamento.pdf']);
    expect(fixture.nativeElement.querySelector('.cartao-cabecalho').textContent).toContain('2 ficheiros');
    expect(emitido).toEqual([novo, outro]);
  });

  it('junta as páginas de 100 em sequência e mostra o total das páginas todas', () => {
    const primeira = Array.from({ length: 100 }, (_, i) => documento({ id: `a${i}` }));
    const segunda = Array.from({ length: 30 }, (_, i) => documento({ id: `b${i}` }));
    listar.mockImplementation((_id: string, _filtro: unknown, page: number) =>
      of(page === 0 ? pagina(primeira, 0, 2, 130) : pagina(segunda, 1, 2, 130)),
    );
    TestBed.configureTestingModule({ providers: [{ provide: DocumentoService, useValue: { listar } }] });
    fixture = TestBed.createComponent(FicheirosRemetidosComponent);
    fixture.componentRef.setInput('processoId', 'p1');
    const emitidos: DocumentoProcesso[][] = [];
    fixture.componentInstance.carregados.subscribe((valor) => emitidos.push(valor));

    fixture.detectChanges();

    expect(listar).toHaveBeenCalledTimes(2);
    expect(listar).toHaveBeenNthCalledWith(1, 'p1', {}, 0, 100);
    expect(listar).toHaveBeenNthCalledWith(2, 'p1', {}, 1, 100);
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(130);
    expect(fixture.nativeElement.querySelector('.cartao-cabecalho').textContent).toContain('130 ficheiros');
    expect(emitidos).toHaveLength(1);
    expect(emitidos[0]).toHaveLength(130);
  });

  it('um erro na segunda página mostra o banner e nenhuma linha parcial', () => {
    listar.mockImplementation((_id: string, _filtro: unknown, page: number) =>
      page === 0
        ? of(pagina([documento()], 0, 2, 101))
        : throwError(() => new ApiError('ERRO_INTERNO', 'Erro.', 't1', null)),
    );
    TestBed.configureTestingModule({ providers: [{ provide: DocumentoService, useValue: { listar } }] });
    fixture = TestBed.createComponent(FicheirosRemetidosComponent);
    fixture.componentRef.setInput('processoId', 'p1');
    let emitido = false;
    fixture.componentInstance.carregados.subscribe(() => (emitido = true));

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(emitido).toBe(false);
  });

  it('sem ficheiros não mostra tabela', () => {
    abrir();

    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Sem ficheiros remetidos.');
    expect(fixture.nativeElement.querySelector('.cartao-cabecalho').textContent).toContain('0 ficheiros');
  });

  it('mostra o erro e nenhum total quando a lista não carrega', () => {
    listar.mockReturnValue(throwError(() => new ApiError('NAO_ENCONTRADO', 'Não encontrado.', 't1', null)));
    abrir();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(fixture.nativeElement.querySelector('.cartao-cabecalho').textContent).not.toContain('ficheiro ');
  });

  it('usa o singular para um único ficheiro', () => {
    expect(textoTotalFicheiros(1)).toBe('1 ficheiro');
    expect(textoTotalFicheiros(2)).toBe('2 ficheiros');
  });
});

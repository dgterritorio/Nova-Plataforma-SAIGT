import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ResumoRejeicaoComponent } from './resumo-rejeicao.component';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoProcesso, EstruturaDocumental } from '../../../models/documento.model';
import { ProcessoDetalhe } from '../../../models/processo.model';
import { DocumentoService } from '../../../services/documento.service';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { PlantasService } from '../../../services/plantas.service';
import { ProcessoService } from '../../../services/processo.service';

function ficheiro(id: string, nomeFicheiro: string): DocumentoProcesso {
  return {
    id,
    documentoConfigId: 1,
    nomeFicheiro,
    categoria: 'PUBLICACAO',
    extensao: 'pdf',
    origem: 'CARREGADO',
    bloqueadoEdicao: false,
    estadoValidacao: 'REJEITADO',
    tamanhoBytes: 1024,
    criadoEm: '2026-10-20T09:30:00Z',
    criadoPorId: 'u1',
    criadoPorNome: 'Helena Marques',
    tags: [],
    justificacaoDesbloqueio: null,
  };
}

const ESTRUTURA: EstruturaDocumental = {
  separadores: [
    {
      separador: 'Alteração ao Regulamento',
      elementoInformacaoBase: null,
      publicacao: [
        {
          documentoConfigId: 1,
          codigo: 'REG',
          nome: 'Regulamento',
          categoria: 'PUBLICACAO',
          tipoElemento: 'PECA_ESCRITA',
          formato: 'EDITAVEL',
          obrigatorio: true,
          extensoesAceites: ['pdf'],
          ficheiros: [ficheiro('d1', 'alteracao-regulamento-v1.pdf'), ficheiro('d2', 'anexo.pdf')],
          observacoes: null,
          pendente: false,
          obtidoDoGeoconform: false,
        },
      ],
      deposito: [],
    },
  ],
  outros: [ficheiro('d3', 'nota.pdf')],
  pendencias: [],
};

const CATEGORIAS = [
  { id: 1, tipo: 'CATEGORIA_REJEICAO_DGT', codigo: 'FORMATO', descricao: 'Formato', ordem: 1, ativo: true },
  { id: 2, tipo: 'CATEGORIA_REJEICAO_DGT', codigo: 'ANTIGA', descricao: 'Conteúdo', ordem: 2, ativo: false },
];
const MOTIVOS = [{ id: 5, tipo: 'MOTIVO_REJEICAO_DGT', codigo: 'NAO_EDITAVEL', descricao: 'Ficheiro em formato não editável', ordem: 1, ativo: true }];

function processo(parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
  return {
    id: 'p1',
    versao: 3,
    estado: 'EM_VALIDACAO_DGT',
    numeroProcesso: '19359',
    ocorrencia: 1,
    dataSubmissao: '2026-06-25T15:51:05Z',
    designacaoInstrumento: 'PDM - Alteração',
    tipoInstrumento: 'Alteração Simplificada',
    nomeProcedimento: 'Decisão que determina a elaboração do plano',
    territorio: { concelhos: [{ codigo: '1503', nome: 'Almada' }], nutsII: [] },
    acoesDisponiveis: ['validar', 'rejeitar'],
    informacaoBase: [
      { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'REJEITADO' },
      { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'VALIDADO' },
    ],
    rejeicoesEmValidacao: [
      { tipo: 'DOCUMENTO', itemId: 'd1', categoriaId: 1, motivoId: 5, notas: 'Solicitar novo carregamento em PDF/A.' },
      { tipo: 'INFORMACAO_BASE', itemId: 'ib-delib', categoriaId: 2, motivoId: null, notas: 'Falta a data.' },
      { tipo: 'DOCUMENTO', itemId: 'd3', categoriaId: null, motivoId: null, notas: 'Sem assinatura.' },
    ],
    ...parcial,
  } as ProcessoDetalhe;
}

describe('ResumoRejeicaoComponent', () => {
  let fixture: ComponentFixture<ResumoRejeicaoComponent>;
  let rejeitar: ReturnType<typeof vi.fn>;
  let estrutura: ReturnType<typeof vi.fn>;
  let listar: ReturnType<typeof vi.fn>;
  let obterPlantas: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    rejeitar = vi.fn().mockReturnValue(of({ id: 'p1', estado: 'REJEITADO_DGT' }));
    estrutura = vi.fn().mockReturnValue(of(ESTRUTURA));
    obterPlantas = vi.fn().mockReturnValue(
      of({ itens: [{ nome: 'Planta de Ordenamento', ficheiro: { id: 'pl-ord', nomeFicheiro: 'Planta_de_Ordenamento.zip' } }, { nome: 'Cartograma', ficheiro: null }] }),
    );
    listar = vi.fn((tipo: string) => of(tipo === 'CATEGORIA_REJEICAO_DGT' ? CATEGORIAS : MOTIVOS));
    TestBed.configureTestingModule({
      providers: [
        { provide: ProcessoService, useValue: { rejeitar } },
        { provide: DocumentoService, useValue: { estrutura } },
        { provide: ListaValoresService, useValue: { listar } },
        { provide: PlantasService, useValue: { obterPlantas } },
      ],
    });
  });

  function abrir(parcial: Partial<ProcessoDetalhe> = {}): void {
    fixture = TestBed.createComponent(ResumoRejeicaoComponent);
    fixture.componentRef.setInput('processo', processo(parcial));
    fixture.detectChanges();
  }

  function texto(): string {
    return fixture.nativeElement.textContent as string;
  }

  function botao(rotulo: string): HTMLButtonElement | undefined {
    return (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find(
      (b) => b.textContent?.trim() === rotulo,
    );
  }

  function clicar(rotulo: string): void {
    botao(rotulo)!.click();
    fixture.detectChanges();
  }

  function linhas(): string[][] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody tr')).map((tr) =>
      Array.from((tr as HTMLElement).querySelectorAll('td')).map((td) => td.textContent!.trim()),
    );
  }

  function dialogo(): HTMLElement | null {
    return fixture.nativeElement.querySelector('[role="dialog"]');
  }

  function recusar(): void {
    clicar('Recusar');
  }

  function escreverNotas(valor: string): void {
    const area = dialogo()!.querySelector('[data-campo="notas-gerais"] textarea') as HTMLTextAreaElement;
    area.value = valor;
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('mostra os dados do processo e os do recibo', () => {
    abrir();

    expect(texto()).toContain('PDM - Alteração');
    expect(texto()).toContain('Alteração Simplificada');
    expect(texto()).toContain('Decisão que determina a elaboração do plano');
    expect(texto()).toContain('Almada');
    expect(texto()).toContain('19359');
    expect(texto()).toMatch(/25\/06\/2026 \d{2}:51:05/);
    expect(texto()).toContain('3 ficheiros');
  });

  it('mostra o número da submissão e não a ocorrência', () => {
    abrir({ ocorrencia: 1, submissao: 2 });

    const rotulo = Array.from(fixture.nativeElement.querySelectorAll('dt') as NodeListOf<HTMLElement>).find(
      (dt) => dt.textContent!.trim() === 'Submissão do processo n.º',
    );
    expect(rotulo!.nextElementSibling!.textContent!.trim()).toBe('2');
  });

  it('lê a estrutura do processo e as duas listas de valores', () => {
    abrir();

    expect(estrutura).toHaveBeenCalledWith('p1');
    expect(listar).toHaveBeenCalledWith('CATEGORIA_REJEICAO_DGT');
    expect(listar).toHaveBeenCalledWith('MOTIVO_REJEICAO_DGT');
  });

  it('o quadro «Elementos Rejeitados» traz o separador, o ficheiro, a categoria, o motivo e as notas de cada item', () => {
    abrir();

    expect(texto()).toContain('Elementos Rejeitados');
    expect(fixture.nativeElement.querySelector('[data-campo="contagem"]').textContent.trim()).toBe('3 elementos');
    expect(linhas()).toEqual([
      ['Alteração ao Regulamento', 'alteracao-regulamento-v1.pdf', 'Formato', 'Ficheiro em formato não editável', 'Solicitar novo carregamento em PDF/A.'],
      ['Informação Base — Deliberação', '—', 'Conteúdo', '—', 'Falta a data.'],
      ['Outros', 'nota.pdf', '—', '—', 'Sem assinatura.'],
    ]);
    expect(fixture.nativeElement.querySelectorAll('tbody app-selo')).toHaveLength(2);
  });

  it('uma planta rejeitada aparece no quadro com o separador «Plantas» e o nome do ficheiro', () => {
    abrir({
      exigePlantasEMetadados: true,
      rejeicoesEmValidacao: [{ tipo: 'DOCUMENTO', itemId: 'pl-ord', categoriaId: 1, motivoId: null, notas: 'Legenda ilegível.' }],
    });

    expect(obterPlantas).toHaveBeenCalledExactlyOnceWith('p1');
    expect(linhas()).toEqual([['Plantas', 'Planta_de_Ordenamento.zip', 'Formato', '—', 'Legenda ilegível.']]);
  });

  it('num processo sem página das Plantas não lê as Plantas', () => {
    abrir();

    expect(obterPlantas).not.toHaveBeenCalled();
  });

  it('como no UI-037: título do quadro com marcador, ícone no ficheiro, selo laranja e título do resumo só para leitores de ecrã', () => {
    abrir();

    expect(fixture.nativeElement.querySelector('.quadro-titulo').textContent.trim()).toBe('Elementos Rejeitados');
    expect(fixture.nativeElement.querySelector('.quadro-marcador')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('tbody .ficheiro-icone')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('tbody app-selo').classList).toContain('selo-laranja');
    expect(fixture.nativeElement.querySelector('#resumo-rejeicao-titulo').classList).toContain('visualmente-oculto');
  });

  it('a tabela tem legenda e cabeçalhos de coluna, e as notas gerais do pop-up têm rótulo', () => {
    abrir();
    recusar();

    expect(fixture.nativeElement.querySelector('table caption').textContent.trim()).toBe('Elementos rejeitados');
    const titulos = Array.from(fixture.nativeElement.querySelectorAll('thead th')) as HTMLElement[];
    expect(titulos.map((th) => th.textContent?.trim())).toEqual(['Separador', 'Nome do Ficheiro', 'Categoria', 'Motivo', 'Notas']);
    expect(titulos.every((th) => th.getAttribute('scope') === 'col')).toBe(true);
    const area = dialogo()!.querySelector('[data-campo="notas-gerais"] textarea') as HTMLTextAreaElement;
    const rotulo = fixture.nativeElement.querySelector(`label[for="${area.id}"]`) as HTMLLabelElement;
    expect(rotulo.textContent).toContain('Notas gerais');
  });

  it('põe o foco no título ao abrir', async () => {
    abrir();
    await fixture.whenStable();

    expect(document.activeElement?.textContent?.trim()).toBe('Resumo da rejeição');
  });

  it('o resumo não tem o campo das notas gerais: «Recusar» abre o pop-up com elas e o «Confirmar Rejeição»', () => {
    abrir();

    expect(fixture.nativeElement.querySelector('[data-campo="notas-gerais"]')).toBeNull();
    expect(botao('Confirmar Rejeição')).toBeUndefined();
    expect(texto()).not.toContain('Guardar');

    recusar();

    expect(dialogo()).not.toBeNull();
    expect(dialogo()!.querySelector('[data-campo="notas-gerais"] textarea')).not.toBeNull();
    expect(botao('Confirmar Rejeição')).toBeDefined();
    expect(rejeitar).not.toHaveBeenCalled();
  });

  it('«Cancelar» fecha o pop-up sem chamar a API', () => {
    abrir();
    recusar();

    clicar('Cancelar');

    expect(dialogo()).toBeNull();
    expect(rejeitar).not.toHaveBeenCalled();
  });

  it('«Confirmar Rejeição» faz o PATCH com as notas gerais aparadas e a versão do detalhe, e emite rejeitado', () => {
    abrir();
    const rejeitado = vi.fn();
    fixture.componentInstance.rejeitado.subscribe(rejeitado);
    recusar();
    escreverNotas('  Rever o regulamento.  ');

    clicar('Confirmar Rejeição');

    expect(rejeitar).toHaveBeenCalledExactlyOnceWith('p1', 3, 'Rever o regulamento.');
    expect(rejeitado).toHaveBeenCalledWith({ id: 'p1', estado: 'REJEITADO_DGT' });
  });

  it('sem notas gerais envia nulo', () => {
    abrir();
    recusar();
    escreverNotas('   ');

    clicar('Confirmar Rejeição');

    expect(rejeitar).toHaveBeenCalledWith('p1', 3, null);
  });

  it('«Anterior» emite anterior sem chamar a API', () => {
    abrir();
    const anterior = vi.fn();
    fixture.componentInstance.anterior.subscribe(anterior);

    clicar('Anterior');

    expect(anterior).toHaveBeenCalledOnce();
    expect(rejeitar).not.toHaveBeenCalled();
  });

  it('um 400 mostra a mensagem do backend no pop-up e relê o quadro e o detalhe', () => {
    rejeitar.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Indique pelo menos um item rejeitado.', 't1', [{ path: 'itens', erro: 'x' }])),
    );
    abrir();
    const recarregar = vi.fn();
    fixture.componentInstance.recarregar.subscribe(recarregar);
    recusar();

    clicar('Confirmar Rejeição');

    expect(dialogo()!.textContent).toContain('Indique pelo menos um item rejeitado.');
    expect(recarregar).toHaveBeenCalledOnce();
    expect(estrutura).toHaveBeenCalledTimes(2);
    expect(botao('Recarregar')).toBeUndefined();
  });

  it('um 409 mostra a mensagem do backend e «Recarregar», sem repetir o pedido', () => {
    rejeitar.mockReturnValue(
      throwError(() => new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado; recarregue.', 't1', null)),
    );
    abrir();
    const recarregar = vi.fn();
    fixture.componentInstance.recarregar.subscribe(recarregar);
    recusar();

    clicar('Confirmar Rejeição');

    expect(dialogo()!.textContent).toContain('O processo foi alterado; recarregue.');
    expect(botao('Confirmar Rejeição')!.disabled).toBe(true);
    clicar('Recarregar');
    expect(recarregar).toHaveBeenCalledOnce();
    expect(rejeitar).toHaveBeenCalledOnce();
    expect(dialogo()).toBeNull();
    expect(texto()).not.toContain('O processo foi alterado; recarregue.');
  });

  it('se o quadro não carrega, mostra o erro e «Tentar novamente», e não deixa confirmar', () => {
    estrutura.mockReturnValueOnce(throwError(() => new ApiError('ERRO_INTERNO', 'Serviço indisponível.', 't1', null)));
    abrir();

    expect(texto()).toContain('Serviço indisponível.');
    expect(botao('Recusar')!.disabled).toBe(true);

    clicar('Tentar novamente');

    expect(linhas()).toHaveLength(3);
    expect(botao('Recusar')!.disabled).toBe(false);
  });
});

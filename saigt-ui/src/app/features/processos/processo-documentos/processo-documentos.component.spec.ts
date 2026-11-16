import { Component, WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, onTestFinished, vi } from 'vitest';
import { ProcessoDocumentosComponent } from './processo-documentos.component';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoProcesso, EstruturaDocumental, ItemDocumental } from '../../../models/documento.model';
import { DescargaService } from '../../../services/descarga.service';
import { CODIGO_CARREGAMENTO_SEM_LIGACAO, DocumentoService } from '../../../services/documento.service';
import { InformacaoBaseAlterada } from '../informacao-base';
import { ProcessoDetalhe } from '../../../models/processo.model';
import { DinamicaService } from '../../../services/dinamica.service';
import { InstrumentoService } from '../../../services/instrumento.service';
import { ProcessoService } from '../../../services/processo.service';

function documento(parcial: Partial<DocumentoProcesso> = {}): DocumentoProcesso {
  return {
    id: 'd1',
    documentoConfigId: 1,
    nomeFicheiro: 'deliberacao.docx',
    categoria: 'PUBLICACAO',
    extensao: 'docx',
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

function item(parcial: Partial<ItemDocumental> = {}): ItemDocumental {
  return {
    documentoConfigId: 1,
    codigo: 'DELIBERACAO_PUB',
    nome: 'Deliberação',
    categoria: 'PUBLICACAO',
    tipoElemento: 'PECA_ESCRITA',
    formato: 'EDITAVEL',
    obrigatorio: true,
    extensoesAceites: ['docx', 'pdf'],
    ficheiros: [],
    observacoes: null,
    pendente: true,
    obtidoDoGeoconform: false,
    ...parcial,
  };
}

function estruturaCom(parcial: Partial<EstruturaDocumental> = {}): EstruturaDocumental {
  return {
    separadores: [
      {
        separador: 'Deliberação',
        elementoInformacaoBase: 'DELIBERACAO',
        publicacao: [item()],
        deposito: [item({ documentoConfigId: 2, codigo: 'DELIBERACAO_DEP', categoria: 'DEPOSITO', obrigatorio: false, pendente: false })],
      },
    ],
    outros: [],
    pendencias: [{ documentoConfigId: 1, codigo: 'DELIBERACAO_PUB', nome: 'Deliberação', categoria: 'PUBLICACAO', motivo: 'Falta carregar um ficheiro.' }],
    ...parcial,
  };
}

const MENSAGEM_CANAL_GEOCONFORM = 'Nesta Dinâmica as peças gráficas são obtidas do GeoConform e não podem ser carregadas.';
const AVISO_GEOCONFORM = 'As peças gráficas desta Dinâmica são obtidas do GeoConform.';

/** Uma peça gráfica e uma peça escrita, cada uma com um ficheiro, para ver o que muda só na gráfica. */
function estruturaPecas(obtidoDoGeoconform: boolean): EstruturaDocumental {
  return estruturaCom({
    separadores: [
      {
        separador: 'Planta',
        elementoInformacaoBase: null,
        publicacao: [
          item({
            documentoConfigId: 7,
            codigo: 'PLANTA_PUB',
            nome: 'Planta de ordenamento',
            tipoElemento: 'PECA_GRAFICA',
            obtidoDoGeoconform,
            ficheiros: [documento({ id: 'd7', documentoConfigId: 7, nomeFicheiro: 'planta-antiga.pdf' })],
            pendente: false,
          }),
        ],
        deposito: [],
      },
      {
        separador: 'Regulamento',
        elementoInformacaoBase: null,
        publicacao: [
          item({
            documentoConfigId: 8,
            codigo: 'REGULAMENTO_PUB',
            nome: 'Regulamento',
            ficheiros: [documento({ id: 'd8', documentoConfigId: 8, nomeFicheiro: 'regulamento.pdf' })],
            pendente: false,
          }),
        ],
        deposito: [],
      },
    ],
    pendencias: [],
  });
}

function escolherFicheiro(campo: HTMLInputElement, ficheiro: File): void {
  Object.defineProperty(campo, 'files', { value: [ficheiro], configurable: true });
  campo.dispatchEvent(new Event('change'));
}

function largarFicheiro(zona: HTMLElement, ficheiro: File): void {
  const evento = new Event('drop', { bubbles: true, cancelable: true });
  Object.defineProperty(evento, 'dataTransfer', { value: { files: [ficheiro] } });
  zona.dispatchEvent(evento);
}

@Component({
  standalone: true,
  imports: [ProcessoDocumentosComponent],
  template: `
    <app-processo-documentos processoId="p1" [acaoItem]="acao" />
    <ng-template #acao let-contexto>
      <span class="acao-teste">{{ contexto.tipo }}:{{ contexto.documento?.id ?? contexto.elemento }}</span>
    </ng-template>
  `,
})
class AnfitriaoComponent {}

describe('ProcessoDocumentosComponent', () => {
  let fixture: ComponentFixture<ProcessoDocumentosComponent>;
  let estrutura: ReturnType<typeof vi.fn>;
  let carregar: ReturnType<typeof vi.fn>;
  let substituir: ReturnType<typeof vi.fn>;
  let remover: ReturnType<typeof vi.fn>;
  let desbloquear: ReturnType<typeof vi.fn>;
  let reclassificar: ReturnType<typeof vi.fn>;
  let descarregar: ReturnType<typeof vi.fn>;
  let erroDescarga: WritableSignal<string | null>;
  let gravarObservacoes: ReturnType<typeof vi.fn>;
  let gravarObservacoesOutros: ReturnType<typeof vi.fn>;
  let pendenciasEmitidas: unknown[];

  function configurar(editavel = true, entradas: Record<string, unknown> = {}): void {
    TestBed.configureTestingModule({
      providers: [{ provide: DocumentoService, useValue: { estrutura, carregar, substituir, remover, desbloquear, gravarObservacoes, gravarObservacoesOutros, reclassificar } },
        { provide: DescargaService, useValue: { descarregar, erro: erroDescarga } },
      ],
    });
    fixture = TestBed.createComponent(ProcessoDocumentosComponent);
    fixture.componentRef.setInput('processoId', 'p1');
    fixture.componentRef.setInput('editavel', editavel);
    for (const [nome, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(nome, valor);
    }
    fixture.componentInstance.estruturaAlterada.subscribe((p) => pendenciasEmitidas.push(p));
    fixture.detectChanges();
  }

  function texto(): string {
    return fixture.nativeElement.textContent;
  }

  function botao(rotulo: string): HTMLButtonElement {
    const botoes = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    return botoes.find((b) => b.textContent?.trim() === rotulo)!;
  }

  beforeEach(() => {
    pendenciasEmitidas = [];
    estrutura = vi.fn().mockReturnValue(of(estruturaCom()));
    carregar = vi.fn().mockReturnValue(of(documento()));
    gravarObservacoes = vi.fn().mockReturnValue(of({ documentoConfigId: 1, observacoes: 'NA' }));
    gravarObservacoesOutros = vi.fn().mockReturnValue(of({ documentoConfigId: null, observacoes: 'Anexo extra' }));
    substituir = vi.fn().mockReturnValue(of(documento()));
    remover = vi.fn().mockReturnValue(of(undefined));
    desbloquear = vi.fn().mockReturnValue(of(documento()));
    reclassificar = vi.fn().mockReturnValue(of(documento()));
    descarregar = vi.fn();
    erroDescarga = signal<string | null>(null);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('mostra os grupos, o separador e as caixas Publicação e Depósito', () => {
    configurar();

    expect(texto()).toContain('Publicação INCM e Depósito DGT');
    expect(texto()).toContain('Depósito DGT');
    expect(texto()).toContain('Deliberação');
    expect(texto()).toContain('Outros');
    const titulos = Array.from(fixture.nativeElement.querySelectorAll('.caixa-titulo')).map((e) => (e as HTMLElement).textContent);
    expect(titulos).toEqual(['Publicação', 'Depósito', 'Publicação', 'Depósito']);
  });

  it('mostra os ficheiros já carregados com a data de carregamento e o utilizador', () => {
    const carregado = documento();
    estrutura.mockReturnValue(
      of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [carregado], pendente: false })], deposito: [] }], pendencias: [] })),
    );
    configurar();

    expect(texto()).toContain('deliberacao.docx');
    expect(texto()).toContain('20/10/2026');
    expect(texto()).toContain('Helena Marques');
  });

  it('mostra a pendência num item obrigatório sem ficheiro, sem as etiquetas Obrigatório e Opcional', () => {
    configurar();

    expect(texto()).not.toContain('Obrigatório');
    expect(texto()).not.toContain('Opcional');
    expect(fixture.nativeElement.querySelectorAll('.pendencia')).toHaveLength(1);
    expect(texto()).toContain('Falta carregar um ficheiro ou indicar o motivo nas observações.');
  });

  it('mostra as extensões aceites como dica', () => {
    configurar();

    expect(texto()).toContain('Extensões aceites: docx, pdf');
  });

  it('emite as pendências depois de cada carregamento da estrutura', () => {
    configurar();

    expect(pendenciasEmitidas).toEqual([estruturaCom().pendencias]);
  });

  it('carrega o ficheiro escolhido na caixa de publicação com a categoria e o item', () => {
    configurar();
    const ficheiro = new File(['x'], 'texto.docx');
    const campo = fixture.nativeElement.querySelector('.zona .ficheiro-input') as HTMLInputElement;

    escolherFicheiro(campo, ficheiro);

    expect(carregar).toHaveBeenCalledWith('p1', ficheiro, 'PUBLICACAO', 1, []);
  });

  it('carrega o ficheiro largado na caixa de publicação', () => {
    configurar();
    const ficheiro = new File(['x'], 'texto.docx');

    largarFicheiro(fixture.nativeElement.querySelector('.zona'), ficheiro);

    expect(carregar).toHaveBeenCalledWith('p1', ficheiro, 'PUBLICACAO', 1, []);
  });

  it('relê a estrutura depois de carregar e deixa de mostrar a pendência', () => {
    configurar();
    estrutura.mockReturnValue(
      of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [documento()], pendente: false })], deposito: [] }], pendencias: [] })),
    );

    escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'texto.docx'));
    fixture.detectChanges();

    expect(estrutura).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('.pendencia')).toBeNull();
    expect(texto()).toContain('deliberacao.docx');
    expect(pendenciasEmitidas.at(-1)).toEqual([]);
  });

  it('mostra no item o erro de campo do servidor e não relê a estrutura', () => {
    carregar.mockReturnValue(throwError(() => new ApiError('VALIDACAO', 'Dados inválidos.', 't1', [{ path: 'ficheiro', erro: 'Extensão não aceite.' }])));
    configurar();

    escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'virus.exe'));
    fixture.detectChanges();

    const erro = fixture.nativeElement.querySelector('.erro-item');
    expect(erro.textContent).toContain('Extensão não aceite.');
    expect(fixture.nativeElement.querySelector('.caixa .erro-item')).toBe(erro);
    expect(estrutura).toHaveBeenCalledTimes(1);
  });

  it.each([
    'DOC_NOME_INVALIDO',
    'DOC_EXTENSAO_NAO_ACEITE',
    'DOC_TAMANHO_EXCEDIDO',
    'DOC_ZIP_INVALIDO',
    'DOC_TIFF_INVALIDO',
    'DOC_CLASSIFICACAO_INVALIDA',
  ])('mostra no item, com role alert, a recusa %s e não no banner geral', (codigo) => {
    carregar.mockReturnValue(throwError(() => new ApiError(codigo, 'Recusado.', 't1', [{ path: 'ficheiro', erro: 'Mensagem do servidor.' }])));
    configurar();

    escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'ficheiro.pdf'));
    fixture.detectChanges();

    const erro = fixture.nativeElement.querySelector('.erro-item');
    expect(erro.getAttribute('role')).toBe('alert');
    expect(erro.textContent).toContain('Mensagem do servidor.');
    expect(fixture.nativeElement.querySelector('app-error-banner [role="alert"]')).toBeNull();
    expect(estrutura).toHaveBeenCalledTimes(1);
  });

  describe('modo acrescentar (PCM no processo atribuído)', () => {
    function estruturaComFicheiros(): EstruturaDocumental {
      return estruturaCom({
        separadores: [
          {
            separador: 'Deliberação',
            elementoInformacaoBase: 'DELIBERACAO',
            publicacao: [item({ ficheiros: [documento()], pendente: false })],
            deposito: [item({ documentoConfigId: 2, codigo: 'DELIBERACAO_DEP', categoria: 'DEPOSITO', obrigatorio: false, pendente: false })],
          },
        ],
        pendencias: [],
      });
    }

    function rotulosDeBotoes(): string[] {
      return (Array.from(fixture.nativeElement.querySelectorAll('button, label.acao')) as HTMLElement[]).map(
        (b) => b.textContent?.trim() ?? '',
      );
    }

    it('cada item e os Outros mantêm a zona de carregamento, operável pelo campo de ficheiro', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiros()));
      configurar(true, { modoAcrescentar: true });

      // Dois itens e os Outros, com publicação e depósito (US-548).
      const zonas = fixture.nativeElement.querySelectorAll('.zona');
      expect(zonas).toHaveLength(4);
      expect(fixture.nativeElement.querySelectorAll('.zona input[type="file"]')).toHaveLength(4);
    });

    it('carrega o ficheiro escolhido no item, como o autor', () => {
      configurar(true, { modoAcrescentar: true });
      const ficheiro = new File(['x'], 'texto-rcm.pdf');

      escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), ficheiro);

      expect(carregar).toHaveBeenCalledWith('p1', ficheiro, 'PUBLICACAO', 1, []);
    });

    it('os ficheiros existentes só se descarregam: sem Substituir, Remover, Reclassificar nem Desbloquear', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiros()));
      configurar(true, { modoAcrescentar: true });

      const rotulos = rotulosDeBotoes();
      expect(rotulos).toContain('Descarregar');
      expect(rotulos.some((r) => r.startsWith('Substituir'))).toBe(false);
      expect(rotulos).not.toContain('Remover');
      expect(rotulos).not.toContain('Reclassificar');
      expect(rotulos).not.toContain('Desbloquear ficheiro');
      expect(fixture.nativeElement.querySelector('li.ficheiro[draggable="true"]')).toBeNull();
    });

    it('um ficheiro bloqueado não oferece Desbloquear à PCM', () => {
      estrutura.mockReturnValue(
        of(
          estruturaCom({
            separadores: [
              {
                separador: 'Deliberação',
                elementoInformacaoBase: 'DELIBERACAO',
                publicacao: [item({ ficheiros: [documento({ bloqueadoEdicao: true })], pendente: false })],
                deposito: [],
              },
            ],
            pendencias: [],
          }),
        ),
      );
      configurar(true, { modoAcrescentar: true });

      expect(rotulosDeBotoes()).not.toContain('Desbloquear ficheiro');
    });

    it('sem a caixa de Observações e o Guardar não as grava', () => {
      configurar(true, { modoAcrescentar: true });

      expect(fixture.nativeElement.querySelector('.observacoes textarea')).toBeNull();
      let gravadas: boolean | undefined;
      fixture.componentInstance.gravarObservacoes().subscribe((resultado) => (gravadas = resultado));
      expect(gravadas).toBe(true);
      expect(gravarObservacoes).not.toHaveBeenCalled();
    });

    it('a Informação Base continua editável', () => {
      configurar(true, { modoAcrescentar: true });

      expect(fixture.nativeElement.querySelector('form.informacao-base-campos')).not.toBeNull();
    });

    it('um 409 do carregamento vai para o banner', () => {
      carregar.mockReturnValue(
        throwError(() => new ApiError('DOC_ESTADO_INVALIDO', 'Os documentos só se carregam em EM_SUBMISSAO.', 't1', null)),
      );
      configurar(true, { modoAcrescentar: true });

      escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'texto-rcm.pdf'));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-error-banner').textContent).toContain('Os documentos só se carregam');
    });

    it('fora do modo acrescentar o autor mantém Remover (o Substituir saiu com a US-545)', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiros()));
      configurar(true);

      const rotulos = rotulosDeBotoes();
      expect(rotulos.some((r) => r.startsWith('Substituir'))).toBe(false);
      expect(rotulos).toContain('Remover');
    });
  });

  it('mostra no item a recusa do tamanho sem campos, como a do 413 do nginx, e não o banner genérico', () => {
    carregar.mockReturnValue(
      throwError(() => new ApiError('DOC_TAMANHO_EXCEDIDO', 'O ficheiro excede o tamanho máximo permitido.', 'unknown', null)),
    );
    configurar();

    escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'pecas-graficas.zip'));
    fixture.detectChanges();

    const erro = fixture.nativeElement.querySelector('.erro-item');
    expect(erro.getAttribute('role')).toBe('alert');
    expect(erro.textContent).toContain('O ficheiro excede o tamanho máximo permitido.');
    expect(fixture.nativeElement.querySelector('app-error-banner [role="alert"]')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Não foi possível concluir a operação');
  });

  it('mostra no item, e não no banner, o carregamento que não chegou ao servidor, sem reler a estrutura', () => {
    carregar.mockReturnValue(
      throwError(() => new ApiError(CODIGO_CARREGAMENTO_SEM_LIGACAO, 'Não foi possível carregar o ficheiro. Tente novamente.', 'unknown', null, 0)),
    );
    configurar();
    const leiturasAntes = estrutura.mock.calls.length;

    escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'Planta_de_Ordenamento.zip'));
    fixture.detectChanges();

    const erro = fixture.nativeElement.querySelector('.erro-item');
    expect(erro.getAttribute('role')).toBe('alert');
    expect(erro.textContent).toContain('Não foi possível carregar o ficheiro. Tente novamente.');
    expect(fixture.nativeElement.querySelector('app-error-banner [role="alert"]')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Http failure');
    expect(estrutura.mock.calls.length).toBe(leiturasAntes);
  });

  describe('itens obtidos do GeoConform', () => {
    function caixas(): HTMLElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('.caixa')) as HTMLElement[];
    }

    it('mostra o aviso no lugar da zona de carregar e deixa a zona nos outros itens', () => {
      estrutura.mockReturnValue(of(estruturaPecas(true)));
      configurar();

      const [planta, regulamento] = caixas();
      expect(planta.querySelector('.zona')).toBeNull();
      expect(planta.querySelector('input[type="file"]')).toBeNull();
      expect(planta.querySelector('app-alerta')?.textContent).toContain(AVISO_GEOCONFORM);
      expect(regulamento.querySelector('.zona')).not.toBeNull();
      expect(regulamento.querySelector('app-alerta')).toBeNull();
      expect(fixture.nativeElement.querySelectorAll('.zona')).toHaveLength(3);
    });

    it('associa o aviso ao item para o leitor de ecrã', () => {
      estrutura.mockReturnValue(of(estruturaPecas(true)));
      configurar();

      const [planta, regulamento] = caixas();
      expect(planta.getAttribute('role')).toBe('group');
      const nome = fixture.nativeElement.querySelector(`#${planta.getAttribute('aria-labelledby')}`) as HTMLElement;
      expect(nome.textContent).toContain('Planta de ordenamento');
      const aviso = fixture.nativeElement.querySelector(`#${planta.getAttribute('aria-describedby')}`) as HTMLElement;
      expect(aviso.tagName.toLowerCase()).toBe('app-alerta');
      expect(aviso.getAttribute('role')).toBe('status');
      expect(aviso.textContent).toContain(AVISO_GEOCONFORM);
      expect(regulamento.getAttribute('aria-describedby')).toBeNull();
    });

    it('mantém visíveis e descarregáveis os ficheiros que já existem', () => {
      estrutura.mockReturnValue(of(estruturaPecas(true)));
      configurar();

      const [planta, regulamento] = caixas();
      expect(planta.textContent).toContain('planta-antiga.pdf');
      expect(planta.textContent).not.toContain('Substituir');
      expect(planta.textContent).toContain('Remover');
      expect(regulamento.textContent).not.toContain('Substituir');
      expect(regulamento.textContent).toContain('Remover');

      const botaoDescarregar = Array.from(planta.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Descarregar')!;
      botaoDescarregar.click();

      expect(descarregar).toHaveBeenCalledWith('p1', 'd7');
    });

    it('mantém a zona quando a Dinâmica não tem GeoConform', () => {
      estrutura.mockReturnValue(of(estruturaPecas(false)));
      configurar();

      const [planta] = caixas();
      expect(planta.querySelector('.zona')).not.toBeNull();
      expect(planta.textContent).toContain('Remover');
      expect(texto()).not.toContain(AVISO_GEOCONFORM);
    });

    it('não mostra o aviso no ecrã só de leitura', () => {
      estrutura.mockReturnValue(of(estruturaPecas(true)));
      configurar(false);

      expect(texto()).not.toContain(AVISO_GEOCONFORM);
      expect(caixas()[0].getAttribute('role')).toBeNull();
    });

    it('mostra junto do item a recusa do carregamento e passa a mostrar o aviso depois de reler a estrutura', () => {
      estrutura.mockReturnValueOnce(of(estruturaPecas(false))).mockReturnValue(of(estruturaPecas(true)));
      carregar.mockReturnValue(throwError(() => new ApiError('DOC_CANAL_GEOCONFORM', MENSAGEM_CANAL_GEOCONFORM, 't1', null)));
      configurar();

      escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'planta.pdf'));
      fixture.detectChanges();

      const [planta] = caixas();
      const erro = planta.querySelector('.erro-item') as HTMLElement;
      expect(erro.getAttribute('role')).toBe('alert');
      expect(erro.textContent).toContain(MENSAGEM_CANAL_GEOCONFORM);
      expect(fixture.nativeElement.querySelector('app-error-banner [role="alert"]')).toBeNull();
      expect(estrutura).toHaveBeenCalledTimes(2);
      expect(planta.querySelector('.zona')).toBeNull();
      expect(planta.querySelector('app-alerta')?.textContent).toContain(AVISO_GEOCONFORM);
    });
  });

  it('relê a estrutura quando o servidor responde DOC_ESTADO_INVALIDO', () => {
    carregar.mockReturnValue(throwError(() => new ApiError('DOC_ESTADO_INVALIDO', 'Operação recusada.', 't1', null)));
    configurar();

    escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'ficheiro.pdf'));
    fixture.detectChanges();

    expect(estrutura).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('app-error-banner [role="alert"]').textContent).toContain('Operação recusada.');
  });

  it('carrega no cartão Outros como DEPOSITO sem documentoConfigId', () => {
    configurar();
    const ficheiro = new File(['x'], 'extra.pdf');
    const zonas = fixture.nativeElement.querySelectorAll('.zona') as NodeListOf<HTMLElement>;

    largarFicheiro(zonas[zonas.length - 1], ficheiro);

    expect(carregar).toHaveBeenCalledWith('p1', ficheiro, 'DEPOSITO', undefined, []);
  });

  it('substitui um ficheiro validado desbloqueado na correção e relê a estrutura', () => {
    const desbloqueado = documento({ estadoValidacao: 'VALIDADO', bloqueadoEdicao: false, justificacaoDesbloqueio: 'Data errada' });
    estrutura.mockReturnValue(
      of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [desbloqueado], pendente: false })], deposito: [] }], pendencias: [] })),
    );
    configurar();
    const novo = new File(['y'], 'novo.docx');

    escolherFicheiro(fixture.nativeElement.querySelector('.ficheiro label .ficheiro-input'), novo);

    expect(substituir).toHaveBeenCalledWith('p1', 'd1', novo);
    expect(estrutura).toHaveBeenCalledTimes(2);
  });

  it('só remove depois de confirmar', () => {
    estrutura.mockReturnValue(
      of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [documento()], pendente: false })], deposito: [] }], pendencias: [] })),
    );
    configurar();

    botao('Remover').click();
    fixture.detectChanges();
    expect(remover).not.toHaveBeenCalled();
    expect(texto()).toContain('Remover o ficheiro "deliberacao.docx"?');

    botao('Sim').click();
    fixture.detectChanges();

    expect(remover).toHaveBeenCalledWith('p1', 'd1');
    expect(estrutura).toHaveBeenCalledTimes(2);
  });

  it('sem resposta da API, mostra uma mensagem legível no banner e trá-lo para a vista', () => {
    estrutura.mockReturnValue(
      of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [documento()], pendente: false })], deposito: [] }], pendencias: [] })),
    );
    remover.mockReturnValue(
      throwError(() => new ApiError('ERRO_DESCONHECIDO', 'Http failure response for /api/v1/x: 502 Bad Gateway', 'unknown', null)),
    );
    const rolar = vi.fn();
    const original = Element.prototype.scrollIntoView;
    onTestFinished(() => {
      Element.prototype.scrollIntoView = original;
    });
    Element.prototype.scrollIntoView = rolar;
    configurar();

    botao('Remover').click();
    fixture.detectChanges();
    botao('Sim').click();
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('app-error-banner [role="alert"]');
    expect(banner.textContent).toContain('Não foi possível concluir a operação. Tente novamente.');
    expect(banner.textContent).not.toContain('Http failure');
    expect(rolar).toHaveBeenCalledWith({ block: 'nearest' });
    expect(rolar.mock.contexts[0]).toBe(banner);
    expect(texto()).toContain('deliberacao.docx');
  });

  it('não remove quando a confirmação é recusada', () => {
    estrutura.mockReturnValue(
      of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [documento()], pendente: false })], deposito: [] }], pendencias: [] })),
    );
    configurar();

    botao('Remover').click();
    fixture.detectChanges();
    botao('Não').click();
    fixture.detectChanges();

    expect(remover).not.toHaveBeenCalled();
    expect(texto()).not.toContain('Remover o ficheiro');
  });

  it('pede a descarga do documento ao clicar em Descarregar, com nome acessível', () => {
    estrutura.mockReturnValue(
      of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [documento()], pendente: false })], deposito: [] }], pendencias: [] })),
    );
    configurar();

    const descarregarBotao = botao('Descarregar');
    expect(descarregarBotao.getAttribute('aria-label')).toBe(`Descarregar ${documento().nomeFicheiro}`);
    descarregarBotao.click();

    expect(descarregar).toHaveBeenCalledWith('p1', 'd1');
  });

  it('mostra a mensagem de erro da descarga com role=alert', () => {
    erroDescarga.set('Não foi possível descarregar o ficheiro. Tente novamente.');
    configurar();

    const alerta = fixture.nativeElement.querySelector('.erro-descarga');
    expect(alerta.getAttribute('role')).toBe('alert');
    expect(alerta.textContent).toContain('Não foi possível descarregar o ficheiro. Tente novamente.');
  });

  it('sem editavel só mostra leitura e Descarregar', () => {
    estrutura.mockReturnValue(
      of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [documento()], pendente: false })], deposito: [] }], pendencias: [] })),
    );
    configurar(false);

    expect(texto()).not.toContain('Escolher ficheiro');
    expect(texto()).not.toContain('Substituir');
    expect(texto()).not.toContain('Remover');
    expect(texto()).toContain('Descarregar');
  });

  it('esconde Substituir e Remover num ficheiro com bloqueadoEdicao, mantendo Escolher ficheiro', () => {
    estrutura.mockReturnValue(
      of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [documento({ bloqueadoEdicao: true })], pendente: false })], deposito: [] }], pendencias: [] })),
    );
    configurar();

    expect(texto()).not.toContain('Substituir');
    expect(texto()).not.toContain('Remover');
    expect(texto()).toContain('Descarregar');
  });

  describe('Desbloquear ficheiro', () => {
    const JUSTIFICACAO = 'A legenda da planta estava desatualizada.';

    function estruturaComFicheiro(ficheiro: DocumentoProcesso): EstruturaDocumental {
      return estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [ficheiro], pendente: false })], deposito: [] }], pendencias: [] });
    }

    const bloqueado = documento({ estadoValidacao: 'VALIDADO', bloqueadoEdicao: true });
    const desbloqueado = documento({ estadoValidacao: 'VALIDADO', bloqueadoEdicao: false, justificacaoDesbloqueio: JUSTIFICACAO });

    function botaoDesbloquear(): HTMLButtonElement {
      return botao('Desbloquear ficheiro');
    }

    function areaJustificacao(): HTMLTextAreaElement | null {
      return fixture.nativeElement.querySelector('#justificacao-desbloqueio');
    }

    function escrever(valor: string): void {
      const area = areaJustificacao()!;
      area.value = valor;
      area.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    function abrirModal(): void {
      const abrir = botaoDesbloquear();
      abrir.focus();
      abrir.click();
      fixture.detectChanges();
    }

    it('mostra «Desbloquear ficheiro» só num ficheiro bloqueado e no ecrã editável, com o nome no nome acessível', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro(bloqueado)));
      configurar();

      expect(botaoDesbloquear().getAttribute('aria-label')).toBe('Desbloquear ficheiro deliberacao.docx');
      expect(texto()).not.toContain('Substituir');
    });

    it('não mostra «Desbloquear ficheiro» em leitura nem num ficheiro não bloqueado', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro(bloqueado)));
      configurar(false);
      expect(texto()).not.toContain('Desbloquear ficheiro');

      TestBed.resetTestingModule();
      estrutura.mockReturnValue(of(estruturaComFicheiro(documento())));
      configurar();
      expect(texto()).not.toContain('Desbloquear ficheiro');
    });

    it('abre o modal com o nome do ficheiro, a área «Justificação», o contador e o aviso para a DGT', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro(bloqueado)));
      configurar();

      abrirModal();

      const dialogo = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
      expect(dialogo.textContent).toContain('Desbloquear ficheiro');
      expect(dialogo.textContent).toContain('deliberacao.docx');
      expect(dialogo.textContent).toContain('A justificação fica visível à DGT na validação.');
      const rotulo = fixture.nativeElement.querySelector('label[for="justificacao-desbloqueio"]') as HTMLLabelElement;
      expect(rotulo.textContent).toContain('Justificação');
      expect(areaJustificacao()!.getAttribute('maxlength')).toBe('2000');
      expect(areaJustificacao()!.getAttribute('aria-describedby')).toContain('justificacao-desbloqueio-ajuda');
      expect(dialogo.textContent).toContain('0/2000');

      escrever(JUSTIFICACAO);
      expect(dialogo.textContent).toContain(`${JUSTIFICACAO.length}/2000`);
    });

    it('confirma com PATCH, fecha, recarrega a estrutura e passa a mostrar «Substituir» e «Desbloqueado: …» sem «Remover»', () => {
      estrutura.mockReturnValueOnce(of(estruturaComFicheiro(bloqueado))).mockReturnValue(of(estruturaComFicheiro(desbloqueado)));
      configurar();

      abrirModal();
      escrever(`  ${JUSTIFICACAO}  `);
      botao('Confirmar').click();
      fixture.detectChanges();

      expect(desbloquear).toHaveBeenCalledWith('p1', 'd1', JUSTIFICACAO);
      expect(estrutura).toHaveBeenCalledTimes(2);
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
      expect(texto()).toContain('Substituir');
      expect(texto()).toContain(`Desbloqueado: ${JUSTIFICACAO}`);
      expect(texto()).not.toContain('Desbloquear ficheiro');
      expect(botao('Remover')).toBeUndefined();
    });

    it('«Cancelar» fecha sem pedido e devolve o foco ao botão', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro(bloqueado)));
      configurar();
      const abrir = botaoDesbloquear();

      abrirModal();
      escrever(JUSTIFICACAO);
      botao('Cancelar').click();
      fixture.detectChanges();

      expect(desbloquear).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
      expect(document.activeElement).toBe(abrir);
    });

    it('sem justificação não faz o pedido e anuncia o erro ligado à área de texto', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro(bloqueado)));
      configurar();

      abrirModal();
      escrever('   ');
      botao('Confirmar').click();
      fixture.detectChanges();

      expect(desbloquear).not.toHaveBeenCalled();
      const erro = fixture.nativeElement.querySelector('#justificacao-desbloqueio-erro') as HTMLElement;
      expect(erro.getAttribute('role')).toBe('alert');
      expect(erro.textContent).toContain('Indique a justificação do desbloqueio.');
      expect(areaJustificacao()!.getAttribute('aria-describedby')).toContain('justificacao-desbloqueio-erro');
      expect(areaJustificacao()!.getAttribute('aria-invalid')).toBe('true');
    });

    it('mostra o 400 do servidor junto da área de texto e mantém o modal aberto', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro(bloqueado)));
      desbloquear.mockReturnValue(
        throwError(() => new ApiError('DOC_JUSTIFICACAO_EM_FALTA', 'Indique a justificação do desbloqueio.', 't1', [
          { path: 'justificacao', erro: 'Indique a justificação do desbloqueio.' },
        ])),
      );
      configurar();

      abrirModal();
      escrever(JUSTIFICACAO);
      botao('Confirmar').click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('#justificacao-desbloqueio-erro')?.textContent).toContain('Indique a justificação do desbloqueio.');
      expect(estrutura).toHaveBeenCalledTimes(1);
    });

    it('mostra um 409 como alerta no modal e relê a estrutura', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro(bloqueado)));
      desbloquear.mockReturnValue(
        throwError(() => new ApiError('DOC_ESTADO_INVALIDO', 'Só é possível desbloquear um ficheiro depois de iniciar a correção.', 't1', null)),
      );
      configurar();

      abrirModal();
      escrever(JUSTIFICACAO);
      botao('Confirmar').click();
      fixture.detectChanges();

      const alerta = fixture.nativeElement.querySelector('[role="dialog"] app-alerta') as HTMLElement;
      expect(alerta.getAttribute('role')).toBe('alert');
      expect(alerta.textContent).toContain('Só é possível desbloquear um ficheiro depois de iniciar a correção.');
      expect(fixture.nativeElement.querySelector('#justificacao-desbloqueio-erro')).toBeNull();
      expect(estrutura).toHaveBeenCalledTimes(2);
    });

    it('mostra a justificação num ficheiro em leitura com o rótulo dado pelo ecrã', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro(desbloqueado)));
      configurar(false, { rotuloJustificacao: 'Justificação do desbloqueio' });

      expect(texto()).toContain(`Justificação do desbloqueio: ${JUSTIFICACAO}`);
    });
  });

  it('mostra a mensagem de estado vazio e só o cartão Outros num processo sem itens', () => {
    estrutura.mockReturnValue(of(estruturaCom({ separadores: [], pendencias: [] })));
    configurar();

    expect(texto()).toContain('Indique os elementos fundamentais afetados e guarde o rascunho para ver os itens documentais.');
    expect(texto()).not.toContain('Publicação INCM e Depósito DGT');
    expect(fixture.nativeElement.querySelectorAll('.acordeao')).toHaveLength(1);
    expect(texto()).toContain('Outros');
  });

  it.each(['ESTADO_INVALIDO', 'DOC_BLOQUEADO'])('mostra o erro e relê a estrutura numa resposta 409 %s', (codigo) => {
    carregar.mockReturnValue(throwError(() => new ApiError(codigo, 'Operação recusada.', 't1', null)));
    configurar();

    escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'texto.docx'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-banner [role="alert"]').textContent).toContain('Operação recusada.');
    expect(estrutura).toHaveBeenCalledTimes(2);
  });

  it('mostra o banner numa falha de rede ou 5xx sem esconder a estrutura', () => {
    carregar.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Erro inesperado.', 't1', null)));
    configurar();

    escolherFicheiro(fixture.nativeElement.querySelector('.zona .ficheiro-input'), new File(['x'], 'texto.docx'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-banner [role="alert"]')).not.toBeNull();
    expect(texto()).toContain('Deliberação');
    expect(estrutura).toHaveBeenCalledTimes(1);
  });

  it('mostra o banner quando a estrutura não carrega', () => {
    estrutura.mockReturnValue(throwError(() => new ApiError('NAO_ENCONTRADO', 'Processo não encontrado.', 't1', null)));
    configurar();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(texto()).not.toContain('Outros');
  });

  describe('Informação Base', () => {
    const INFORMACAO_BASE = {
      deliberacao: {
        prazoAnos: 1,
        prazoMeses: 2,
        prazoDias: 3,
        avaliacaoAmbiental: true,
        dataDeliberacao: '2026-09-01',
        textoPublicar: 'Texto a publicar.',
      },
      textoAto: { prazoAnos: 4, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: false },
    };

    beforeEach(() => {
      estrutura.mockReturnValue(
        of(
          estruturaCom({
            separadores: [
              { separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item()], deposito: [] },
              { separador: 'Texto do Ato', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [item({ documentoConfigId: 3, codigo: 'TEXTO_ATO_PUB', nome: 'Texto do Ato' })], deposito: [] },
              { separador: 'Planta de Localização', elementoInformacaoBase: null, publicacao: [item({ documentoConfigId: 5, codigo: 'PLANTA_PUB', nome: 'Planta de Localização' })], deposito: [] },
            ],
          }),
        ),
      );
    });

    function bloco(separador: string): HTMLElement | null {
      return fixture.nativeElement.querySelector(`.informacao-base[aria-label="Informação Base — ${separador}"]`);
    }

    function campo(raiz: HTMLElement, seletor: string): HTMLInputElement {
      return raiz.querySelector(seletor) as HTMLInputElement;
    }

    function escrever(entrada: HTMLInputElement | HTMLTextAreaElement, valor: string): void {
      entrada.value = valor;
      entrada.dispatchEvent(new Event('input'));
    }

    it('editável: o acordeão Deliberação mostra Prazo, Avaliação Ambiental, Data da deliberação e Texto a publicar', () => {
      configurar();

      const deliberacao = bloco('Deliberação')!;
      expect(deliberacao).not.toBeNull();
      expect(deliberacao.textContent).toContain('Prazo');
      expect(deliberacao.querySelectorAll('input[type="number"]')).toHaveLength(1);
      expect(Array.from(deliberacao.querySelectorAll('.prazo select option')).map((opcao) => opcao.textContent?.trim())).toEqual(['anos', 'meses', 'dias']);
      expect(deliberacao.textContent).toContain('anos');
      expect(deliberacao.textContent).toContain('meses');
      expect(deliberacao.textContent).toContain('dias');
      expect(deliberacao.textContent).toContain('Avaliação Ambiental');
      expect(deliberacao.querySelectorAll('input[type="radio"]')).toHaveLength(2);
      expect(deliberacao.textContent).toContain('Sim');
      expect(deliberacao.textContent).toContain('Não');
      expect(deliberacao.textContent).toContain('Data da deliberação');
      expect(deliberacao.querySelector('input[type="date"]')).not.toBeNull();
      expect(deliberacao.textContent).toContain('Texto a publicar');
      expect(deliberacao.querySelector('textarea')?.getAttribute('placeholder')).toBe('Escreva aqui');
      expect(deliberacao.textContent).not.toContain('Data de Assinatura do ato');
    });

    it('editável: o acordeão Texto do Ato mostra Prazo, Avaliação Ambiental e Data de Assinatura do ato', () => {
      configurar();

      const textoAto = bloco('Texto do Ato')!;
      expect(textoAto).not.toBeNull();
      expect(textoAto.querySelectorAll('input[type="number"]')).toHaveLength(1);
      expect(textoAto.querySelectorAll('input[type="radio"]')).toHaveLength(2);
      expect(textoAto.textContent).toContain('Data de Assinatura do ato');
      expect(textoAto.querySelectorAll('input[type="date"]')).toHaveLength(1);
      expect(textoAto.querySelector('textarea')).toBeNull();
      expect(textoAto.textContent).not.toContain('Texto a publicar');
      expect(textoAto.textContent).not.toContain('Data da deliberação');
    });

    it('a Planta de Localização e Outros não mostram o bloco', () => {
      configurar();

      expect(bloco('Planta de Localização')).toBeNull();
      expect(fixture.nativeElement.querySelectorAll('.informacao-base')).toHaveLength(2);
    });

    it('o bloco segue o elemento configurado, seja qual for o nome do separador', () => {
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ separador: 'Deliberação da Câmara', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item()], deposito: [] }] })),
      );
      configurar();

      const deliberacao = bloco('Deliberação')!;
      expect(deliberacao).not.toBeNull();
      expect(deliberacao.closest('.acordeao')?.querySelector('.acordeao-titulo')?.textContent).toBe('Deliberação da Câmara');
      expect(deliberacao.textContent).toContain('Data da deliberação');
    });

    it('um separador sem elemento configurado não mostra o bloco, mesmo com o nome de um elemento', () => {
      estrutura.mockReturnValue(
        of(
          estruturaCom({
            separadores: [
              { separador: 'Deliberação', elementoInformacaoBase: null, publicacao: [item()], deposito: [] },
              { separador: 'Texto do Ato', elementoInformacaoBase: null, publicacao: [item({ documentoConfigId: 3 })], deposito: [] },
            ],
          }),
        ),
      );
      configurar();

      expect(texto()).toContain('Texto do Ato');
      expect(fixture.nativeElement.querySelectorAll('.informacao-base')).toHaveLength(0);
    });

    it('não constrói o botão Validar nem uma validação cruzada de prazo', () => {
      configurar();
      escrever(campo(bloco('Deliberação')!, 'input[aria-label="Prazo"]'), '1');
      escrever(campo(bloco('Texto do Ato')!, 'input[aria-label="Prazo"]'), '9');
      fixture.detectChanges();

      expect(botao('Validar')).toBeUndefined();
      expect(fixture.nativeElement.querySelectorAll('.erro-campo')).toHaveLength(0);
      expect(texto()).not.toContain('Os prazos');
    });

    it('rascunho novo: campos vazios e nenhuma opção de Avaliação Ambiental marcada', () => {
      configurar();

      expect(fixture.componentInstance.form.getRawValue()).toEqual({
        deliberacao: { prazoAnos: null, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: null, dataDeliberacao: '', textoPublicar: '' },
        textoAto: { prazoAnos: null, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: null },
        dataAssinatura: '',
      });
      const radios = Array.from(fixture.nativeElement.querySelectorAll('.informacao-base input[type="radio"]')) as HTMLInputElement[];
      expect(radios.every((radio) => !radio.checked)).toBe(true);
    });

    it('rascunho retomado: os campos aparecem preenchidos com os valores gravados', () => {
      configurar(true, { informacaoBase: INFORMACAO_BASE, dataAssinatura: '2026-09-02' });

      const deliberacao = bloco('Deliberação')!;
      expect(campo(deliberacao, 'input[aria-label="Prazo"]').value).toBe('1');
      expect((deliberacao.querySelector('.prazo select') as HTMLSelectElement).value).toBe('prazoAnos');
      const sim = Array.from(deliberacao.querySelectorAll('input[type="radio"]')) as HTMLInputElement[];
      expect(sim.map((radio) => radio.checked)).toEqual([true, false]);
      expect(campo(deliberacao, 'input[type="date"]').value).toBe('2026-09-01');
      expect(campo(deliberacao, 'textarea').value).toBe('Texto a publicar.');
      const textoAto = bloco('Texto do Ato')!;
      expect(campo(textoAto, 'input[aria-label="Prazo"]').value).toBe('4');
      expect((Array.from(textoAto.querySelectorAll('input[type="radio"]')) as HTMLInputElement[]).map((radio) => radio.checked)).toEqual([false, true]);
      expect(campo(textoAto, 'input[type="date"]').value).toBe('2026-09-02');
    });

    it('emite a Informação Base e a data de assinatura sem fazer nenhum pedido de gravação', () => {
      configurar();
      const emitidos: unknown[] = [];
      fixture.componentInstance.informacaoBaseAlterada.subscribe((valor) => emitidos.push(valor));

      const unidade = bloco('Deliberação')!.querySelector('.prazo select') as HTMLSelectElement;
      unidade.value = 'prazoMeses';
      unidade.dispatchEvent(new Event('change'));
      escrever(campo(bloco('Deliberação')!, 'input[aria-label="Prazo"]'), '6');
      escrever(campo(bloco('Deliberação')!, 'textarea'), 'Texto novo.');
      escrever(campo(bloco('Texto do Ato')!, 'input[type="date"]'), '2026-10-01');

      expect(emitidos.at(-1)).toEqual({
        informacaoBase: {
          deliberacao: { prazoAnos: null, prazoMeses: 6, prazoDias: null, avaliacaoAmbiental: null, dataDeliberacao: null, textoPublicar: 'Texto novo.' },
          textoAto: null,
        },
        dataAssinatura: '2026-10-01',
      });
      expect(carregar).not.toHaveBeenCalled();
      expect(substituir).not.toHaveBeenCalled();
      expect(remover).not.toHaveBeenCalled();
    });

    it('com tudo vazio a Informação Base emitida é nula e os campos vazios valem null, nunca a string vazia', () => {
      configurar(true, { informacaoBase: INFORMACAO_BASE, dataAssinatura: '2026-09-02' });
      const emitidos: InformacaoBaseAlterada[] = [];
      fixture.componentInstance.informacaoBaseAlterada.subscribe((valor) => emitidos.push(valor));

      fixture.componentInstance.form.reset({}, { emitEvent: true });

      expect(emitidos.at(-1)).toEqual({ informacaoBase: null, dataAssinatura: null });
    });

    it('um valor novo nas entradas repõe os campos sem emitir', () => {
      configurar();
      const emitidos: unknown[] = [];
      fixture.componentInstance.informacaoBaseAlterada.subscribe((valor) => emitidos.push(valor));

      fixture.componentRef.setInput('informacaoBase', INFORMACAO_BASE);
      fixture.detectChanges();

      expect(fixture.componentInstance.form.getRawValue().deliberacao.prazoAnos).toBe(1);
      expect(emitidos).toEqual([]);
    });

    it('o que se escreve sobrevive a fechar e abrir o acordeão', () => {
      configurar();
      escrever(campo(bloco('Deliberação')!, 'textarea'), 'Rascunho.');

      botao('Deliberação').click();
      fixture.detectChanges();
      expect(bloco('Deliberação')).toBeNull();
      botao('Deliberação').click();
      fixture.detectChanges();

      expect(campo(bloco('Deliberação')!, 'textarea').value).toBe('Rascunho.');
    });

    it('mudar a unidade do prazo mantém o número e limpa as outras unidades', () => {
      configurar(true, { informacaoBase: INFORMACAO_BASE });
      const textoAto = bloco('Texto do Ato')!;
      const unidade = textoAto.querySelector('.prazo select') as HTMLSelectElement;

      unidade.value = 'prazoDias';
      unidade.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(fixture.componentInstance.form.getRawValue().textoAto).toEqual(
        expect.objectContaining({ prazoAnos: null, prazoMeses: null, prazoDias: 4 }),
      );
      expect(campo(textoAto, 'input[aria-label="Prazo"]').value).toBe('4');
    });

    it('erro de campo: a mensagem aparece junto do prazo do Texto do Ato', () => {
      configurar(true, { errosCampo: { 'informacaoBase.textoAto.prazoAnos': 'O prazo tem de ser um número inteiro não negativo.' } });

      expect(bloco('Texto do Ato')!.querySelector('.erro-campo')?.textContent).toContain('O prazo tem de ser um número inteiro não negativo.');
      expect(bloco('Deliberação')!.querySelector('.erro-campo')).toBeNull();
    });

    it('erro de campo: a mensagem da data de assinatura aparece junto da data do Texto do Ato', () => {
      configurar(true, { errosCampo: { dataAssinatura: 'Indique uma data válida.' } });

      expect(bloco('Texto do Ato')!.querySelector('.erro-campo')?.textContent).toContain('Indique uma data válida.');
    });

    it('com a data limite da Dinâmica, a Data da deliberação tem a ajuda com a data, ligada ao campo', () => {
      configurar(true, { dataLimiteDinamica: '2027-03-31' });

      const deliberacao = bloco('Deliberação')!;
      const data = campo(deliberacao, '#informacao-base-data-deliberacao');
      const ajuda = deliberacao.querySelector('#ajuda-data-deliberacao');
      expect(ajuda?.textContent?.trim()).toBe('Tem de ser igual ou anterior a 31/03/2027, data limite da dinâmica.');
      expect(data.getAttribute('aria-describedby')).toBe('ajuda-data-deliberacao');
      expect(data.disabled).toBe(false);
    });

    it('sem a data limite da Dinâmica, a Data da deliberação não tem ajuda nem descrição', () => {
      configurar(true, { dataLimiteDinamica: null });

      const deliberacao = bloco('Deliberação')!;
      expect(deliberacao.querySelector('#ajuda-data-deliberacao')).toBeNull();
      expect(deliberacao.textContent).not.toContain('data limite da dinâmica');
      expect(campo(deliberacao, '#informacao-base-data-deliberacao').hasAttribute('aria-describedby')).toBe(false);
    });

    it('com a data limite e o campo bloqueado, a descrição junta a ajuda e o aviso', () => {
      configurar(true, { dataLimiteDinamica: '2027-03-31', camposBloqueados: ['informacaoBase.deliberacao.dataDeliberacao'] });

      const data = campo(bloco('Deliberação')!, '#informacao-base-data-deliberacao');
      expect(data.getAttribute('aria-describedby')).toBe('ajuda-data-deliberacao bloqueado-data-deliberacao');
    });

    it('o 400 da data limite aparece junto de «Data da deliberação», a seguir à ajuda', () => {
      configurar(true, {
        dataLimiteDinamica: '2027-03-31',
        errosCampo: { 'informacaoBase.deliberacao.dataDeliberacao': 'Posterior à data limite da dinâmica (31/03/2027).' },
      });

      const campoData = bloco('Deliberação')!.querySelector('#informacao-base-data-deliberacao')!.closest('.campo')!;
      expect(campoData.querySelector('.ajuda-campo')).not.toBeNull();
      const erro = campoData.querySelector('.erro-campo');
      expect(erro?.textContent).toBe('Posterior à data limite da dinâmica (31/03/2027).');
      expect(erro?.getAttribute('role')).toBe('alert');
    });

    describe('coerência entre a Deliberação e o Texto do Ato, no ecrã', () => {
      const ERRO_PRAZO = 'O prazo tem de ser o mesmo da Deliberação.';
      const ERRO_AVALIACAO = 'A avaliação ambiental tem de ser a mesma da Deliberação.';
      const ERRO_DATA = 'A data da deliberação tem de ser igual ou anterior à data do texto do ato.';

      function sair(entrada: HTMLElement): void {
        entrada.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
        fixture.detectChanges();
      }

      function erros(separador: string): string[] {
        return Array.from(bloco(separador)!.querySelectorAll('.erro-campo')).map((erro) => erro.textContent!.trim());
      }

      /** O prazo é um número e a unidade da lista (US-549); sem escolha, a unidade é anos. */
      function prazo(separador: string): HTMLInputElement {
        return campo(bloco(separador)!, 'input[aria-label="Prazo"]') as HTMLInputElement;
      }

      function unidade(separador: string, valor: 'prazoAnos' | 'prazoMeses' | 'prazoDias'): void {
        const lista = bloco(separador)!.querySelector('.prazo select') as HTMLSelectElement;
        lista.value = valor;
        lista.dispatchEvent(new Event('change'));
        fixture.detectChanges();
      }

      function escolher(separador: string, valor: 'Sim' | 'Não'): HTMLInputElement {
        const opcao = Array.from(bloco(separador)!.querySelectorAll('label.opcao')).find((rotulo) => rotulo.textContent!.trim() === valor)!;
        const radio = opcao.querySelector('input') as HTMLInputElement;
        radio.click();
        fixture.detectChanges();
        return radio;
      }

      it('o prazo diferente aparece no prazo do Texto do Ato ao sair do campo, e não antes; corrigido, desaparece', () => {
        configurar();
        escrever(prazo('Deliberação'), '1');
        const anosTextoAto = prazo('Texto do Ato');
        escrever(anosTextoAto, '2');
        fixture.detectChanges();
        expect(erros('Texto do Ato')).toEqual([]);

        sair(anosTextoAto);
        expect(erros('Texto do Ato')).toEqual([ERRO_PRAZO]);
        expect(bloco('Texto do Ato')!.querySelector('.erro-campo')!.getAttribute('role')).toBe('alert');
        expect(erros('Deliberação')).toEqual([]);

        escrever(anosTextoAto, '1');
        fixture.detectChanges();
        expect(erros('Texto do Ato')).toEqual([]);
      });

      it('1 ano não é 12 meses, e um prazo vazio conta como 0', () => {
        configurar();
        escrever(prazo('Deliberação'), '1');
        unidade('Texto do Ato', 'prazoMeses');
        const prazoTextoAto = prazo('Texto do Ato');
        escrever(prazoTextoAto, '12');
        sair(prazoTextoAto);
        expect(erros('Texto do Ato')).toEqual([ERRO_PRAZO]);

        unidade('Texto do Ato', 'prazoAnos');
        escrever(prazoTextoAto, '1');
        fixture.detectChanges();
        expect(erros('Texto do Ato')).toEqual([]);
      });

      it('a avaliação ambiental diferente aparece na do Texto do Ato; igual, desaparece', () => {
        configurar();
        escolher('Deliberação', 'Sim');
        sair(escolher('Texto do Ato', 'Não'));
        expect(erros('Texto do Ato')).toEqual([ERRO_AVALIACAO]);

        escolher('Texto do Ato', 'Sim');
        expect(erros('Texto do Ato')).toEqual([]);
      });

      it('a data da deliberação depois da data de assinatura aparece na data da deliberação; igual, passa', () => {
        configurar();
        escrever(campo(bloco('Texto do Ato')!, '#informacao-base-data-assinatura'), '2027-03-31');
        const dataDeliberacao = campo(bloco('Deliberação')!, '#informacao-base-data-deliberacao');
        escrever(dataDeliberacao, '2027-04-01');
        sair(dataDeliberacao);
        expect(erros('Deliberação')).toEqual([ERRO_DATA]);

        escrever(dataDeliberacao, '2027-03-31');
        fixture.detectChanges();
        expect(erros('Deliberação')).toEqual([]);
      });

      it('com um dos lados vazio não há comparação', () => {
        configurar();
        const anosDeliberacao = prazo('Deliberação');
        escrever(anosDeliberacao, '1');
        escolher('Deliberação', 'Sim');
        const dataDeliberacao = campo(bloco('Deliberação')!, '#informacao-base-data-deliberacao');
        escrever(dataDeliberacao, '2030-01-01');
        sair(dataDeliberacao);

        expect(erros('Deliberação')).toEqual([]);
        expect(erros('Texto do Ato')).toEqual([]);
      });

      it('o 400 da submissão mostra o prazo, a avaliação ambiental e a data nos campos respetivos', () => {
        configurar(true, {
          errosCampo: {
            'informacaoBase.textoAto.prazoAnos': ERRO_PRAZO,
            'informacaoBase.textoAto.avaliacaoAmbiental': ERRO_AVALIACAO,
            'informacaoBase.deliberacao.dataDeliberacao': ERRO_DATA,
          },
        });

        expect(erros('Texto do Ato')).toEqual([ERRO_PRAZO, ERRO_AVALIACAO]);
        const campoData = campo(bloco('Deliberação')!, '#informacao-base-data-deliberacao').closest('.campo')!;
        expect(campoData.querySelector('.erro-campo')!.textContent).toBe(ERRO_DATA);
      });

      it('o erro do servidor de uma regra deixa de aparecer quando o utilizador corrige um dos seus campos', () => {
        configurar(true, {
          informacaoBase: { deliberacao: { ...INFORMACAO_BASE.deliberacao, avaliacaoAmbiental: true }, textoAto: { ...INFORMACAO_BASE.textoAto } },
          errosCampo: {
            'informacaoBase.textoAto.avaliacaoAmbiental': ERRO_AVALIACAO,
            'informacaoBase.textoAto.prazoAnos': ERRO_PRAZO,
          },
        });

        sair(escolher('Texto do Ato', 'Sim'));

        expect(erros('Texto do Ato')).toEqual([ERRO_PRAZO]);
      });

      it('um novo 400 volta a mostrar os erros do servidor', () => {
        configurar(true, { errosCampo: { 'informacaoBase.textoAto.avaliacaoAmbiental': ERRO_AVALIACAO } });
        escolher('Texto do Ato', 'Sim');
        expect(erros('Texto do Ato')).toEqual([]);

        fixture.componentRef.setInput('errosCampo', { 'informacaoBase.textoAto.avaliacaoAmbiental': ERRO_AVALIACAO });
        fixture.detectChanges();

        expect(erros('Texto do Ato')).toEqual([ERRO_AVALIACAO]);
      });
    });

    it('erro de campo: mostra as mensagens da data e do texto a publicar da Deliberação', () => {
      configurar(true, {
        errosCampo: {
          'informacaoBase.deliberacao.dataDeliberacao': 'Indique uma data válida.',
          'informacaoBase.deliberacao.textoPublicar': 'O texto a publicar excede 2000 caracteres.',
        },
      });

      const mensagens = Array.from(bloco('Deliberação')!.querySelectorAll('.erro-campo')).map((e) => e.textContent);
      expect(mensagens).toEqual(['Indique uma data válida.', 'O texto a publicar excede 2000 caracteres.']);
    });

    it('erro de elemento: a recusa de um elemento não previsto aparece no bloco desse elemento', () => {
      configurar(true, { errosCampo: { 'informacaoBase.textoAto': 'Este procedimento não prevê Informação Base no Texto do ato.' } });

      const alerta = bloco('Texto do Ato')!.querySelector('.erro-campo');
      expect(alerta?.textContent).toContain('Este procedimento não prevê Informação Base no Texto do ato.');
      expect(alerta?.getAttribute('role')).toBe('alert');
      expect(bloco('Deliberação')!.querySelector('.erro-campo')).toBeNull();
    });

    it('erro de elemento: mostra a recusa de cada elemento no seu bloco', () => {
      configurar(true, {
        errosCampo: {
          'informacaoBase.deliberacao': 'Este procedimento não prevê Informação Base na Deliberação.',
          'informacaoBase.textoAto': 'Este procedimento não prevê Informação Base no Texto do ato.',
        },
      });

      expect(bloco('Deliberação')!.querySelector('.erro-campo')?.textContent).toContain('na Deliberação.');
      expect(bloco('Texto do Ato')!.querySelector('.erro-campo')?.textContent).toContain('no Texto do ato.');
    });

    it('só de leitura: mostra os valores gravados, sem entradas editáveis', () => {
      configurar(false, { informacaoBase: INFORMACAO_BASE, dataAssinatura: '2026-09-02' });

      const deliberacao = bloco('Deliberação')!;
      expect(deliberacao.textContent).toContain('1 ano, 2 meses, 3 dias');
      expect(deliberacao.textContent).toContain('Sim');
      expect(deliberacao.textContent).toContain('01/09/2026');
      expect(deliberacao.textContent).toContain('Texto a publicar.');
      const textoAto = bloco('Texto do Ato')!;
      expect(textoAto.textContent).toContain('4 anos');
      expect(textoAto.textContent).toContain('Não');
      expect(textoAto.textContent).toContain('02/09/2026');
      expect(fixture.nativeElement.querySelectorAll('.informacao-base input, .informacao-base textarea')).toHaveLength(0);
    });

    it('só de leitura sem valores gravados mostra traços', () => {
      configurar(false);

      expect(bloco('Deliberação')!.querySelectorAll('dd')).toHaveLength(4);
      expect(Array.from(bloco('Deliberação')!.querySelectorAll('dd')).map((dd) => dd.textContent?.trim())).toEqual(['—', '—', '—', '—']);
      expect(Array.from(bloco('Texto do Ato')!.querySelectorAll('dd')).map((dd) => dd.textContent?.trim())).toEqual(['—', '—', '—']);
    });

    describe('depois da primeira submissão', () => {
      const DELIBERACAO = ['prazoAnos', 'prazoMeses', 'prazoDias', 'avaliacaoAmbiental', 'dataDeliberacao', 'textoPublicar'].map(
        (campo) => `informacaoBase.deliberacao.${campo}`,
      );
      const TEXTO_ATO = ['prazoAnos', 'prazoMeses', 'prazoDias', 'avaliacaoAmbiental'].map((campo) => `informacaoBase.textoAto.${campo}`);
      const TODAS = [...DELIBERACAO, ...TEXTO_ATO, 'dataAssinatura'];

      function abrirBloqueado(camposBloqueados: string[]): void {
        configurar(true, { informacaoBase: INFORMACAO_BASE, dataAssinatura: '2026-09-02', camposBloqueados });
      }

      it('desativa todos os controlos da lista', () => {
        abrirBloqueado(TODAS);

        expect(fixture.componentInstance.form.enabled).toBe(false);
        expect(fixture.componentInstance.form.controls.deliberacao.disabled).toBe(true);
        expect(fixture.componentInstance.form.controls.textoAto.disabled).toBe(true);
        expect(fixture.componentInstance.form.controls.dataAssinatura.disabled).toBe(true);
        const campos = Array.from(fixture.nativeElement.querySelectorAll('.informacao-base input, .informacao-base textarea')) as HTMLInputElement[];
        expect(campos.length).toBeGreaterThan(0);
        expect(campos.every((c) => c.disabled)).toBe(true);
      });

      it('mostra o aviso por baixo de cada campo e liga-o por aria-describedby', () => {
        abrirBloqueado(TODAS);

        const deliberacao = bloco('Deliberação')!;
        const avisos = Array.from(deliberacao.querySelectorAll('.aviso-bloqueado')).map((aviso) => aviso.textContent?.trim());
        expect(avisos).toEqual(Array(4).fill('Bloqueado depois da primeira submissão'));
        expect(campo(deliberacao, 'textarea').getAttribute('aria-describedby')).toBe('bloqueado-texto-publicar');
        expect(deliberacao.querySelector('#bloqueado-texto-publicar')?.textContent).toContain('Bloqueado depois da primeira submissão');
        expect(deliberacao.querySelector('fieldset')!.getAttribute('aria-describedby')).toBe('bloqueado-prazo-deliberacao');
        expect(bloco('Texto do Ato')!.querySelectorAll('.aviso-bloqueado')).toHaveLength(3);
      });

      it('os valores gravados continuam a contar no que o ecrã envia', () => {
        abrirBloqueado(TODAS);

        expect(fixture.componentInstance.valores()).toEqual({ informacaoBase: INFORMACAO_BASE, dataAssinatura: '2026-09-02' });
      });

      it('só desativa as chaves da lista', () => {
        abrirBloqueado(['informacaoBase.deliberacao.textoPublicar']);

        const { deliberacao, dataAssinatura } = fixture.componentInstance.form.controls;
        expect(deliberacao.controls.textoPublicar.disabled).toBe(true);
        expect(deliberacao.controls.prazoAnos.enabled).toBe(true);
        expect(dataAssinatura.enabled).toBe(true);
        expect(bloco('Deliberação')!.querySelectorAll('.aviso-bloqueado')).toHaveLength(1);
      });

      it('sem camposBloqueados nada fica desativado nem avisado', () => {
        abrirBloqueado([]);

        expect(fixture.componentInstance.form.enabled).toBe(true);
        expect(fixture.nativeElement.querySelector('.aviso-bloqueado')).toBeNull();
      });
    });
  });

  describe('Observações (FR-137)', () => {
    function escrever(texto: string): HTMLTextAreaElement {
      const caixa = fixture.nativeElement.querySelector('.observacoes textarea') as HTMLTextAreaElement;
      caixa.value = texto;
      caixa.dispatchEvent(new Event('input'));
      return caixa;
    }

    it('mostra uma caixa por separador com itens sem ficheiro, com rótulo e ajuda ligados', () => {
      configurar(true);

      const caixa = fixture.nativeElement.querySelector('.observacoes textarea') as HTMLTextAreaElement;
      const rotulo = fixture.nativeElement.querySelector('.observacoes label') as HTMLLabelElement;
      expect(rotulo.htmlFor).toBe(caixa.id);
      expect(caixa.getAttribute('aria-describedby')).toContain('observacoes-ajuda-0');
      expect(caixa.maxLength).toBe(1000);
    });

    it('não mostra a caixa quando todos os itens do separador têm ficheiro', () => {
      estrutura.mockReturnValue(
        of(
          estruturaCom({
            separadores: [
              {
                ...estruturaCom().separadores[0],
                publicacao: [item({ ficheiros: [documento()], pendente: false })],
                deposito: [item({ documentoConfigId: 2, ficheiros: [documento({ id: 'd2' })], pendente: false })],
              },
            ],
          }),
        ),
      );
      configurar(true);

      expect(fixture.nativeElement.querySelector('#observacoes-0')).toBeNull();
    });

    it('são obrigatórias quando falta o ficheiro de um item obrigatório', () => {
      configurar(true);

      const caixa = fixture.nativeElement.querySelector('#observacoes-0') as HTMLTextAreaElement;
      expect(caixa.required).toBe(true);
      expect(caixa.closest('.observacoes')?.querySelector('label .obrigatorio')).not.toBeNull();
      expect(caixa.getAttribute('placeholder')).toBe('Indique o motivo de não carregar ficheiro');
    });

    it('continuam opcionais quando só faltam ficheiros de itens opcionais', () => {
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ ...estruturaCom().separadores[0], publicacao: [item({ obrigatorio: false, pendente: false })] }] })),
      );
      configurar(true);

      const caixa = fixture.nativeElement.querySelector('#observacoes-0') as HTMLTextAreaElement;
      expect(caixa.required).toBe(false);
      expect(caixa.closest('.observacoes')?.querySelector('label .obrigatorio')).toBeNull();
      expect(caixa.getAttribute('placeholder')).toContain('(opcional)');
    });

    it('mostra as observações já gravadas', () => {
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ ...estruturaCom().separadores[0], publicacao: [item({ observacoes: 'NA', pendente: false })] }] })),
      );
      configurar(true);

      expect((fixture.nativeElement.querySelector('.observacoes textarea') as HTMLTextAreaElement).value).toBe('NA');
    });

    it('gravarObservacoes escreve o texto do separador em cada item sem ficheiro e emite verdadeiro', () => {
      configurar(true);
      escrever('  Não se aplica  ');
      const resultados: boolean[] = [];

      fixture.componentInstance.gravarObservacoes().subscribe((ok) => resultados.push(ok));

      expect(gravarObservacoes).toHaveBeenCalledTimes(2);
      expect(gravarObservacoes).toHaveBeenCalledWith('p1', 1, 'Não se aplica');
      expect(gravarObservacoes).toHaveBeenCalledWith('p1', 2, 'Não se aplica');
      expect(resultados).toEqual([true]);
    });

    it('gravarObservacoes não faz pedidos sem texto escrito nem para itens com ficheiro', () => {
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ ...estruturaCom().separadores[0], deposito: [item({ documentoConfigId: 2, ficheiros: [documento()] })] }] })),
      );
      configurar(true);
      const resultados: boolean[] = [];

      fixture.componentInstance.gravarObservacoes().subscribe((ok) => resultados.push(ok));
      expect(gravarObservacoes).not.toHaveBeenCalled();
      expect(resultados).toEqual([true]);

      escrever('NA');
      fixture.componentInstance.gravarObservacoes().subscribe();
      expect(gravarObservacoes).toHaveBeenCalledTimes(1);
      expect(gravarObservacoes).toHaveBeenCalledWith('p1', 1, 'NA');
    });

    it('um 400 aparece junto da caixa, anunciado, e emite falso', () => {
      gravarObservacoes.mockReturnValue(
        throwError(
          () => new ApiError('DOC_OBSERVACOES_VAZIAS', 'Documento inválido.', 't1', [{ path: 'observacoes', erro: 'Indique as observações («NA» se não aplicável).' }]),
        ),
      );
      configurar(true);
      const caixa = escrever('x');
      const resultados: boolean[] = [];

      fixture.componentInstance.gravarObservacoes().subscribe((ok) => resultados.push(ok));
      fixture.detectChanges();

      expect(resultados).toEqual([false]);
      const erro = fixture.nativeElement.querySelector('.observacoes .erro-campo') as HTMLElement;
      expect(erro.getAttribute('role')).toBe('alert');
      expect(erro.textContent).toContain('Indique as observações');
      expect(caixa.getAttribute('aria-describedby')).toContain(erro.id);
    });

    it('em leitura mostra as observações gravadas de um item, sem campo editável', () => {
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ ...estruturaCom().separadores[0], publicacao: [item({ observacoes: 'Não se aplica', pendente: false })] }] })),
      );
      configurar(false);

      expect(texto()).toContain('Observações: Não se aplica');
      expect(fixture.nativeElement.querySelector('.observacoes textarea')).toBeNull();
    });
  });

  describe('no assistente, com o aspeto do Figma da Elaboração (embutido)', () => {
    it('marca o anfitrião com a classe embutido e põe cada separador com o ícone do Figma', () => {
      configurar(true, { embutido: true });

      expect((fixture.nativeElement as HTMLElement).classList).toContain('embutido');
      expect(fixture.nativeElement.querySelectorAll('.acordeao-cabecalho .icone-caixa').length).toBe(2);
      expect(fixture.nativeElement.querySelector('.zona .zona-icone')).not.toBeNull();
    });

    it('abre só o primeiro separador e fecha os restantes e «Outros», como no Figma', () => {
      estrutura.mockReturnValue(
        of(
          estruturaCom({
            separadores: [
              ...estruturaCom().separadores,
              { separador: 'Texto do Ato', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [item({ documentoConfigId: 3 })], deposito: [] },
            ],
          }),
        ),
      );
      configurar(true, { embutido: true });

      const cabecalhos = Array.from(fixture.nativeElement.querySelectorAll('.acordeao-cabecalho') as NodeListOf<HTMLButtonElement>);
      expect(cabecalhos.map((cabecalho) => cabecalho.getAttribute('aria-expanded'))).toEqual(['true', 'false', 'false']);
    });

    it('fora do assistente mantém o aspeto anterior, sem seletor de prazo nem Validar', () => {
      configurar(true);

      expect((fixture.nativeElement as HTMLElement).classList).not.toContain('embutido');
      expect(fixture.nativeElement.querySelector('.seletor-prazo')).toBeNull();
      expect(botao('Validar')).toBeUndefined();
      expect(fixture.nativeElement.querySelector('input[aria-label="Prazo"]')).not.toBeNull();
    });

    it('o seletor do Prazo mostra as unidades vazio, abre os três campos e resume o que se escreve', () => {
      configurar(true, { embutido: true });
      const seletor = fixture.nativeElement.querySelector('.seletor-prazo') as HTMLButtonElement;
      const painel = fixture.nativeElement.querySelector('#prazo-painel-deliberacao') as HTMLElement;

      expect(seletor.textContent?.trim()).toBe('anos, meses, dias');
      expect(seletor.getAttribute('aria-expanded')).toBe('false');
      expect(painel.hidden).toBe(true);

      seletor.click();
      fixture.detectChanges();
      expect(seletor.getAttribute('aria-expanded')).toBe('true');
      expect(painel.hidden).toBe(false);
    });

    it('Escape fecha o painel do Prazo e devolve o foco ao seletor', () => {
      configurar(true, { embutido: true });
      const seletor = fixture.nativeElement.querySelector('.seletor-prazo') as HTMLButtonElement;
      seletor.click();
      fixture.detectChanges();
      const painel = fixture.nativeElement.querySelector('#prazo-painel-deliberacao') as HTMLElement;
      const anos = painel.querySelector('input[aria-label="Prazo"]') as HTMLInputElement;

      anos.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();

      expect(painel.hidden).toBe(true);
      expect(document.activeElement).toBe(seletor);
    });

    it('a ajuda das Observações aparece sempre por baixo do rótulo e fica ligada à caixa', () => {
      configurar(true, { embutido: true });
      const texto = fixture.nativeElement.querySelector('#observacoes-ajuda-0') as HTMLElement;

      expect(fixture.nativeElement.querySelector('.observacoes .ajuda')).toBeNull();
      expect(texto.hidden).toBe(false);
      expect(texto.textContent).toContain('Se não carregar ficheiro, indique o motivo');
      expect((fixture.nativeElement.querySelector('.observacoes textarea') as HTMLTextAreaElement).getAttribute('aria-describedby')).toContain(
        'observacoes-ajuda-0',
      );
    });

    it('no assistente, a Informação Base não tem botão Validar: grava-se com o Seguinte ou o Guardar rascunho', () => {
      configurar(true, { embutido: true });

      expect(botao('Validar')).toBeUndefined();
    });

    it('cada cabeçalho que se recolhe aponta, por aria-controls, para o corpo que existe na página', () => {
      configurar(true, { embutido: true });
      const alternadores = Array.from(
        fixture.nativeElement.querySelectorAll('.acordeao-cabecalho, .informacao-base-alternar') as NodeListOf<HTMLButtonElement>,
      );

      expect(alternadores.length).toBeGreaterThan(0);
      for (const alternador of alternadores) {
        const alvo = alternador.getAttribute('aria-controls');
        expect(alvo).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#' + alvo)).not.toBeNull();
      }
    });

    it('Escape no próprio seletor fecha o painel do Prazo', () => {
      configurar(true, { embutido: true });
      const seletor = fixture.nativeElement.querySelector('.seletor-prazo') as HTMLButtonElement;
      seletor.click();
      fixture.detectChanges();

      seletor.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();

      expect(seletor.getAttribute('aria-expanded')).toBe('false');
      expect((fixture.nativeElement.querySelector('#prazo-painel-deliberacao') as HTMLElement).hidden).toBe(true);
    });

    it('o check mostra se o item é obrigatório, sem etiqueta, e o leitor de ecrã ouve também se já tem ficheiro', () => {
      configurar(true, { embutido: true });
      const [obrigatorio, opcional] = Array.from(fixture.nativeElement.querySelectorAll('.caixa .marca-caixa')) as HTMLElement[];

      expect(obrigatorio.classList).toContain('marca-caixa-com');
      expect(opcional.classList).not.toContain('marca-caixa-com');
      expect(fixture.nativeElement.querySelector('.caixa .marca')).toBeNull();
      const leitor = fixture.nativeElement.querySelector('.caixa .caixa-cabecalho .so-leitor') as HTMLElement;
      expect(leitor.textContent).toMatch(/obrigatório/);
      expect(leitor.textContent).toMatch(/sem ficheiro/);
    });

    it('abre o separador fechado onde o servidor apontou um erro', () => {
      estrutura.mockReturnValue(
        of(
          estruturaCom({
            separadores: [
              ...estruturaCom().separadores,
              { separador: 'Texto do Ato', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [item({ documentoConfigId: 3 })], deposito: [] },
            ],
          }),
        ),
      );
      configurar(true, { embutido: true, errosCampo: { 'informacaoBase.textoAto.prazoAnos': 'O prazo tem de ser um número inteiro não negativo.' } });

      const cabecalhos = Array.from(fixture.nativeElement.querySelectorAll('.acordeao-cabecalho') as NodeListOf<HTMLButtonElement>);
      expect(cabecalhos[1].getAttribute('aria-expanded')).toBe('true');
    });

    it('fora do assistente, um erro do servidor não reabre o separador que o utilizador fechou', () => {
      configurar(true);
      const cabecalho = fixture.nativeElement.querySelector('.acordeao-cabecalho') as HTMLButtonElement;
      cabecalho.click();
      fixture.detectChanges();
      expect(cabecalho.getAttribute('aria-expanded')).toBe('false');

      fixture.componentRef.setInput('errosCampo', { 'informacaoBase.deliberacao.prazoAnos': 'O prazo tem de ser um número inteiro não negativo.' });
      fixture.detectChanges();

      expect(cabecalho.getAttribute('aria-expanded')).toBe('false');
    });

    it('a Informação Base recolhe-se pelo título', () => {
      configurar(true, { embutido: true });
      const alternar = fixture.nativeElement.querySelector('.informacao-base-alternar') as HTMLButtonElement;

      alternar.click();
      fixture.detectChanges();

      expect(alternar.getAttribute('aria-expanded')).toBe('false');
      expect(fixture.nativeElement.querySelector('.seletor-prazo')).toBeNull();
    });
  });

  describe('tags de depósito obrigatório', () => {
    const MSG_TAGS = 'Só é possível associar o ficheiro a itens de depósito obrigatório.';

    /** A e B: depósito obrigatório; C: depósito opcional; D: depósito obrigatório só jpg; P: publicação obrigatória. */
    function estruturaTags(ficheirosA: DocumentoProcesso[] = [], ficheirosB: DocumentoProcesso[] = []): EstruturaDocumental {
      return estruturaCom({
        separadores: [
          {
            separador: 'Relatório',
            elementoInformacaoBase: null,
            publicacao: [item({ documentoConfigId: 13, codigo: 'P', nome: 'Item P' })],
            deposito: [
              item({ documentoConfigId: 10, codigo: 'A', nome: 'Item A', categoria: 'DEPOSITO', ficheiros: ficheirosA, pendente: ficheirosA.length === 0 }),
              item({ documentoConfigId: 11, codigo: 'B', nome: 'Item B', categoria: 'DEPOSITO', ficheiros: ficheirosB, pendente: ficheirosB.length === 0 }),
              item({ documentoConfigId: 12, codigo: 'C', nome: 'Item C', categoria: 'DEPOSITO', obrigatorio: false, pendente: false }),
              item({ documentoConfigId: 14, codigo: 'D', nome: 'Item D', categoria: 'DEPOSITO', extensoesAceites: ['jpg'], pendente: false }),
            ],
          },
        ],
        pendencias: [],
      });
    }

    function caixaDe(nome: string): HTMLElement {
      const caixas = Array.from(fixture.nativeElement.querySelectorAll('.caixa')) as HTMLElement[];
      return caixas.find((caixa) => caixa.querySelector('.item-nome')?.textContent?.trim() === nome)!;
    }

    function rotulosDasTags(caixa: HTMLElement): string[] {
      return Array.from(caixa.querySelectorAll('.tags label')).map((rotulo) => (rotulo as HTMLElement).textContent!.trim());
    }

    it('mostra na zona de A o bloco com uma caixa por cada outro item de depósito obrigatório, com legenda e label', () => {
      estrutura.mockReturnValue(of(estruturaTags()));
      configurar();

      const caixaA = caixaDe('Item A');
      const bloco = caixaA.querySelector('fieldset.tags') as HTMLFieldSetElement;
      expect(bloco.querySelector('legend')?.textContent?.trim()).toBe('Este ficheiro também serve para');
      expect(rotulosDasTags(caixaA)).toEqual(['Relatório — Item B']);
      const caixaTag = bloco.querySelector('input[type="checkbox"]') as HTMLInputElement;
      expect(caixaTag.disabled).toBe(false);
      expect(bloco.querySelector(`label[for="${caixaTag.id}"]`)).not.toBeNull();
    });

    it('só mostra o bloco nas zonas de depósito obrigatório (EF: «ficheiros para depósito obrigatório»)', () => {
      estrutura.mockReturnValue(of(estruturaTags()));
      configurar();

      expect(caixaDe('Item P').querySelector('fieldset.tags')).toBeNull();
      expect(caixaDe('Item C').querySelector('fieldset.tags')).toBeNull();
      expect(rotulosDasTags(caixaDe('Item B'))).toEqual(['Relatório — Item A']);
    });

    it('não oferece um item que não aceita nenhuma das extensões do item de origem', () => {
      estrutura.mockReturnValue(of(estruturaTags()));
      configurar();

      expect(rotulosDasTags(caixaDe('Item A'))).not.toContain('Relatório — Item D');
      expect(caixaDe('Item D').querySelector('fieldset.tags')).toBeNull();
    });

    it('não mostra o bloco quando não há itens elegíveis', () => {
      configurar();

      expect(fixture.nativeElement.querySelector('fieldset.tags')).toBeNull();
    });

    it('não mostra o bloco em leitura', () => {
      estrutura.mockReturnValue(of(estruturaTags()));
      configurar(false);

      expect(fixture.nativeElement.querySelector('fieldset.tags')).toBeNull();
    });

    it('envia as tags escolhidas no upload dessa zona e limpa as caixas no fim', () => {
      estrutura.mockReturnValue(of(estruturaTags()));
      configurar();
      const caixaTag = caixaDe('Item A').querySelector('.tags input') as HTMLInputElement;
      caixaTag.click();
      fixture.detectChanges();
      const ficheiro = new File(['x'], 'relatorio.pdf');

      escolherFicheiro(caixaDe('Item A').querySelector('.zona .ficheiro-input') as HTMLInputElement, ficheiro);
      fixture.detectChanges();

      expect(carregar).toHaveBeenCalledWith('p1', ficheiro, 'DEPOSITO', 10, [11]);
      expect((caixaDe('Item A').querySelector('.tags input') as HTMLInputElement).checked).toBe(false);
      expect(fixture.componentInstance.tagsEscolhidas()).toEqual({});
    });

    it('limpa as caixas e mostra no item, com role alert, a recusa das tags', () => {
      carregar.mockReturnValue(throwError(() => new ApiError('DOC_CLASSIFICACAO_INVALIDA', 'Documento inválido.', 't1', [{ path: 'tags', erro: MSG_TAGS }])));
      estrutura.mockReturnValue(of(estruturaTags()));
      configurar();
      (caixaDe('Item A').querySelector('.tags input') as HTMLInputElement).click();
      fixture.detectChanges();

      escolherFicheiro(caixaDe('Item A').querySelector('.zona .ficheiro-input') as HTMLInputElement, new File(['x'], 'r.pdf'));
      fixture.detectChanges();

      const erro = caixaDe('Item A').querySelector('.erro-item') as HTMLElement;
      expect(erro.getAttribute('role')).toBe('alert');
      expect(erro.textContent).toContain(MSG_TAGS);
      expect((caixaDe('Item A').querySelector('.tags input') as HTMLInputElement).checked).toBe(false);
    });

    it('mostra o ficheiro associado por tag com a origem e só a ação Descarregar', () => {
      const comTag = documento({ id: 'd10', documentoConfigId: 10, nomeFicheiro: 'relatorio.pdf', categoria: 'DEPOSITO', tags: [11] });
      estrutura.mockReturnValue(of(estruturaTags([comTag], [comTag])));
      configurar();

      const caixaB = caixaDe('Item B');
      expect(caixaB.textContent).toContain('relatorio.pdf');
      expect(caixaB.textContent).toContain('Associado a partir de: Item A');
      const acoes = Array.from(caixaB.querySelectorAll('.ficheiro-acoes button, .ficheiro-acoes label')).map((acao) => acao.textContent!.trim());
      expect(acoes).toEqual(['Descarregar']);
      const caixaA = caixaDe('Item A');
      expect(caixaA.textContent).not.toContain('Associado a partir de');
      expect(caixaA.querySelector('.acao-perigo')?.textContent?.trim()).toBe('Remover');
    });

    it('conta uma só vez o ficheiro que aparece no item principal e no da tag', () => {
      const comTag = documento({ id: 'd10', documentoConfigId: 10, categoria: 'DEPOSITO', tags: [11] });
      estrutura.mockReturnValue(of(estruturaTags([comTag], [comTag])));
      const emitidos: DocumentoProcesso[][] = [];
      configurar();
      fixture.componentInstance.ficheirosAlterados.subscribe((ficheiros) => emitidos.push(ficheiros));
      fixture.componentInstance.recarregar();

      expect(emitidos.at(-1)!.map((ficheiro) => ficheiro.id)).toEqual(['d10']);
    });
  });

  describe('em leitura', () => {
    function estruturaComFicheiro(): EstruturaDocumental {
      return estruturaCom({
        separadores: [
          { separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [documento()], pendente: false })], deposito: [] },
          { separador: 'Texto do Ato', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [], deposito: [] },
        ],
        pendencias: [],
      });
    }

    it('sem ficheiros mostra «Sem documentos carregados» e mantém o bloco da Informação Base', () => {
      configurar(false);

      expect(texto()).toContain('Sem documentos carregados');
      expect(fixture.nativeElement.querySelectorAll('.informacao-base')).toHaveLength(1);
    });

    it('com ficheiros não mostra «Sem documentos carregados»', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro()));
      configurar(false);

      expect(texto()).not.toContain('Sem documentos carregados');
    });

    it('em edição não mostra «Sem documentos carregados»', () => {
      configurar(true);

      expect(texto()).not.toContain('Sem documentos carregados');
    });

    it('não oferece carregar, substituir nem remover, mas deixa descarregar', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro()));
      configurar(false);

      expect(texto()).not.toContain('Escolher ficheiro');
      expect(texto()).not.toContain('Substituir');
      expect(texto()).not.toContain('Remover');
      expect(botao('Descarregar')).toBeDefined();
    });

    it('emite todos os ficheiros da estrutura, também os de «outros»', () => {
      const outro = documento({ id: 'd2', documentoConfigId: null, categoria: 'DEPOSITO' });
      estrutura.mockReturnValue(of({ ...estruturaComFicheiro(), outros: [outro] }));
      const emitidos: DocumentoProcesso[][] = [];
      TestBed.configureTestingModule({ providers: [{ provide: DocumentoService, useValue: { estrutura } }] });
      fixture = TestBed.createComponent(ProcessoDocumentosComponent);
      fixture.componentRef.setInput('processoId', 'p1');
      fixture.componentInstance.ficheirosAlterados.subscribe((ficheiros) => emitidos.push(ficheiros));

      fixture.detectChanges();

      expect(emitidos).toEqual([[documento(), outro]]);
    });

    it('desenha a ação do ecrã em cada ficheiro e em cada bloco da Informação Base', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro()));
      TestBed.configureTestingModule({ providers: [{ provide: DocumentoService, useValue: { estrutura } }] });
      const anfitriao = TestBed.createComponent(AnfitriaoComponent);

      anfitriao.detectChanges();

      const acoes = Array.from(anfitriao.nativeElement.querySelectorAll('.acao-teste') as NodeListOf<HTMLElement>).map((e) =>
        e.textContent!.trim(),
      );
      expect(acoes).toEqual(['DOCUMENTO:d1', 'INFORMACAO_BASE:DELIBERACAO', 'INFORMACAO_BASE:TEXTO_ATO']);
    });

    it('a ação do bloco do Texto do ato recebe o elemento TEXTO_ATO', () => {
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ separador: 'Texto do ato publicado', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [], deposito: [] }], pendencias: [] })),
      );
      TestBed.configureTestingModule({ providers: [{ provide: DocumentoService, useValue: { estrutura } }] });
      const anfitriao = TestBed.createComponent(AnfitriaoComponent);

      anfitriao.detectChanges();

      const bloco = anfitriao.nativeElement.querySelector('.informacao-base[aria-label="Informação Base — Texto do Ato"]') as HTMLElement;
      expect(bloco.querySelector('.acao-teste')?.textContent?.trim()).toBe('INFORMACAO_BASE:TEXTO_ATO');
    });

    it('na validação, o elemento esperado sem separador aparece à parte com o rótulo e o outro fica no seu separador', () => {
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ separador: 'Texto do Ato', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [], deposito: [] }], pendencias: [] })),
      );
      configurar(false, { elementosEsperados: ['DELIBERACAO', 'TEXTO_ATO'] });

      const deliberacao = fixture.nativeElement.querySelector('.informacao-base[aria-label="Informação Base — Deliberação"]') as HTMLElement;
      expect(deliberacao.closest('.acordeao')).toBeNull();
      expect(deliberacao.closest('section.grupo')?.querySelector('h2')?.textContent).toBe('Deliberação');
      const textoAto = fixture.nativeElement.querySelector('.informacao-base[aria-label="Informação Base — Texto do Ato"]') as HTMLElement;
      expect(textoAto.closest('.acordeao')?.querySelector('.acordeao-titulo')?.textContent).toBe('Texto do Ato');
    });

    it('sem separadores, mostra à parte os elementos esperados da Informação Base', () => {
      estrutura.mockReturnValue(of(estruturaCom({ separadores: [], pendencias: [] })));
      configurar(false, { elementosEsperados: ['DELIBERACAO', 'TEXTO_ATO'] });

      const blocos = Array.from(fixture.nativeElement.querySelectorAll('.informacao-base') as NodeListOf<HTMLElement>).map((e) =>
        e.getAttribute('aria-label'),
      );
      expect(blocos).toEqual(['Informação Base — Deliberação', 'Informação Base — Texto do Ato']);
    });

    it('não repete o elemento que um separador já mostra', () => {
      estrutura.mockReturnValue(of(estruturaComFicheiro()));
      configurar(false, { elementosEsperados: ['DELIBERACAO', 'TEXTO_ATO'] });

      expect(fixture.nativeElement.querySelectorAll('.informacao-base')).toHaveLength(2);
    });

    it('só mostra à parte o elemento esperado cujo separador falta', () => {
      estrutura.mockReturnValue(of(estruturaCom({ pendencias: [] })));
      configurar(false, { elementosEsperados: ['DELIBERACAO', 'TEXTO_ATO'] });

      const blocos = Array.from(fixture.nativeElement.querySelectorAll('.informacao-base') as NodeListOf<HTMLElement>).map((e) =>
        e.getAttribute('aria-label'),
      );
      expect(blocos).toEqual(['Informação Base — Deliberação', 'Informação Base — Texto do Ato']);
    });

    it('sem elementos esperados não mostra nenhum bloco à parte', () => {
      estrutura.mockReturnValue(of(estruturaCom({ separadores: [], pendencias: [] })));
      configurar(false);

      expect(fixture.nativeElement.querySelectorAll('.informacao-base')).toHaveLength(0);
    });

    it('em edição não mostra elementos à parte', () => {
      estrutura.mockReturnValue(of(estruturaCom({ separadores: [], pendencias: [] })));
      configurar(true, { elementosEsperados: ['DELIBERACAO', 'TEXTO_ATO'] });

      expect(fixture.nativeElement.querySelectorAll('.informacao-base')).toHaveLength(0);
    });

    it('se a estrutura não carrega, mostra o erro e Tentar novamente, que a lê de novo', () => {
      estrutura
        .mockReturnValueOnce(throwError(() => new ApiError('ERRO_INTERNO', 'Serviço indisponível.', 't1', null)))
        .mockReturnValueOnce(of(estruturaCom()));
      configurar(false);
      expect(texto()).toContain('Serviço indisponível.');

      botao('Tentar novamente').click();
      fixture.detectChanges();

      expect(estrutura).toHaveBeenCalledTimes(2);
      expect(texto()).not.toContain('Serviço indisponível.');
      expect(texto()).not.toContain('Tentar novamente');
      expect(texto()).toContain('Publicação INCM e Depósito DGT');
    });

    it('um erro depois de a estrutura estar carregada não oferece Tentar novamente', () => {
      configurar(true);
      fixture.componentInstance.erro.set(new ApiError('ESTADO_INVALIDO', 'Estado alterado.', 't1', null));
      fixture.detectChanges();

      expect(texto()).toContain('Estado alterado.');
      expect(texto()).not.toContain('Tentar novamente');
    });
  });
describe('planos ou programas associados', () => {
    function comServicosDaAssociacao(): void {
      TestBed.configureTestingModule({
        providers: [
          { provide: InstrumentoService, useValue: { listarConcelhos: vi.fn().mockReturnValue(of([])), pesquisarEmVigor: vi.fn() } },
          { provide: DinamicaService, useValue: { associarInstrumentos: vi.fn() } },
        ],
      });
    }

    function detalhe(parcial: Partial<ProcessoDetalhe>): ProcessoDetalhe {
      return {
        id: 'p1',
        dinamicaId: 'd1',
        associacaoInstrumentos: 'OPCIONAL',
        podeAssociarInstrumentos: false,
        tiposInstrumentoAdmitidos: [],
        instrumentosAbrangidos: [],
        ...parcial,
      } as ProcessoDetalhe;
    }

    it('aparece com o detalhe de uma Suspensão ou Medidas Preventivas, em leitura sem poder associar', () => {
      comServicosDaAssociacao();
      configurar(false, { processo: detalhe({}) });

      expect(texto()).toContain('Planos ou programas associados');
      expect(texto()).toContain('Sem IGT associado');
      expect(fixture.nativeElement.querySelector('app-planos-associados form')).toBeNull();
    });

    it('não aparece sem o detalhe nem numa Dinâmica UM', () => {
      comServicosDaAssociacao();
      configurar(true, { processo: detalhe({ associacaoInstrumentos: 'UM' }) });
      expect(texto()).not.toContain('Planos ou programas associados');

      fixture.componentRef.setInput('processo', null);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('app-planos-associados')).toBeNull();
    });
  });
  describe('destino do processo', () => {
    let guardarRascunho: ReturnType<typeof vi.fn>;

    const pedido = { sumario: 'Resumo.', elementosAfetados: ['PECAS_ESCRITAS'] };

    function comServicoDosProcessos(): void {
      guardarRascunho = vi.fn().mockReturnValue(of({ id: 'p1', versao: 4 }));
      TestBed.configureTestingModule({ providers: [{ provide: ProcessoService, useValue: { guardarRascunho } }] });
    }

    function detalhe(parcial: Partial<ProcessoDetalhe>): ProcessoDetalhe {
      return {
        id: 'p1',
        versao: 3,
        associacaoInstrumentos: 'UM',
        destino: 'PUBLICACAO_DEPOSITO',
        destinoEditavel: true,
        ...parcial,
      } as ProcessoDetalhe;
    }

    function grupo(): HTMLFieldSetElement | null {
      return fixture.nativeElement.querySelector('app-destino-processo fieldset');
    }

    function opcao(valor: string): HTMLInputElement {
      return fixture.nativeElement.querySelector(`app-destino-processo input[value="${valor}"]`);
    }

    function anuncio(): string {
      return (fixture.nativeElement.querySelector('p.so-leitor[aria-live="polite"]') as HTMLElement).textContent!.trim();
    }

    /** Só itens de depósito, como o servidor devolve depois de gravar «Só depósito». */
    function estruturaSoDeposito(): EstruturaDocumental {
      return estruturaCom({
        separadores: [
          {
            separador: 'Deliberação',
            elementoInformacaoBase: 'DELIBERACAO',
            publicacao: [],
            deposito: [item({ documentoConfigId: 2, codigo: 'DELIBERACAO_DEP', categoria: 'DEPOSITO' })],
          },
        ],
        pendencias: [{ documentoConfigId: 2, codigo: 'DELIBERACAO_DEP', nome: 'Deliberação', categoria: 'DEPOSITO', motivo: 'Falta.' }],
      });
    }

    it('num processo da REN sem ficheiros aparece antes dos separadores, editável', () => {
      comServicoDosProcessos();
      configurar(true, { processo: detalhe({}), pedidoRascunho: () => pedido });

      expect(grupo()).not.toBeNull();
      expect(grupo()!.querySelector('legend')!.textContent).toBe('Destino do processo');
      const destino = fixture.nativeElement.querySelector('app-destino-processo') as HTMLElement;
      const separadores = fixture.nativeElement.querySelector('.grupo') as HTMLElement;
      expect(destino.compareDocumentPosition(separadores) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(opcao('PUBLICACAO_DEPOSITO').checked).toBe(true);
      expect(opcao('SO_DEPOSITO').disabled).toBe(false);
    });

    it('escolher «Só depósito» grava logo com o pedido, a versao e o destino, relê a estrutura e anuncia', () => {
      comServicoDosProcessos();
      const gravados: unknown[] = [];
      configurar(true, { processo: detalhe({}), pedidoRascunho: () => pedido });
      fixture.componentInstance.destinoGravado.subscribe((gravado) => gravados.push(gravado));
      expect(texto()).toContain('Publicação');
      estrutura.mockReturnValue(of(estruturaSoDeposito()));

      opcao('SO_DEPOSITO').click();
      fixture.detectChanges();

      expect(guardarRascunho).toHaveBeenCalledWith('p1', { ...pedido, versao: 3, destino: 'SO_DEPOSITO' });
      expect(estrutura).toHaveBeenCalledTimes(2);
      expect(gravados).toEqual([{ versao: 4, destino: 'SO_DEPOSITO' }]);
      expect(anuncio()).toBe('Destino gravado.');
      const titulos = Array.from(fixture.nativeElement.querySelectorAll('.caixa-titulo')).map((e) => (e as HTMLElement).textContent);
      // O item e os Outros, sem as caixas de publicação (os Outros também, US-548 com US-287).
      expect(titulos).toEqual(['Depósito', 'Depósito']);
    });

    it('uma recusa fica junto do grupo, com a opção gravada outra vez marcada, e não relê a estrutura', () => {
      comServicoDosProcessos();
      guardarRascunho.mockReturnValue(
        throwError(() => new ApiError('ESTADO_INVALIDO', 'Remova os ficheiros carregados antes de mudar o destino.', 't1', null)),
      );
      configurar(true, { processo: detalhe({}), pedidoRascunho: () => pedido });

      opcao('SO_DEPOSITO').click();
      fixture.detectChanges();

      const erro = grupo()!.querySelector('.erro') as HTMLElement;
      expect(erro.textContent).toBe('Remova os ficheiros carregados antes de mudar o destino.');
      expect(grupo()!.getAttribute('aria-describedby')).toContain(erro.id);
      expect(opcao('PUBLICACAO_DEPOSITO').checked).toBe(true);
      expect(estrutura).toHaveBeenCalledTimes(1);
      expect(anuncio()).toBe('');
    });

    it('o 400 DESTINO_INVALIDO mostra a mensagem do campo destino', () => {
      comServicoDosProcessos();
      guardarRascunho.mockReturnValue(
        throwError(
          () =>
            new ApiError('DESTINO_INVALIDO', 'Este processo não pode ser só para depósito.', 't1', [
              { path: 'destino', erro: 'Este processo não pode ser só para depósito.' },
            ]),
        ),
      );
      configurar(true, { processo: detalhe({}), pedidoRascunho: () => pedido });

      opcao('SO_DEPOSITO').click();
      fixture.detectChanges();

      expect(grupo()!.querySelector('.erro')!.textContent).toBe('Este processo não pode ser só para depósito.');
    });

    it('com ficheiros carregados, o destino aparece só de leitura com a indicação de os remover', () => {
      comServicoDosProcessos();
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [], deposito: [item({ documentoConfigId: 2, categoria: 'DEPOSITO', ficheiros: [documento({ documentoConfigId: 2, categoria: 'DEPOSITO' })], pendente: false })] }], pendencias: [] })),
      );
      configurar(true, { processo: detalhe({ destino: 'SO_DEPOSITO', destinoEditavel: false }), pedidoRascunho: () => pedido });

      expect(grupo()).not.toBeNull();
      expect(grupo()!.querySelectorAll('input[type="radio"]')).toHaveLength(0);
      expect(grupo()!.textContent).toContain('Só depósito');
      expect(grupo()!.textContent).toContain('Para mudar o destino, remova primeiro os ficheiros carregados.');
    });

    it('carregar o primeiro ficheiro neste ecrã passa o destino a só de leitura', () => {
      comServicoDosProcessos();
      configurar(true, { processo: detalhe({}), pedidoRascunho: () => pedido });
      expect(opcao('SO_DEPOSITO')).not.toBeNull();
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [documento()], pendente: false })], deposito: [] }], pendencias: [] })),
      );

      fixture.componentInstance.recarregar();
      fixture.detectChanges();

      expect(grupo()!.querySelectorAll('input[type="radio"]')).toHaveLength(0);
      expect(grupo()!.textContent).toContain('Para mudar o destino, remova primeiro os ficheiros carregados.');
    });

    it('num processo de PDM (sem destinoEditavel e para publicação) o grupo não aparece', () => {
      comServicoDosProcessos();
      configurar(true, { processo: detalhe({ destinoEditavel: false, destino: 'PUBLICACAO_DEPOSITO' }), pedidoRascunho: () => pedido });

      expect(fixture.nativeElement.querySelector('app-destino-processo')).toBeNull();
    });

    it('em leitura (validação DGT), um só para depósito aparece sem opções', () => {
      configurar(false, { processo: detalhe({ destino: 'SO_DEPOSITO', destinoEditavel: false }) });

      expect(grupo()!.querySelectorAll('input[type="radio"]')).toHaveLength(0);
      expect(grupo()!.textContent).toContain('Só depósito');
    });

    it('sem o pedido do formulário o grupo fica só de leitura', () => {
      comServicoDosProcessos();
      configurar(true, { processo: detalhe({}) });

      expect(grupo()!.querySelectorAll('input[type="radio"]')).toHaveLength(0);
      fixture.componentInstance.gravarDestino('SO_DEPOSITO');
      expect(guardarRascunho).not.toHaveBeenCalled();
    });
  });
  describe('correção de ficheiros rejeitados', () => {
    function comFicheiro(ficheiro: DocumentoProcesso): void {
      estrutura.mockReturnValue(
        of(estruturaCom({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [item({ ficheiros: [ficheiro], pendente: false })], deposito: [] }], pendencias: [] })),
      );
    }

    it('na edição, marca o ficheiro rejeitado pela DGT e diz como o corrigir', () => {
      comFicheiro(documento({ estadoValidacao: 'REJEITADO' }));
      configurar();

      const linha = fixture.nativeElement.querySelector('.ficheiro') as HTMLElement;
      expect(linha.classList).toContain('ficheiro-rejeitado');
      expect(linha.textContent).toContain('Rejeitado pela DGT');
      expect(linha.textContent).toContain('Remover');
    });

    it('só de leitura, não mostra o aviso de correção', () => {
      comFicheiro(documento({ estadoValidacao: 'REJEITADO' }));
      configurar(false);

      expect(texto()).not.toContain('Rejeitado pela DGT');
    });
  });

  describe('secção Outros', () => {
    function corpoOutros(): HTMLElement {
      return fixture.nativeElement.querySelector('#acordeao-corpo-outros') as HTMLElement;
    }

    it('tem as caixas Publicação e Depósito, cada uma com os seus ficheiros', () => {
      estrutura.mockReturnValue(
        of(
          estruturaCom({
            outros: [
              documento({ id: 'o1', documentoConfigId: null, nomeFicheiro: 'publicar.pdf', categoria: 'PUBLICACAO' }),
              documento({ id: 'o2', documentoConfigId: null, nomeFicheiro: 'depositar.pdf', categoria: 'DEPOSITO' }),
            ],
          }),
        ),
      );
      configurar();

      const [publicacao, deposito] = Array.from(corpoOutros().querySelectorAll('.caixa')) as HTMLElement[];
      expect(publicacao.querySelector('.caixa-titulo')?.textContent).toBe('Publicação');
      expect(publicacao.textContent).toContain('publicar.pdf');
      expect(publicacao.textContent).not.toContain('depositar.pdf');
      expect(deposito.querySelector('.caixa-titulo')?.textContent).toBe('Depósito');
      expect(deposito.textContent).toContain('depositar.pdf');
    });

    it('carrega na caixa Publicação como PUBLICACAO sem documentoConfigId', () => {
      configurar();
      const ficheiro = new File(['x'], 'extra.pdf');

      largarFicheiro(corpoOutros().querySelector('.zona') as HTMLElement, ficheiro);

      expect(carregar).toHaveBeenCalledWith('p1', ficheiro, 'PUBLICACAO', undefined, []);
    });

    it('grava as observações dos Outros', () => {
      configurar();
      const caixa = corpoOutros().querySelector('#observacoes-outros') as HTMLTextAreaElement;
      caixa.value = '  Anexo extra  ';
      caixa.dispatchEvent(new Event('input'));
      const resultados: boolean[] = [];

      fixture.componentInstance.gravarObservacoes().subscribe((ok) => resultados.push(ok));

      expect(gravarObservacoesOutros).toHaveBeenCalledWith('p1', 'Anexo extra');
      expect(resultados).toEqual([true]);
    });

    it('mostra as observações gravadas dos Outros', () => {
      estrutura.mockReturnValue(of(estruturaCom({ observacoesOutros: 'Anexo extra' })));
      configurar();

      expect((corpoOutros().querySelector('#observacoes-outros') as HTMLTextAreaElement).value).toBe('Anexo extra');
    });
  });
});

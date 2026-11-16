import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { Plantas } from '../../../models/plantas.model';
import { CODIGO_CARREGAMENTO_SEM_LIGACAO, DocumentoService } from '../../../services/documento.service';
import { PlantasService } from '../../../services/plantas.service';
import { ProcessoPlantasComponent } from './processo-plantas.component';

describe('ProcessoPlantasComponent', () => {
  let fixture: ComponentFixture<ProcessoPlantasComponent>;
  let plantasServiceMock: {
    obterPlantas: ReturnType<typeof vi.fn>;
    gravarPlantas: ReturnType<typeof vi.fn>;
    cartogramaPdf: ReturnType<typeof vi.fn>;
  };
  let documentoServiceMock: { carregar: ReturnType<typeof vi.fn>; remover: ReturnType<typeof vi.fn> };

  function plantas(overrides: Partial<Plantas> = {}): Plantas {
    return {
      exigePlantas: true,
      itens: [
        { documentoConfigId: 1, codigo: 'PLANTAS_ZIP_ORDENAMENTO_DEP', nome: 'Planta de Ordenamento', descricao: null, categoria: 'DEPOSITO', obrigatorio: true, extensoesAceites: ['zip'], ficheiro: { id: 'f1', nomeFicheiro: 'ordenamento.zip' } },
        { documentoConfigId: 2, codigo: 'PLANTAS_ZIP_CONDICIONANTES_DEP', nome: 'Planta de Condicionantes', descricao: null, categoria: 'DEPOSITO', obrigatorio: true, extensoesAceites: ['zip'], ficheiro: null },
        { documentoConfigId: 3, codigo: 'PLANTAS_TXT_NOMES_DEP', nome: 'Nomes das plantas', descricao: null, categoria: 'DEPOSITO', obrigatorio: true, extensoesAceites: ['txt'], ficheiro: { id: 'f3', nomeFicheiro: 'nomes.txt' } },
        { documentoConfigId: 4, codigo: 'PLANTAS_CARTOGRAMA_DEP', nome: 'Cartograma das folhas', descricao: null, categoria: 'DEPOSITO', obrigatorio: false, extensoesAceites: ['pdf', 'jpg', 'png'], ficheiro: null },
      ],
      linhas: [
        { ordem: 0, tipo: 'Ordenamento', nome: 'Uso do Solo', folha: 'A', nomeTiff: 'Ord_A.tif', opcoes: ['Ord_A.tif', 'Ord_B.tif'] },
        { ordem: 1, tipo: 'Ordenamento', nome: 'Uso do Solo', folha: 'B', nomeTiff: null, opcoes: ['Ord_A.tif', 'Ord_B.tif'] },
      ],
      errosTxt: [],
      exigeCartograma: true,
      cartogramaPdfDisponivel: false,
      ...overrides,
    };
  }

  function seletores(): HTMLSelectElement[] {
    return [...fixture.nativeElement.querySelectorAll('.seletor-tiff')];
  }

  beforeEach(() => {
    plantasServiceMock = {
      obterPlantas: vi.fn().mockReturnValue(of(plantas())),
      gravarPlantas: vi.fn().mockReturnValue(of(plantas())),
      cartogramaPdf: vi.fn().mockReturnValue(of(new Blob(['%PDF'], { type: 'application/pdf' }))),
    };
    documentoServiceMock = { carregar: vi.fn().mockReturnValue(of({})), remover: vi.fn().mockReturnValue(of(undefined)) };
    TestBed.configureTestingModule({
      imports: [ProcessoPlantasComponent],
      providers: [
        { provide: PlantasService, useValue: plantasServiceMock },
        { provide: DocumentoService, useValue: documentoServiceMock },
      ],
    });
    fixture = TestBed.createComponent(ProcessoPlantasComponent);
    fixture.componentInstance.processoId = 'processo-1';
    fixture.detectChanges();
  });

  it('mostra os ZIP, o TXT, a tabela das plantas e o cartograma pela ordem do Figma', () => {
    const titulos = [...fixture.nativeElement.querySelectorAll('.titulo-passo')].map((e: Element) => e.textContent?.trim());
    expect(titulos).toEqual([
      'Carregar ficheiro ZIP com as plantas',
      'Carregar ficheiro TXT com nome das plantas',
      'Associar plantas ao nome Tiff',
      'Cartograma das folhas',
    ]);
    expect(fixture.nativeElement.querySelector('.zona-carregada .nome-ficheiro').textContent).toContain('ordenamento.zip');
    expect(fixture.nativeElement.textContent).toContain('Obrigatório: há plantas com mais de uma folha');
  });

  it('um TIFF escolhido numa linha desaparece das opções das outras', () => {
    expect([...seletores()[1].options].map((opcao) => opcao.value)).toEqual(['', 'Ord_B.tif']);
  });

  it('grava só as linhas com TIFF escolhido', () => {
    const select = seletores()[1];
    select.value = 'Ord_B.tif';
    select.dispatchEvent(new Event('change'));

    let resultado: boolean | undefined;
    fixture.componentInstance.gravar().subscribe((gravou) => (resultado = gravou));

    expect(resultado).toBe(true);
    expect(plantasServiceMock.gravarPlantas).toHaveBeenCalledWith('processo-1', {
      associacoes: [
        { ordem: 0, nomeTiff: 'Ord_A.tif' },
        { ordem: 1, nomeTiff: 'Ord_B.tif' },
      ],
    });
  });

  it('um erro ao gravar fica junto da tabela e não deixa avançar', () => {
    plantasServiceMock.gravarPlantas.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Inválido.', 't1', [{ path: 'associacoes[0].nomeTiff', erro: 'Este TIFF já está associado a outra planta.' }])),
    );

    let resultado: boolean | undefined;
    fixture.componentInstance.gravar().subscribe((gravou) => (resultado = gravou));
    fixture.detectChanges();

    expect(resultado).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Este TIFF já está associado a outra planta.');
  });

  it('carregar um ficheiro envia-o para o item da página e relê as plantas', () => {
    const input = fixture.nativeElement.querySelector('.zona .ficheiro-input') as HTMLInputElement;
    const ficheiro = new File(['x'], 'condicionantes.zip');
    Object.defineProperty(input, 'files', { value: [ficheiro] });
    input.dispatchEvent(new Event('change'));

    expect(documentoServiceMock.carregar).toHaveBeenCalledWith('processo-1', ficheiro, 'DEPOSITO', 2);
    expect(plantasServiceMock.obterPlantas).toHaveBeenCalledTimes(2);
  });

  it('um carregamento sem ligação ao servidor mostra a mensagem junto do item, que continua sem ficheiro', () => {
    documentoServiceMock.carregar.mockReturnValue(
      throwError(() => new ApiError(CODIGO_CARREGAMENTO_SEM_LIGACAO, 'Não foi possível carregar o ficheiro. Tente novamente.', 'unknown', null, 0)),
    );
    const input = fixture.nativeElement.querySelector('.zona .ficheiro-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'condicionantes.zip')] });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const erro = fixture.nativeElement.querySelector('#erro-PLANTAS_ZIP_CONDICIONANTES_DEP');
    expect(erro.getAttribute('role')).toBe('alert');
    expect(erro.textContent).toContain('Não foi possível carregar o ficheiro. Tente novamente.');
    expect(fixture.nativeElement.textContent).not.toContain('Http failure');
    expect(plantasServiceMock.obterPlantas).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('.zona .ficheiro-input')).toBe(input);
  });

  describe('PDF do cartograma', () => {
    const comCartograma = (): Plantas => {
      const base = plantas();
      return {
        ...base,
        itens: base.itens.map((item) =>
          item.codigo === 'PLANTAS_CARTOGRAMA_DEP' ? { ...item, ficheiro: { id: 'f4', nomeFicheiro: 'cartograma.png' } } : item,
        ),
        linhas: base.linhas.map((linha) => ({ ...linha, nomeTiff: linha.nomeTiff ?? 'Ord_B.tif' })),
        cartogramaPdfDisponivel: true,
      };
    };

    function botaoPdf(): HTMLButtonElement | null {
      return fixture.nativeElement.querySelector('.ver-pdf');
    }

    function mostrar(estado: Plantas): void {
      plantasServiceMock.obterPlantas.mockReturnValue(of(estado));
      plantasServiceMock.gravarPlantas.mockReturnValue(of(estado));
      fixture.componentInstance.recarregar();
      fixture.detectChanges();
    }

    it('só aparece com o cartograma carregado e todas as linhas associadas', () => {
      expect(botaoPdf()).toBeNull();

      mostrar(comCartograma());

      expect(botaoPdf()?.textContent).toContain('Ver PDF do cartograma');
      expect(botaoPdf()?.textContent).toContain('(abre num separador novo)');
    });

    it('grava a associação, pede o PDF e abre-o no separador aberto no clique', () => {
      mostrar(comCartograma());
      const separador = { location: { href: '' }, close: vi.fn() };
      const abrir = vi.spyOn(window, 'open').mockReturnValue(separador as unknown as Window);
      const criarUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:cartograma');

      botaoPdf()?.click();

      expect(abrir).toHaveBeenCalledWith('', '_blank');
      expect(plantasServiceMock.gravarPlantas).toHaveBeenCalled();
      expect(plantasServiceMock.cartogramaPdf).toHaveBeenCalledWith('processo-1');
      expect(separador.location.href).toBe('blob:cartograma');
      abrir.mockRestore();
      criarUrl.mockRestore();
    });

    it('se a API recusar, fecha o separador e mostra o que falta', () => {
      mostrar(comCartograma());
      const separador = { location: { href: '' }, close: vi.fn() };
      const abrir = vi.spyOn(window, 'open').mockReturnValue(separador as unknown as Window);
      plantasServiceMock.cartogramaPdf.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Ainda não.', 't1', [{ path: 'plantas.cartograma', erro: 'Carregue o cartograma das folhas.' }])),
      );

      botaoPdf()?.click();
      fixture.detectChanges();

      expect(separador.close).toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('.ver-pdf ~ .erro')?.textContent).toContain('Carregue o cartograma das folhas.');
      abrir.mockRestore();
    });
  });

  it('as linhas do TXT que não se leram aparecem como erro', () => {
    plantasServiceMock.obterPlantas.mockReturnValue(of(plantas({ errosTxt: ['Linha 3: use o formato «Tipo; Nome; Folha».'] })));
    fixture.componentInstance.recarregar();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Linha 3: use o formato «Tipo; Nome; Folha».');
  });
});

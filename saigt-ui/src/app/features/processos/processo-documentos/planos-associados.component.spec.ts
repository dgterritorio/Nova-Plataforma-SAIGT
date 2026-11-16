import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { Instrumento, TipoInstrumento } from '../../../models/instrumento.model';
import { AssociacaoInstrumentos, InstrumentoAbrangido, ProcessoDetalhe } from '../../../models/processo.model';
import { DinamicaService } from '../../../services/dinamica.service';
import { InstrumentoService } from '../../../services/instrumento.service';
import { PlanosAssociadosComponent } from './planos-associados.component';

const PDM: TipoInstrumento = { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', entidadeInstrutoraObrigatoria: true };
const PU: TipoInstrumento = { id: 2, codigo: 'PU', nome: 'Plano de Urbanização', categoria: 'PLANO', entidadeInstrutoraObrigatoria: true };
const SINTRA: InstrumentoAbrangido = { id: 'i-sintra', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' };

function processo(parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
  return {
    id: 'p1',
    dinamicaId: 'd1',
    associacaoInstrumentos: 'OPCIONAL' as AssociacaoInstrumentos,
    podeAssociarInstrumentos: true,
    tiposInstrumentoAdmitidos: [PDM, PU],
    instrumentosAbrangidos: [],
    ...parcial,
  } as ProcessoDetalhe;
}

function instrumento(parcial: Partial<Instrumento>): Instrumento {
  return {
    id: 'i1',
    tipoInstrumentoId: 1,
    tipoInstrumentoNome: 'Plano Diretor Municipal',
    numero: 'N1',
    designacao: 'PDM de Cascais',
    responsavelId: 'u1',
    emVigor: true,
    origemDados: 'NATIVO',
    criadoPorId: 'u1',
    criadoEm: '2026-11-01T10:00:00Z',
    concelhos: ['1105'],
    ...parcial,
  };
}

function pagina(content: Instrumento[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('PlanosAssociadosComponent', () => {
  let fixture: ComponentFixture<PlanosAssociadosComponent>;
  let instrumentoService: { pesquisarEmVigor: ReturnType<typeof vi.fn>; listarConcelhos: ReturnType<typeof vi.fn> };
  let dinamicaService: { associarInstrumentos: ReturnType<typeof vi.fn> };

  function abrir(p: ProcessoDetalhe): HTMLElement {
    fixture = TestBed.createComponent(PlanosAssociadosComponent);
    fixture.componentRef.setInput('processo', p);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function botao(elemento: HTMLElement, texto: string): HTMLButtonElement {
    const encontrado = [...elemento.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto || b.getAttribute('aria-label') === texto,
    );
    if (!encontrado) {
      throw new Error(`Botão «${texto}» não encontrado`);
    }
    return encontrado;
  }

  function pesquisar(elemento: HTMLElement): void {
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    instrumentoService = {
      pesquisarEmVigor: vi.fn().mockReturnValue(of(pagina([]))),
      listarConcelhos: vi.fn().mockReturnValue(of([{ codigo: '1105', nome: 'Cascais' }, { codigo: '1111', nome: 'Sintra' }])),
    };
    dinamicaService = { associarInstrumentos: vi.fn() };
    TestBed.configureTestingModule({
      imports: [PlanosAssociadosComponent],
      providers: [
        { provide: InstrumentoService, useValue: instrumentoService },
        { provide: DinamicaService, useValue: dinamicaService },
      ],
    });
  });

  describe('por perfil e estado', () => {
    it('o autor em EM_SUBMISSAO vê a pesquisa, a lista e a ação de gravar', () => {
      const elemento = abrir(processo({ instrumentosAbrangidos: [SINTRA] }));

      expect(elemento.querySelector('h3')?.textContent).toContain('Planos ou programas associados');
      expect(elemento.querySelector('form[role="search"]')).not.toBeNull();
      expect(botao(elemento, 'Remover PDM de Sintra')).toBeTruthy();
      expect(botao(elemento, 'Gravar associação').disabled).toBe(true);
    });

    it('sem poder associar mostra só a lista, sem pesquisa nem ações, ou «Sem IGT associado»', () => {
      let elemento = abrir(processo({ podeAssociarInstrumentos: false, instrumentosAbrangidos: [SINTRA] }));
      expect(elemento.querySelector('form')).toBeNull();
      expect(elemento.querySelectorAll('button').length).toBe(0);
      expect(elemento.querySelector('tbody')?.textContent).toContain('PDM de Sintra');
      expect(instrumentoService.listarConcelhos).not.toHaveBeenCalled();

      fixture.destroy();
      elemento = abrir(processo({ podeAssociarInstrumentos: false }));
      expect(elemento.textContent).toContain('Sem IGT associado');
    });

    it('na Suspensão da CCDR o bloco chama-se «Planos associados» e mostra os planos', () => {
      const elemento = abrir(
        processo({ formulario: 'SUSPENSAO_CCDR', associacaoInstrumentos: 'OPCIONAL', instrumentosAbrangidos: [SINTRA] }),
      );
      expect(elemento.querySelector('h3')?.textContent?.trim()).toBe('Planos associados');
      expect(elemento.querySelector('caption.visualmente-oculto')?.textContent?.trim()).toBe('Planos associados');
      expect(elemento.querySelector('tbody')?.textContent).toContain('PDM de Sintra');
    });

    it('numa Dinâmica UM, como a Ratificação, o bloco não aparece', () => {
      const elemento = abrir(processo({ associacaoInstrumentos: 'UM', tiposInstrumentoAdmitidos: [] }));
      expect(elemento.querySelector('section')).toBeNull();
    });
  });

  describe('pesquisa', () => {
    it('pede só Instrumentos em vigor dos tipos admitidos, com os filtros, e mostra designação, tipo e concelhos', () => {
      instrumentoService.pesquisarEmVigor.mockReturnValue(of(pagina([instrumento({})])));
      const elemento = abrir(processo());
      const componente = fixture.componentInstance;

      pesquisar(elemento);
      expect(instrumentoService.pesquisarEmVigor).toHaveBeenLastCalledWith(
        { tiposInstrumentoId: [1, 2], concelho: null, designacao: '' },
        0,
        20,
      );
      const linha = elemento.querySelectorAll('table')[0].querySelector('tbody tr')!;
      expect(linha.textContent).toContain('PDM de Cascais');
      expect(linha.textContent).toContain('PDM');
      expect(linha.textContent).toContain('Cascais');

      componente.filtros.setValue({ tipo: '2', concelho: '1111', designacao: 'Sintra' });
      pesquisar(elemento);
      expect(instrumentoService.pesquisarEmVigor).toHaveBeenLastCalledWith(
        { tiposInstrumentoId: [2], concelho: '1111', designacao: 'Sintra' },
        0,
        20,
      );
    });

    it('o filtro de tipo só oferece os tipos admitidos e descarta resultados fora de vigor ou de outro tipo', () => {
      instrumentoService.pesquisarEmVigor.mockReturnValue(
        of(pagina([instrumento({}), instrumento({ id: 'i2', emVigor: false }), instrumento({ id: 'i3', tipoInstrumentoId: 9 })])),
      );
      const elemento = abrir(processo());

      const opcoes = [...elemento.querySelectorAll('select')[0].querySelectorAll('option')].map((o) => o.textContent?.trim());
      expect(opcoes).toEqual(['Todos os tipos admitidos', 'PDM — Plano Diretor Municipal', 'PU — Plano de Urbanização']);
      pesquisar(elemento);
      expect(elemento.querySelectorAll('table')[0].querySelectorAll('tbody tr').length).toBe(1);
    });
  });

  describe('lista', () => {
    it('não junta o mesmo Instrumento duas vezes e «Remover» tira-o da lista, anunciando o número', () => {
      instrumentoService.pesquisarEmVigor.mockReturnValue(of(pagina([instrumento({})])));
      const elemento = abrir(processo());
      pesquisar(elemento);

      botao(elemento, 'Adicionar PDM de Cascais').click();
      fixture.detectChanges();
      fixture.componentInstance.adicionar(instrumento({}));
      fixture.detectChanges();
      expect(fixture.componentInstance.lista().map((i) => i.id)).toEqual(['i1']);
      expect(elemento.textContent).toContain('Na lista');
      const contagem = elemento.querySelector('p.contagem[aria-live="polite"]')!;
      expect(contagem.textContent).toContain('1 plano ou programa associado');

      botao(elemento, 'Remover PDM de Cascais').click();
      fixture.detectChanges();
      expect(fixture.componentInstance.lista()).toEqual([]);
      expect(contagem.textContent).toContain('0 planos ou programas associados');
    });

    it('a lista é uma tabela com cabeçalhos', () => {
      const elemento = abrir(processo({ instrumentosAbrangidos: [SINTRA] }));
      const cabecalhos = [...elemento.querySelectorAll('table th[scope="col"]')].map((th) => th.textContent?.trim());
      expect(cabecalhos).toContain('Designação');
      expect(cabecalhos).toContain('Tipo');
    });
  });

  describe('gravar', () => {
    it('envia o conjunto completo, fica como gravada e avisa o ecrã', () => {
      instrumentoService.pesquisarEmVigor.mockReturnValue(of(pagina([instrumento({})])));
      const gravados = [SINTRA, { id: 'i1', designacao: 'PDM de Cascais', tipoInstrumento: 'PDM' }];
      dinamicaService.associarInstrumentos.mockReturnValue(of({ instrumentos: gravados }));
      const elemento = abrir(processo({ instrumentosAbrangidos: [SINTRA] }));
      const emitidos: InstrumentoAbrangido[][] = [];
      fixture.componentInstance.associacaoGravada.subscribe((lista) => emitidos.push(lista));
      pesquisar(elemento);
      botao(elemento, 'Adicionar PDM de Cascais').click();
      fixture.detectChanges();

      botao(elemento, 'Gravar associação').click();
      fixture.detectChanges();

      expect(dinamicaService.associarInstrumentos).toHaveBeenCalledWith('d1', ['i-sintra', 'i1']);
      expect(fixture.componentInstance.lista()).toEqual(gravados);
      expect(fixture.componentInstance.alterada()).toBe(false);
      expect(elemento.querySelector('[role="status"]')?.textContent).toContain('Associação gravada.');
      expect(emitidos).toEqual([gravados]);
    });

    it('400 ASSOCIACAO_INVALIDA aparece junto da lista com a designação e a lista mantém o que o autor editou', () => {
      dinamicaService.associarInstrumentos.mockReturnValue(
        throwError(() => new ApiError('ASSOCIACAO_INVALIDA', 'Só se associam instrumentos em vigor.', 't', [
          { path: 'instrumentoIds[0]', erro: 'Só se associam instrumentos em vigor.' },
        ])),
      );
      const elemento = abrir(processo({ instrumentosAbrangidos: [SINTRA] }));
      fixture.componentInstance.adicionar(instrumento({}));
      fixture.detectChanges();

      botao(elemento, 'Gravar associação').click();
      fixture.detectChanges();

      const alerta = elemento.querySelector('p[role="alert"]');
      expect(alerta?.textContent).toContain('PDM de Sintra: Só se associam instrumentos em vigor.');
      expect(fixture.componentInstance.lista().map((i) => i.id)).toEqual(['i-sintra', 'i1']);
      expect(elemento.querySelector('.error-banner')).toBeNull();
    });

    it('404 e 409 vão para o banner de erro', () => {
      dinamicaService.associarInstrumentos.mockReturnValue(
        throwError(() => new ApiError('ESTADO_INVALIDO', 'A dinâmica não tem nenhum processo seu em submissão.', 't', null)),
      );
      const elemento = abrir(processo());
      fixture.componentInstance.adicionar(instrumento({}));
      fixture.detectChanges();

      botao(elemento, 'Gravar associação').click();
      fixture.detectChanges();

      expect(elemento.querySelector('.error-banner')).not.toBeNull();
      expect(elemento.querySelector('p.erro')).toBeNull();
    });
  });
});

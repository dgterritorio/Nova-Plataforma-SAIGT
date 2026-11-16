import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ReciboSubmissaoComponent } from './recibo-submissao.component';
import { ApiError } from '../../core/models/error-response.model';
import { Processo } from '../../models/processo.model';
import { DocumentoService } from '../../services/documento.service';

describe('ReciboSubmissaoComponent', () => {
  let fixture: ComponentFixture<ReciboSubmissaoComponent>;

  type ProcessoSubmetido = Processo & { idus?: string | null; submissao?: number };

  function processo(overrides: Partial<ProcessoSubmetido> = {}): ProcessoSubmetido {
    return {
      id: 'processo-123',
      dinamicaId: 'd1',
      instrumentoProcedimentoConfigId: 10,
      estado: 'ENVIADO_DGT',
      numeroProcesso: '00001',
      ocorrencia: 1,
      versao: 1,
      origemDados: 'NATIVO',
      tipoAtoId: null,
      criadoPorId: 'u1',
      dadosFormulario: null,
      dataSubmissao: '2026-08-19T15:30:00Z',
      pcmAtribuidoAId: null,
      camposBloqueados: [],
      ...overrides,
    };
  }

  let listar: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    listar = vi.fn().mockReturnValue(
      of({
        content: [
          { id: 'd1', nomeFicheiro: 'deliberacao.pdf', extensao: 'pdf', criadoEm: '2026-10-20T09:30:00Z' },
          { id: 'd2', nomeFicheiro: 'planta.dwg', extensao: 'dwg', criadoEm: '2026-10-21T14:05:00Z' },
        ],
        page: 0,
        size: 100,
        totalElements: 2,
        totalPages: 1,
      }),
    );
    TestBed.configureTestingModule({
      imports: [ReciboSubmissaoComponent],
      providers: [{ provide: DocumentoService, useValue: { listar } }],
    });
    fixture = TestBed.createComponent(ReciboSubmissaoComponent);
  });

  function recibo(): HTMLElement {
    return (Array.from(fixture.nativeElement.querySelectorAll('app-cartao')) as HTMLElement[])[1];
  }

  function rotulosDoRecibo(): string[] {
    return Array.from(recibo().querySelectorAll('dt')).map((dt) => dt.textContent!.trim());
  }

  it('mostra o IDUS do processo e nunca o identificador técnico nem um número de recibo inventado', () => {
    fixture.componentInstance.processo = { ...processo(), idus: 'SAIGT.1111.PDM.42.1.1.1.DI.1' };
    fixture.detectChanges();

    expect(rotulosDoRecibo()).toContain('IDUS');
    expect(recibo().textContent).toContain('SAIGT.1111.PDM.42.1.1.1.DI.1');
    expect(fixture.nativeElement.textContent).not.toContain('processo-123');
    expect(fixture.nativeElement.textContent).not.toContain('Identificador do processo');
    expect(fixture.nativeElement.textContent).not.toContain('19359');
  });

  it('sem IDUS não mostra a linha do IDUS nem o identificador técnico', () => {
    fixture.componentInstance.processo = { ...processo(), idus: null };
    fixture.detectChanges();

    expect(rotulosDoRecibo()).not.toContain('IDUS');
    expect(fixture.nativeElement.textContent).not.toContain('processo-123');
  });

  it('mostra o n.º do processo SAIGT em destaque e, nos Dados do recibo, seguido do IDUS', () => {
    fixture.componentInstance.processo = { ...processo({ numeroProcesso: '00042' }), idus: 'SAIGT.1111.PDM.42.1.1.1.DI.1' };
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.numero-valor')!.textContent!.trim()).toBe('00042');
    expect(rotulosDoRecibo().slice(0, 2)).toEqual(['N.º do processo SAIGT', 'IDUS']);
  });

  it('mostra traço no n.º do processo SAIGT quando vem vazio', () => {
    fixture.componentInstance.processo = processo({ numeroProcesso: '' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.numero-valor')!.textContent!.trim()).toBe('—');
  });

  it('mostra os Dados do processo do Figma e o rodapé com a data de geração', () => {
    fixture.componentInstance.processo = processo({ ocorrencia: 1, submissao: 2 });
    fixture.componentInstance.designacao = 'PDM Porto';
    fixture.componentInstance.tipoInstrumento = 'Plano Diretor Municipal';
    fixture.componentInstance.tipoProcedimento = 'Decisão de elaboração';
    fixture.componentInstance.concelho = 'Porto';
    fixture.detectChanges();

    const dados = (Array.from(fixture.nativeElement.querySelectorAll('app-cartao')) as HTMLElement[])[0];
    const pares = Array.from(dados.querySelectorAll('dt')).map((dt) => [dt.textContent!.trim(), dt.nextElementSibling!.textContent!.trim()]);
    expect(pares).toEqual([
      ['Designação', 'PDM Porto'],
      ['Tipo de instrumento', 'Plano Diretor Municipal'],
      ['Tipo de procedimento', 'Decisão de elaboração'],
      ['Concelho', 'Porto'],
      ['Submissão do processo n.º', '2'],
    ]);
    expect(fixture.nativeElement.querySelector('.rodape-gerado')!.textContent).toMatch(/Gerado em 19\/08\/2026 .* · Portal IGT — DGT/);
  });

  it('copia o n.º do processo e anuncia-o ao leitor de ecrã', async () => {
    const escrever = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: escrever }, configurable: true });
    fixture.componentInstance.processo = processo({ numeroProcesso: '00042' });
    fixture.detectChanges();

    const copiar = fixture.nativeElement.querySelector('.botao-copiar') as HTMLButtonElement;
    expect(copiar.getAttribute('aria-label')).toBe('Copiar número do processo');
    copiar.click();
    await Promise.resolve();
    fixture.detectChanges();

    expect(escrever).toHaveBeenCalledWith('00042');
    expect(fixture.nativeElement.querySelector('[aria-live="polite"]')!.textContent!.trim()).toBe('Número copiado.');
  });

  function submissaoDoProcesso(): string {
    const rotulo = (Array.from(fixture.nativeElement.querySelectorAll('dt')) as HTMLElement[]).find(
      (dt) => dt.textContent!.trim() === 'Submissão do processo n.º',
    );
    return rotulo!.nextElementSibling!.textContent!.trim();
  }

  it('mostra o número da submissão e a data de submissão quando existem', () => {
    fixture.componentInstance.processo = processo({ submissao: 2, dataSubmissao: '2026-08-19T15:30:00Z' });
    fixture.detectChanges();

    expect(submissaoDoProcesso()).toBe('2');
    expect(fixture.nativeElement.textContent).toMatch(/19\/08\/2026/);
  });

  it('na segunda Abertura da discussão pública, primeira submissão, mostra 1 e não a ocorrência', () => {
    fixture.componentInstance.processo = processo({ ocorrencia: 2, submissao: 1, idus: '01.1111.01.001001.02.90001.01.031.01' });
    fixture.detectChanges();

    expect(submissaoDoProcesso()).toBe('1');
  });

  it('sem o detalhe relido mostra traço no número da submissão, nunca a ocorrência', () => {
    fixture.componentInstance.processo = processo({ ocorrencia: 2 });
    fixture.detectChanges();

    expect(submissaoDoProcesso()).toBe('—');
  });

  it('não conta nem lista os ficheiros substituídos na correção', () => {
    listar.mockReturnValue(
      of({
        content: [
          { id: 'd1', nomeFicheiro: 'deliberacao.pdf', extensao: 'pdf', criadoEm: '2026-10-20T09:30:00Z', estadoValidacao: 'SUBSTITUIDO' },
          { id: 'd2', nomeFicheiro: 'deliberacao-v2.pdf', extensao: 'pdf', criadoEm: '2026-10-22T09:30:00Z', estadoValidacao: 'POR_VALIDAR' },
          { id: 'd3', nomeFicheiro: 'planta.dwg', extensao: 'dwg', criadoEm: '2026-10-21T14:05:00Z', estadoValidacao: 'VALIDADO' },
        ],
        page: 0,
        size: 100,
        totalElements: 3,
        totalPages: 1,
      }),
    );
    fixture.componentInstance.processo = processo({ submissao: 2 });
    fixture.detectChanges();

    const nomes = Array.from(fixture.nativeElement.querySelectorAll('tbody tr')).map((linha) => (linha as HTMLElement).querySelector('td')!.textContent!.trim());
    expect(nomes).toEqual(['deliberacao-v2.pdf', 'planta.dwg']);
    const total = (Array.from(recibo().querySelectorAll('dt')) as HTMLElement[]).find((dt) => dt.textContent!.trim() === 'Total de ficheiros remetidos');
    expect(total!.nextElementSibling!.textContent!.trim()).toBe('2 ficheiros');
  });

  it('nunca mostra data-limite: a caducidade é da Dinâmica, não do Processo', () => {
    fixture.componentInstance.processo = processo();
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent;
    expect(texto).not.toMatch(/prazo limite|data-limite|data limite/i);
  });

  it('mostra traço no tipo de procedimento quando não é fornecido', () => {
    fixture.componentInstance.processo = processo();
    fixture.componentInstance.tipoProcedimento = null;
    fixture.detectChanges();

    const rotulo = (Array.from(fixture.nativeElement.querySelectorAll('dt')) as HTMLElement[]).find(
      (dt) => dt.textContent!.trim() === 'Tipo de procedimento',
    );
    expect(rotulo!.nextElementSibling!.textContent!.trim()).toBe('—');
  });

  it('mostra os ficheiros remetidos do processo, com o total no bloco e nos dados do recibo', () => {
    fixture.componentInstance.processo = processo();
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(listar).toHaveBeenCalledWith('processo-123', {}, 0, 100);
    expect(texto).toContain('Ficheiros remetidos');
    expect(texto).toContain('deliberacao.pdf');
    expect(texto).toContain('PDF');
    expect(texto).toContain('Recebido');
    expect(texto).toContain('Total de ficheiros remetidos');
    expect(texto).toContain('2 ficheiros');
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
  });

  it('não mostra o total nos dados do recibo enquanto a lista não carregou', () => {
    listar.mockReturnValue(throwError(() => new ApiError('NAO_ENCONTRADO', 'Não encontrado.', 't1', null)));
    fixture.componentInstance.processo = processo();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Total de ficheiros remetidos');
  });

  it('emite voltar ao clicar em "Voltar ao menu"', () => {
    fixture.componentInstance.processo = processo();
    fixture.detectChanges();

    let emitido = false;
    fixture.componentInstance.voltar.subscribe(() => (emitido = true));
    (Array.from(fixture.nativeElement.querySelectorAll('.rodape-acoes button')) as HTMLButtonElement[]).find((b) => b.textContent!.includes('Voltar ao menu'))!.click();

    expect(emitido).toBe(true);
  });

  it('chama window.print ao clicar em "Imprimir recibo"', () => {
    fixture.componentInstance.processo = processo();
    fixture.detectChanges();

    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    (Array.from(fixture.nativeElement.querySelectorAll('.rodape-acoes button')) as HTMLButtonElement[]).find((b) => b.textContent!.includes('Imprimir recibo'))!.click();

    expect(printSpy).toHaveBeenCalledTimes(1);
    printSpy.mockRestore();
  });
});

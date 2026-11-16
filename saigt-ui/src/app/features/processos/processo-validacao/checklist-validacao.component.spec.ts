import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoProcesso, EstruturaDocumental, VerificacaoDocumental } from '../../../models/documento.model';
import { DescargaService } from '../../../services/descarga.service';
import { DocumentoService } from '../../../services/documento.service';
import { ChecklistValidacaoComponent } from './checklist-validacao.component';

function linha(parcial: Partial<VerificacaoDocumental>): VerificacaoDocumental {
  return { verificacao: 'FORMATO_QUADRO_ANEXO', documentoId: 'd1', nomeFicheiro: 'regulamento.docx', resultado: 'OK', detalhe: null, ...parcial };
}

// Desordenadas de propósito: o agrupamento segue o FR-092, não a ordem de chegada.
const LINHAS: VerificacaoDocumental[] = [
  linha({ verificacao: 'CORRECAO_ZIP', documentoId: 'z1', nomeFicheiro: 'plantas.zip', resultado: 'OK' }),
  linha({
    verificacao: 'GEORREFERENCIACAO_MATRICIAL',
    documentoId: 'z1',
    nomeFicheiro: '1199_Ord_A.tif',
    resultado: 'FALHA',
    detalhe: 'Falta o ficheiro 1199_Ord_A.tfw no mesmo ZIP.',
  }),
  linha({ verificacao: 'CORRESPONDENCIA_ATO_TEXTO', documentoId: null, nomeFicheiro: null, resultado: 'MANUAL', detalhe: 'Confirme o ato.' }),
  linha({}),
  linha({ verificacao: 'FOLHA_NOME_TIF', documentoId: 'z1', nomeFicheiro: '1199_Ord_A.tif', resultado: 'MANUAL', detalhe: 'Confirme a folha.' }),
  linha({ verificacao: 'CONSISTENCIA_VETORIAL_MATRICIAL', documentoId: null, nomeFicheiro: null, resultado: 'MANUAL', detalhe: 'Compare.' }),
];

function ficheiro(id: string, justificacaoDesbloqueio: string | null): DocumentoProcesso {
  return { id, justificacaoDesbloqueio } as DocumentoProcesso;
}

function estruturaCom(...ficheiros: DocumentoProcesso[]): EstruturaDocumental {
  return {
    separadores: [{ separador: 'Plantas', elementoInformacaoBase: null, publicacao: [], deposito: [{ ficheiros } as never] }],
    outros: [],
    pendencias: [],
  };
}

describe('ChecklistValidacaoComponent', () => {
  let fixture: ComponentFixture<ChecklistValidacaoComponent>;
  let verificacoes: ReturnType<typeof vi.fn>;
  let estrutura: ReturnType<typeof vi.fn>;
  let descarregar: ReturnType<typeof vi.fn>;
  let emCurso: ReturnType<typeof signal<string | null>>;
  let erroDescarga: ReturnType<typeof signal<string | null>>;

  function abrir(processoId = 'p1'): void {
    fixture = TestBed.createComponent(ChecklistValidacaoComponent);
    fixture.componentRef.setInput('processoId', processoId);
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function botoes(rotulo: string): HTMLButtonElement[] {
    return Array.from(el().querySelectorAll('button')).filter((b) => b.textContent?.trim() === rotulo);
  }

  function grupos(): HTMLElement[] {
    return Array.from(el().querySelectorAll('section.grupo'));
  }

  function linhasDoGrupo(verificacao: string): HTMLTableRowElement[] {
    return Array.from(el().querySelectorAll(`section.grupo[data-verificacao="${verificacao}"] tbody tr`));
  }

  beforeEach(() => {
    verificacoes = vi.fn().mockReturnValue(of(LINHAS));
    estrutura = vi.fn().mockReturnValue(of(estruturaCom()));
    descarregar = vi.fn();
    emCurso = signal<string | null>(null);
    erroDescarga = signal<string | null>(null);
    TestBed.configureTestingModule({
      imports: [ChecklistValidacaoComponent],
      providers: [
        { provide: DocumentoService, useValue: { verificacoes, estrutura } },
        { provide: DescargaService, useValue: { descarregar, emCurso, erro: erroDescarga } },
      ],
    });
  });

  it('pede a checklist do processo e mostra o h2, um grupo por verificação pela ordem do FR-092 e o resumo', () => {
    abrir();

    expect(verificacoes).toHaveBeenCalledExactlyOnceWith('p1');
    expect(el().querySelector('h2')?.textContent?.trim()).toBe('Checklist de validação');
    expect(grupos().map((g) => g.querySelector('h3')?.textContent?.trim())).toEqual([
      'Correspondência entre o ato e o texto do diploma',
      'Formato do quadro anexo',
      'Georreferenciação matricial',
      'Consistência entre informação vetorial e matricial',
      'N.º de folha no nome do ficheiro TIF',
      'Correção do ZIP',
    ]);
    const resumo = el().querySelector('[data-campo="resumo"]') as HTMLElement;
    expect(resumo.getAttribute('role')).toBe('status');
    expect(resumo.textContent?.trim()).toBe('2 conformes · 1 não conformes · 3 a verificar manualmente');
  });

  it('não mostra os grupos sem linhas', () => {
    verificacoes.mockReturnValue(of([LINHAS[2], LINHAS[3]]));

    abrir();

    expect(grupos().map((g) => g.dataset['verificacao'])).toEqual(['CORRESPONDENCIA_ATO_TEXTO', 'FORMATO_QUADRO_ANEXO']);
  });

  it.each([
    ['GEORREFERENCIACAO_MATRICIAL', 'Não conforme', 'erro', 'Falta o ficheiro 1199_Ord_A.tfw no mesmo ZIP.'],
    ['FORMATO_QUADRO_ANEXO', 'Conforme', 'sucesso', '—'],
    ['CORRESPONDENCIA_ATO_TEXTO', 'Verificação manual', 'aviso', 'Confirme o ato.'],
  ])('%s: selo «%s» (tom %s) com texto e o detalhe da API', (verificacao, rotulo, tom, detalhe) => {
    abrir();

    const [tr] = linhasDoGrupo(verificacao);
    const selo = tr.querySelector('app-selo') as HTMLElement;
    expect(selo.textContent?.trim()).toBe(rotulo);
    expect(selo.className).toContain(`selo-${tom}`);
    expect(tr.querySelector('[data-campo="detalhe"]')?.textContent?.trim()).toBe(detalhe);
  });

  it('a tabela de cada grupo tem cabeçalhos de coluna e a legenda com o nome do grupo', () => {
    abrir();

    const grupo = grupos()[2];
    expect(grupo.getAttribute('aria-labelledby')).toBe(grupo.querySelector('h3')?.id);
    expect(grupo.querySelector('caption')?.textContent?.trim()).toBe('Georreferenciação matricial');
    expect(Array.from(grupo.querySelectorAll('th[scope="col"]')).map((th) => th.textContent?.trim())).toEqual([
      'Ficheiro',
      'Resultado',
      'Detalhe',
      'Ações',
    ]);
  });

  it('a linha do processo mostra «Processo» e não tem «Descarregar»', () => {
    abrir();

    const [tr] = linhasDoGrupo('CORRESPONDENCIA_ATO_TEXTO');
    expect(tr.querySelector('[data-campo="ficheiro"]')?.textContent?.trim()).toBe('Processo');
    expect(tr.querySelector('button')).toBeNull();
  });

  it('«Descarregar» tem o nome do ficheiro e pede a descarga do documento da linha', () => {
    abrir();

    const [tr] = linhasDoGrupo('GEORREFERENCIACAO_MATRICIAL');
    const botao = tr.querySelector('button') as HTMLButtonElement;
    expect(botao.textContent?.trim()).toBe('Descarregar');
    expect(botao.getAttribute('aria-label')).toBe('Descarregar 1199_Ord_A.tif');

    botao.click();

    expect(descarregar).toHaveBeenCalledExactlyOnceWith('p1', 'z1');
  });

  it('desativa o «Descarregar» do documento em descarga e mostra o erro da descarga pedida aqui', () => {
    abrir();
    erroDescarga.set('Não foi possível descarregar o ficheiro. Tente novamente.');
    fixture.detectChanges();
    expect(el().querySelector('.erro-descarga')).toBeNull();

    linhasDoGrupo('FORMATO_QUADRO_ANEXO')[0].querySelector('button')!.click();
    emCurso.set('z1');
    fixture.detectChanges();

    expect(linhasDoGrupo('GEORREFERENCIACAO_MATRICIAL')[0].querySelector('button')?.disabled).toBe(true);
    expect(linhasDoGrupo('FORMATO_QUADRO_ANEXO')[0].querySelector('button')?.disabled).toBe(false);
    expect(el().querySelector('.erro-descarga')?.textContent).toContain('Não foi possível descarregar o ficheiro.');
  });

  it('mostra «Desbloqueado: …» por baixo do nome quando o ficheiro tem justificação de desbloqueio', () => {
    estrutura.mockReturnValue(of(estruturaCom(ficheiro('z1', 'Legenda desatualizada.'), ficheiro('d1', null))));

    abrir();

    expect(linhasDoGrupo('CORRECAO_ZIP')[0].querySelector('[data-campo="desbloqueio"]')?.textContent?.trim()).toBe(
      'Desbloqueado: Legenda desatualizada.',
    );
    expect(linhasDoGrupo('FORMATO_QUADRO_ANEXO')[0].querySelector('[data-campo="desbloqueio"]')).toBeNull();
  });

  it('sem a estrutura documental, mostra a checklist sem justificações', () => {
    estrutura.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Falhou.', 't1', null)));

    abrir();

    expect(grupos()).toHaveLength(6);
    expect(el().querySelector('[data-campo="desbloqueio"]')).toBeNull();
    expect(el().querySelector('[role="alert"]')).toBeNull();
  });

  it('mostra «A carregar a checklist…» enquanto espera pela API', () => {
    const pedido = new Subject<VerificacaoDocumental[]>();
    verificacoes.mockReturnValue(pedido);

    abrir();
    expect(el().textContent).toContain('A carregar a checklist…');

    pedido.next(LINHAS);
    fixture.detectChanges();
    expect(el().textContent).not.toContain('A carregar a checklist…');
  });

  it('em erro mostra o banner e «Tentar novamente», que pede a checklist de novo', () => {
    verificacoes
      .mockReturnValueOnce(throwError(() => new ApiError('ERRO_INTERNO', 'Serviço indisponível.', 't1', null)))
      .mockReturnValueOnce(of(LINHAS));

    abrir();
    expect(el().querySelector('app-error-banner [role="alert"]')?.textContent).toContain('Serviço indisponível.');
    expect(grupos()).toHaveLength(0);

    botoes('Tentar novamente')[0].click();
    fixture.detectChanges();

    expect(verificacoes).toHaveBeenCalledTimes(2);
    expect(el().textContent).not.toContain('Serviço indisponível.');
    expect(grupos()).toHaveLength(6);
  });

  it('«Atualizar» pede a checklist de novo', () => {
    abrir();
    verificacoes.mockReturnValue(of([LINHAS[2]]));

    botoes('Atualizar')[0].click();
    fixture.detectChanges();

    expect(verificacoes).toHaveBeenCalledTimes(2);
    expect(estrutura).toHaveBeenCalledTimes(2);
    expect(grupos()).toHaveLength(1);
  });

  it('volta a pedir a checklist quando o processo muda', () => {
    abrir();

    fixture.componentRef.setInput('processoId', 'p2');
    fixture.detectChanges();

    expect(verificacoes).toHaveBeenLastCalledWith('p2');
  });

  it('com a lista vazia diz que não há verificações a mostrar', () => {
    verificacoes.mockReturnValue(of([]));

    abrir();

    expect(el().textContent).toContain('Este processo não tem verificações a mostrar.');
    expect(el().querySelector('[data-campo="resumo"]')?.textContent?.trim()).toBe(
      '0 conformes · 0 não conformes · 0 a verificar manualmente',
    );
  });

  it('não tem forma de marcar uma verificação manual e a nota diz que não ficam registadas', () => {
    abrir();

    expect(el().querySelectorAll('input, select, textarea, [role="checkbox"]')).toHaveLength(0);
    expect(
      Array.from(el().querySelectorAll('button'))
        .map((b) => b.textContent?.trim())
        .filter((rotulo) => rotulo !== 'Descarregar'),
    ).toEqual(['Atualizar']);
    expect(el().querySelector('app-alerta')?.textContent).toContain('As verificações manuais não ficam registadas.');
  });
});

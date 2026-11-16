import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { EstruturaDocumental } from '../../../models/documento.model';
import { ConjuntoMotuDados, MetadadosMotu, RegistoMotu, SerieMotuDados } from '../../../models/plantas.model';
import { DocumentoService } from '../../../services/documento.service';
import { PlantasService } from '../../../services/plantas.service';
import { ConjuntoMotuComponent } from './conjunto-motu.component';
import { ProcessoMetadadosMotuComponent } from './processo-metadados-motu.component';

function registoSerie(completo = true): RegistoMotu {
  return { id: 'serie-1', tipo: 'SERIE', opcional: false, completo, titulo: 'PDM de Sintra', dados: {} as SerieMotuDados };
}

function registoConjunto(id: string, completo: boolean, zip: { id: string; nome: string } | null): RegistoMotu {
  return { id, tipo: 'CDG', opcional: false, completo, titulo: 'Planta ' + id, zip, dados: {} as ConjuntoMotuDados, plantas: [] };
}

function registoOpcional(parcial: Partial<RegistoMotu> = {}): RegistoMotu {
  return {
    id: 'opc-1',
    tipo: 'CDG',
    opcional: true,
    completo: false,
    titulo: 'PDM de Sintra',
    zip: null,
    dados: {} as ConjuntoMotuDados,
    plantas: [],
    ...parcial,
  };
}

function estrutura(): EstruturaDocumental {
  const ficheiro = (id: string, nome: string) => ({ id, nomeFicheiro: nome, extensao: 'zip' }) as never;
  const item = (tipoElemento: string, ficheiros: unknown[]) => ({ tipoElemento, ficheiros }) as never;
  return {
    separadores: [
      {
        separador: 'Plantas',
        elementoInformacaoBase: null,
        publicacao: [
          item('PECA_GRAFICA', [ficheiro('p1', 'ordenamento-pub.zip'), ficheiro('p2', 'condicionantes-pub.zip')]),
          item('PECA_ESCRITA', [ficheiro('e1', 'regulamento.zip')]),
        ],
        deposito: [item('PECA_GRAFICA', [ficheiro('p3', 'ordenamento-dep.zip')])],
      },
    ],
    outros: [],
    pendencias: [],
  };
}

function ficha(registos: RegistoMotu[]): MetadadosMotu {
  return { aplicaFichaMotu: true, responsavel: {} as never, registos };
}

describe('Fichas de Conjunto opcionais na página', () => {
  let fixture: ComponentFixture<ProcessoMetadadosMotuComponent>;
  let plantas: {
    obterMetadadosMotu: ReturnType<typeof vi.fn>;
    criarFichaConjuntoOpcional: ReturnType<typeof vi.fn>;
    removerFichaConjuntoOpcional: ReturnType<typeof vi.fn>;
    gravarSerieMotu: ReturnType<typeof vi.fn>;
    gravarConjuntoMotu: ReturnType<typeof vi.fn>;
  };

  function montar(registos: RegistoMotu[], editavel = true): void {
    plantas.obterMetadadosMotu.mockReturnValue(of(ficha(registos)));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProcessoMetadadosMotuComponent],
      providers: [
        { provide: PlantasService, useValue: plantas },
        { provide: DocumentoService, useValue: { estrutura: vi.fn().mockReturnValue(of(estrutura())) } },
      ],
    });
    fixture = TestBed.createComponent(ProcessoMetadadosMotuComponent);
    fixture.componentInstance.processoId = 'processo-1';
    fixture.componentInstance.editavel = editavel;
    fixture.detectChanges();
  }

  function el<T extends HTMLElement = HTMLElement>(seletor: string): T {
    return fixture.nativeElement.querySelector(seletor);
  }

  function clicar(seletor: string): void {
    el(seletor).click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    plantas = {
      obterMetadadosMotu: vi.fn(),
      criarFichaConjuntoOpcional: vi.fn().mockReturnValue(of(registoOpcional())),
      removerFichaConjuntoOpcional: vi.fn().mockReturnValue(of(undefined)),
      gravarSerieMotu: vi.fn(),
      gravarConjuntoMotu: vi.fn(),
    };
    montar([registoSerie(), registoConjunto('c1', true, { id: 'p1', nome: 'ordenamento-pub.zip' })]);
  });

  it('em edição mostra «Adicionar ficha de Conjunto» e fora dela só leitura, sem adicionar nem remover', () => {
    expect(el('.adicionar-ficha').textContent?.trim()).toBe('Adicionar ficha de Conjunto');

    montar([registoSerie(), registoOpcional()], false);

    expect(el('.adicionar-ficha')).toBeNull();
    expect(el('.remover-ficha')).toBeNull();
    expect(el('.etiqueta-opcional').textContent?.trim()).toBe('Opcional');
    expect(el('#cdg-opc-1-processoDocumentoId')).toBeNull();
  });

  it('cria a ficha sem reproduzir a última e mostra o bloco com a etiqueta «Opcional»', () => {
    clicar('.adicionar-ficha');
    expect(el('.reproduzir-ultima').closest('label')?.textContent).toContain(
      'Pretende que a próxima ficha de metadados reproduza os dados constantes da última ficha',
    );

    clicar('.confirmar-adicionar');

    expect(plantas.criarFichaConjuntoOpcional).toHaveBeenCalledWith('processo-1', false);
    expect(el('.etiqueta-opcional').textContent?.trim()).toBe('Opcional');
    expect(el('.confirmar-adicionar')).toBeNull();
  });

  it('cria a ficha a reproduzir a última', () => {
    clicar('.adicionar-ficha');
    clicar('.reproduzir-ultima');

    clicar('.confirmar-adicionar');

    expect(plantas.criarFichaConjuntoOpcional).toHaveBeenCalledWith('processo-1', true);
  });

  it('um erro ao criar fica no diálogo e não acrescenta a ficha', () => {
    plantas.criarFichaConjuntoOpcional.mockReturnValue(throwError(() => new ApiError('MOTU_NAO_APLICAVEL', 'Não se aplica.', 't', null)));
    clicar('.adicionar-ficha');

    clicar('.confirmar-adicionar');

    expect(el('[role="alert"]').textContent).toContain('Não se aplica.');
    expect(el('.etiqueta-opcional')).toBeNull();
  });

  it('o seletor só oferece as peças gráficas sem ficha e «Sem ZIP»', () => {
    montar([registoSerie(), registoConjunto('c1', true, { id: 'p1', nome: 'ordenamento-pub.zip' }), registoOpcional()]);

    const opcoes = [...fixture.nativeElement.querySelectorAll('#cdg-opc-1-processoDocumentoId option')].map((o: Element) =>
      o.textContent?.trim(),
    );

    expect(opcoes).toEqual(['Sem ZIP', 'condicionantes-pub.zip', 'ordenamento-dep.zip']);
  });

  it('remover pede confirmação: cancelar não remove', () => {
    montar([registoSerie(), registoOpcional()]);

    clicar('.remover-ficha');
    expect(el('[role="dialog"]').textContent).toContain('Remover esta ficha de Conjunto?');
    clicar('.botao-nao');

    expect(plantas.removerFichaConjuntoOpcional).not.toHaveBeenCalled();
    expect(el('.etiqueta-opcional')).not.toBeNull();
  });

  it('confirmar a remoção faz DELETE, tira a ficha da lista e leva o foco para «Adicionar ficha de Conjunto»', async () => {
    montar([registoSerie(), registoOpcional()]);
    document.body.appendChild(fixture.nativeElement);

    clicar('.remover-ficha');
    clicar('.botao-sim');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(plantas.removerFichaConjuntoOpcional).toHaveBeenCalledWith('processo-1', 'opc-1');
    expect(el('.etiqueta-opcional')).toBeNull();
    expect(document.activeElement).toBe(el('.adicionar-ficha'));
    fixture.nativeElement.remove();
  });

  it('a Série e os Conjuntos das peças não têm «Remover ficha» nem seletor de ZIP', () => {
    expect(el('.remover-ficha')).toBeNull();
    expect(el('#cdg-c1-processoDocumentoId')).toBeNull();
  });

  it('«Remover ficha» diz no aria-label qual é a ficha', () => {
    montar([registoSerie(), registoOpcional({ zip: { id: 'p2', nome: 'condicionantes-pub.zip' } })]);

    expect(el('.remover-ficha').getAttribute('aria-label')).toContain('(condicionantes-pub.zip)');
  });
});

describe('ConjuntoMotuComponent numa ficha opcional', () => {
  let fixture: ComponentFixture<ConjuntoMotuComponent>;
  let servico: { gravarConjuntoMotu: ReturnType<typeof vi.fn>; removerFichaConjuntoOpcional: ReturnType<typeof vi.fn> };
  const gravada = vi.fn();

  function montar(registo: RegistoMotu): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ConjuntoMotuComponent],
      providers: [{ provide: PlantasService, useValue: servico }],
    });
    fixture = TestBed.createComponent(ConjuntoMotuComponent);
    fixture.componentInstance.processoId = 'processo-1';
    fixture.componentInstance.registo = registo;
    fixture.componentRef.setInput('pecasLivres', [
      { id: 'p2', nome: 'condicionantes-pub.zip' },
      { id: 'p3', nome: 'ordenamento-dep.zip' },
    ]);
    fixture.componentInstance.gravada.subscribe(gravada);
    fixture.detectChanges();
  }

  function opcoesDoSeletor(): string[] {
    const seletor = fixture.nativeElement.querySelector('#cdg-opc-1-processoDocumentoId') as HTMLSelectElement;
    return Array.from(seletor.options).map((opcao) => opcao.value);
  }

  it('atualiza o seletor quando as peças livres chegam depois de desenhar o bloco', () => {
    fixture.componentRef.setInput('pecasLivres', []);
    fixture.detectChanges();
    expect(opcoesDoSeletor()).toEqual(['']);

    fixture.componentRef.setInput('pecasLivres', [{ id: 'p4', nome: 'tarde.zip' }]);
    fixture.detectChanges();

    expect(opcoesDoSeletor()).toEqual(['', 'p4']);
  });

  function escolher(valor: string): void {
    const seletor = fixture.nativeElement.querySelector('#cdg-opc-1-processoDocumentoId') as HTMLSelectElement;
    seletor.value = valor;
    seletor.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    gravada.mockReset();
    servico = {
      gravarConjuntoMotu: vi.fn().mockReturnValue(of(registoOpcional({ zip: { id: 'p2', nome: 'condicionantes-pub.zip' } }))),
      removerFichaConjuntoOpcional: vi.fn(),
    };
    montar(registoOpcional());
  });

  it('liga a ficha ao ZIP escolhido com processoDocumentoId e mostra o ZIP', () => {
    escolher('p2');
    const resultado = vi.fn();

    fixture.componentInstance.gravar().subscribe(resultado);
    fixture.detectChanges();

    expect(servico.gravarConjuntoMotu.mock.calls[0][2].processoDocumentoId).toBe('p2');
    expect(resultado).toHaveBeenCalledWith(true);
    expect(gravada).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('.zip-conjunto').textContent.trim()).toBe('condicionantes-pub.zip');
  });

  it('«Sem ZIP» desliga a ficha do ZIP com null', () => {
    montar(registoOpcional({ zip: { id: 'p2', nome: 'condicionantes-pub.zip' } }));
    escolher('');

    fixture.componentInstance.gravar().subscribe();

    expect(servico.gravarConjuntoMotu.mock.calls[0][2].processoDocumentoId).toBeNull();
  });

  it('o 409 MOTU_PECA_COM_FICHA aparece junto ao campo e o que foi preenchido fica', () => {
    servico.gravarConjuntoMotu.mockReturnValue(
      throwError(() => new ApiError('MOTU_PECA_COM_FICHA', 'Esta peça já tem uma ficha de metadados.', 't', null)),
    );
    const resumo = fixture.nativeElement.querySelector('#cdg-opc-1-resumo') as HTMLTextAreaElement;
    resumo.value = 'Resumo preenchido';
    resumo.dispatchEvent(new Event('input'));
    escolher('p2');
    const resultado = vi.fn();

    fixture.componentInstance.gravar().subscribe(resultado);
    fixture.detectChanges();

    expect(resultado).toHaveBeenCalledWith(false);
    const erro = fixture.nativeElement.querySelector('#cdg-opc-1-erro-processoDocumentoId');
    expect(erro.textContent).toContain('já tem uma ficha');
    expect(fixture.nativeElement.querySelector('#cdg-opc-1-processoDocumentoId').getAttribute('aria-invalid')).toBe('true');
    expect(resumo.value).toBe('Resumo preenchido');
    expect(gravada).not.toHaveBeenCalled();
  });

  it('o 400 no processoDocumentoId também aparece junto ao campo', () => {
    servico.gravarConjuntoMotu.mockReturnValue(
      throwError(
        () => new ApiError('MOTU_CAMPOS_INVALIDOS', 'Campos inválidos.', 't', [{ path: 'processoDocumentoId', erro: 'Não é uma peça gráfica.' }]),
      ),
    );
    escolher('p3');

    fixture.componentInstance.gravar().subscribe();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#cdg-opc-1-erro-processoDocumentoId').textContent).toContain('Não é uma peça gráfica.');
  });

  it('um erro na remoção fica visível e a ficha mantém-se', () => {
    servico.removerFichaConjuntoOpcional.mockReturnValue(throwError(() => new ApiError('DOC_ESTADO_INVALIDO', 'Fora de edição.', 't', null)));
    const removida = vi.fn();
    fixture.componentInstance.removida.subscribe(removida);

    (fixture.nativeElement.querySelector('.remover-ficha') as HTMLButtonElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.botao-sim') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(removida).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.erro-geral').textContent).toContain('Fora de edição.');
  });
});

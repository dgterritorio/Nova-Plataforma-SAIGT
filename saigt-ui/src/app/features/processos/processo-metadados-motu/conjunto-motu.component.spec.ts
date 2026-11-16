import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { ConjuntoMotuDados, RegistoMotu } from '../../../models/plantas.model';
import { PlantasService } from '../../../services/plantas.service';
import { ConjuntoMotuComponent } from './conjunto-motu.component';

describe('ConjuntoMotuComponent', () => {
  let fixture: ComponentFixture<ConjuntoMotuComponent>;
  let servico: { gravarConjuntoMotu: ReturnType<typeof vi.fn> };

  const dadosHomologada: ConjuntoMotuDados = {
    tituloAlternativo: 'Planta de Ordenamento do PDM',
    resumo: 'Uso do solo',
    palavrasChave: 'solo',
    denominadorEscala: '10000',
    sistemaReferencia: 'ETRS89',
    codigoSistema: null,
    cartografiaTipo: 'CARTOGRAFIA_HOMOLOGADA',
    dataEdicao: '2024-05-01',
    dataAtualizacao: null,
    atualizacaoComplemento: 'Nenhuma',
    entidadeProprietaria: 'DGT',
    entidadeProdutora: 'Município',
    sistemaReferenciaNome: 'ETRS89',
    sistemaReferenciaCodigo: '4258',
    exatidaoPosicional: '1 m',
    exatidaoTematica: '95 %',
    numeroHomologacao: 'H-1',
    dataHomologacao: '2023-01-15',
    entidadeHomologacao: 'IGeoE',
  };

  function registo(parcial: Partial<RegistoMotu> = {}): RegistoMotu {
    return {
      id: 'cdg-1',
      tipo: 'CDG',
      opcional: false,
      completo: false,
      titulo: 'Planta de ordenamento',
      zip: { id: 'doc-1', nome: 'ordenamento-pub.zip' },
      dados: dadosHomologada,
      plantas: [
        { id: 'p1', nomeRegulamento: 'Uso do Solo', numeroFolha: '1', nomeTiff: 'o1.tif' },
        { id: 'p2', nomeRegulamento: 'Uso do Solo', numeroFolha: '2', nomeTiff: 'o2.tif' },
      ],
      ...parcial,
    };
  }

  function montar(reg: RegistoMotu, editavel = true): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ConjuntoMotuComponent],
      providers: [{ provide: PlantasService, useValue: servico }],
    });
    fixture = TestBed.createComponent(ConjuntoMotuComponent);
    fixture.componentInstance.processoId = 'processo-1';
    fixture.componentInstance.registo = reg;
    fixture.componentInstance.editavel = editavel;
    fixture.detectChanges();
  }

  function el(seletor: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(seletor);
  }

  function escrever(seletor: string, valor: string): void {
    const campo = el(seletor);
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function escolherTipo(valor: string): void {
    const campo = el('#cdg-cdg-1-cartografiaTipo') as unknown as HTMLSelectElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function clicarEditar(): void {
    (fixture.nativeElement.querySelector('.editar') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    servico = { gravarConjuntoMotu: vi.fn().mockReturnValue(of(registo({ completo: true }))) };
    montar(registo());
  });

  it('mostra o título da planta, o nome do ZIP, o estado e os grupos', () => {
    expect(el('.titulo-conjunto').textContent?.trim()).toBe('Planta de ordenamento');
    expect(el('.zip-conjunto').textContent?.trim()).toBe('ordenamento-pub.zip');
    expect(el('.estado-conjunto').textContent?.trim()).toBe('Incompleto');
    const titulos = [...fixture.nativeElement.querySelectorAll('.titulo')].map((e: Element) => e.textContent?.trim());
    expect(titulos).toEqual(['Identificação', 'Sistema de Referência', 'Cartografia de Referência', 'Plantas SAIGT SNIT']);
  });

  it('mostra os campos da Cartografia Homologada e esconde-os ao mudar para «Outro»', () => {
    expect(el('#cdg-cdg-1-numeroHomologacao')).not.toBeNull();
    expect(el('#cdg-cdg-1-dataHomologacao')).not.toBeNull();
    expect(el('#cdg-cdg-1-entidadeHomologacao')).not.toBeNull();
    expect(el('#cdg-cdg-1-serieCartograficaOficial')).toBeNull();

    escolherTipo('OUTRO');

    expect(el('#cdg-cdg-1-numeroHomologacao')).toBeNull();
    expect(el('#cdg-cdg-1-dataHomologacao')).toBeNull();
    expect(el('#cdg-cdg-1-entidadeHomologacao')).toBeNull();

    escolherTipo('SERIE_CARTOGRAFICA_OFICIAL');
    expect(el('#cdg-cdg-1-serieCartograficaOficial')).not.toBeNull();
  });

  it('a tabela abre em leitura, passa a edição com «Editar» e o TIFF não se edita', () => {
    expect(fixture.nativeElement.querySelectorAll('.tabela tbody tr').length).toBe(2);
    expect(fixture.nativeElement.querySelectorAll('.tabela input').length).toBe(0);
    expect(fixture.nativeElement.querySelector('.tabela').textContent).toContain('o1.tif');

    clicarEditar();

    expect(fixture.nativeElement.querySelectorAll('.tabela tbody tr:first-child input').length).toBe(2);
    expect(fixture.nativeElement.querySelector('.tabela tbody tr:first-child td:last-child input')).toBeNull();
    expect(fixture.nativeElement.querySelector('.tabela input').getAttribute('aria-required')).toBe('true');
  });

  it('sem folhas mostra a indicação para associar na página das Plantas', () => {
    montar(registo({ plantas: [] }));

    expect(fixture.nativeElement.querySelector('.sem-folhas').textContent).toContain(
      'Sem folhas associadas: associe as folhas na página das Plantas',
    );
    expect(fixture.nativeElement.querySelector('.tabela')).toBeNull();
    expect(fixture.nativeElement.querySelector('.editar')).toBeNull();
  });

  it('não grava quando nada mudou', () => {
    const resultado = vi.fn();

    fixture.componentInstance.gravar().subscribe(resultado);

    expect(resultado).toHaveBeenCalledWith(true);
    expect(servico.gravarConjuntoMotu).not.toHaveBeenCalled();
  });

  it('grava os campos do tipo escolhido e as folhas corrigidas e passa a «Completo»', () => {
    clicarEditar();
    escrever('.tabela tbody tr:first-child td:first-child input', '  Corrigido  ');
    const resultado = vi.fn();

    fixture.componentInstance.gravar().subscribe(resultado);
    fixture.detectChanges();

    const chamada = servico.gravarConjuntoMotu.mock.calls[0];
    const corpo = chamada[2];
    expect(chamada.slice(0, 2)).toEqual(['processo-1', 'cdg-1']);
    expect(corpo.cartografiaTipo).toBe('CARTOGRAFIA_HOMOLOGADA');
    expect(corpo.numeroHomologacao).toBe('H-1');
    expect(corpo.codigoSistema).toBeNull();
    expect(corpo.plantas).toEqual([
      { id: 'p1', nomeRegulamento: 'Corrigido', numeroFolha: '1' },
      { id: 'p2', nomeRegulamento: 'Uso do Solo', numeroFolha: '2' },
    ]);
    expect(resultado).toHaveBeenCalledWith(true);
    expect(el('.estado-conjunto').textContent?.trim()).toBe('Completo');
    expect(fixture.nativeElement.querySelectorAll('.tabela input').length).toBe(0);
  });

  it('ao mudar para «Outro» não envia os campos da homologação', () => {
    escolherTipo('OUTRO');

    fixture.componentInstance.gravar().subscribe();

    const corpo = servico.gravarConjuntoMotu.mock.calls[0][2];
    expect(corpo.cartografiaTipo).toBe('OUTRO');
    expect('numeroHomologacao' in corpo).toBe(false);
    expect('dataHomologacao' in corpo).toBe(false);
    expect('entidadeHomologacao' in corpo).toBe(false);
  });

  it('um 400 mostra o erro no campo e na folha certos e não avança', () => {
    servico.gravarConjuntoMotu.mockReturnValue(
      throwError(
        () =>
          new ApiError('MOTU_CAMPOS_INVALIDOS', 'Campos da ficha MOTU inválidos.', 't1', [
            { path: 'exatidaoPosicional', erro: 'tamanho máximo 100' },
            { path: 'plantas[1].numeroFolha', erro: 'Excede 20 caracteres.' },
          ]),
      ),
    );
    clicarEditar();
    escrever('#cdg-cdg-1-exatidaoPosicional', 'x');
    const resultado = vi.fn();

    fixture.componentInstance.gravar().subscribe(resultado);
    fixture.detectChanges();

    expect(resultado).toHaveBeenCalledWith(false);
    expect(el('#cdg-cdg-1-erro-exatidaoPosicional').textContent).toContain('tamanho máximo 100');
    expect(el('#cdg-cdg-1-exatidaoPosicional').getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.querySelector('.tabela tbody tr:nth-child(2) .erro').textContent).toContain('Excede 20');
  });

  it('não grava quando não é editável e os campos ficam desativados', () => {
    montar(registo(), false);

    fixture.componentInstance.gravar().subscribe();

    expect(servico.gravarConjuntoMotu).not.toHaveBeenCalled();
    expect(el('#cdg-cdg-1-resumo').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.editar')).toBeNull();
  });
});

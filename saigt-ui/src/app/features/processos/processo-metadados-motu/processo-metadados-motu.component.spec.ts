import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { MetadadosMotu, RegistoMotu, SerieMotuDados } from '../../../models/plantas.model';
import { DocumentoService } from '../../../services/documento.service';
import { PlantasService } from '../../../services/plantas.service';
import { ProcessoMetadadosMotuComponent } from './processo-metadados-motu.component';

describe('ProcessoMetadadosMotuComponent', () => {
  let fixture: ComponentFixture<ProcessoMetadadosMotuComponent>;
  let plantasServiceMock: {
    obterMetadadosMotu: ReturnType<typeof vi.fn>;
    gravarSerieMotu: ReturnType<typeof vi.fn>;
    gravarConjuntoMotu: ReturnType<typeof vi.fn>;
  };

  const vazia: SerieMotuDados = {
    tituloAlternativo: null,
    resumo: null,
    palavrasChave: null,
    denominadorEscala: null,
    sistemaReferencia: null,
    codigoSistema: null,
    historico: null,
    relatorio: null,
  };

  function registo(dados: Partial<SerieMotuDados> = {}, completo = false): RegistoMotu {
    return { id: 'serie-1', tipo: 'SERIE', opcional: false, completo, titulo: 'PDM de Sintra', dados: { ...vazia, ...dados } };
  }

  function ficha(serie: RegistoMotu): MetadadosMotu {
    return {
      aplicaFichaMotu: true,
      responsavel: {
        nomeOrganizacao: 'Município de Sintra',
        funcao: 'Produtor',
        correioEletronico: null,
        enderecoUrl: null,
        rua: null,
        localidade: null,
        codigoPostal: null,
        pais: 'Portugal',
        telefone: null,
        fax: null,
      },
      registos: [serie],
    };
  }

  function entrada(campo: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(`#motu-${campo}`);
  }

  function escrever(campo: string, valor: string): void {
    const el = entrada(campo);
    el.value = valor;
    el.dispatchEvent(new Event('input'));
  }

  beforeEach(() => {
    plantasServiceMock = {
      obterMetadadosMotu: vi.fn().mockReturnValue(of(ficha(registo({ palavrasChave: 'ordenamento' })))),
      gravarSerieMotu: vi.fn().mockReturnValue(of(registo({ palavrasChave: 'ordenamento' }))),
      gravarConjuntoMotu: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [ProcessoMetadadosMotuComponent],
      providers: [
        { provide: PlantasService, useValue: plantasServiceMock },
        { provide: DocumentoService, useValue: { estrutura: vi.fn().mockReturnValue(of({ separadores: [], outros: [], pendencias: [] })) } },
      ],
    });
    fixture = TestBed.createComponent(ProcessoMetadadosMotuComponent);
    fixture.componentInstance.processoId = 'processo-1';
    fixture.detectChanges();
  });

  it('mostra a Série com os quatro grupos, o responsável e o título só de leitura e «Incompleto»', () => {
    const titulos = [...fixture.nativeElement.querySelectorAll('.titulo')].map((e: Element) => e.textContent?.trim());
    expect(titulos).toEqual(['Responsável', 'Identificação', 'Sistema de Referência', 'Qualidade']);
    expect(fixture.nativeElement.textContent).toContain('Município de Sintra');
    expect(fixture.nativeElement.textContent).toContain('PDM de Sintra');
    expect(fixture.nativeElement.querySelector('.estado-serie').textContent.trim()).toBe('Incompleto');
    expect(entrada('palavrasChave').value).toBe('ordenamento');
    expect(entrada('historico').getAttribute('maxlength')).toBe('4000');
  });

  it('marca os obrigatórios com * e o código do sistema não', () => {
    const rotulo = fixture.nativeElement.querySelector('label[for="motu-historico"]') as HTMLLabelElement;
    expect(rotulo.textContent).toContain('Histórico');
    expect(rotulo.querySelector('.obrigatorio')).not.toBeNull();
    expect(entrada('historico').getAttribute('aria-required')).toBe('true');
    expect(entrada('codigoSistema').getAttribute('aria-required')).toBeNull();
  });

  it('grava os campos vazios como nulos e passa a «Completo» com o que a API devolve', () => {
    plantasServiceMock.gravarSerieMotu.mockReturnValue(of(registo({ resumo: 'Plano diretor' }, true)));
    escrever('resumo', '  Plano diretor  ');
    const resultado = vi.fn();

    fixture.componentInstance.gravar().subscribe(resultado);
    fixture.detectChanges();

    expect(plantasServiceMock.gravarSerieMotu).toHaveBeenCalledWith('processo-1', 'serie-1', { ...vazia, palavrasChave: 'ordenamento', resumo: 'Plano diretor' });
    expect(resultado).toHaveBeenCalledWith(true);
    expect(fixture.nativeElement.querySelector('.estado-serie').textContent.trim()).toBe('Completo');
  });

  it('um 400 mostra o erro no campo e não avança', () => {
    plantasServiceMock.gravarSerieMotu.mockReturnValue(
      throwError(() => new ApiError('MOTU_CAMPOS_INVALIDOS', 'Campos da ficha MOTU inválidos.', 't1', [{ path: 'tituloAlternativo', erro: 'tamanho máximo 300' }])),
    );
    const resultado = vi.fn();

    fixture.componentInstance.gravar().subscribe(resultado);
    fixture.detectChanges();

    expect(resultado).toHaveBeenCalledWith(false);
    expect(fixture.nativeElement.querySelector('#motu-erro-tituloAlternativo').textContent).toContain('tamanho máximo 300');
    expect(entrada('tituloAlternativo').getAttribute('aria-invalid')).toBe('true');
  });

  it('um erro sem campo conhecido aparece no topo', () => {
    plantasServiceMock.gravarSerieMotu.mockReturnValue(
      throwError(() => new ApiError('MOTU_NAO_APLICAVEL', 'A ficha MOTU não se aplica a este Procedimento.', 't2', null)),
    );

    fixture.componentInstance.gravar().subscribe();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.erro-geral').textContent).toContain('não se aplica');
  });

  it('não grava quando não é editável', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProcessoMetadadosMotuComponent],
      providers: [
        { provide: PlantasService, useValue: plantasServiceMock },
        { provide: DocumentoService, useValue: { estrutura: vi.fn().mockReturnValue(of({ separadores: [], outros: [], pendencias: [] })) } },
      ],
    });
    fixture = TestBed.createComponent(ProcessoMetadadosMotuComponent);
    fixture.componentInstance.processoId = 'processo-1';
    fixture.componentInstance.editavel = false;
    fixture.detectChanges();
    const resultado = vi.fn();

    fixture.componentInstance.gravar().subscribe(resultado);

    expect(resultado).toHaveBeenCalledWith(true);
    expect(plantasServiceMock.gravarSerieMotu).not.toHaveBeenCalled();
    expect(entrada('resumo').disabled).toBe(true);
  });

  describe('com Conjuntos', () => {
    function conjunto(id: string, titulo: string, zip: string): RegistoMotu {
      return {
        id,
        tipo: 'CDG',
        opcional: false,
        completo: false,
        titulo,
        zip: { id: `doc-${id}`, nome: zip },
        dados: { cartografiaTipo: null } as RegistoMotu['dados'],
        plantas: [{ id: `p-${id}`, nomeRegulamento: 'Nome', numeroFolha: '1', nomeTiff: 'a.tif' }],
      };
    }

    function escrever(seletor: string, valor: string): void {
      const campo = fixture.nativeElement.querySelector(seletor) as HTMLTextAreaElement;
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
    }

    beforeEach(() => {
      const comConjuntos = ficha(registo());
      comConjuntos.registos.push(
        conjunto('c1', 'Planta de ordenamento', 'ord.zip'),
        conjunto('c2', 'Planta de condicionantes', 'con.zip'),
      );
      plantasServiceMock.obterMetadadosMotu.mockReturnValue(of(comConjuntos));
      plantasServiceMock.gravarSerieMotu.mockReturnValue(of(registo({}, true)));
      fixture = TestBed.createComponent(ProcessoMetadadosMotuComponent);
      fixture.componentInstance.processoId = 'processo-1';
      fixture.detectChanges();
    });

    it('mostra a Série e um bloco por Conjunto com o título, o ZIP e a tabela das folhas', () => {
      const blocos = fixture.nativeElement.querySelectorAll('app-conjunto-motu');
      expect(blocos.length).toBe(2);
      expect(blocos[0].querySelector('.titulo-conjunto').textContent.trim()).toBe('Planta de ordenamento');
      expect(blocos[1].querySelector('.zip-conjunto').textContent.trim()).toBe('con.zip');
      expect(blocos[1].querySelector('.tabela')).not.toBeNull();
    });

    it('«Seguinte» grava a Série e só os Conjuntos alterados', () => {
      plantasServiceMock.gravarConjuntoMotu.mockReturnValue(
        of({ ...conjunto('c1', 'Planta de ordenamento', 'ord.zip'), completo: true }),
      );
      escrever('#cdg-c1-resumo', 'Resumo novo');
      const resultado = vi.fn();

      fixture.componentInstance.gravar().subscribe(resultado);
      fixture.detectChanges();

      expect(plantasServiceMock.gravarSerieMotu).toHaveBeenCalledTimes(1);
      expect(plantasServiceMock.gravarConjuntoMotu).toHaveBeenCalledTimes(1);
      expect(plantasServiceMock.gravarConjuntoMotu.mock.calls[0][1]).toBe('c1');
      expect(resultado).toHaveBeenCalledWith(true);
      const estados = fixture.nativeElement.querySelectorAll('.estado-conjunto');
      expect(estados[0].textContent.trim()).toBe('Completo');
      expect(estados[1].textContent.trim()).toBe('Incompleto');
    });

    it('um 400 num Conjunto não deixa avançar e mostra o erro no campo', () => {
      plantasServiceMock.gravarConjuntoMotu.mockReturnValue(
        throwError(
          () =>
            new ApiError('MOTU_CAMPOS_INVALIDOS', 'Campos da ficha MOTU inválidos.', 't', [
              { path: 'resumo', erro: 'tamanho máximo 4000' },
            ]),
        ),
      );
      escrever('#cdg-c2-resumo', 'x');
      const resultado = vi.fn();

      fixture.componentInstance.gravar().subscribe(resultado);
      fixture.detectChanges();

      expect(resultado).toHaveBeenCalledWith(false);
      expect(fixture.nativeElement.querySelector('#cdg-c2-erro-resumo').textContent).toContain('tamanho máximo 4000');
    });
  });
});

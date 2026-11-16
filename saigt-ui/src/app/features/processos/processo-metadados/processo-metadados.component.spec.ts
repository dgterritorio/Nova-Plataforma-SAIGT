import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { Metadados, MetadadosProcesso } from '../../../models/plantas.model';
import { PlantasService } from '../../../services/plantas.service';
import { ProcessoMetadadosComponent } from './processo-metadados.component';

describe('ProcessoMetadadosComponent', () => {
  let fixture: ComponentFixture<ProcessoMetadadosComponent>;
  let plantasServiceMock: { obterMetadados: ReturnType<typeof vi.fn>; gravarMetadados: ReturnType<typeof vi.fn> };

  const vazio: MetadadosProcesso = {
    palavrasChave: null,
    resolucaoEspacialEscala: null,
    tituloAlternativo: null,
    resumo: null,
    sistemaReferencia: null,
    codigoSistema: null,
    cartografiaTipo: null,
    entidadeProprietaria: null,
    dataEdicao: null,
    entidadeProdutora: null,
    dataAtualizacao: null,
    atualizacaoComplemento: null,
    sistemaReferenciaNome: null,
    sistemaReferenciaCodigo: null,
    exatidaoPosicional: null,
    exatidaoTematica: null,
  };

  function metadados(dados: Partial<MetadadosProcesso> = {}): Metadados {
    return {
      exigeMetadados: true,
      titulo: 'PDM de Sintra',
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
      dados: { ...vazio, ...dados },
    };
  }

  function entrada(campo: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(`#metadados-${campo}`);
  }

  beforeEach(() => {
    plantasServiceMock = {
      obterMetadados: vi.fn().mockReturnValue(of(metadados({ palavrasChave: 'ordenamento' }))),
      gravarMetadados: vi.fn().mockReturnValue(of(metadados({ palavrasChave: 'ordenamento' }))),
    };
    TestBed.configureTestingModule({
      imports: [ProcessoMetadadosComponent],
      providers: [{ provide: PlantasService, useValue: plantasServiceMock }],
    });
    fixture = TestBed.createComponent(ProcessoMetadadosComponent);
    fixture.componentInstance.processoId = 'processo-1';
    fixture.detectChanges();
  });

  it('mostra os cartões do Figma com o responsável e o título só de leitura e os valores gravados', () => {
    const titulos = [...fixture.nativeElement.querySelectorAll('.titulo')].map((e: Element) => e.textContent?.trim());
    expect(titulos).toEqual(['Responsável', 'Identificação', 'Sistema de Referência', 'Cartografia de Referência']);
    expect(fixture.nativeElement.textContent).toContain('Município de Sintra');
    expect(fixture.nativeElement.textContent).toContain('PDM de Sintra');
    expect(entrada('palavrasChave').value).toBe('ordenamento');
    expect(entrada('resumo').getAttribute('maxlength')).toBe('4000');
  });

  it('cada campo tem o rótulo ligado e os obrigatórios ficam marcados', () => {
    const rotulo = fixture.nativeElement.querySelector('label[for="metadados-exatidaoTematica"]') as HTMLLabelElement;
    expect(rotulo.textContent).toContain('Exatidão Temática');
    expect(entrada('exatidaoTematica').getAttribute('aria-required')).toBe('true');
    expect(entrada('codigoSistema').getAttribute('aria-required')).toBeNull();
  });

  it('grava os campos vazios como nulos', () => {
    const input = entrada('resumo') as unknown as HTMLTextAreaElement;
    input.value = '  Plano diretor  ';
    input.dispatchEvent(new Event('input'));

    let gravou: boolean | undefined;
    fixture.componentInstance.gravar().subscribe((resultado) => (gravou = resultado));

    expect(gravou).toBe(true);
    expect(plantasServiceMock.gravarMetadados).toHaveBeenCalledWith(
      'processo-1',
      expect.objectContaining({ palavrasChave: 'ordenamento', resumo: 'Plano diretor', exatidaoTematica: null }),
    );
  });

  it('os campos em falta na submissão aparecem junto de cada um', () => {
    fixture.componentInstance.errosSubmissao = [
      { path: 'metadados.exatidaoTematica', erro: 'Preencha este campo da ficha de metadados.' },
      { path: 'plantas.cartograma', erro: 'Carregue o cartograma.' },
    ];
    fixture.detectChanges();

    expect(entrada('exatidaoTematica').getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.querySelector('#erro-exatidaoTematica').textContent).toContain('Preencha este campo');
    expect(fixture.nativeElement.textContent).not.toContain('Carregue o cartograma.');
  });

  it('um erro que não é de um campo aparece no topo e não deixa avançar', () => {
    plantasServiceMock.gravarMetadados.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Este processo não tem ficha de metadados.', 't1', [{ path: 'metadados', erro: 'Só sem GeoConform.' }])),
    );

    let gravou: boolean | undefined;
    fixture.componentInstance.gravar().subscribe((resultado) => (gravou = resultado));
    fixture.detectChanges();

    expect(gravou).toBe(false);
    expect(fixture.nativeElement.querySelector('.erro-geral').textContent).toContain('Só sem GeoConform.');
  });
});

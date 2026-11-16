import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { DinamicaConfig, TipoDinamicaConfig, TipoInstrumentoConfig } from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfiguracaoDinamicasComponent } from './configuracao-dinamicas.component';
import { AJUDA_TIPO_EM_USO } from './dinamica-config-modal.component';

const PROT: TipoInstrumentoConfig = { id: 2, codigo: 'PROT', nome: 'Programa Regional', categoria: 'PROGRAMA', ativo: false };
const PDM: TipoInstrumentoConfig = { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', ativo: true };
const TIPOS_DINAMICA: TipoDinamicaConfig[] = [
  { id: 11, codigo: 'ELABORACAO', nome: 'Elaboração', ativo: true },
  { id: 12, codigo: 'REVISAO', nome: 'Revisão', ativo: true },
];
const ALTERACAO_PDM: DinamicaConfig = {
  id: 100,
  tipoInstrumento: { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal' },
  tipoDinamica: { id: 13, codigo: 'ALTERACAO', nome: 'Alteração' },
  associacaoInstrumentos: 'UM_OU_MAIS',
  ativo: true,
  emUso: true,
};
const SUSPENSAO_PDM: DinamicaConfig = {
  ...ALTERACAO_PDM,
  id: 101,
  tipoDinamica: { id: 14, codigo: 'SUSPENSAO', nome: 'Suspensão' },
  associacaoInstrumentos: 'OPCIONAL',
  ativo: false,
  emUso: false,
};

function pagina<T>(linhas: T[]): PageResponse<T> {
  return { content: linhas, page: 0, size: 20, totalElements: linhas.length, totalPages: 1 };
}

describe('ConfiguracaoDinamicasComponent', () => {
  let fixture: ComponentFixture<ConfiguracaoDinamicasComponent>;
  let servico: Record<string, ReturnType<typeof vi.fn>>;

  function configurar(): void {
    servico = {
      // Fora de ordem de propósito: o seletor ordena por código e escolhe o primeiro.
      listarTiposInstrumento: vi.fn().mockReturnValue(of(pagina([PROT, PDM]))),
      listarTiposDinamica: vi.fn().mockReturnValue(of(pagina(TIPOS_DINAMICA))),
      listarDinamicasConfig: vi.fn().mockReturnValue(of(pagina([ALTERACAO_PDM, SUSPENSAO_PDM]))),
      criarDinamicaConfig: vi.fn(),
      atualizarDinamicaConfig: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [ConfiguracaoDinamicasComponent],
      providers: [{ provide: ConfiguracaoService, useValue: servico }],
    });
    fixture = TestBed.createComponent(ConfiguracaoDinamicasComponent);
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const dialogo = (): HTMLElement | null => document.querySelector('[role="dialog"]');
  const botao = (texto: string, raiz: ParentNode = el()): HTMLButtonElement =>
    [...raiz.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === texto)!;
  const seletor = (rotulo: string, raiz: ParentNode): HTMLSelectElement => {
    const label = [...raiz.querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return document.getElementById(label.htmlFor) as HTMLSelectElement;
  };

  function clicar(alvo: HTMLElement): void {
    alvo.click();
    fixture.detectChanges();
  }

  function escolher(entrada: HTMLSelectElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('abre no primeiro tipo de Instrumento por código (PDM) e lista as Dinâmicas dele pela ordem da API, com a associação por extenso', () => {
    configurar();

    expect(servico['listarTiposInstrumento']).toHaveBeenCalledWith({}, 0, 100);
    expect(servico['listarTiposDinamica']).toHaveBeenCalledWith({ ativo: true }, 0, 100);
    expect(servico['listarDinamicasConfig']).toHaveBeenCalledWith({ tipoInstrumentoId: 1, ativo: undefined }, 0, 20);
    const tipo = seletor('Tipo de Instrumento', el());
    expect(tipo.value).toBe('1');
    expect([...tipo.options].map((o) => o.textContent?.trim()).slice(1)).toEqual([
      'PDM — Plano Diretor Municipal',
      'PROT — Programa Regional (inativo)',
    ]);
    expect([...el().querySelectorAll('th[scope="col"]')].map((th) => th.textContent?.trim())).toEqual([
      'Dinâmica',
      'Código',
      'Associação de Instrumentos',
      'Estado',
      'Ações',
    ]);
    const linhas = el().querySelectorAll('tbody tr');
    expect(linhas[0].querySelector('.coluna-dinamica')?.textContent?.trim()).toBe('Alteração');
    expect(linhas[0].querySelector('.coluna-associacao')?.textContent?.trim()).toBe('Um ou mais Instrumentos');
    expect(linhas[1].querySelector('.coluna-associacao')?.textContent?.trim()).toBe('Opcional');
    expect(linhas[1].querySelector('.coluna-estado')?.textContent?.trim()).toBe('Inativa');
    expect(el().querySelector('.contador')?.textContent).toContain('2 Dinâmicas');
    expect(el().querySelector('caption')?.textContent).toContain('Dinâmicas de PDM');
  });

  it('mudar o tipo de Instrumento ou o filtro lista na página 0', () => {
    configurar();
    fixture.componentInstance.irParaPagina(1);
    expect(servico['listarDinamicasConfig']).toHaveBeenLastCalledWith({ tipoInstrumentoId: 1, ativo: undefined }, 1, 20);

    escolher(seletor('Tipo de Instrumento', el()), '2');
    expect(servico['listarDinamicasConfig']).toHaveBeenLastCalledWith({ tipoInstrumentoId: 2, ativo: undefined }, 0, 20);

    clicar(botao('Inativas'));
    expect(servico['listarDinamicasConfig']).toHaveBeenLastCalledWith({ tipoInstrumentoId: 2, ativo: false }, 0, 20);
  });

  it('Adicionar Dinâmica no PROT com Revisão e "Um Instrumento": POST com o tipoInstrumentoId do PROT e a linha aparece', () => {
    configurar();
    escolher(seletor('Tipo de Instrumento', el()), '2');
    const revisao: DinamicaConfig = {
      id: 200,
      tipoInstrumento: { id: 2, codigo: 'PROT', nome: 'Programa Regional' },
      tipoDinamica: { id: 12, codigo: 'REVISAO', nome: 'Revisão' },
      associacaoInstrumentos: 'UM',
      ativo: true,
      emUso: false,
    };
    servico['criarDinamicaConfig'].mockReturnValue(of(revisao));
    servico['listarDinamicasConfig'].mockReturnValue(of(pagina([revisao])));

    clicar(botao('Adicionar Dinâmica'));
    expect(dialogo()?.textContent).toContain('PROT — Programa Regional');
    escolher(seletor('Tipo de Dinâmica', dialogo()!), '12');
    escolher(seletor('Associação de Instrumentos', dialogo()!), 'UM');
    clicar(botao('Guardar', dialogo()!));

    expect(servico['criarDinamicaConfig']).toHaveBeenCalledWith({
      tipoInstrumentoId: 2,
      tipoDinamicaId: 12,
      associacaoInstrumentos: 'UM',
      ativo: true,
    });
    expect(dialogo()).toBeNull();
    expect(el().querySelector('app-alerta[role="status"]')?.textContent).toContain('Dinâmica criada.');
    expect(el().querySelector('tbody')?.textContent).toContain('Revisão');
  });

  it('editar uma Dinâmica com emUso: o Tipo de Dinâmica fica desativado com a ajuda; guarda a associação por PUT', () => {
    configurar();
    servico['atualizarDinamicaConfig'].mockReturnValue(of({ ...ALTERACAO_PDM, associacaoInstrumentos: 'UM' }));

    clicar(el().querySelectorAll<HTMLButtonElement>('.btn-acoes')[0]);
    clicar(botao('Editar'));
    const tipo = seletor('Tipo de Dinâmica', dialogo()!);
    expect(tipo.disabled).toBe(true);
    expect(dialogo()?.textContent).toContain(AJUDA_TIPO_EM_USO);

    escolher(seletor('Associação de Instrumentos', dialogo()!), 'UM');
    clicar(botao('Guardar', dialogo()!));
    expect(servico['atualizarDinamicaConfig']).toHaveBeenCalledWith(100, {
      tipoInstrumentoId: 1,
      tipoDinamicaId: 13,
      associacaoInstrumentos: 'UM',
      ativo: true,
    });
    expect(el().querySelector('app-alerta[role="status"]')?.textContent).toContain('Dinâmica atualizada.');
  });

  it('400 no tipoDinamicaId fica no campo e o modal continua aberto; 409 vai para o alerta do modal', () => {
    configurar();
    servico['criarDinamicaConfig'].mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Pedido inválido.', 't', [{ path: 'tipoDinamicaId', erro: 'Já configurada neste tipo.' }])),
    );
    clicar(botao('Adicionar Dinâmica'));
    escolher(seletor('Tipo de Dinâmica', dialogo()!), '11');
    clicar(botao('Guardar', dialogo()!));
    expect(dialogo()?.textContent).toContain('Já configurada neste tipo.');

    servico['criarDinamicaConfig'].mockReturnValue(throwError(() => new ApiError('EM_USO', 'Configuração em uso.', 't', null)));
    clicar(botao('Guardar', dialogo()!));
    expect(dialogo()?.querySelector('app-alerta[role="alert"]')?.textContent).toContain('Configuração em uso.');
  });

  it('Desativar pede a confirmação com a Dinâmica e o tipo, e faz PUT com ativo=false; Reativar o inverso', () => {
    configurar();
    servico['atualizarDinamicaConfig'].mockReturnValue(of(ALTERACAO_PDM));

    clicar(el().querySelectorAll<HTMLButtonElement>('.btn-acoes')[0]);
    clicar(botao('Desativar'));
    expect(dialogo()?.textContent).toContain(
      'Desativar a Dinâmica Alteração em PDM? Deixa de ser oferecida; as Dinâmicas em curso não são afetadas.',
    );
    clicar(botao('Sim', dialogo()!));
    expect(servico['atualizarDinamicaConfig']).toHaveBeenLastCalledWith(100, {
      tipoInstrumentoId: 1,
      tipoDinamicaId: 13,
      associacaoInstrumentos: 'UM_OU_MAIS',
      ativo: false,
    });

    clicar(el().querySelectorAll<HTMLButtonElement>('.btn-acoes')[1]);
    clicar(botao('Reativar'));
    clicar(botao('Sim', dialogo()!));
    expect(servico['atualizarDinamicaConfig']).toHaveBeenLastCalledWith(101, {
      tipoInstrumentoId: 1,
      tipoDinamicaId: 14,
      associacaoInstrumentos: 'OPCIONAL',
      ativo: true,
    });
  });

  it('falha de rede ao listar: app-error-banner e o seletor continua a funcionar', () => {
    configurar();
    servico['listarDinamicasConfig'].mockReturnValue(throwError(() => new ApiError('ERRO_REDE', 'Sem ligação.', 't', null)));
    clicar(botao('Ativas'));
    expect(el().querySelector('app-error-banner .error-banner')).not.toBeNull();
    expect(el().querySelector('app-tabela')).toBeNull();

    servico['listarDinamicasConfig'].mockReturnValue(of(pagina([ALTERACAO_PDM])));
    escolher(seletor('Tipo de Instrumento', el()), '2');
    expect(el().querySelector('app-tabela')).not.toBeNull();
  });
});

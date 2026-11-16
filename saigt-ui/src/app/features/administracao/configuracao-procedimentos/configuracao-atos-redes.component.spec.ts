import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { RedeDefesaConfig, TipoAtoConfig } from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfiguracaoAtosRedesComponent } from './configuracao-atos-redes.component';

const DELIB: TipoAtoConfig = { id: 1, codigo: 'DELIB', nome: 'Deliberação', incmActTypeIdSerie1: 7, incmActTypeIdSerie2: 8, ordem: 1, ativo: true };
const AVISO: TipoAtoConfig = { id: 2, codigo: 'AVISO', nome: 'Aviso', incmActTypeIdSerie1: null, incmActTypeIdSerie2: 10, ordem: 2, ativo: false };

const REDES: RedeDefesaConfig[] = [
  ['MPGC', 'Mosaico de parcelas de gestão de combustível', true, true, true],
  ['PA', 'Pontos de água', true, true, true],
  ['RDFCI', 'Rede de defesa da floresta contra incêndios', true, true, false],
  ['RPA', 'Rede de pontos de água', true, true, true],
  ['RPFGC', 'Rede primária de faixas de gestão de combustível', true, true, true],
  ['RSFGC', 'Rede secundária de faixas de gestão de combustível', true, false, true],
  ['RVF', 'Rede viária florestal', true, true, true],
  ['RVM', 'Rede de vigilância e deteção', false, true, true],
].map(([codigo, nome, aplicaPra, aplicaPsa, publicavel], i) => ({
  id: i + 1,
  codigo: codigo as string,
  nome: nome as string,
  aplicaPra: aplicaPra as boolean,
  aplicaPsa: aplicaPsa as boolean,
  publicavel: publicavel as boolean,
}));

function pagina<T>(linhas: T[]): PageResponse<T> {
  return { content: linhas, page: 0, size: 20, totalElements: linhas.length, totalPages: 1 };
}

describe('ConfiguracaoAtosRedesComponent', () => {
  let fixture: ComponentFixture<ConfiguracaoAtosRedesComponent>;
  let servico: Record<string, ReturnType<typeof vi.fn>>;

  function configurar(): void {
    servico = {
      listarTiposAto: vi.fn().mockReturnValue(of(pagina([DELIB, AVISO]))),
      criarTipoAto: vi.fn(),
      atualizarTipoAto: vi.fn(),
      listarRedesDefesa: vi.fn().mockReturnValue(of(pagina(REDES))),
      criarRedeDefesa: vi.fn(),
      atualizarRedeDefesa: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [ConfiguracaoAtosRedesComponent],
      providers: [{ provide: ConfiguracaoService, useValue: servico }],
    });
    fixture = TestBed.createComponent(ConfiguracaoAtosRedesComponent);
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const cartaoAtos = (): HTMLElement => el().querySelector('.cartao-atos')!;
  const cartaoRedes = (): HTMLElement => el().querySelector('.cartao-redes')!;
  const dialogo = (): HTMLElement | null => document.querySelector('[role="dialog"]');
  const botao = (texto: string, raiz: ParentNode = el()): HTMLButtonElement =>
    [...raiz.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === texto)!;
  const campo = (rotulo: string): HTMLInputElement => {
    const label = [...dialogo()!.querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return (label.htmlFor ? document.getElementById(label.htmlFor) : label.querySelector('input')) as HTMLInputElement;
  };
  const colunas = (raiz: HTMLElement): string[] => [...raiz.querySelectorAll('th[scope="col"]')].map((th) => th.textContent?.trim() ?? '');

  function clicar(alvo: HTMLElement): void {
    alvo.click();
    fixture.detectChanges();
  }

  function escrever(rotulo: string, valor: string): void {
    const entrada = campo(rotulo);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('dois cartões com h2: tipos de ato por ordem com as séries ("—" quando vazias) e redes por código', () => {
    configurar();

    expect([...el().querySelectorAll('h2')].map((h) => h.textContent?.trim())).toEqual(['Tipos de ato', 'Redes de defesa do SGIFR']);
    expect(servico['listarTiposAto']).toHaveBeenCalledWith({ ativo: undefined }, 0, 20);
    expect(servico['listarRedesDefesa']).toHaveBeenCalledWith(0, 20);
    expect(colunas(cartaoAtos())).toEqual(['Ordem', 'Código', 'Nome', 'ActTypeId 1.ª série', 'ActTypeId 2.ª série', 'Estado', 'Ações']);
    const linhaAviso = cartaoAtos().querySelectorAll('tbody tr')[1];
    expect(linhaAviso.querySelector('.coluna-serie1')?.textContent?.trim()).toBe('—');
    expect(linhaAviso.querySelector('.coluna-serie2')?.textContent?.trim()).toBe('10');
    expect(linhaAviso.querySelector('.coluna-estado')?.textContent?.trim()).toBe('Inativo');
  });

  it('a tabela das redes mostra as 8 redes com PRA, PSA e Publicável e não tem ação Desativar', () => {
    configurar();

    expect(colunas(cartaoRedes())).toEqual(['Código', 'Nome', 'PRA', 'PSA', 'Publicável', 'Ações']);
    const linhas = cartaoRedes().querySelectorAll('tbody tr');
    expect(linhas).toHaveLength(8);
    expect([...linhas].map((l) => l.querySelector('.coluna-codigo')?.textContent?.trim())).toEqual(REDES.map((r) => r.codigo));
    const rsfgc = linhas[5];
    expect(rsfgc.querySelector('.coluna-pra')?.textContent?.trim()).toBe('Sim');
    expect(rsfgc.querySelector('.coluna-psa')?.textContent?.trim()).toBe('Não');
    expect(linhas[2].querySelector('.coluna-publicavel')?.textContent?.trim()).toBe('Não');
    expect(cartaoRedes().textContent).not.toContain('Desativar');
    expect(cartaoRedes().textContent).not.toContain('Reativar');
    expect(cartaoRedes().querySelectorAll('.btn-acoes')).toHaveLength(0);
    expect(linhas[6].querySelector('button')?.getAttribute('aria-label')).toBe('Editar RVF');
  });

  it('editar a RVF com "Aplica-se ao PSA" desmarcado: PUT com aplicaPsa=false e a tabela mostra "Não"', () => {
    configurar();
    const rvf = REDES[6];
    servico['atualizarRedeDefesa'].mockReturnValue(of({ ...rvf, aplicaPsa: false }));
    servico['listarRedesDefesa'].mockReturnValue(of(pagina(REDES.map((r) => (r.id === rvf.id ? { ...r, aplicaPsa: false } : r)))));

    clicar(cartaoRedes().querySelectorAll('tbody tr')[6].querySelector('button')!);
    clicar(campo('Aplica-se ao PSA'));
    clicar(botao('Guardar', dialogo()!));

    expect(servico['atualizarRedeDefesa']).toHaveBeenCalledWith(rvf.id, {
      codigo: 'RVF',
      nome: 'Rede viária florestal',
      aplicaPra: true,
      aplicaPsa: false,
      publicavel: true,
    });
    expect(dialogo()).toBeNull();
    expect(cartaoRedes().querySelectorAll('tbody tr')[6].querySelector('.coluna-psa')?.textContent?.trim()).toBe('Não');
    expect(el().querySelector('app-alerta[role="status"]')?.textContent).toContain('Rede atualizada.');
  });

  it('Adicionar rede faz POST', () => {
    configurar();
    servico['criarRedeDefesa'].mockReturnValue(of({ ...REDES[0], id: 99, codigo: 'RNOVA' }));
    clicar(botao('Adicionar rede'));
    escrever('Código', 'RNOVA');
    escrever('Nome', 'Rede nova');
    clicar(botao('Guardar', dialogo()!));
    expect(servico['criarRedeDefesa']).toHaveBeenCalledWith({ codigo: 'RNOVA', nome: 'Rede nova', aplicaPra: false, aplicaPsa: false, publicavel: false });
  });

  it('tipo de ato sem nenhuma série: "Indique pelo menos uma série." e nenhum pedido', () => {
    configurar();
    clicar(botao('Adicionar tipo de ato'));
    escrever('Código', 'DELIB2');
    escrever('Nome', 'Deliberação 2');
    escrever('Ordem', '3');
    clicar(botao('Guardar', dialogo()!));

    expect(dialogo()?.textContent).toContain('Indique pelo menos uma série.');
    expect(servico['criarTipoAto']).not.toHaveBeenCalled();
    expect(servico['atualizarTipoAto']).not.toHaveBeenCalled();
  });

  it('tipo de ato com a 2.ª série: POST, fecha e recarrega', () => {
    configurar();
    servico['criarTipoAto'].mockReturnValue(of({ ...DELIB, id: 3 }));
    clicar(botao('Adicionar tipo de ato'));
    escrever('Código', 'DELIB2');
    escrever('Nome', 'Deliberação 2');
    escrever('ActTypeId da INCM — 2.ª série', '45');
    escrever('Ordem', '3');
    clicar(botao('Guardar', dialogo()!));

    expect(servico['criarTipoAto']).toHaveBeenCalledWith({
      codigo: 'DELIB2',
      nome: 'Deliberação 2',
      incmActTypeIdSerie1: null,
      incmActTypeIdSerie2: 45,
      ordem: 3,
      ativo: true,
    });
    expect(dialogo()).toBeNull();
    expect(servico['listarTiposAto']).toHaveBeenCalledTimes(2);
  });

  it('Desativar um tipo de ato pede confirmação e faz PUT com ativo=false; filtro Inativos pede ativo=false', () => {
    configurar();
    servico['atualizarTipoAto'].mockReturnValue(of(DELIB));

    clicar(cartaoAtos().querySelectorAll<HTMLButtonElement>('.btn-acoes')[0]);
    clicar(botao('Desativar'));
    expect(dialogo()?.textContent).toContain('Desativar o tipo de ato Deliberação?');
    clicar(botao('Sim', dialogo()!));
    expect(servico['atualizarTipoAto']).toHaveBeenCalledWith(1, {
      codigo: 'DELIB',
      nome: 'Deliberação',
      incmActTypeIdSerie1: 7,
      incmActTypeIdSerie2: 8,
      ordem: 1,
      ativo: false,
    });

    clicar(botao('Inativos', cartaoAtos()));
    expect(servico['listarTiposAto']).toHaveBeenLastCalledWith({ ativo: false }, 0, 20);
  });

  it('falha ao listar as redes não impede os tipos de ato', () => {
    configurar();
    servico['listarRedesDefesa'].mockReturnValue(throwError(() => new ApiError('ERRO_REDE', 'Sem ligação.', 't', null)));
    fixture.componentInstance.irParaPaginaRedes(0);
    fixture.detectChanges();
    expect(cartaoRedes().querySelector('app-error-banner .error-banner')).not.toBeNull();
    expect(cartaoAtos().querySelector('app-tabela')).not.toBeNull();
  });
});

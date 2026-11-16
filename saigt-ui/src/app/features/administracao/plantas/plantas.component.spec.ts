import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PlantasComponent } from './plantas.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { Planta } from '../../../models/planta.model';
import { PlantaService } from '../../../services/planta.service';

describe('PlantasComponent', () => {
  let fixture: ComponentFixture<PlantasComponent>;
  let plantaServiceMock: { listar: ReturnType<typeof vi.fn>; atualizar: ReturnType<typeof vi.fn> };

  const planta: Planta = {
    id: 'p-1',
    processoId: 'proc-1',
    numeroProcesso: '2025/000123',
    numeroDeposito: '01.03.07/PDM/03/2025/1',
    idus: '01.0307.01.004321.03.00005.01.002.01.2025',
    processoDocumentoId: 'doc-1',
    nomeRegulamento: 'Planta de Ordenamento',
    numeroFolha: '01',
    nomeTiff: '0307PO_A',
    nomeZip: '0307Plantas.zip',
    linkJpeg: 'https://saigt.invalid/api/v1/publico/token/0307PO_A.jpg',
    emVigor: false,
    atualizadoEm: '2026-11-10T10:00:00Z',
    atualizadoPorNome: null,
  };
  const semIdus: Planta = { ...planta, id: 'p-2', numeroProcesso: '2025/000100', numeroDeposito: null, idus: null, nomeTiff: '0307PO_B' };

  function pagina(plantas: Planta[], total = plantas.length): PageResponse<Planta> {
    return { content: plantas, page: 0, size: 20, totalElements: total, totalPages: Math.ceil(total / 20) };
  }

  function configurar(plantas: Planta[] = [planta, semIdus], total?: number): void {
    plantaServiceMock = {
      listar: vi.fn().mockReturnValue(of(pagina(plantas, total))),
      atualizar: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [PlantasComponent],
      providers: [
        provideRouter([]),
        { provide: PlantaService, useValue: plantaServiceMock },
        {
          provide: AuthService,
          useValue: {
            perfilInterno: vi.fn().mockReturnValue('TECNICO'),
            nome: vi.fn().mockReturnValue('Técnica Fictícia'),
            origem: vi.fn().mockReturnValue('AD_DGT'),
            entidade: vi.fn().mockReturnValue(null),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(PlantasComponent);
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const dialogo = (): HTMLElement | null => document.querySelector('[role="dialog"]');
  const campo = (rotulo: string): HTMLInputElement => {
    const label = [...document.querySelectorAll<HTMLLabelElement>('[role="dialog"] label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return document.getElementById(label.htmlFor) as HTMLInputElement;
  };
  const botaoEditar = (indice = 0): HTMLButtonElement => el().querySelectorAll<HTMLButtonElement>('.botao-editar')[indice];
  const botaoGuardar = (): HTMLButtonElement =>
    [...document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')].find((b) => b.textContent?.trim() === 'Guardar')!;

  function abrirEdicao(): HTMLButtonElement {
    const botao = botaoEditar();
    botao.focus();
    botao.click();
    fixture.detectChanges();
    return botao;
  }

  function escrever(entrada: HTMLInputElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  afterEach(() => {
    vi.useRealTimers();
  });

  it('mostra a tabela com cabeçalhos de coluna, legenda e os dados de cada planta; IDUS e depósito em falta aparecem como —', () => {
    configurar();

    expect(plantaServiceMock.listar).toHaveBeenCalledWith({ numeroProcesso: undefined, numeroDeposito: undefined, emVigor: undefined }, 0, 20);
    const cabecalhos = [...el().querySelectorAll('th[scope="col"]')].map((th) => th.textContent?.trim());
    expect(cabecalhos).toEqual([
      'N.º do processo',
      'N.º de depósito',
      'IDUS',
      'Nome conforme o regulamento',
      'N.º de folha',
      'TIFF',
      'ZIP',
      'Em vigor',
      'Última atualização',
      'Ações',
    ]);
    expect(el().querySelector('caption')?.textContent).toContain('Plantas SAIGT/SNIT');
    const linhas = el().querySelectorAll('tbody tr');
    expect(linhas).toHaveLength(2);
    expect(linhas[0].textContent).toContain('01.0307.01.004321');
    expect(linhas[0].textContent).toContain('0307PO_A');
    expect(linhas[0].querySelector('.coluna-emVigor')?.textContent?.trim()).toBe('Não');
    expect(linhas[1].querySelector('.coluna-idus')?.textContent?.trim()).toBe('—');
    expect(linhas[1].querySelector('.coluna-numeroDeposito')?.textContent?.trim()).toBe('—');
    expect(el().querySelector('app-paginacao')).not.toBeNull();
  });

  it('sem resultados mostra a mensagem dos filtros', () => {
    configurar([]);
    expect(el().querySelector('.tabela-vazio')?.textContent).toContain('Sem plantas para os filtros indicados');
  });

  it('os filtros têm rótulo acessível', () => {
    configurar();
    const pesquisas = [...el().querySelectorAll<HTMLInputElement>('app-campo-pesquisa input')].map((i) => i.getAttribute('aria-label'));
    expect(pesquisas).toEqual(['N.º do processo', 'N.º de depósito']);
    const select = el().querySelector<HTMLSelectElement>('app-seletor select')!;
    expect(el().querySelector(`label[for="${select.id}"]`)?.textContent?.trim()).toBe('Em vigor');
  });

  it('pesquisar um n.º de processo recarrega na primeira página com o filtro', () => {
    vi.useFakeTimers();
    configurar([planta], 45);
    fixture.componentInstance.irParaPagina(2);
    plantaServiceMock.listar.mockClear();

    const entrada = el().querySelectorAll<HTMLInputElement>('app-campo-pesquisa input')[0];
    entrada.value = ' 2025/000123 ';
    entrada.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);

    expect(plantaServiceMock.listar).toHaveBeenCalledTimes(1);
    expect(plantaServiceMock.listar).toHaveBeenCalledWith({ numeroProcesso: '2025/000123', numeroDeposito: undefined, emVigor: undefined }, 0, 20);
    expect(fixture.componentInstance.pagina()).toBe(0);
  });

  it('pesquisar um n.º de depósito filtra por ele', () => {
    vi.useFakeTimers();
    configurar();
    plantaServiceMock.listar.mockClear();

    const entrada = el().querySelectorAll<HTMLInputElement>('app-campo-pesquisa input')[1];
    entrada.value = '01.03.07/PDM/03/2025/1';
    entrada.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);

    expect(plantaServiceMock.listar).toHaveBeenCalledWith(
      { numeroProcesso: undefined, numeroDeposito: '01.03.07/PDM/03/2025/1', emVigor: undefined },
      0,
      20,
    );
  });

  it('mudar «Em vigor» recarrega na primeira página com o filtro; «Todos» tira-o', () => {
    configurar([planta], 45);
    fixture.componentInstance.irParaPagina(1);
    plantaServiceMock.listar.mockClear();
    const select = el().querySelector<HTMLSelectElement>('app-seletor select')!;

    select.value = 'SIM';
    select.dispatchEvent(new Event('change'));
    expect(plantaServiceMock.listar).toHaveBeenLastCalledWith({ numeroProcesso: undefined, numeroDeposito: undefined, emVigor: true }, 0, 20);

    select.value = 'NAO';
    select.dispatchEvent(new Event('change'));
    expect(plantaServiceMock.listar).toHaveBeenLastCalledWith({ numeroProcesso: undefined, numeroDeposito: undefined, emVigor: false }, 0, 20);

    select.value = 'TODOS';
    select.dispatchEvent(new Event('change'));
    expect(plantaServiceMock.listar).toHaveBeenLastCalledWith({ numeroProcesso: undefined, numeroDeposito: undefined, emVigor: undefined }, 0, 20);
  });

  it('a paginação pede a página escolhida', () => {
    configurar([planta], 45);
    fixture.componentInstance.irParaPagina(2);
    expect(plantaServiceMock.listar).toHaveBeenLastCalledWith({ numeroProcesso: undefined, numeroDeposito: undefined, emVigor: undefined }, 2, 20);
  });

  it('«Editar» abre a modal com os valores da planta e sem campo para o link da imagem', () => {
    configurar();
    abrirEdicao();

    expect(dialogo()).not.toBeNull();
    expect(dialogo()!.getAttribute('aria-modal')).toBe('true');
    expect(campo('Nome conforme o regulamento').value).toBe('Planta de Ordenamento');
    expect(campo('N.º de folha').value).toBe('01');
    expect(campo('Nome do TIFF').value).toBe('0307PO_A');
    expect(campo('Nome do ZIP').value).toBe('0307Plantas.zip');
    expect(dialogo()!.querySelector<HTMLInputElement>('input[type="checkbox"]')!.checked).toBe(false);
    expect(dialogo()!.textContent).not.toContain('JPEG');
    expect(dialogo()!.querySelectorAll('input')).toHaveLength(5);
  });

  it('gravar alterações válidas fecha a modal, atualiza a linha, anuncia a gravação e devolve o foco ao «Editar»', async () => {
    configurar();
    plantaServiceMock.atualizar.mockReturnValue(
      of({ ...planta, numeroFolha: '12-A', emVigor: true, atualizadoPorNome: 'Técnica Fictícia' }),
    );
    const botao = abrirEdicao();

    escrever(campo('N.º de folha'), '12-A');
    const caixa = dialogo()!.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    caixa.click();
    fixture.detectChanges();
    botaoGuardar().click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(plantaServiceMock.atualizar).toHaveBeenCalledWith('p-1', {
      nomeRegulamento: 'Planta de Ordenamento',
      numeroFolha: '12-A',
      nomeTiff: '0307PO_A',
      nomeZip: '0307Plantas.zip',
      emVigor: true,
    });
    expect(dialogo()).toBeNull();
    const linha = el().querySelectorAll('tbody tr')[0];
    expect(linha.querySelector('.coluna-numeroFolha')?.textContent?.trim()).toBe('12-A');
    expect(linha.querySelector('.coluna-emVigor')?.textContent?.trim()).toBe('Sim');
    expect(linha.querySelector('.coluna-atualizacao')?.textContent).toContain('Técnica Fictícia');
    expect(el().querySelector('[role="status"]')?.textContent).toContain('gravada');
    expect(document.activeElement).toBe(botao);
  });

  it('um 400 com campos mostra o erro junto do campo e a modal fica aberta', () => {
    configurar();
    plantaServiceMock.atualizar.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't', [{ path: 'nomeRegulamento', erro: 'Máximo de 500 caracteres.' }])),
    );
    abrirEdicao();

    botaoGuardar().click();
    fixture.detectChanges();

    expect(dialogo()).not.toBeNull();
    const entrada = campo('Nome conforme o regulamento');
    expect(entrada.getAttribute('aria-invalid')).toBe('true');
    expect(document.getElementById(entrada.getAttribute('aria-describedby')!.split(' ').pop()!)?.textContent).toContain('Máximo de 500');
    expect(dialogo()!.querySelector('app-alerta')).toBeNull();
  });

  it('outro erro mostra um alerta na modal, que fica aberta', () => {
    configurar();
    plantaServiceMock.atualizar.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Erro inesperado.', 't', null)));
    abrirEdicao();

    botaoGuardar().click();
    fixture.detectChanges();

    expect(dialogo()).not.toBeNull();
    expect(dialogo()!.querySelector('app-alerta')?.textContent).toContain('Não foi possível gravar a planta.');
  });

  it('o foco fica preso na modal: Tab no último botão volta ao primeiro elemento', async () => {
    vi.useFakeTimers();
    configurar();
    abrirEdicao();
    vi.runAllTimers();

    const focaveis = [...dialogo()!.querySelectorAll<HTMLElement>('button, input')];
    focaveis[focaveis.length - 1].focus();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));

    expect(document.activeElement).toBe(focaveis[0]);
  });
});

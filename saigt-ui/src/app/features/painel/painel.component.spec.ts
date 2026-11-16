import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PainelComponent } from './painel.component';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/models/error-response.model';
import { GrupoPainel, GrupoProcesso, PainelProcessos, ProcessoListagem } from '../../models/processo.model';
import { ProcessoService } from '../../services/processo.service';

describe('PainelComponent', () => {
  let fixture: ComponentFixture<PainelComponent>;
  let processoServiceMock: { painel: ReturnType<typeof vi.fn> };
  let authServiceMock: {
    nome: ReturnType<typeof vi.fn>;
    origem: ReturnType<typeof vi.fn>;
    entidade: ReturnType<typeof vi.fn>;
    perfilInterno: ReturnType<typeof vi.fn>;
  };

  function processoExemplo(overrides: Partial<ProcessoListagem> = {}): ProcessoListagem {
    return {
      id: 'p1',
      numeroProcesso: '1042',
      instrumentoId: 'i1',
      designacaoInstrumento: 'PDM-2025-001',
      numeroInstrumento: '1',
      tipoInstrumento: 'PDM',
      tipoDinamica: 'ELABORACAO',
      nomeDinamica: 'Elaboração',
      tipoProcedimento: 'DISCUSSAO_PUBLICA',
      nomeProcedimento: 'Discussão pública',
      entidadeNif: '501234567',
      entidadeNome: 'Câmara Municipal de Lisboa',
      estado: 'REJEITADO_DGT',
      atualizadoEm: '2026-08-01T10:00:00Z',
      autorNome: 'Ana Ferreira',
      concelhos: [],
      ...overrides,
    };
  }

  function grupo(nome: GrupoProcesso, contagem: number, processos: ProcessoListagem[] = []): GrupoPainel {
    return { grupo: nome, contagem, processos };
  }

  /** Os quatro grupos do utilizador externo, pela ordem da API. */
  function painelEntidade(overrides: Partial<Record<GrupoProcesso, GrupoPainel>> = {}): PainelProcessos {
    return {
      grupos: [
        overrides.PUBLICADOS ?? grupo('PUBLICADOS', 22),
        overrides.EM_CURSO ?? grupo('EM_CURSO', 1),
        overrides.PARA_CORRECAO ?? grupo('PARA_CORRECAO', 3),
        overrides.EM_VALIDACAO ?? grupo('EM_VALIDACAO', 4),
      ],
    };
  }

  /** Os dois grupos da DGT, pela ordem da API. */
  function painelDgt(overrides: Partial<Record<GrupoProcesso, GrupoPainel>> = {}): PainelProcessos {
    return { grupos: [overrides.PUBLICADOS ?? grupo('PUBLICADOS', 9), overrides.EM_VALIDACAO ?? grupo('EM_VALIDACAO', 11)] };
  }

  beforeEach(() => {
    processoServiceMock = { painel: vi.fn().mockReturnValue(of(painelEntidade())) };
    authServiceMock = {
      nome: vi.fn().mockReturnValue('Ana Teixeira'),
      origem: vi.fn().mockReturnValue('INCM'),
      entidade: vi.fn().mockReturnValue(null),
      perfilInterno: vi.fn().mockReturnValue(null),
    };

    TestBed.configureTestingModule({
      imports: [PainelComponent],
      providers: [
        provideRouter([]),
        { provide: ProcessoService, useValue: processoServiceMock },
        { provide: AuthService, useValue: authServiceMock },
      ],
    });
  });

  function criarComponente(): void {
    fixture = TestBed.createComponent(PainelComponent);
    fixture.detectChanges();
  }

  function valoresDosCartoes(): (string | undefined)[] {
    return [...fixture.nativeElement.querySelectorAll('.cartao-valor')].map((el: Element) => el.textContent?.trim());
  }

  function hrefsDasAcoes(): (string | null)[] {
    return [...fixture.nativeElement.querySelectorAll('.indicador-acao')].map((link: HTMLAnchorElement) => link.getAttribute('href'));
  }

  describe('variante de entidade (utilizador INCM) — painel "Recusados"', () => {
    it('faz uma só chamada ao painel, com a primeira página de 5 de cada grupo', () => {
      criarComponente();

      expect(processoServiceMock.painel).toHaveBeenCalledTimes(1);
      expect(processoServiceMock.painel).toHaveBeenCalledWith(5);
    });

    it('mostra os 4 cartões com a contagem de cada grupo e os rótulos do Figma, "Publicados" sem ano', () => {
      processoServiceMock.painel.mockReturnValue(
        of(
          painelEntidade({
            PUBLICADOS: grupo('PUBLICADOS', 9),
            EM_CURSO: grupo('EM_CURSO', 2),
            PARA_CORRECAO: grupo('PARA_CORRECAO', 5),
            EM_VALIDACAO: grupo('EM_VALIDACAO', 7),
          }),
        ),
      );

      criarComponente();

      expect(valoresDosCartoes()).toEqual(['9', '2', '5', '7']);
      const rotulos = [...fixture.nativeElement.querySelectorAll('.indicador-rotulo')].map((el: Element) => el.textContent?.trim());
      expect(rotulos).toEqual(['Publicados', 'Em Formação', 'Para Correção', 'Em Validação SAIGT e INCM']);
      expect(fixture.nativeElement.textContent).not.toMatch(/Publicados \(\d{4}\)/);
    });

    it('preenche a lista "Recusados" com os processos do grupo PARA_CORRECAO e a sua contagem total', () => {
      const processos = [processoExemplo()];
      processoServiceMock.painel.mockReturnValue(of(painelEntidade({ PARA_CORRECAO: grupo('PARA_CORRECAO', 3, processos) })));

      criarComponente();

      expect(fixture.componentInstance.processosLista()).toEqual(processos);
      expect(fixture.componentInstance.totalLista()).toBe(3);
      expect(fixture.componentInstance.loading()).toBe(false);
      expect(fixture.nativeElement.querySelector('.lista-cabecalho h2')?.textContent).toContain('Recusados');
      expect(fixture.nativeElement.querySelector('.lista-contagem')?.textContent?.trim()).toBe('3 processos');
      expect(fixture.nativeElement.textContent).toContain('Câmara Municipal de Lisboa');
    });

    it('mostra o estado com o mesmo selo da lista de processos', () => {
      processoServiceMock.painel.mockReturnValue(
        of(painelEntidade({ PARA_CORRECAO: grupo('PARA_CORRECAO', 1, [processoExemplo()]) })),
      );
      criarComponente();

      expect(fixture.nativeElement.querySelector('tbody app-estado-processo-badge')?.textContent?.trim()).toBe('Rejeitado DGT');
    });

    it('com 1 processo, a contagem diz "1 processo"', () => {
      processoServiceMock.painel.mockReturnValue(
        of(painelEntidade({ PARA_CORRECAO: grupo('PARA_CORRECAO', 1, [processoExemplo()]) })),
      );
      criarComponente();

      expect(fixture.nativeElement.querySelector('.lista-contagem')?.textContent?.trim()).toBe('1 processo');
    });

    it('mostra a Referência "#{numeroProcesso}" ligada ao detalhe e as colunas Tipo, Fase, Nome, Entidade e Estado', () => {
      processoServiceMock.painel.mockReturnValue(
        of(painelEntidade({ PARA_CORRECAO: grupo('PARA_CORRECAO', 1, [processoExemplo({ id: 'p7', numeroProcesso: '2077' })]) })),
      );
      criarComponente();

      const titulos = [...fixture.nativeElement.querySelectorAll('app-tabela th')].map((el: Element) => el.textContent?.trim());
      expect(titulos).toEqual(['Referência', 'Tipo', 'Fase', 'Nome', 'Entidade', 'Estado']);
      const referencia: HTMLAnchorElement = fixture.nativeElement.querySelector('td.coluna-numeroProcesso a');
      expect(referencia.textContent?.trim()).toBe('#2077');
      expect(referencia.getAttribute('href')).toBe('/processos/p7');
      expect(fixture.nativeElement.querySelector('td.coluna-tipoInstrumento')?.textContent?.trim()).toBe('PDM');
      expect(fixture.nativeElement.querySelector('td.coluna-nomeProcedimento')?.textContent?.trim()).toBe('Discussão pública');
      expect(fixture.nativeElement.querySelector('td.coluna-designacaoInstrumento')?.textContent?.trim()).toBe('PDM-2025-001');
      expect(fixture.nativeElement.querySelector('td.coluna-entidadeNome')?.textContent?.trim()).toBe('Câmara Municipal de Lisboa');
    });

    it('mostra "Sem processos recusados." quando o grupo está vazio', () => {
      criarComponente();
      expect(fixture.nativeElement.querySelector('.tabela-vazio')?.textContent?.trim()).toBe('Sem processos recusados.');
    });

    it('mostra a faixa "Quer iniciar um novo processo?" e o botão "Criar Processo"', () => {
      criarComponente();

      expect(fixture.nativeElement.querySelector('.faixa-criar-texto')?.textContent?.trim()).toBe('Quer iniciar um novo processo?');
      expect(fixture.nativeElement.querySelector('.faixa-criar a[app-botao].botao-criar')?.textContent?.trim()).toBe('Criar Processo');
    });

    it('não mostra o painel de Notificações/Alertas/Notícias nem os seus dados de exemplo', () => {
      criarComponente();
      expect(fixture.nativeElement.querySelector('.notificacoes')).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('Dinâmicas perto de caducar');
    });

    it('usa o cartão, a tabela compacta e o selo partilhados', () => {
      processoServiceMock.painel.mockReturnValue(
        of(painelEntidade({ PARA_CORRECAO: grupo('PARA_CORRECAO', 1, [processoExemplo()]) })),
      );
      criarComponente();

      expect(fixture.nativeElement.querySelectorAll('.indicadores app-cartao.cartao-indicador')).toHaveLength(4);
      expect(fixture.nativeElement.querySelector('app-cartao.lista')?.classList).toContain('cartao-elevado');
      expect(fixture.nativeElement.querySelector('app-cartao.lista app-tabela')?.classList).toContain('tabela-compacta');
      expect(fixture.nativeElement.querySelector('tbody app-selo')).not.toBeNull();
    });

    it('as ações dos cartões da entidade são todas azul-escuro', () => {
      criarComponente();

      const links: HTMLAnchorElement[] = [...fixture.nativeElement.querySelectorAll('.indicador-acao')];
      expect(links.every((link) => link.classList.contains('indicador-acao-primaria'))).toBe(true);
      expect(links.map((link) => link.querySelector('img')?.getAttribute('src'))).toEqual(
        Array(4).fill('icones/painel/chevron-primaria.svg'),
      );
    });

    it('os ícones são decorativos: alt vazio', () => {
      criarComponente();

      const imagens: HTMLImageElement[] = [...fixture.nativeElement.querySelectorAll('.painel img')];
      expect(imagens.length).toBeGreaterThan(0);
      expect(imagens.every((img) => img.getAttribute('alt') === '')).toBe(true);
    });

    it('liga as ações aos ecrãs próprios de Continuar e Corrigir e as restantes a /processos com o grupo', () => {
      criarComponente();

      expect(hrefsDasAcoes()).toEqual([
        '/processos?grupo=PUBLICADOS',
        '/processos/continuar',
        '/processos/corrigir',
        '/processos?grupo=EM_VALIDACAO',
      ]);
    });

    it('o cartão "Para Correção" tem a ação "Corrigir Processo", que abre /processos/corrigir', () => {
      criarComponente();

      const acao: HTMLAnchorElement = [...fixture.nativeElement.querySelectorAll('.indicador-acao')][2];
      expect(acao.textContent?.trim()).toBe('Corrigir Processo');
      expect(acao.getAttribute('href')).toBe('/processos/corrigir');
    });

    it('o cartão "Em Formação" tem a ação "Continuar Processo", que abre /processos/continuar', () => {
      criarComponente();

      const acao: HTMLAnchorElement = [...fixture.nativeElement.querySelectorAll('.indicador-acao')][1];
      expect(acao.textContent?.trim()).toBe('Continuar Processo');
      expect(acao.getAttribute('href')).toBe('/processos/continuar');
    });

    it('o link "Ver todos" dos Recusados abre /processos/corrigir', () => {
      criarComponente();
      const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.lista-ver-todos');
      expect(link.getAttribute('href')).toBe('/processos/corrigir');
    });
  });

  describe.each(['MASTER', 'TECNICO', 'VISITANTE'])('variante da DGT (%s) — painel "Em validação"', (perfil) => {
    beforeEach(() => {
      authServiceMock.perfilInterno.mockReturnValue(perfil);
      processoServiceMock.painel.mockReturnValue(of(painelDgt()));
    });

    it('faz uma só chamada ao painel', () => {
      criarComponente();

      expect(processoServiceMock.painel).toHaveBeenCalledTimes(1);
      expect(processoServiceMock.painel).toHaveBeenCalledWith(5);
    });

    it('mostra as contagens de PUBLICADOS e EM_VALIDACAO nos dois cartões', () => {
      criarComponente();
      expect(valoresDosCartoes()).toEqual(['9', '11']);
    });

    it('esconde a faixa "Criar Processo" e mostra só 2 cartões (com "Validar Processo"), a duas colunas', () => {
      criarComponente();

      expect(fixture.nativeElement.querySelector('.faixa-criar')).toBeNull();
      expect(fixture.nativeElement.querySelector('.botao-criar')).toBeNull();
      expect(fixture.nativeElement.querySelectorAll('.indicadores app-cartao')).toHaveLength(2);
      expect(fixture.nativeElement.querySelector('.indicadores.indicadores-dgt')).not.toBeNull();

      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('Publicados');
      expect(texto).toContain('Em Validação SAIGT e INCM');
      expect(texto).toContain('Validar Processo');
      expect(texto).not.toContain('Em Formação');
      expect(texto).not.toContain('Para Correção');
    });

    it('liga os cartões a /processos com o grupo, "Validar Processo" incluído', () => {
      criarComponente();
      expect(hrefsDasAcoes()).toEqual(['/processos?grupo=PUBLICADOS', '/processos?grupo=EM_VALIDACAO']);
    });

    it('as ações dos cartões da DGT são verde e âmbar escuros, com o chevron da mesma cor', () => {
      criarComponente();

      const links: HTMLAnchorElement[] = [...fixture.nativeElement.querySelectorAll('.indicador-acao')];
      expect(links[0].classList).toContain('indicador-acao-sucesso');
      expect(links[1].classList).toContain('indicador-acao-aviso');
      expect(links.map((link) => link.querySelector('img')?.getAttribute('src'))).toEqual([
        'icones/painel/chevron-sucesso.svg',
        'icones/painel/chevron-aviso.svg',
      ]);
    });

    it('mostra o título "Em validação" com os processos do grupo EM_VALIDACAO, sem lista de PARA_CORRECAO', () => {
      processoServiceMock.painel.mockReturnValue(
        of(painelDgt({ EM_VALIDACAO: grupo('EM_VALIDACAO', 11, [processoExemplo({ estado: 'EM_VALIDACAO_DGT' })]) })),
      );
      criarComponente();

      expect(fixture.nativeElement.querySelector('.lista-cabecalho h2')?.textContent).toContain('Em validação');
      expect(fixture.nativeElement.querySelector('.lista-contagem')?.textContent?.trim()).toBe('11 processos');
      expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
      expect(fixture.nativeElement.querySelectorAll('app-tabela th')).toHaveLength(6);
    });

    it('mostra em "Fase" o nome do Procedimento e em "Nome" a designação do plano, nunca o código interno', () => {
      processoServiceMock.painel.mockReturnValue(
        of(
          painelDgt({
            EM_VALIDACAO: grupo('EM_VALIDACAO', 1, [
              processoExemplo({
                estado: 'ENVIADO_DGT',
                tipoProcedimento: 'DECISAO_INICIO',
                nomeProcedimento: 'Decisão de início',
                designacaoInstrumento: 'PDM de Sintra',
              }),
            ]),
          }),
        ),
      );
      criarComponente();

      const fase = fixture.nativeElement.querySelector('td.coluna-nomeProcedimento')?.textContent?.trim();
      expect(fase).toBe('Decisão de início');
      expect(fixture.nativeElement.querySelector('td.coluna-designacaoInstrumento')?.textContent?.trim()).toBe('PDM de Sintra');
      expect(fixture.nativeElement.querySelector('tbody')?.textContent).not.toContain('DECISAO_INICIO');
    });

    it('numa Dinâmica sem IGT mostra em "Nome" o descritivo que a API devolve na designação, ou "—" sem ele', () => {
      processoServiceMock.painel.mockReturnValue(
        of(
          painelDgt({
            EM_VALIDACAO: grupo('EM_VALIDACAO', 2, [
              processoExemplo({ id: 'p1', instrumentoId: undefined, numeroInstrumento: null, designacaoInstrumento: 'Suspensão parcial na Serra' }),
              processoExemplo({ id: 'p2', instrumentoId: undefined, numeroInstrumento: null, designacaoInstrumento: '' }),
            ]),
          }),
        ),
      );
      criarComponente();

      const nomes = [...fixture.nativeElement.querySelectorAll('td.coluna-designacaoInstrumento')].map((el: Element) =>
        el.textContent?.trim(),
      );
      expect(nomes).toEqual(['Suspensão parcial na Serra', '—']);
    });

    it('mostra em cada linha o estado real do processo no badge', () => {
      processoServiceMock.painel.mockReturnValue(
        of(
          painelDgt({
            EM_VALIDACAO: grupo('EM_VALIDACAO', 3, [
              processoExemplo({ id: 'p1', estado: 'ENVIADO_DGT' }),
              processoExemplo({ id: 'p2', estado: 'EM_VALIDACAO_DGT' }),
              processoExemplo({ id: 'p3', estado: 'VALIDADO_DGT' }),
            ]),
          }),
        ),
      );
      criarComponente();

      const badges = [...fixture.nativeElement.querySelectorAll('tbody app-estado-processo-badge')].map((el: Element) =>
        el.textContent?.trim(),
      );
      expect(badges).toEqual(['Enviado DGT', 'Em Validação DGT', 'Validado DGT']);
    });

    it('o processo devolvido pela PCM tem o selo «Devolvido pela PCM», em texto, ao lado do estado; os outros não', () => {
      processoServiceMock.painel.mockReturnValue(
        of(
          painelDgt({
            EM_VALIDACAO: grupo('EM_VALIDACAO', 2, [
              processoExemplo({ id: 'pa', estado: 'VALIDADO_DGT', situacaoPcm: 'SEM_PCM' }),
              processoExemplo({ id: 'pc', estado: 'VALIDADO_DGT', situacaoPcm: 'DEVOLVIDO', pcmDevolvidoEm: '2026-11-14T10:00:00Z' }),
            ]),
          }),
        ),
      );
      criarComponente();

      const linhas = [...fixture.nativeElement.querySelectorAll('tbody tr')] as HTMLElement[];
      expect(linhas).toHaveLength(2);
      expect(linhas[0].querySelector('app-selo[data-campo="devolvido-pcm"]')).toBeNull();
      const selo = linhas[1].querySelector('app-selo[data-campo="devolvido-pcm"]') as HTMLElement;
      expect(selo.textContent?.trim()).toBe('Devolvido pela PCM');
      expect(selo.closest('.estado-celula')?.querySelector('app-estado-processo-badge')?.textContent?.trim()).toBe('Validado DGT');
    });

    it('abre o detalhe do processo pela Referência', () => {
      processoServiceMock.painel.mockReturnValue(
        of(painelDgt({ EM_VALIDACAO: grupo('EM_VALIDACAO', 1, [processoExemplo({ id: 'p9', estado: 'ENVIADO_DGT' })]) })),
      );
      criarComponente();

      const link: HTMLAnchorElement = fixture.nativeElement.querySelector('td.coluna-numeroProcesso a');
      expect(link.getAttribute('href')).toBe('/processos/p9');
    });

    it('mostra "Sem processos em validação." quando não há nenhum', () => {
      criarComponente();
      expect(fixture.nativeElement.querySelector('.tabela-vazio')?.textContent?.trim()).toBe('Sem processos em validação.');
    });

    it('o link "Ver todos" abre /processos com o grupo EM_VALIDACAO', () => {
      criarComponente();
      const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.lista-ver-todos');
      expect(link.getAttribute('href')).toBe('/processos?grupo=EM_VALIDACAO');
    });
  });

  describe('quando a API do painel falha', () => {
    const erro = new ApiError('ERRO_INTERNO', 'Ocorreu um erro interno.', 't1', null);

    beforeEach(() => {
      processoServiceMock.painel.mockReturnValue(throwError(() => erro));
    });

    it('substitui a lista pelo banner de erro e deixa os cartões a 0', () => {
      criarComponente();

      expect(fixture.componentInstance.erro()).toBe(erro);
      expect(fixture.componentInstance.loading()).toBe(false);
      expect(fixture.nativeElement.querySelector('app-cartao.lista app-error-banner')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('app-tabela')).toBeNull();
      expect(valoresDosCartoes()).toEqual(['0', '0', '0', '0']);
    });
  });

  it('enquanto carrega, a tabela mostra o esqueleto e não a mensagem de vazio', () => {
    processoServiceMock.painel.mockReturnValue(NEVER);
    criarComponente();

    expect(fixture.nativeElement.querySelector('app-tabela table')?.getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelector('.tabela-vazio')).toBeNull();
  });

  it('saúda pelo primeiro nome e mostra a origem DGT no subtítulo para um utilizador interno', () => {
    authServiceMock.origem.mockReturnValue('AD_DGT');
    authServiceMock.perfilInterno.mockReturnValue('TECNICO');
    processoServiceMock.painel.mockReturnValue(of(painelDgt()));
    criarComponente();

    expect(fixture.componentInstance.saudacao()).toContain('Ana');
    expect(fixture.componentInstance.subtitulo()).toBe('DGT — Direção-Geral do Território');
  });

  it.each([
    [9, 'Bom dia'],
    [15, 'Boa tarde'],
    [22, 'Boa noite'],
  ])('às %ih saúda com "%s"', (hora, periodo) => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 0, 1, hora));
    criarComponente();

    expect(fixture.componentInstance.saudacao().startsWith(periodo)).toBe(true);
    vi.useRealTimers();
  });

  it('mostra o nome da entidade no subtítulo para um utilizador externo', () => {
    authServiceMock.origem.mockReturnValue('INCM');
    authServiceMock.entidade.mockReturnValue({ nipc: '501234567', nome: 'Câmara Municipal de Lisboa' });

    criarComponente();

    expect(fixture.componentInstance.subtitulo()).toBe('Câmara Municipal de Lisboa');
  });

  it('o botão "Criar Processo" aponta para /processos/criar', () => {
    criarComponente();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.botao-criar');
    expect(link.getAttribute('href')).toBe('/processos/criar');
  });
});

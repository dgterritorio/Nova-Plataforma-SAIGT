import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { RegraConfig, TipoDinamicaConfig, TipoProcedimentoConfig } from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { AlvoRegra, MENSAGEM_SEM_CONDICAO_ATIVA, RegraEditorModalComponent } from './regra-editor-modal.component';

const TIPOS_DINAMICA: TipoDinamicaConfig[] = [
  { id: 1, codigo: 'ELABORACAO', nome: 'Elaboração', ativo: true },
  { id: 2, codigo: 'REVISAO', nome: 'Revisão', ativo: true },
  { id: 3, codigo: 'SUSPENSAO_ANTIGA', nome: 'Suspensão antiga', ativo: false },
];
const TIPOS_PROCEDIMENTO: TipoProcedimentoConfig[] = [
  { id: 11, codigo: 'DECISAO_INICIO', nome: 'Decisão de início', ativo: true, formulario: 'INFORMACAO_GERAL' },
];

const ALVO_DINAMICA: AlvoRegra = { tipo: 'DINAMICA', id: 101, nome: 'Elaboração', contexto: 'PDM — Elaboração' };
const ALVO_PROCEDIMENTO: AlvoRegra = { tipo: 'PROCEDIMENTO', id: 501, nome: 'Decisão de início', contexto: 'PDM — Alteração' };

const REGRA_DOIS_GRUPOS: RegraConfig = {
  id: 310,
  instrumentoDinamicaConfigId: 101,
  instrumentoProcedimentoConfigId: null,
  substituiSequenciamento: false,
  operador: 'OR',
  descricao: 'Duas vias',
  ativo: true,
  condicoes: [
    { id: 401, avaliador: 'INSTRUMENTO_EM_VIGOR', parametros: {}, grupo: 1, ativo: true },
    { id: 402, avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'REVISAO', estado: 'EM_CURSO' }, grupo: 2, ativo: true },
  ],
};

describe('RegraEditorModalComponent', () => {
  let fixture: ComponentFixture<RegraEditorModalComponent>;
  let servico: { criarRegra: ReturnType<typeof vi.fn>; atualizarRegra: ReturnType<typeof vi.fn> };
  let gravadas: RegraConfig[];

  afterEach(() => fixture?.destroy());

  function abrir(alvo: AlvoRegra, regra: RegraConfig | null = null): void {
    servico = {
      criarRegra: vi.fn((pedido) => of({ ...pedido, id: 999, condicoes: [] })),
      atualizarRegra: vi.fn((id, pedido) => of({ ...pedido, id, condicoes: [] })),
    };
    TestBed.configureTestingModule({
      imports: [RegraEditorModalComponent],
      providers: [{ provide: ConfiguracaoService, useValue: servico }],
    });
    fixture = TestBed.createComponent(RegraEditorModalComponent);
    gravadas = [];
    fixture.componentInstance.gravada.subscribe((regraGravada) => gravadas.push(regraGravada));
    fixture.componentRef.setInput('alvo', alvo);
    fixture.componentRef.setInput('regra', regra);
    fixture.componentRef.setInput('tiposDinamica', TIPOS_DINAMICA);
    fixture.componentRef.setInput('tiposProcedimento', TIPOS_PROCEDIMENTO);
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();
  }

  const dialogo = (): HTMLElement => document.querySelector<HTMLElement>('[role="dialog"]')!;
  const linhas = (): HTMLElement[] => [...dialogo().querySelectorAll<HTMLElement>('.condicao')];
  const grupos = (): HTMLFieldSetElement[] => [...dialogo().querySelectorAll<HTMLFieldSetElement>('fieldset.grupo')];
  const rotulos = (raiz: ParentNode): string[] => [...raiz.querySelectorAll('label')].map((l) => l.textContent?.replace('*', '').trim() ?? '');
  const campo = <T extends HTMLElement>(rotulo: string, raiz: ParentNode): T => {
    const label = [...raiz.querySelectorAll('label')].find((l) => l.textContent?.replace('*', '').trim() === rotulo)!;
    return (label.htmlFor ? document.getElementById(label.htmlFor) : label.querySelector('input')) as T;
  };
  const botao = (texto: string, raiz: ParentNode = dialogo()): HTMLButtonElement =>
    [...raiz.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === texto)!;
  const opcoes = (select: HTMLSelectElement): string[] => [...select.options].map((o) => o.textContent?.trim() ?? '');

  function clicar(alvo: HTMLElement): void {
    alvo.click();
    fixture.detectChanges();
  }

  function escolher(rotulo: string, valor: string, raiz: ParentNode): void {
    const select = campo<HTMLSelectElement>(rotulo, raiz);
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function guardar(): void {
    clicar(botao('Guardar'));
  }

  const proximoCiclo = (): Promise<void> => new Promise((resolver) => setTimeout(resolver));

  it('regra de elegibilidade: título com a Dinâmica e sem "Substitui a ordem dos Procedimentos"', () => {
    abrir(ALVO_DINAMICA);

    expect(dialogo().querySelector('.modal-titulo')?.textContent?.trim()).toBe('Regra de elegibilidade — Elaboração');
    expect(dialogo().textContent).not.toContain('Substitui a ordem dos Procedimentos');
    expect(campo<HTMLInputElement>('Regra ativa', dialogo()).checked).toBe(true);
    expect(campo<HTMLInputElement>('Todos os grupos têm de se cumprir (E)', dialogo()).checked).toBe(true);
    expect(grupos()).toHaveLength(1);
    expect(grupos()[0].querySelector('legend')?.textContent?.trim()).toBe('Grupo 1');
    expect(grupos()[0].textContent).toContain('As condições deste grupo têm de se cumprir todas.');
  });

  it('exceção ao sequenciamento: mostra "Substitui a ordem" com a ajuda e envia-a marcada', () => {
    abrir(ALVO_PROCEDIMENTO);
    expect(dialogo().querySelector('.modal-titulo')?.textContent?.trim()).toBe('Exceção ao sequenciamento — Decisão de início');
    const substitui = campo<HTMLInputElement>('Substitui a ordem dos Procedimentos', dialogo());
    expect(document.getElementById(substitui.getAttribute('aria-describedby')!)?.textContent).toContain(
      'Desmarcada: a regra soma-se à ordem. Marcada: substitui-a.',
    );
    clicar(substitui);
    escolher('Avaliador', 'INSTRUMENTO_EM_VIGOR', linhas()[0]);
    guardar();

    const pedido = servico.criarRegra.mock.calls[0][0];
    expect(pedido.instrumentoProcedimentoConfigId).toBe(501);
    expect(pedido.instrumentoDinamicaConfigId).toBeNull();
    expect(pedido.substituiSequenciamento).toBe(true);
  });

  it('os campos de cada avaliador: tipo de ocorrência, código e estado, só código, ou "Sem parâmetros."', () => {
    abrir(ALVO_DINAMICA);
    const linha = (): HTMLElement => linhas()[0];
    expect(opcoes(campo('Avaliador', linha())).slice(1)).toEqual([
      'Existe ocorrência num estado',
      'Não existe ocorrência',
      'O Instrumento está em vigor',
      'Dinâmica em curso no sistema anterior',
    ]);

    escolher('Avaliador', 'OCORRENCIA_EM_ESTADO', linha());
    expect(rotulos(linha())).toEqual(['Avaliador', 'Tipo de ocorrência', 'Grupo', 'Ativa']);
    escolher('Tipo de ocorrência', 'DINAMICA', linha());
    expect(rotulos(linha())).toEqual(['Avaliador', 'Tipo de ocorrência', 'Código', 'Estado', 'Grupo', 'Ativa']);
    expect(opcoes(campo('Código', linha()))).toEqual(['Selecione…', 'Elaboração (ELABORACAO)', 'Revisão (REVISAO)', 'Suspensão antiga (SUSPENSAO_ANTIGA)']);
    expect(opcoes(campo('Estado', linha())).slice(1)).toEqual(['Em curso', 'Terminada', 'Caducada']);
    escolher('Tipo de ocorrência', 'PROCESSO', linha());
    expect(opcoes(campo('Código', linha())).slice(1)).toEqual(['Decisão de início (DECISAO_INICIO)']);
    expect(opcoes(campo('Estado', linha()))).toContain('Publicado');

    escolher('Avaliador', 'INSTRUMENTO_EM_VIGOR', linha());
    expect(rotulos(linha())).toEqual(['Avaliador', 'Grupo', 'Ativa']);
    expect(linha().textContent).toContain('Sem parâmetros.');

    escolher('Avaliador', 'DINAMICA_BLOQUEADA_LEGADO', linha());
    expect(rotulos(linha())).toEqual(['Avaliador', 'Código', 'Grupo', 'Ativa']);
  });

  it('"Não existe ocorrência" sobre Dinâmica: código e estado opcionais ("Qualquer Dinâmica", "Em nenhum estado"); sobre processo, o código é obrigatório', () => {
    abrir(ALVO_DINAMICA);
    const linha = linhas()[0];
    escolher('Avaliador', 'OCORRENCIA_NAO_EXISTE', linha);
    escolher('Tipo de ocorrência', 'DINAMICA', linha);
    const codigo = campo<HTMLSelectElement>('Código', linha);
    expect(codigo.options[0].textContent?.trim()).toBe('Qualquer Dinâmica');
    expect(codigo.options[0].disabled).toBe(false);
    expect(campo<HTMLSelectElement>('Estado', linha).options[0].textContent?.trim()).toBe('Em nenhum estado');

    escolher('Tipo de ocorrência', 'PROCESSO', linha);
    expect(campo<HTMLSelectElement>('Código', linha).options[0].textContent?.trim()).toBe('Selecione…');
    guardar();
    expect(linha.textContent).toContain('Escolha o código.');
    expect(servico.criarRegra).not.toHaveBeenCalled();
  });

  it('regra nova "Não existe ocorrência", "Dinâmica", "Revisão", "Em curso": o POST leva a condição do contrato e o modal emite a regra gravada', () => {
    abrir(ALVO_DINAMICA);
    const linha = linhas()[0];
    escolher('Avaliador', 'OCORRENCIA_NAO_EXISTE', linha);
    escolher('Tipo de ocorrência', 'DINAMICA', linha);
    escolher('Código', 'REVISAO', linha);
    escolher('Estado', 'EM_CURSO', linha);
    guardar();

    expect(servico.criarRegra).toHaveBeenCalledTimes(1);
    const pedido = servico.criarRegra.mock.calls[0][0];
    expect(pedido).toEqual({
      instrumentoDinamicaConfigId: 101,
      instrumentoProcedimentoConfigId: null,
      substituiSequenciamento: false,
      operador: 'AND',
      descricao: null,
      ativo: true,
      condicoes: [
        { avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'REVISAO', estado: 'EM_CURSO' }, grupo: 1, ativo: true },
      ],
    });
    expect(pedido.condicoes[0]).not.toHaveProperty('id');
    expect(gravadas).toHaveLength(1);
  });

  it('"Qualquer Dinâmica" e "Em curso": os parâmetros vão sem codigo', () => {
    abrir(ALVO_DINAMICA);
    const linha = linhas()[0];
    escolher('Avaliador', 'OCORRENCIA_NAO_EXISTE', linha);
    escolher('Tipo de ocorrência', 'DINAMICA', linha);
    escolher('Código', '', linha);
    escolher('Estado', 'EM_CURSO', linha);
    guardar();

    const parametros = servico.criarRegra.mock.calls[0][0].condicoes[0].parametros;
    expect(parametros).toEqual({ tipo_ocorrencia: 'DINAMICA', estado: 'EM_CURSO' });
    expect(parametros).not.toHaveProperty('codigo');
  });

  it('"O Instrumento está em vigor": os parâmetros vão como {}', () => {
    abrir(ALVO_DINAMICA);
    escolher('Avaliador', 'INSTRUMENTO_EM_VIGOR', linhas()[0]);
    guardar();

    expect(servico.criarRegra.mock.calls[0][0].condicoes).toEqual([{ avaliador: 'INSTRUMENTO_EM_VIGOR', parametros: {}, grupo: 1, ativo: true }]);
  });

  it('mudar de avaliador ou de tipo de ocorrência limpa os parâmetros que deixam de se aplicar', () => {
    abrir(ALVO_DINAMICA);
    const linha = linhas()[0];
    const condicao = (): { tipoOcorrencia: string | null; codigo: string | null; estado: string | null } => {
      const valor = fixture.componentInstance.grupos.at(0).controls.condicoes.at(0).getRawValue();
      return { tipoOcorrencia: valor.tipoOcorrencia, codigo: valor.codigo, estado: valor.estado };
    };
    escolher('Avaliador', 'OCORRENCIA_EM_ESTADO', linha);
    escolher('Tipo de ocorrência', 'DINAMICA', linha);
    escolher('Código', 'REVISAO', linha);
    escolher('Estado', 'TERMINADA', linha);

    escolher('Avaliador', 'OCORRENCIA_NAO_EXISTE', linha);
    expect(condicao()).toEqual({ tipoOcorrencia: 'DINAMICA', codigo: 'REVISAO', estado: 'TERMINADA' });

    escolher('Avaliador', 'DINAMICA_BLOQUEADA_LEGADO', linha);
    expect(condicao()).toEqual({ tipoOcorrencia: null, codigo: 'REVISAO', estado: null });

    escolher('Avaliador', 'INSTRUMENTO_EM_VIGOR', linha);
    expect(condicao()).toEqual({ tipoOcorrencia: null, codigo: null, estado: null });

    escolher('Avaliador', 'OCORRENCIA_EM_ESTADO', linha);
    escolher('Tipo de ocorrência', 'DINAMICA', linha);
    escolher('Código', 'ELABORACAO', linha);
    escolher('Estado', 'EM_CURSO', linha);
    escolher('Tipo de ocorrência', 'PROCESSO', linha);
    expect(condicao()).toEqual({ tipoOcorrencia: 'PROCESSO', codigo: null, estado: null });
  });

  it('abre uma regra gravada com os grupos, as condições e os valores; o resumo descreve-a', () => {
    abrir(ALVO_DINAMICA, REGRA_DOIS_GRUPOS);

    expect(campo<HTMLTextAreaElement>('Descrição', dialogo()).value).toBe('Duas vias');
    expect(campo<HTMLInputElement>('Basta um grupo cumprir-se (OU)', dialogo()).checked).toBe(true);
    expect(grupos()).toHaveLength(2);
    expect(campo<HTMLSelectElement>('Código', linhas()[1]).value).toBe('REVISAO');
    expect(campo<HTMLSelectElement>('Grupo', linhas()[1]).value).toBe('2');
    const resumo = dialogo().querySelector('.resumo')!;
    expect(resumo.getAttribute('aria-live')).toBe('polite');
    expect(resumo.textContent).toContain('Resumo: (o Instrumento está em vigor) ou (não existe Dinâmica Revisão em curso)');
    expect(botao('Remover grupo')).toBeUndefined();
  });

  it('mover a condição do grupo 2 para o grupo 1 e remover o grupo 2 vazio: todas as condições vão com grupo = 1', async () => {
    abrir(ALVO_DINAMICA, REGRA_DOIS_GRUPOS);
    escolher('Grupo', '1', linhas()[1]);
    await proximoCiclo();

    expect(grupos()[0].querySelectorAll('.condicao')).toHaveLength(2);
    expect(grupos()[1].querySelectorAll('.condicao')).toHaveLength(0);
    expect(document.activeElement).toBe(campo('Grupo', linhas()[1]));
    expect(dialogo().querySelector('.resumo')?.textContent).toContain('(o Instrumento está em vigor e não existe Dinâmica Revisão em curso)');
    clicar(botao('Remover grupo', grupos()[1]));
    expect(grupos()).toHaveLength(1);
    guardar();

    expect(servico.atualizarRegra).toHaveBeenCalledWith(310, expect.anything());
    const condicoes = servico.atualizarRegra.mock.calls[0][1].condicoes;
    expect(condicoes.map((c: { id: number; grupo: number }) => [c.id, c.grupo])).toEqual([
      [401, 1],
      [402, 1],
    ]);
  });

  it('os grupos renumeram-se sem buracos: um grupo vazio que fica no meio não conta', () => {
    abrir(ALVO_DINAMICA, {
      ...REGRA_DOIS_GRUPOS,
      condicoes: [...REGRA_DOIS_GRUPOS.condicoes, { id: 403, avaliador: 'INSTRUMENTO_EM_VIGOR', parametros: {}, grupo: 3, ativo: true }],
    });
    escolher('Grupo', '1', linhas()[1]);
    guardar();

    const condicoes = servico.atualizarRegra.mock.calls[0][1].condicoes;
    expect(condicoes.map((c: { id: number; grupo: number }) => [c.id, c.grupo])).toEqual([
      [401, 1],
      [402, 1],
      [403, 2],
    ]);
  });

  it('uma condição gravada removida no editor não vai no PUT; uma condição inativa vai com ativo = false', () => {
    abrir(ALVO_DINAMICA, {
      ...REGRA_DOIS_GRUPOS,
      condicoes: [
        REGRA_DOIS_GRUPOS.condicoes[0],
        { ...REGRA_DOIS_GRUPOS.condicoes[1], grupo: 1 },
        { id: 403, avaliador: 'DINAMICA_BLOQUEADA_LEGADO', parametros: { codigo: 'ELABORACAO' }, grupo: 1, ativo: false },
      ],
    });
    expect(linhas()[2].getAttribute('aria-label')).toBe('Condição 3 do grupo 1');
    const remover = linhas()[1].querySelector<HTMLButtonElement>('.acao-remover-condicao')!;
    expect(remover.getAttribute('aria-label')).toBe('Remover a condição 2 do grupo 1');
    clicar(remover);
    guardar();

    const condicoes = servico.atualizarRegra.mock.calls[0][1].condicoes;
    expect(condicoes.map((c: { id: number }) => c.id)).toEqual([401, 403]);
    expect(condicoes[1]).toEqual({ id: 403, avaliador: 'DINAMICA_BLOQUEADA_LEGADO', parametros: { codigo: 'ELABORACAO' }, grupo: 1, ativo: false });
  });

  it('uma regra ativa sem condições ativas não grava: "Indique pelo menos uma condição ativa." e nenhum pedido', () => {
    abrir(ALVO_DINAMICA, REGRA_DOIS_GRUPOS);
    for (const caixa of dialogo().querySelectorAll<HTMLInputElement>('.caixa-ativa')) {
      clicar(caixa);
    }
    guardar();

    expect(dialogo().querySelector('.erro-sem-condicao')?.textContent?.trim()).toBe(MENSAGEM_SEM_CONDICAO_ATIVA);
    expect(dialogo().querySelector('.erro-sem-condicao')?.getAttribute('role')).toBe('alert');
    expect(servico.atualizarRegra).not.toHaveBeenCalled();
    expect(dialogo().querySelector('.resumo')?.textContent).toContain('Sem condições ativas');

    clicar(campo<HTMLInputElement>('Regra ativa', dialogo()));
    guardar();
    expect(servico.atualizarRegra).toHaveBeenCalledTimes(1);
    expect(servico.atualizarRegra.mock.calls[0][1].ativo).toBe(false);
  });

  it('campos obrigatórios por avaliador: sem avaliador não grava e o erro fica na condição', () => {
    abrir(ALVO_DINAMICA);
    guardar();
    expect(linhas()[0].textContent).toContain('Escolha o avaliador.');
    expect(servico.criarRegra).not.toHaveBeenCalled();

    escolher('Avaliador', 'OCORRENCIA_EM_ESTADO', linhas()[0]);
    escolher('Tipo de ocorrência', 'DINAMICA', linhas()[0]);
    guardar();
    expect(linhas()[0].textContent).toContain('Escolha o código.');
    expect(linhas()[0].textContent).toContain('Escolha o estado.');
    expect(servico.criarRegra).not.toHaveBeenCalled();
  });

  it('um 400 com campos[0].path = "condicoes[1].parametros" mostra a mensagem na segunda condição enviada e o modal fica aberto', () => {
    abrir(ALVO_DINAMICA, REGRA_DOIS_GRUPOS);
    servico.atualizarRegra.mockReturnValue(
      throwError(
        () =>
          new ApiError('VALIDACAO', 'Parâmetros inválidos.', 'trace', [
            { path: 'condicoes[1].parametros', erro: 'codigo inexistente em tipo_dinamica: REVISAO' },
          ]),
      ),
    );
    guardar();

    const segunda = linhas()[1];
    const erro = segunda.querySelector<HTMLElement>('.erro-condicao')!;
    expect(erro.textContent).toContain('codigo inexistente em tipo_dinamica: REVISAO');
    expect(erro.getAttribute('role')).toBe('alert');
    expect(segunda.getAttribute('aria-describedby')).toBe(erro.id);
    expect(linhas()[0].querySelector('.erro-condicao')).toBeNull();
    expect(dialogo().textContent).toContain('Parâmetros inválidos.');
    expect(gravadas).toHaveLength(0);
    expect(dialogo()).not.toBeNull();
  });

  it('um 400 num campo da regra (substituiSequenciamento) aparece no topo do modal', () => {
    abrir(ALVO_PROCEDIMENTO);
    escolher('Avaliador', 'INSTRUMENTO_EM_VIGOR', linhas()[0]);
    servico.criarRegra.mockReturnValue(
      throwError(
        () =>
          new ApiError('VALIDACAO', 'Regra mal configurada.', 'trace', [
            { path: 'instrumentoProcedimentoConfigId', erro: 'Este Procedimento já tem uma regra; edite a existente.' },
          ]),
      ),
    );
    guardar();

    const topo = dialogo().querySelector('form > app-alerta')!;
    expect(topo.textContent).toContain('Não foi possível gravar a regra.');
    expect(topo.textContent).toContain('Regra mal configurada.');
    expect(topo.textContent).toContain('Este Procedimento já tem uma regra; edite a existente.');
    expect(linhas()[0].querySelector('.erro-condicao')).toBeNull();
    expect(gravadas).toHaveLength(0);
  });

  it('só com o teclado: ao adicionar, o foco vai para o campo novo; ao remover, para a ação seguinte; o resumo acompanha', async () => {
    abrir(ALVO_DINAMICA, { ...REGRA_DOIS_GRUPOS, condicoes: [REGRA_DOIS_GRUPOS.condicoes[0]] });
    clicar(botao('Adicionar condição'));
    await proximoCiclo();
    expect(linhas()).toHaveLength(2);
    expect(document.activeElement).toBe(campo('Avaliador', linhas()[1]));
    expect(botao('Adicionar condição').getAttribute('aria-label')).toBe('Adicionar condição ao grupo 1');

    escolher('Avaliador', 'OCORRENCIA_NAO_EXISTE', linhas()[1]);
    escolher('Tipo de ocorrência', 'DINAMICA', linhas()[1]);
    expect(dialogo().querySelector('.resumo')?.textContent).toContain('(o Instrumento está em vigor e não existe nenhuma Dinâmica)');

    clicar(linhas()[0].querySelector<HTMLButtonElement>('.acao-remover-condicao')!);
    await proximoCiclo();
    expect(linhas()).toHaveLength(1);
    expect(document.activeElement).toBe(linhas()[0].querySelector('.acao-remover-condicao'));
    expect(dialogo().querySelector('.resumo')?.textContent).toContain('Resumo: (não existe nenhuma Dinâmica)');

    clicar(linhas()[0].querySelector<HTMLButtonElement>('.acao-remover-condicao')!);
    await proximoCiclo();
    expect(document.activeElement).toBe(botao('Adicionar condição'));

    clicar(botao('Adicionar grupo'));
    await proximoCiclo();
    expect(grupos()).toHaveLength(2);
    expect(document.activeElement).toBe(campo('Avaliador', grupos()[1]));
    expect(campo<HTMLSelectElement>('Grupo', grupos()[1]).getAttribute('aria-label')).toBe('Grupo da condição 1 do grupo 2');

    // O nome acessível contém o texto visível (WCAG 2.5.3).
    expect(botao('Remover grupo', grupos()[1]).getAttribute('aria-label')).toBe('Remover grupo 2');
    clicar(botao('Remover grupo', grupos()[1]));
    await proximoCiclo();
    expect(grupos()).toHaveLength(1);
    expect(document.activeElement).toBe(botao('Adicionar grupo'));
  });
});

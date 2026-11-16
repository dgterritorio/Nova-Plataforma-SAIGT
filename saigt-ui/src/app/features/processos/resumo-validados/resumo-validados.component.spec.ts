import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ResumoValidadosComponent } from './resumo-validados.component';
import { ApiError } from '../../../core/models/error-response.model';
import { ProcessoDetalhe } from '../../../models/processo.model';
import { ProcessoService } from '../../../services/processo.service';

const OPCOES_PEOT = [
  { id: 41, codigo: 'POAP', descricao: 'POAP - Arriba Fóssil da Costa da Caparica' },
  { id: 42, codigo: 'POC', descricao: 'POC - Ovar-Marinha Grande' },
];

function processo(parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
  return {
    id: 'p1',
    versao: 3,
    estado: 'EM_VALIDACAO_DGT',
    designacaoInstrumento: 'PDM - Alteração',
    tipoInstrumento: 'PDM',
    nomeProcedimento: 'Decisão que determina a elaboração do plano',
    territorio: { concelhos: [{ codigo: '1503', nome: 'Almada' }], nutsII: [] },
    acoesDisponiveis: ['validar', 'rejeitar'],
    notasIncm: null,
    notasDgt: null,
    regUsoSolo: null,
    alteracaoPeotId: null,
    alteracaoPeotDescricao: null,
    opcoesAlteracaoPeot: OPCOES_PEOT,
    ...parcial,
  } as ProcessoDetalhe;
}

describe('ResumoValidadosComponent', () => {
  let fixture: ComponentFixture<ResumoValidadosComponent>;
  let validar: ReturnType<typeof vi.fn>;
  let enviarIncm: ReturnType<typeof vi.fn>;

  function abrir(parcial: Partial<ProcessoDetalhe> = {}): void {
    fixture = TestBed.createComponent(ResumoValidadosComponent);
    fixture.componentRef.setInput('processo', processo(parcial));
    fixture.detectChanges();
  }

  function el<T extends Element = HTMLElement>(seletor: string): T | null {
    return fixture.nativeElement.querySelector(seletor) as T | null;
  }

  function texto(): string {
    return (fixture.nativeElement.textContent ?? '') as string;
  }

  function botoes(rotulo: string): HTMLButtonElement[] {
    return (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).filter(
      (b) => b.textContent?.trim() === rotulo,
    );
  }

  function escrever(seletor: string, valor: string): void {
    const campo = el<HTMLTextAreaElement>(seletor)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  function clicar(botao: HTMLElement): void {
    botao.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    validar = vi.fn().mockReturnValue(of({ id: 'p1', estado: 'VALIDADO_DGT', versao: 4 }));
    enviarIncm = vi.fn().mockReturnValue(of({ id: 'p1', estado: 'EM_VALIDACAO_INCM', versao: 5 }));
    TestBed.configureTestingModule({
      imports: [ResumoValidadosComponent],
      providers: [{ provide: ProcessoService, useValue: { validar, enviarIncm } }],
    });
  });

  describe('ao abrir', () => {
    it('mostra os dados do processo e os quatro campos, como no UI-041', () => {
      abrir();

      expect(texto()).toContain('Dados do processo');
      expect(texto()).toContain('PDM - Alteração');
      expect(texto()).toContain('Decisão que determina a elaboração do plano');
      expect(texto()).toContain('Almada');
      expect(el('#resumo-notasIncm')).not.toBeNull();
      expect(el('#resumo-notasDgt')).not.toBeNull();
      expect(fixture.nativeElement.querySelectorAll('input[type="radio"][name="regUsoSolo"]')).toHaveLength(2);
      const opcoes = Array.from(el<HTMLSelectElement>('#resumo-alteracao-peot')!.options).map((o) => o.textContent?.trim());
      expect(opcoes).toEqual(['Sem alteração PEOT', OPCOES_PEOT[0].descricao, OPCOES_PEOT[1].descricao]);
    });

    it('põe o foco no título do resumo', async () => {
      abrir();
      await fixture.whenStable();

      expect(document.activeElement?.id).toBe('resumo-validados-titulo');
    });

    it('sem opções na lista ALTERACAO_PEOT, «Alteração PEOT\'s» não aparece', () => {
      abrir({ opcoesAlteracaoPeot: [] });

      expect(el('[data-campo="alteracao-peot"]')).toBeNull();
      expect(texto()).not.toContain("Alteração PEOT's");
      expect(el('[data-campo="reg-uso-solo"]')).not.toBeNull();
    });

    it('o aviso antes de gravar fala dos campos deste resumo, sem os contar', () => {
      abrir({ opcoesAlteracaoPeot: [] });

      const aviso = el('#aviso-gravar')!.textContent!.replace(/\s+/g, ' ').trim();
      expect(aviso).toBe('Ao gravar, o processo fica validado com os campos deste resumo, que deixam de se poder alterar.');
      expect(aviso).not.toContain('quatro');
    });

    it('antes da validação não há «Publicar» nem «Enviar PCM», e há um «Gravar» por bloco', () => {
      abrir();

      expect(botoes('Publicar')).toHaveLength(0);
      expect(botoes('Enviar PCM')).toHaveLength(0);
      expect(botoes('Gravar')).toHaveLength(3);
      expect(botoes('Voltar')).toHaveLength(1);
    });
  });

  describe('«Gravar»', () => {
    it('envia o validar com os quatro campos e emite validado', () => {
      abrir();
      const validado = vi.fn();
      fixture.componentInstance.validado.subscribe(validado);

      escrever('#resumo-notasIncm', '  Publicar com a planta.  ');
      escrever('#resumo-notasDgt', 'Conferido pela DGT.');
      clicar(fixture.nativeElement.querySelectorAll('input[name="regUsoSolo"]')[0]);
      const seletor = el<HTMLSelectElement>('#resumo-alteracao-peot')!;
      seletor.value = seletor.options[2].value;
      seletor.dispatchEvent(new Event('change'));
      clicar(botoes('Gravar')[2]);

      expect(validar).toHaveBeenCalledExactlyOnceWith('p1', 3, {
        notasIncm: 'Publicar com a planta.',
        notasDgt: 'Conferido pela DGT.',
        regUsoSolo: true,
        alteracaoPeotId: 42,
      });
      expect(validado).toHaveBeenCalledWith({ id: 'p1', estado: 'VALIDADO_DGT', versao: 4 });
      expect(el('.sucesso')?.textContent).toContain('Processo validado');
    });

    it('sem nada preenchido envia os quatro campos vazios', () => {
      abrir({ opcoesAlteracaoPeot: [] });

      clicar(botoes('Gravar')[0]);

      expect(validar).toHaveBeenCalledWith('p1', 3, { notasIncm: null, notasDgt: null, regUsoSolo: null, alteracaoPeotId: null });
    });

    it('enquanto grava, os «Gravar» ficam desativados e um segundo clique não repete o pedido', () => {
      validar.mockReturnValue(new Subject());
      abrir();

      clicar(botoes('Gravar')[0]);
      clicar(botoes('Gravar')[1]);

      expect(validar).toHaveBeenCalledOnce();
      expect(botoes('Gravar').every((b) => b.disabled)).toBe(true);
    });

    it('um 400 no campo da Alteração PEOT assinala-o junto do campo', () => {
      validar.mockReturnValue(
        throwError(
          () =>
            new ApiError('VALIDACAO', 'Valor de lista inválido.', 't1', [
              { path: 'alteracaoPeotId', erro: 'Não existe ou não é um valor ativo de ALTERACAO_PEOT.' },
            ]),
        ),
      );
      abrir();

      clicar(botoes('Gravar')[0]);

      expect(el('#erro-alteracao-peot')?.textContent).toContain('Não existe ou não é um valor ativo');
      expect(el('#resumo-alteracao-peot')?.getAttribute('aria-invalid')).toBe('true');
      expect(el('#resumo-alteracao-peot')?.getAttribute('aria-describedby')).toBe('erro-alteracao-peot');
    });

    it('um 409 mostra a mensagem do backend e Recarregar, sem repetir o pedido', () => {
      validar.mockReturnValue(throwError(() => new ApiError('ITENS_POR_REVER', 'Reveja todos os documentos antes de continuar.', 't1', null)));
      abrir();
      clicar(botoes('Gravar')[0]);
      expect(texto()).toContain('Reveja todos os documentos antes de continuar.');

      validar.mockReturnValue(throwError(() => new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado.', 't2', null)));
      clicar(botoes('Gravar')[0]);
      const recarregar = vi.fn();
      fixture.componentInstance.recarregar.subscribe(recarregar);

      expect(botoes('Gravar').every((b) => b.disabled)).toBe(true);
      clicar(botoes('Recarregar')[0]);
      expect(recarregar).toHaveBeenCalledOnce();
      expect(validar).toHaveBeenCalledTimes(2);
    });

    it('um 403 mostra o erro de permissão', () => {
      validar.mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'Sem permissão.', 't1', null)));
      abrir();

      clicar(botoes('Gravar')[0]);

      expect(el('app-error-banner [role="alert"]')?.textContent).toContain('Não tem permissão para executar esta ação.');
    });
  });

  describe('já validado', () => {
    const VALIDADO = {
      estado: 'VALIDADO_DGT',
      notasIncm: 'Publicar com a planta.',
      notasDgt: 'Conferido.',
      regUsoSolo: false,
      alteracaoPeotId: 41,
      alteracaoPeotDescricao: OPCOES_PEOT[0].descricao,
    } as Partial<ProcessoDetalhe>;

    it('mostra os valores gravados só de leitura, sem «Gravar» nem «Voltar»', () => {
      abrir({ ...VALIDADO, acoesDisponiveis: ['enviar-incm'] });

      expect(texto()).toContain('Publicar com a planta.');
      expect(texto()).toContain('Conferido.');
      expect(el('[data-campo="reg-uso-solo"]')?.textContent).toContain('Não');
      expect(el('[data-campo="alteracao-peot"]')?.textContent).toContain(OPCOES_PEOT[0].descricao);
      expect(el('textarea')).toBeNull();
      expect(el('select')).toBeNull();
      expect(botoes('Gravar')).toHaveLength(0);
      expect(botoes('Voltar')).toHaveLength(0);
    });

    it('com `enviar-incm` mostra «Publicar»; sem ela, não', () => {
      abrir({ ...VALIDADO, acoesDisponiveis: ['enviar-incm'] });
      expect(botoes('Publicar')).toHaveLength(1);

      abrir({ ...VALIDADO, acoesDisponiveis: [] });
      expect(botoes('Publicar')).toHaveLength(0);
    });

    it('com `enviar-pcm` (1.ª série) mostra «Enviar PCM» antes de «Publicar»; sem ela (2.ª série), não', () => {
      abrir({ ...VALIDADO, acoesDisponiveis: ['enviar-incm', 'enviar-pcm'] });
      const rodape = Array.from(fixture.nativeElement.querySelectorAll('.rodape button')).map((b) => (b as HTMLElement).textContent?.trim());
      expect(rodape).toEqual(['Enviar PCM', 'Publicar']);

      abrir({ ...VALIDADO, acoesDisponiveis: ['enviar-incm'] });
      expect(botoes('Enviar PCM')).toHaveLength(0);
    });

    it('«Publicar» envia o enviar-incm com a versão e mostra o estado devolvido', () => {
      abrir({ ...VALIDADO, acoesDisponiveis: ['enviar-incm'] });
      const publicado = vi.fn();
      fixture.componentInstance.publicado.subscribe(publicado);

      clicar(botoes('Publicar')[0]);

      expect(enviarIncm).toHaveBeenCalledExactlyOnceWith('p1', 3);
      expect(publicado).toHaveBeenCalledWith({ id: 'p1', estado: 'EM_VALIDACAO_INCM', versao: 5 });
      expect(el('.sucesso')?.textContent).toContain('Estado: Em validação INCM.');
    });

    it('com a INCM indisponível mostra a mensagem do backend e «Publicar» continua disponível', () => {
      enviarIncm.mockReturnValue(
        throwError(() => new ApiError('INTEGRACAO_INDISPONIVEL', 'A INCM não respondeu. Tente mais tarde.', 't1', null)),
      );
      abrir({ ...VALIDADO, acoesDisponiveis: ['enviar-incm'] });
      const publicado = vi.fn();
      fixture.componentInstance.publicado.subscribe(publicado);

      clicar(botoes('Publicar')[0]);

      expect(texto()).toContain('A INCM não respondeu. Tente mais tarde.');
      expect(publicado).not.toHaveBeenCalled();
      expect(botoes('Recarregar')).toHaveLength(0);
      expect(botoes('Publicar')[0].getAttribute('aria-disabled')).toBeNull();
    });

    it('um 409 no «Publicar» mostra a mensagem e Recarregar, sem repetir o pedido', () => {
      enviarIncm.mockReturnValue(throwError(() => new ApiError('ESTADO_INVALIDO', 'Transição não permitida.', 't1', null)));
      abrir({ ...VALIDADO, acoesDisponiveis: ['enviar-incm'] });
      const recarregar = vi.fn();
      fixture.componentInstance.recarregar.subscribe(recarregar);

      clicar(botoes('Publicar')[0]);
      clicar(botoes('Publicar')[0]);

      expect(texto()).toContain('Transição não permitida.');
      expect(enviarIncm).toHaveBeenCalledOnce();
      expect(botoes('Publicar')[0].getAttribute('aria-disabled')).toBe('true');
      clicar(botoes('Recarregar')[0]);
      expect(recarregar).toHaveBeenCalledOnce();
    });

    it('«Enviar PCM» emite o pedido do diálogo, sem chamar a API', () => {
      abrir({ ...VALIDADO, acoesDisponiveis: ['enviar-pcm'] });
      const enviarPcm = vi.fn();
      fixture.componentInstance.enviarPcm.subscribe(enviarPcm);

      clicar(botoes('Enviar PCM')[0]);

      expect(enviarPcm).toHaveBeenCalledOnce();
      expect(enviarIncm).not.toHaveBeenCalled();
    });

    it('sem opções nem valor gravado, «Alteração PEOT\'s» não aparece', () => {
      abrir({ ...VALIDADO, alteracaoPeotId: null, alteracaoPeotDescricao: null, opcoesAlteracaoPeot: [] });

      expect(el('[data-campo="alteracao-peot"]')).toBeNull();
    });
  });

  describe('acessibilidade', () => {
    it('cada campo tem rótulo associado e o Sim/Não é um fieldset com legend', () => {
      abrir();

      for (const id of ['resumo-notasIncm', 'resumo-notasDgt', 'resumo-alteracao-peot']) {
        expect(el(`label[for="${id}"]`), id).not.toBeNull();
      }
      const grupo = el('fieldset[data-campo="reg-uso-solo"]');
      expect(grupo?.querySelector('legend')?.textContent?.trim()).toBe('Reg. Uso do Solo');
      expect(grupo?.querySelectorAll('label input[type="radio"]')).toHaveLength(2);
    });

    it('as mensagens de sucesso são anunciadas e os erros vão num alerta', () => {
      abrir();
      clicar(botoes('Gravar')[0]);

      expect(el('.sucesso')?.getAttribute('role')).toBe('status');
      expect(el('.mensagens')?.getAttribute('aria-live')).toBe('polite');
    });

    it('os botões do rodapé seguem a ordem visual: «Enviar PCM» e depois «Publicar»', () => {
      abrir({ estado: 'VALIDADO_DGT', acoesDisponiveis: ['enviar-incm', 'enviar-pcm'] });

      const todos = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
      const indice = (rotulo: string): number => todos.findIndex((b) => b.textContent?.trim() === rotulo);
      expect(indice('Enviar PCM')).toBeLessThan(indice('Publicar'));
      expect(todos.every((b) => b.getAttribute('tabindex') !== '-1')).toBe(true);
    });
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EnviarPcmComponent } from './enviar-pcm.component';
import { ApiError } from '../../../core/models/error-response.model';
import { Processo, ProcessoDetalhe } from '../../../models/processo.model';
import { Utilizador } from '../../../models/utilizador.model';
import { EntidadeService } from '../../../services/entidade.service';
import { ProcessoService } from '../../../services/processo.service';
import { UtilizadorService } from '../../../services/utilizador.service';

const PROCESSO = { id: 'p1', versao: 3, estado: 'VALIDADO_DGT', acoesDisponiveis: ['enviar-pcm'] } as ProcessoDetalhe;

function utilizador(id: string, nome: string, email: string): Utilizador {
  return { id, nome, email, origemAutenticacao: 'INCM', perfilInterno: null, cargo: null, entidades: [], ultimoLoginEm: null };
}

function pagina<T>(content: T[]) {
  return of({ content, page: 0, size: 100, totalElements: content.length, totalPages: 1 });
}

describe('EnviarPcmComponent', () => {
  let fixture: ComponentFixture<EnviarPcmComponent>;
  let listarEntidades: ReturnType<typeof vi.fn>;
  let listarUtilizadores: ReturnType<typeof vi.fn>;
  let enviarPcm: ReturnType<typeof vi.fn>;

  function montar(): void {
    fixture = TestBed.createComponent(EnviarPcmComponent);
    fixture.componentRef.setInput('processo', PROCESSO);
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();
  }

  function select(): HTMLSelectElement {
    return fixture.nativeElement.querySelector('app-seletor[data-campo="utilizador-pcm"] select') as HTMLSelectElement;
  }

  function opcoes(): string[] {
    return Array.from(select().options)
      .slice(1)
      .map((o) => o.textContent?.trim() ?? '');
  }

  function placeholder(): string {
    return select().options[0].textContent?.trim() ?? '';
  }

  function botao(texto: string): HTMLButtonElement | undefined {
    return Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLElement).textContent?.trim() === texto,
    ) as HTMLButtonElement | undefined;
  }

  function escolher(id: string): void {
    select().value = id;
    select().dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function confirmar(): void {
    botao('Confirmar')!.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    listarEntidades = vi.fn().mockReturnValue(pagina([{ id: 90 }, { id: 91 }]));
    listarUtilizadores = vi.fn().mockImplementation(({ entidadeId }: { entidadeId: number }) =>
      entidadeId === 90
        ? pagina([utilizador('u-rita', 'Rita Paiva', 'rita.paiva@pcm.exemplo.pt'), utilizador('u-joao', 'João Lemos', 'joao.lemos@pcm.exemplo.pt')])
        : pagina([utilizador('u-joao', 'João Lemos', 'joao.lemos@pcm.exemplo.pt'), utilizador('u-ines', 'Inês Mota', 'ines.mota@pcm.exemplo.pt')]),
    );
    enviarPcm = vi.fn().mockReturnValue(of({ id: 'p1', estado: 'VALIDADO_DGT', versao: 4, pcmAtribuidoAId: 'u-rita' }));
    TestBed.configureTestingModule({
      imports: [EnviarPcmComponent],
      providers: [
        { provide: EntidadeService, useValue: { listar: listarEntidades } },
        { provide: UtilizadorService, useValue: { listar: listarUtilizadores } },
        { provide: ProcessoService, useValue: { enviarPcm } },
      ],
    });
  });

  it('fechado não mostra nada nem lê os candidatos', () => {
    fixture = TestBed.createComponent(EnviarPcmComponent);
    fixture.componentRef.setInput('processo', PROCESSO);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(listarEntidades).not.toHaveBeenCalled();
  });

  it('lista os utilizadores das duas entidades PCM ativas, sem repetidos, como «nome · email»', () => {
    montar();

    expect(fixture.nativeElement.querySelector('.modal-titulo')?.textContent).toContain('Enviar PCM');
    expect(listarEntidades).toHaveBeenCalledExactlyOnceWith({ tipoEntidade: 'PCM', ativo: true }, 0, 100);
    expect(listarUtilizadores).toHaveBeenCalledWith({ entidadeId: 90 }, 0, 100);
    expect(listarUtilizadores).toHaveBeenCalledWith({ entidadeId: 91 }, 0, 100);
    expect(opcoes()).toEqual([
      'Rita Paiva · rita.paiva@pcm.exemplo.pt',
      'João Lemos · joao.lemos@pcm.exemplo.pt',
      'Inês Mota · ines.mota@pcm.exemplo.pt',
    ]);
    expect(placeholder()).toBe('Selecione…');
    expect(select().disabled).toBe(false);
  });

  it('enquanto carrega mostra «A carregar…» e o Confirmar fica desativado', () => {
    listarEntidades.mockReturnValue(new Subject());
    montar();

    expect(placeholder()).toBe('A carregar…');
    expect(select().disabled).toBe(true);
    expect(botao('Confirmar')!.disabled).toBe(true);
  });

  it.each([
    ['sem entidades PCM', () => listarEntidades.mockReturnValue(pagina([]))],
    ['com entidades PCM sem utilizadores', () => listarUtilizadores.mockReturnValue(pagina([]))],
  ])('%s o seletor fica desativado e o aviso é anunciado', (_caso, preparar) => {
    preparar();
    montar();

    expect(select().disabled).toBe(true);
    const aviso = fixture.nativeElement.querySelector('app-alerta') as HTMLElement;
    expect(aviso.textContent).toContain('Não há utilizadores da PCM disponíveis.');
    expect(aviso.getAttribute('role')).toBe('status');
    expect(aviso.parentElement?.getAttribute('aria-live')).toBe('polite');
    expect(select().getAttribute('aria-describedby')).toContain(aviso.id);
    expect(botao('Confirmar')!.disabled).toBe(true);
  });

  it.each([
    ['as entidades respondem 403', () => listarEntidades.mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'Sem permissão.', 't1', null)))],
    ['os utilizadores falham', () => listarUtilizadores.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Erro.', 't1', null)))],
  ])('quando %s o seletor mostra «Indisponível» e o Confirmar fica desativado', (_caso, preparar) => {
    preparar();
    montar();

    expect(placeholder()).toBe('Indisponível');
    expect(select().disabled).toBe(true);
    expect(botao('Confirmar')!.disabled).toBe(true);
    expect(botao('Tentar novamente')).toBeDefined();
    expect(fixture.nativeElement.querySelector('app-alerta')).toBeNull();
  });

  it('«Tentar novamente» volta a ler os candidatos', () => {
    listarEntidades.mockReturnValueOnce(throwError(() => new ApiError('ERRO_INTERNO', 'Erro.', 't1', null)));
    montar();

    botao('Tentar novamente')!.click();
    fixture.detectChanges();

    expect(listarEntidades).toHaveBeenCalledTimes(2);
    expect(opcoes()).toHaveLength(3);
  });

  it('o Confirmar só fica ativo com um utilizador escolhido e sem pedido em curso', () => {
    const resposta = new Subject<Processo>();
    enviarPcm.mockReturnValue(resposta);
    montar();
    expect(botao('Confirmar')!.disabled).toBe(true);

    escolher('u-ines');
    expect(botao('Confirmar')!.disabled).toBe(false);

    confirmar();
    expect(botao('A processar…')!.disabled).toBe(true);
    botao('A processar…')!.click();
    expect(enviarPcm).toHaveBeenCalledOnce();
  });

  it('Confirmar envia o PATCH com a versão e o utilizador e emite a resposta', () => {
    montar();
    const alterado = vi.fn();
    fixture.componentInstance.processoAlterado.subscribe(alterado);

    escolher('u-rita');
    confirmar();

    expect(enviarPcm).toHaveBeenCalledExactlyOnceWith('p1', 3, 'u-rita');
    expect(alterado).toHaveBeenCalledWith({ id: 'p1', estado: 'VALIDADO_DGT', versao: 4, pcmAtribuidoAId: 'u-rita' });
  });

  it.each([
    ['PCM_JA_ATRIBUIDO', 'O processo já foi enviado à PCM.'],
    ['VERSAO_DESATUALIZADA', 'O processo foi alterado entretanto. Recarregue.'],
    ['PCM_DOCUMENTOS_POR_VALIDAR', 'Há documentos por validar.'],
  ])('um 409 %s mostra a mensagem do backend e oferece Recarregar, sem repetir o pedido', (codigo, mensagem) => {
    enviarPcm.mockReturnValue(throwError(() => new ApiError(codigo, mensagem, 't1', null)));
    montar();
    const recarregar = vi.fn();
    fixture.componentInstance.recarregar.subscribe(recarregar);

    escolher('u-rita');
    confirmar();

    expect(fixture.nativeElement.querySelector('app-error-banner')?.textContent).toContain(mensagem);
    expect(botao('Confirmar')!.disabled).toBe(true);
    botao('Confirmar')!.click();
    expect(enviarPcm).toHaveBeenCalledOnce();

    botao('Recarregar')!.click();
    expect(recarregar).toHaveBeenCalledOnce();
    expect(enviarPcm).toHaveBeenCalledOnce();
  });

  it('um 400 PCM_UTILIZADOR_INVALIDO fica junto ao seletor, ligado por aria-describedby, e não no banner', () => {
    const mensagem = 'O utilizador indicado não pertence à PCM.';
    enviarPcm.mockReturnValue(
      throwError(() => new ApiError('PCM_UTILIZADOR_INVALIDO', mensagem, 't1', [{ path: 'utilizadorId', erro: mensagem }])),
    );
    montar();

    escolher('u-rita');
    confirmar();

    const erro = fixture.nativeElement.querySelector('app-seletor .seletor-erro') as HTMLElement;
    expect(erro.textContent).toContain(mensagem);
    expect(select().getAttribute('aria-describedby')).toContain(erro.id);
    expect(select().getAttribute('aria-invalid')).toBe('true');
    expect(erro.parentElement?.getAttribute('aria-live')).toBe('polite');
    expect(fixture.nativeElement.querySelector('app-error-banner')?.textContent ?? '').not.toContain(mensagem);
    expect(botao('Recarregar')).toBeUndefined();

    escolher('u-ines');
    expect(fixture.nativeElement.querySelector('app-seletor .seletor-erro')).toBeNull();
    expect(botao('Confirmar')!.disabled).toBe(false);
  });

  it('um 403 mostra o banner com a mensagem do backend', () => {
    const mensagem = 'Só a DGT (MASTER ou TECNICO) pode enviar processos à PCM.';
    enviarPcm.mockReturnValue(throwError(() => new ApiError('PCM_ATOR_NAO_AUTORIZADO', mensagem, 't1', null)));
    montar();

    escolher('u-rita');
    confirmar();

    expect(fixture.nativeElement.querySelector('app-error-banner')?.textContent).toContain(mensagem);
    expect(botao('Recarregar')).toBeUndefined();
  });

  it('o seletor tem rótulo associado e vem antes de Cancelar e Confirmar, pela ordem visual', () => {
    montar();

    const rotulo = fixture.nativeElement.querySelector('app-seletor label') as HTMLLabelElement;
    expect(rotulo.textContent).toContain('Utilizador da PCM');
    expect(rotulo.htmlFor).toBe(select().id);
    expect(select().required).toBe(true);
    const focaveis = Array.from(fixture.nativeElement.querySelectorAll('.modal-corpo select, .modal-rodape button')).map(
      (el) => (el as HTMLElement).tagName + ':' + ((el as HTMLElement).textContent?.trim() ?? ''),
    );
    expect(focaveis[0].startsWith('SELECT')).toBe(true);
    expect(focaveis.slice(1)).toEqual(['BUTTON:Cancelar', 'BUTTON:Confirmar']);
  });

  it('reabrir recomeça sem escolha e lê os candidatos de novo; Cancelar emite fechar', () => {
    montar();
    const fechar = vi.fn();
    fixture.componentInstance.fechar.subscribe(fechar);
    escolher('u-rita');

    botao('Cancelar')!.click();
    expect(fechar).toHaveBeenCalledOnce();

    fixture.componentRef.setInput('aberto', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();

    expect(listarEntidades).toHaveBeenCalledTimes(2);
    expect(select().value).toBe('');
    expect(botao('Confirmar')!.disabled).toBe(true);
  });
});

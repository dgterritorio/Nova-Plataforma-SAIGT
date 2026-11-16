import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { Mock, beforeEach, describe, expect, it, vi } from 'vitest';
import { ItemARejeitar, RejeitarItemModalComponent } from './rejeitar-item-modal.component';
import { ApiError } from '../../../core/models/error-response.model';
import { ListaValoresItem } from '../../../models/lista-valores.model';
import { ProcessoDetalhe, ValidacaoItensResponse } from '../../../models/processo.model';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { ProcessoService } from '../../../services/processo.service';

function valor(id: number, tipo: string, descricao: string, ativo = true): ListaValoresItem {
  return { id, tipo, codigo: `C${id}`, descricao, ordem: id, ativo };
}

const CATEGORIAS = [valor(1, 'CATEGORIA_REJEICAO_DGT', 'Peça escrita'), valor(2, 'CATEGORIA_REJEICAO_DGT', 'Antiga', false)];
const MOTIVOS = [valor(5, 'MOTIVO_REJEICAO_DGT', 'Ilegível')];

const ITEM: ItemARejeitar = { tipo: 'DOCUMENTO', itemId: 'd-pub', nome: 'Planta_Condicionante.TIFF' };

const RESPOSTA: ValidacaoItensResponse = {
  itens: [
    {
      tipo: 'DOCUMENTO',
      itemId: 'd-pub',
      estadoValidacao: 'REJEITADO',
      rejeicao: { categoriaId: 1, motivoId: 5, notas: 'Página 3 ilegível.' },
    },
  ],
  todosRevistos: true,
};

describe('RejeitarItemModalComponent', () => {
  let fixture: ComponentFixture<RejeitarItemModalComponent>;
  let listar: ReturnType<typeof vi.fn>;
  let gravarValidacaoItens: ReturnType<typeof vi.fn>;
  let rejeitado: Mock<(resposta: ValidacaoItensResponse) => void>;
  let recarregar: Mock<() => void>;

  function listas(categorias: ListaValoresItem[], motivos: ListaValoresItem[]): void {
    listar.mockImplementation((tipo: string) => of(tipo === 'CATEGORIA_REJEICAO_DGT' ? categorias : motivos));
  }

  function abrir(): void {
    fixture = TestBed.createComponent(RejeitarItemModalComponent);
    fixture.componentRef.setInput('processo', { id: 'p1', versao: 3 } as ProcessoDetalhe);
    fixture.componentRef.setInput('item', ITEM);
    fixture.componentInstance.rejeitado.subscribe(rejeitado);
    fixture.componentInstance.recarregar.subscribe(recarregar);
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();
  }

  function elemento<T extends Element>(seletor: string): T | null {
    return (fixture.nativeElement as HTMLElement).querySelector<T>(seletor);
  }

  function texto(): string {
    return elemento('.modal-caixa')?.textContent ?? '';
  }

  function botao(rotulo: string): HTMLButtonElement | undefined {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.modal-caixa button')).find(
      (b) => b.textContent?.trim() === rotulo,
    );
  }

  function confirmarAtivo(): boolean {
    return !botao('Confirmar Rejeição')?.disabled;
  }

  function escolher(campo: string, valorEscolhido: string): void {
    const select = elemento<HTMLSelectElement>(`[data-campo="${campo}"] select`)!;
    select.value = valorEscolhido;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function escreverNotas(notas: string): void {
    const area = elemento<HTMLTextAreaElement>('[data-campo="notas"] textarea')!;
    area.value = notas;
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function preencher(): void {
    escolher('categoria', '1');
    escolher('motivo', '5');
    escreverNotas('  Página 3 ilegível.  ');
  }

  beforeEach(() => {
    listar = vi.fn();
    listas(CATEGORIAS, MOTIVOS);
    gravarValidacaoItens = vi.fn().mockReturnValue(of(RESPOSTA));
    rejeitado = vi.fn<(resposta: ValidacaoItensResponse) => void>();
    recarregar = vi.fn<() => void>();
    TestBed.configureTestingModule({
      providers: [
        { provide: ProcessoService, useValue: { gravarValidacaoItens } },
        { provide: ListaValoresService, useValue: { listar } },
      ],
    });
  });

  it('mostra o título, o subtítulo e o nome do item, como no UI-035', () => {
    abrir();

    expect(texto()).toContain('Rejeitar pedido - Seleção de Documentos');
    expect(texto()).toContain('Indique quais os documentos a rejeitar, o motivo e notas adicionais para a Câmara Municipal.');
    expect(elemento('[data-campo="nome-item"]')?.textContent?.trim()).toBe('Planta_Condicionante.TIFF');
  });

  it('os seletores só têm os valores ativos de cada lista', () => {
    abrir();

    const opcoes = Array.from(elemento<HTMLSelectElement>('[data-campo="categoria"] select')!.options).map((o) => o.text);
    expect(opcoes).toEqual(['Selecione…', 'Peça escrita']);
    expect(listar).toHaveBeenCalledWith('CATEGORIA_REJEICAO_DGT');
    expect(listar).toHaveBeenCalledWith('MOTIVO_REJEICAO_DGT');
  });

  it('«Confirmar Rejeição» só fica ativo com categoria, motivo e notas', () => {
    abrir();
    expect(confirmarAtivo()).toBe(false);

    escolher('categoria', '1');
    escolher('motivo', '5');
    expect(confirmarAtivo()).toBe(false);

    escreverNotas('   ');
    expect(confirmarAtivo()).toBe(false);

    escreverNotas('Página 3 ilegível.');
    expect(confirmarAtivo()).toBe(true);
  });

  it('confirmar grava REJEITADO com os três campos e as notas sem espaços nas pontas, e emite a resposta', () => {
    abrir();
    preencher();

    botao('Confirmar Rejeição')!.click();
    fixture.detectChanges();

    expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
      { tipo: 'DOCUMENTO', itemId: 'd-pub', decisao: 'REJEITADO', categoriaId: 1, motivoId: 5, notas: 'Página 3 ilegível.' },
    ]);
    expect(rejeitado).toHaveBeenCalledExactlyOnceWith(RESPOSTA);
  });

  it('enquanto grava, o botão diz «A processar…» e os campos ficam desativados', () => {
    gravarValidacaoItens.mockReturnValue(new Subject<ValidacaoItensResponse>());
    abrir();
    preencher();

    botao('Confirmar Rejeição')!.click();
    fixture.detectChanges();

    expect(botao('A processar…')?.disabled).toBe(true);
    expect(elemento<HTMLTextAreaElement>('[data-campo="notas"] textarea')!.disabled).toBe(true);
  });

  it('com as duas listas vazias, os seletores não aparecem e a rejeição confirma-se só com as notas', () => {
    listas([valor(2, 'CATEGORIA_REJEICAO_DGT', 'Antiga', false)], []);
    abrir();

    expect(elemento('[data-campo="categoria"]')).toBeNull();
    expect(elemento('[data-campo="motivo"]')).toBeNull();
    expect(confirmarAtivo()).toBe(false);

    escreverNotas('Ficheiro corrompido.');
    expect(confirmarAtivo()).toBe(true);
    botao('Confirmar Rejeição')!.click();

    expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
      { tipo: 'DOCUMENTO', itemId: 'd-pub', decisao: 'REJEITADO', categoriaId: null, motivoId: null, notas: 'Ficheiro corrompido.' },
    ]);
  });

  it('só com a lista de categorias vazia, pede o motivo e as notas', () => {
    listas([], MOTIVOS);
    abrir();

    expect(elemento('[data-campo="categoria"]')).toBeNull();
    escreverNotas('Notas.');
    expect(confirmarAtivo()).toBe(false);
    escolher('motivo', '5');
    expect(confirmarAtivo()).toBe(true);
  });

  it('um 400 na categoria mostra a mensagem junto do campo, limpa a escolha e relê as listas', () => {
    gravarValidacaoItens.mockReturnValue(
      throwError(
        () =>
          new ApiError('VALIDACAO', 'Itens de validação inválidos.', 't1', [
            { path: 'itens[0].categoriaId', erro: 'Não existe ou não é um valor ativo de CATEGORIA_REJEICAO_DGT.' },
          ]),
      ),
    );
    abrir();
    preencher();
    expect(listar).toHaveBeenCalledTimes(2);

    botao('Confirmar Rejeição')!.click();
    fixture.detectChanges();

    const select = elemento<HTMLSelectElement>('[data-campo="categoria"] select')!;
    const idErro = select.getAttribute('aria-describedby')!;
    expect(document.getElementById(idErro)?.textContent).toContain('Não existe ou não é um valor ativo');
    expect(select.value).toBe('');
    expect(listar).toHaveBeenCalledTimes(4);
    expect(confirmarAtivo()).toBe(false);
    expect(rejeitado).not.toHaveBeenCalled();
  });

  it('um 400 nas notas fica junto das notas, ligado por aria-describedby', () => {
    gravarValidacaoItens.mockReturnValue(
      throwError(
        () =>
          new ApiError('VALIDACAO', 'Itens de validação inválidos.', 't1', [
            { path: 'itens[0].notas', erro: 'Indique a categoria, o motivo e as notas da rejeição.' },
          ]),
      ),
    );
    abrir();
    preencher();

    botao('Confirmar Rejeição')!.click();
    fixture.detectChanges();

    const area = elemento<HTMLTextAreaElement>('[data-campo="notas"] textarea')!;
    expect(area.getAttribute('aria-invalid')).toBe('true');
    expect(document.getElementById(area.getAttribute('aria-describedby')!)?.textContent).toContain(
      'Indique a categoria, o motivo e as notas da rejeição.',
    );
  });

  it('um 409 mostra a mensagem do backend e «Recarregar», sem repetir o pedido', () => {
    gravarValidacaoItens.mockReturnValue(
      throwError(() => new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado entretanto.', 't1', null)),
    );
    abrir();
    preencher();

    botao('Confirmar Rejeição')!.click();
    fixture.detectChanges();

    expect(texto()).toContain('O processo foi alterado entretanto.');
    expect(confirmarAtivo()).toBe(false);
    botao('Recarregar')!.click();
    expect(recarregar).toHaveBeenCalledOnce();
    expect(gravarValidacaoItens).toHaveBeenCalledOnce();
  });

  it('se as listas não carregam, mostra o erro e «Tentar de novo»', () => {
    listar.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Serviço indisponível.', 't1', null)));
    abrir();

    expect(texto()).toContain('Serviço indisponível.');
    expect(confirmarAtivo()).toBe(false);

    listas(CATEGORIAS, MOTIVOS);
    botao('Tentar de novo')!.click();
    fixture.detectChanges();
    expect(elemento('[data-campo="categoria"]')).not.toBeNull();
  });

  it('cada campo tem rótulo associado e o diálogo tem nome acessível', () => {
    abrir();

    for (const campo of ['categoria', 'motivo']) {
      const select = elemento<HTMLSelectElement>(`[data-campo="${campo}"] select`)!;
      expect(elemento(`label[for="${select.id}"]`)).not.toBeNull();
    }
    const area = elemento<HTMLTextAreaElement>('[data-campo="notas"] textarea')!;
    expect(elemento(`label[for="${area.id}"]`)?.textContent).toContain('Notas');
    const dialogo = elemento('[role="dialog"]')!;
    expect(document.getElementById(dialogo.getAttribute('aria-labelledby')!)?.textContent).toBe(
      'Rejeitar pedido - Seleção de Documentos',
    );
  });

  it('ao abrir de novo recomeça sem as escolhas anteriores', () => {
    abrir();
    preencher();

    fixture.componentRef.setInput('aberto', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();

    expect(elemento<HTMLTextAreaElement>('[data-campo="notas"] textarea')!.value).toBe('');
    expect(confirmarAtivo()).toBe(false);
  });
});

import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { CatalogoTipos, TipoConfig, TipoConfigRequest } from '../../../models/configuracao.model';
import { TipoConfigModalComponent } from './tipo-config-modal.component';

@Component({
  imports: [TipoConfigModalComponent],
  template: `
    <app-tipo-config-modal
      [aberto]="aberto()"
      [catalogo]="catalogo()"
      [tipo]="tipo()"
      [erroServidor]="erro()"
      (guardar)="pedidos.push($event)"
      (fechar)="fechos = fechos + 1"
    />
  `,
})
class AnfitriaoComponent {
  readonly aberto = signal(true);
  readonly catalogo = signal<CatalogoTipos>('instrumento');
  readonly tipo = signal<TipoConfig | null>(null);
  readonly erro = signal<ApiError | null>(null);
  readonly pedidos: TipoConfigRequest[] = [];
  fechos = 0;
}

describe('TipoConfigModalComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  function criar(catalogo: CatalogoTipos, tipo: TipoConfig | null = null): AnfitriaoComponent {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.componentInstance.catalogo.set(catalogo);
    fixture.componentInstance.tipo.set(tipo);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const dialogo = (): HTMLElement => document.querySelector('[role="dialog"]') as HTMLElement;
  const campo = (rotulo: string): HTMLInputElement & HTMLSelectElement => {
    const label = [...dialogo().querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return document.getElementById(label.htmlFor) as HTMLInputElement & HTMLSelectElement;
  };
  const erroDe = (entrada: HTMLElement): string | null => {
    const ids = (entrada.getAttribute('aria-describedby') ?? '').split(' ').filter((id) => id.endsWith('-erro'));
    return ids.length ? (document.getElementById(ids[0])?.textContent?.trim() ?? null) : null;
  };
  const botaoGuardar = (): HTMLButtonElement =>
    [...dialogo().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'Guardar')!;

  function escrever(entrada: HTMLInputElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function escolher(seletor: HTMLSelectElement, valor: string): void {
    seletor.value = valor;
    seletor.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('código "alteracao x": mostra o erro no campo Código, o Guardar fica desativado e nada é emitido', () => {
    const anfitriao = criar('dinamica');
    escrever(campo('Código'), 'alteracao x');
    escrever(campo('Nome'), 'Alteração X');

    expect(erroDe(campo('Código'))).toContain('maiúsculas');
    expect(campo('Código').getAttribute('aria-invalid')).toBe('true');
    expect(botaoGuardar().disabled).toBe(true);
    dialogo().querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(anfitriao.pedidos).toEqual([]);
  });

  it('código com mais de 20 caracteres no Instrumento é inválido; 40 nos outros catálogos', () => {
    criar('instrumento');
    expect(campo('Código').getAttribute('maxlength')).toBe('20');
    TestBed.resetTestingModule();
    criar('procedimento');
    expect(campo('Código').getAttribute('maxlength')).toBe('40');
    escrever(campo('Código'), 'A'.repeat(41));
    expect(erroDe(campo('Código'))).toBe('Até 40 caracteres.');
  });

  it('categoria só existe e só é obrigatória no catálogo de Instrumento', () => {
    const anfitriao = criar('instrumento');
    escrever(campo('Código'), 'PIOT');
    escrever(campo('Nome'), 'Plano Intermunicipal');
    expect(botaoGuardar().disabled).toBe(true);
    escolher(campo('Categoria'), 'PLANO');
    expect(botaoGuardar().disabled).toBe(false);
    botaoGuardar().click();
    expect(anfitriao.pedidos).toEqual([{ codigo: 'PIOT', nome: 'Plano Intermunicipal', ativo: true, categoria: 'PLANO' }]);

    TestBed.resetTestingModule();
    const outro = criar('dinamica');
    expect([...dialogo().querySelectorAll('label')].some((l) => l.textContent?.includes('Categoria'))).toBe(false);
    escrever(campo('Código'), 'ALTERACAO_X');
    escrever(campo('Nome'), 'Alteração X');
    botaoGuardar().click();
    expect(outro.pedidos).toEqual([{ codigo: 'ALTERACAO_X', nome: 'Alteração X', ativo: true }]);
  });

  it('no catálogo de Procedimento, ao criar, avisa que o tipo novo fica sem formulário; ao editar não', () => {
    criar('procedimento');
    expect(dialogo().textContent).toContain('Um tipo de Procedimento novo só tem formulário depois de desenvolvimento na aplicação.');
    TestBed.resetTestingModule();
    criar('procedimento', { id: 1, codigo: 'DECISAO_INICIO', nome: 'Decisão de início', ativo: true, formulario: 'INFORMACAO_GERAL' });
    expect(dialogo().textContent).not.toContain('Um tipo de Procedimento novo');
  });

  it('ao editar, preenche a linha e mantém o ativo dela', () => {
    const anfitriao = criar('instrumento', { id: 3, codigo: 'PROT', nome: 'Programa Regional', categoria: 'PROGRAMA', ativo: false });
    expect(dialogo().querySelector('h2')?.textContent).toContain('Editar tipo');
    expect(campo('Código').value).toBe('PROT');
    expect(campo('Categoria').value).toBe('PROGRAMA');
    botaoGuardar().click();
    expect(anfitriao.pedidos).toEqual([{ codigo: 'PROT', nome: 'Programa Regional', ativo: false, categoria: 'PROGRAMA' }]);
  });

  it('400 com campos[0].path = "codigo": o erro aparece no campo Código e sai quando o código muda', () => {
    const anfitriao = criar('dinamica');
    escrever(campo('Código'), 'ALTERACAO');
    escrever(campo('Nome'), 'Alteração');
    anfitriao.erro.set(new ApiError('VALIDACAO', 'Pedido inválido.', 't', [{ path: 'codigo', erro: 'Já existe um tipo com este código.' }]));
    fixture.detectChanges();

    expect(erroDe(campo('Código'))).toBe('Já existe um tipo com este código.');
    expect(dialogo().querySelector('app-alerta[role="alert"]')).toBeNull();
    escrever(campo('Código'), 'ALTERACAO_2');
    expect(erroDe(campo('Código'))).toBeNull();
  });

  it('409 EM_USO: a mensagem do envelope aparece num alerta de erro e o formulário mantém os valores', () => {
    const anfitriao = criar('dinamica', { id: 1, codigo: 'ALTERACAO', nome: 'Alteração', ativo: true });
    escrever(campo('Código'), 'ALTERACAO_NOVA');
    anfitriao.erro.set(new ApiError('EM_USO', 'O código está em uso e não pode mudar.', 't', null));
    fixture.detectChanges();

    const alerta = dialogo().querySelector('app-alerta[role="alert"]');
    expect(alerta?.textContent).toContain('O código está em uso e não pode mudar.');
    expect(campo('Código').value).toBe('ALTERACAO_NOVA');
    expect(anfitriao.fechos).toBe(0);
  });

  it('Cancelar emite fechar', () => {
    const anfitriao = criar('dinamica');
    [...dialogo().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'Cancelar')!.click();
    expect(anfitriao.fechos).toBe(1);
  });
});

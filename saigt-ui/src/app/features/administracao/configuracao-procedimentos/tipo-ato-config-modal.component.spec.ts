import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { TipoAtoConfig, TipoAtoConfigRequest } from '../../../models/configuracao.model';
import { ERRO_SEM_SERIE, TipoAtoConfigModalComponent } from './tipo-ato-config-modal.component';

const AVISO: TipoAtoConfig = { id: 4, codigo: 'AVISO', nome: 'Aviso', incmActTypeIdSerie1: null, incmActTypeIdSerie2: 10, ordem: 3, ativo: false };

@Component({
  imports: [TipoAtoConfigModalComponent],
  template: `<app-tipo-ato-config-modal [aberto]="true" [tipoAto]="tipoAto()" [erroServidor]="erro()" (guardar)="valores.push($event)" />`,
})
class AnfitriaoComponent {
  readonly tipoAto = signal<TipoAtoConfig | null>(null);
  readonly erro = signal<ApiError | null>(null);
  readonly valores: TipoAtoConfigRequest[] = [];
}

describe('TipoAtoConfigModalComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  function criar(tipoAto: TipoAtoConfig | null = null): AnfitriaoComponent {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.componentInstance.tipoAto.set(tipoAto);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const dialogo = (): HTMLElement => document.querySelector('[role="dialog"]') as HTMLElement;
  const campo = (rotulo: string): HTMLInputElement => {
    const label = [...dialogo().querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return document.getElementById(label.htmlFor) as HTMLInputElement;
  };
  const descricao = (entrada: HTMLElement): string =>
    (entrada.getAttribute('aria-describedby') ?? '')
      .split(' ')
      .filter(Boolean)
      .map((id) => document.getElementById(id)?.textContent?.trim())
      .join(' | ');
  const guardar = (): void => {
    [...dialogo().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'Guardar')!.click();
    fixture.detectChanges();
  };

  function escrever(rotulo: string, valor: string): void {
    const entrada = campo(rotulo);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('sem nenhuma série: "Indique pelo menos uma série." ligado aos dois campos, anunciado, e nada é emitido', () => {
    const anfitriao = criar();
    escrever('Código', 'DELIB');
    escrever('Nome', 'Deliberação');
    escrever('Ordem', '1');
    expect(dialogo().textContent).not.toContain(ERRO_SEM_SERIE);

    guardar();

    expect(anfitriao.valores).toEqual([]);
    expect(descricao(campo('ActTypeId da INCM — 1.ª série'))).toContain('Indique pelo menos uma série.');
    expect(descricao(campo('ActTypeId da INCM — 2.ª série'))).toContain('Indique pelo menos uma série.');
    expect(campo('ActTypeId da INCM — 1.ª série').getAttribute('aria-invalid')).toBe('true');
    expect(dialogo().querySelector('p[role="alert"]')?.textContent?.trim()).toBe('Indique pelo menos uma série.');

    escrever('ActTypeId da INCM — 2.ª série', '45');
    expect(dialogo().textContent).not.toContain(ERRO_SEM_SERIE);
    guardar();
    expect(anfitriao.valores).toEqual([
      { codigo: 'DELIB', nome: 'Deliberação', incmActTypeIdSerie1: null, incmActTypeIdSerie2: 45, ordem: 1, ativo: true },
    ]);
  });

  it('código com minúsculas ou acima de 10, série 0 e ordem vazia dão erro no campo', () => {
    const anfitriao = criar();
    escrever('Código', 'delib');
    expect(descricao(campo('Código'))).toContain('Use só letras maiúsculas sem acentos, algarismos e _ (sem espaços).');
    escrever('Código', 'DELIBERACAO1');
    expect(descricao(campo('Código'))).toContain('Até 10 caracteres.');
    escrever('ActTypeId da INCM — 1.ª série', '0');
    expect(descricao(campo('ActTypeId da INCM — 1.ª série'))).toContain('Use um número inteiro igual ou superior a 1.');
    guardar();
    expect(descricao(campo('Ordem'))).toContain('Indique a ordem.');
    expect(descricao(campo('Nome'))).toContain('Indique o nome.');
    expect(anfitriao.valores).toEqual([]);
  });

  it('edição: preenche os campos, mantém o estado e grava a série 2 nova', () => {
    const anfitriao = criar(AVISO);
    expect(dialogo().querySelector('h2')?.textContent).toContain('Editar tipo de ato');
    expect(campo('ActTypeId da INCM — 1.ª série').value).toBe('');
    expect(campo('ActTypeId da INCM — 2.ª série').value).toBe('10');

    escrever('ActTypeId da INCM — 2.ª série', '4545');
    guardar();
    expect(anfitriao.valores).toEqual([
      { codigo: 'AVISO', nome: 'Aviso', incmActTypeIdSerie1: null, incmActTypeIdSerie2: 4545, ordem: 3, ativo: false },
    ]);
  });

  it('erros do servidor: por campo no campo, 409 no alerta do modal', () => {
    const anfitriao = criar(AVISO);
    anfitriao.erro.set(
      new ApiError('VALIDACAO', 'Pedido inválido.', 't', [
        { path: 'incmActTypeIdSerie1', erro: 'Indique pelo menos uma série.' },
        { path: 'codigo', erro: 'Já existe um registo com este código.' },
      ]),
    );
    fixture.detectChanges();
    expect(descricao(campo('Código'))).toContain('Já existe um registo com este código.');
    expect(descricao(campo('ActTypeId da INCM — 1.ª série'))).toContain('Indique pelo menos uma série.');

    anfitriao.erro.set(new ApiError('EM_USO', 'O código está em uso num processo.', 't', null));
    fixture.detectChanges();
    expect(dialogo().querySelector('app-alerta[role="alert"]')?.textContent).toContain('O código está em uso num processo.');
  });
});

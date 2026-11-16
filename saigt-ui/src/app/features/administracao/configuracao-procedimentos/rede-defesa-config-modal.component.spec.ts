import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { RedeDefesaConfig, RedeDefesaConfigRequest } from '../../../models/configuracao.model';
import { RedeDefesaConfigModalComponent } from './rede-defesa-config-modal.component';

const RVF: RedeDefesaConfig = { id: 1, codigo: 'RVF', nome: 'Rede viária florestal', aplicaPra: true, aplicaPsa: true, publicavel: true };

@Component({
  imports: [RedeDefesaConfigModalComponent],
  template: `<app-rede-defesa-config-modal [aberto]="true" [rede]="rede()" [erroServidor]="erro()" (guardar)="valores.push($event)" />`,
})
class AnfitriaoComponent {
  readonly rede = signal<RedeDefesaConfig | null>(null);
  readonly erro = signal<ApiError | null>(null);
  readonly valores: RedeDefesaConfigRequest[] = [];
}

describe('RedeDefesaConfigModalComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  function criar(rede: RedeDefesaConfig | null = null): AnfitriaoComponent {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.componentInstance.rede.set(rede);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const dialogo = (): HTMLElement => document.querySelector('[role="dialog"]') as HTMLElement;
  const etiqueta = (rotulo: string): HTMLLabelElement => [...dialogo().querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
  const campo = (rotulo: string): HTMLInputElement => {
    const label = etiqueta(rotulo);
    return (label.htmlFor ? document.getElementById(label.htmlFor) : label.querySelector('input')) as HTMLInputElement;
  };
  const botaoGuardar = (): HTMLButtonElement =>
    [...dialogo().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'Guardar')!;

  function escrever(rotulo: string, valor: string): void {
    const entrada = campo(rotulo);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('edição da RVF com "Aplica-se ao PSA" desmarcado emite aplicaPsa=false', () => {
    const anfitriao = criar(RVF);
    expect(dialogo().querySelector('h2')?.textContent).toContain('Editar rede');
    expect(campo('Aplica-se ao PRA').checked).toBe(true);
    expect(campo('Aplica-se ao PSA').checked).toBe(true);
    expect(campo('Publicável').checked).toBe(true);

    campo('Aplica-se ao PSA').click();
    fixture.detectChanges();
    botaoGuardar().click();
    fixture.detectChanges();

    expect(anfitriao.valores).toEqual([{ codigo: 'RVF', nome: 'Rede viária florestal', aplicaPra: true, aplicaPsa: false, publicavel: true }]);
  });

  it('criação: caixas nativas desmarcadas, cada uma com label; código obrigatório até 10 e nome obrigatório', () => {
    const anfitriao = criar();
    const caixas = dialogo().querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
    expect(caixas).toHaveLength(3);
    for (const caixa of caixas) {
      expect(caixa.checked).toBe(false);
      expect(caixa.closest('label')).not.toBeNull();
    }
    expect(botaoGuardar().disabled).toBe(true);

    escrever('Código', 'REDE_NOVA_12');
    expect(dialogo().textContent).toContain('Até 10 caracteres.');
    escrever('Código', 'RPN');
    escrever('Nome', ' Rede primária nova ');
    campo('Publicável').click();
    fixture.detectChanges();
    botaoGuardar().click();
    fixture.detectChanges();
    expect(anfitriao.valores).toEqual([{ codigo: 'RPN', nome: 'Rede primária nova', aplicaPra: false, aplicaPsa: false, publicavel: true }]);
  });

  it('erro 400 no código fica no campo; 409 no alerta', () => {
    const anfitriao = criar(RVF);
    anfitriao.erro.set(new ApiError('VALIDACAO', 'Pedido inválido.', 't', [{ path: 'codigo', erro: 'Já existe um registo com este código.' }]));
    fixture.detectChanges();
    expect(campo('Código').getAttribute('aria-invalid')).toBe('true');
    expect(dialogo().textContent).toContain('Já existe um registo com este código.');

    anfitriao.erro.set(new ApiError('EM_USO', 'A rede está escolhida num processo.', 't', null));
    fixture.detectChanges();
    expect(dialogo().querySelector('app-alerta[role="alert"]')?.textContent).toContain('A rede está escolhida num processo.');
  });
});

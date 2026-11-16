import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { CampoTextoComponent } from './campo-texto.component';

@Component({
  imports: [CampoTextoComponent, ReactiveFormsModule],
  template: `
    <app-campo-texto
      rotulo="Descritivo"
      ajuda="Até 50 caracteres."
      [obrigatorio]="true"
      [erro]="erro()"
      [multilinha]="multilinha()"
      [compacto]="compacto()"
      [ajudaAoLado]="ajudaAoLado()"
      [maxlength]="50"
      [formControl]="controlo"
    />
  `,
})
class AnfitriaoComponent {
  readonly erro = signal<string | null>(null);
  readonly multilinha = signal(false);
  readonly compacto = signal(false);
  readonly ajudaAoLado = signal(false);
  readonly controlo = new FormControl('');
}

function criar() {
  const fixture = TestBed.createComponent(AnfitriaoComponent);
  document.body.appendChild(fixture.nativeElement);
  fixture.detectChanges();
  const el: HTMLElement = fixture.nativeElement;
  return { fixture, el, anfitriao: fixture.componentInstance, campo: () => el.querySelector<HTMLInputElement>('input, textarea')! };
}

describe('CampoTextoComponent', () => {
  it('ao escrever, o valor chega ao FormControl', () => {
    const { campo, anfitriao } = criar();

    campo().value = 'Revisão do PDM';
    campo().dispatchEvent(new Event('input'));

    expect(anfitriao.controlo.value).toBe('Revisão do PDM');
  });

  it('mostra o valor do FormControl e respeita o desativado', () => {
    const { fixture, campo, anfitriao } = criar();

    anfitriao.controlo.setValue('Alteração');
    anfitriao.controlo.disable();
    fixture.detectChanges();

    expect(campo().value).toBe('Alteração');
    expect(campo().disabled).toBe(true);
  });

  it('o rótulo está ligado ao campo (clicar nele foca-o); o obrigatório e o maxlength chegam ao input', () => {
    const { el, campo } = criar();

    // O jsdom não ativa o campo ao clicar no rótulo; a ligação for/id é o que faz o browser fazê-lo.
    const rotulo = el.querySelector('label')!;
    expect(rotulo.htmlFor).toBe(campo().id);
    expect(campo().labels?.[0]).toBe(rotulo);
    expect(campo().required).toBe(true);
    expect(campo().getAttribute('maxlength')).toBe('50');
    el.remove();
  });

  it('a ajuda fica ligada por aria-describedby; com erro, junta a mensagem e aria-invalid', () => {
    const { fixture, el, campo, anfitriao } = criar();
    const ajuda = el.querySelector('.campo-texto-ajuda')!;
    expect(campo().getAttribute('aria-describedby')).toBe(ajuda.id);
    expect(campo().hasAttribute('aria-invalid')).toBe(false);

    anfitriao.erro.set('Indique o descritivo.');
    fixture.detectChanges();

    const erro = el.querySelector('.campo-texto-erro')!;
    expect(erro.textContent?.trim()).toBe('Indique o descritivo.');
    expect(campo().getAttribute('aria-describedby')).toBe(`${ajuda.id} ${erro.id}`);
    expect(campo().getAttribute('aria-invalid')).toBe('true');
    el.remove();
  });

  it('o erro aparece dentro de uma região aria-live que já existia sem erro, para ser anunciado', () => {
    const { fixture, el, anfitriao } = criar();
    const regiao = el.querySelector('[aria-live="polite"]')!;
    expect(regiao.textContent?.trim()).toBe('');

    anfitriao.erro.set('Indique o descritivo.');
    fixture.detectChanges();

    expect(el.querySelector('[aria-live="polite"]')).toBe(regiao);
    expect(regiao.querySelector('.campo-texto-erro')?.textContent?.trim()).toBe('Indique o descritivo.');
    el.remove();
  });

  it('multilinha usa uma textarea e compacto marca o host', () => {
    const { fixture, el, anfitriao } = criar();

    anfitriao.multilinha.set(true);
    anfitriao.compacto.set(true);
    fixture.detectChanges();

    expect(el.querySelector('textarea')).not.toBeNull();
    expect(el.querySelector('input')).toBeNull();
    expect(el.querySelector('app-campo-texto')?.classList).toContain('campo-texto-compacto');
    el.remove();
  });

  it('preenchido e rotuloMaiusculas marcam o host; autocomplete chega ao input', () => {
    const fixture = TestBed.createComponent(CampoTextoComponent);
    fixture.componentRef.setInput('rotulo', 'Email');
    fixture.componentRef.setInput('preenchido', true);
    fixture.componentRef.setInput('rotuloMaiusculas', true);
    fixture.componentRef.setInput('autocomplete', 'username');
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;

    expect(host.classList).toContain('campo-texto-preenchido');
    expect(host.classList).toContain('campo-texto-rotulo-maiusculas');
    expect(host.querySelector('input')?.getAttribute('autocomplete')).toBe('username');
  });

  it('projeta o elemento com o atributo sufixo dentro da caixa do campo', () => {
    @Component({
      imports: [CampoTextoComponent],
      template: `<app-campo-texto rotulo="Palavra-passe" tipo="password"><button sufixo type="button" aria-label="Mostrar">o</button></app-campo-texto>`,
    })
    class ComSufixoComponent {}

    const fixture = TestBed.createComponent(ComSufixoComponent);
    fixture.detectChanges();

    const botao = fixture.nativeElement.querySelector('.campo-texto-caixa .campo-texto-sufixo button');
    expect(botao?.getAttribute('aria-label')).toBe('Mostrar');
    expect(fixture.nativeElement.querySelector('input')?.type).toBe('password');
  });

  it('ajudaAoLado marca o host e mantém a ajuda ligada ao campo', () => {
    const { fixture, el, anfitriao, campo } = criar();
    anfitriao.ajudaAoLado.set(true);
    fixture.detectChanges();

    expect(el.querySelector('app-campo-texto')!.classList).toContain('campo-texto-ajuda-ao-lado');
    const ajuda = el.querySelector(`#${campo().getAttribute('aria-describedby')}`);
    expect(ajuda?.textContent?.trim()).toBe('Até 50 caracteres.');
  });

  it('marca o controlo como tocado ao sair do campo', () => {
    const { campo, anfitriao, el } = criar();

    campo().dispatchEvent(new Event('blur'));

    expect(anfitriao.controlo.touched).toBe(true);
    el.remove();
  });
});

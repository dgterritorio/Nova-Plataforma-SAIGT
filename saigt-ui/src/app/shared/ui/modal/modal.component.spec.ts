import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ModalComponent, VarianteModal } from './modal.component';

@Component({
  imports: [ModalComponent],
  template: `
    <button type="button" class="abrir" (click)="aberto.set(true)">Abrir</button>
    <app-modal
      titulo="Alterações"
      [subtitulo]="subtitulo()"
      [descritoPor]="descritoPor()"
      [aberto]="aberto()"
      [variante]="variante()"
      [fecharComFundo]="fecharComFundo()"
      (fechar)="fechos = fechos + 1; aberto.set(false)"
    >
      <p id="descricao-anfitriao">Texto que descreve o diálogo.</p>
      <input class="campo" aria-label="campo" />
      <div rodape>
        <button type="button" class="cancelar">Cancelar</button>
        <button type="button" class="confirmar">Confirmar</button>
      </div>
    </app-modal>
  `,
})
class AnfitriaoComponent {
  readonly aberto = signal(false);
  readonly variante = signal<VarianteModal>('normal');
  readonly fecharComFundo = signal(true);
  readonly subtitulo = signal<string | undefined>('Verifique antes de avançar.');
  readonly descritoPor = signal<string | undefined>(undefined);
  fechos = 0;
}

@Component({
  imports: [ModalComponent],
  template: `
    <button type="button" class="abrir" (click)="aberto.set(true)">Abrir</button>
    <app-modal titulo="Aviso" variante="confirmacao" [aberto]="aberto()" (fechar)="aberto.set(false)">
      <p>Texto sem campos nem botões.</p>
    </app-modal>
  `,
})
class AnfitriaoSemFocaveisComponent {
  readonly aberto = signal(false);
}

@Component({
  imports: [ModalComponent],
  template: `
    <button type="button" class="abrir" (click)="baixo.set(true)">Abrir</button>
    <app-modal titulo="Contactos" [aberto]="baixo()" (fechar)="baixo.set(false)">
      <button type="button" class="abrir-cima" (click)="cima.set(true)">Editar</button>
    </app-modal>
    <app-modal titulo="Editar contacto" [aberto]="cima()" (fechar)="cima.set(false)">
      <input class="campo-cima" aria-label="E-mail" />
    </app-modal>
  `,
})
class AnfitriaoEmPilhaComponent {
  readonly baixo = signal(false);
  readonly cima = signal(false);
}

const ALTURA_CORPO = 400;
let alturaConteudo = 0;

/** O jsdom não faz layout: a altura visível e a do conteúdo do corpo são simuladas. */
function simularAlturas(): void {
  vi.spyOn(Element.prototype, 'scrollHeight', 'get').mockImplementation(function (this: Element) {
    return this.classList.contains('modal-corpo') ? Math.max(alturaConteudo, ALTURA_CORPO) : 0;
  });
  vi.spyOn(Element.prototype, 'clientHeight', 'get').mockImplementation(function (this: Element) {
    return this.classList.contains('modal-corpo') ? ALTURA_CORPO : 0;
  });
}

class ResizeObserverFalso {
  static instancias: ResizeObserverFalso[] = [];
  readonly observados = new Set<Element>();

  constructor(private readonly aoMudar: ResizeObserverCallback) {
    ResizeObserverFalso.instancias.push(this);
  }

  observe(elemento: Element): void {
    this.observados.add(elemento);
  }

  unobserve(elemento: Element): void {
    this.observados.delete(elemento);
  }

  disconnect(): void {
    this.observados.clear();
  }

  disparar(): void {
    this.aoMudar([], this as unknown as ResizeObserver);
  }
}

function prepararScroll(): void {
  alturaConteudo = 0;
  ResizeObserverFalso.instancias = [];
  vi.stubGlobal('ResizeObserver', ResizeObserverFalso);
  simularAlturas();
}

function limparScroll(): void {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
}

describe('ModalComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;
  const el = () => fixture.nativeElement as HTMLElement;
  const caixa = () => el().querySelector<HTMLElement>('[role="dialog"]');
  const botaoAbrir = () => el().querySelector<HTMLButtonElement>('.abrir')!;

  function tecla(key: string, shiftKey = false): void {
    (document.activeElement ?? caixa())!.dispatchEvent(new KeyboardEvent('keydown', { key, shiftKey, bubbles: true }));
    fixture.detectChanges();
  }

  /** Abre como um utilizador: o foco está no botão quando o modal abre. */
  function abrir(): void {
    botaoAbrir().focus();
    botaoAbrir().click();
    fixture.detectChanges();
    vi.runAllTimers();
  }

  beforeEach(() => {
    document.body.style.overflow = '';
    vi.useFakeTimers();
    fixture = TestBed.createComponent(AnfitriaoComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.nativeElement.remove();
    vi.useRealTimers();
  });

  it('fechado, não mostra nada', () => {
    expect(caixa()).toBeNull();
  });

  it('é um diálogo modal com o título como nome acessível', () => {
    abrir();

    expect(caixa()?.getAttribute('aria-modal')).toBe('true');
    const titulo = el().querySelector(`#${caixa()!.getAttribute('aria-labelledby')}`);
    expect(titulo?.textContent?.trim()).toBe('Alterações');
  });

  it('ao abrir, põe o foco no primeiro campo e bloqueia o scroll da página', () => {
    abrir();

    expect(document.activeElement).toBe(el().querySelector('.campo'));
    expect(document.body.style.overflow).toBe('hidden');
  });

  it('Esc fecha, emite fechar e devolve o foco ao botão que o abriu', () => {
    abrir();

    tecla('Escape');

    expect(fixture.componentInstance.fechos).toBe(1);
    expect(caixa()).toBeNull();
    expect(document.activeElement).toBe(botaoAbrir());
    expect(document.body.style.overflow).toBe('');
  });

  it('o × tem aria-label "Fechar", fecha e devolve o foco', () => {
    abrir();

    const fecharBotao = el().querySelector<HTMLButtonElement>('button[aria-label="Fechar"]')!;
    fecharBotao.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.fechos).toBe(1);
    expect(document.activeElement).toBe(botaoAbrir());
  });

  it('Tab repetido nunca tira o foco da caixa: do último volta ao primeiro, e Shift+Tab ao contrário', () => {
    abrir();
    const focaveis = [...caixa()!.querySelectorAll<HTMLElement>('button, input')];
    const primeiro = focaveis[0];
    const ultimo = focaveis[focaveis.length - 1];

    ultimo.focus();
    tecla('Tab');
    expect(document.activeElement).toBe(primeiro);

    tecla('Tab', true);
    expect(document.activeElement).toBe(ultimo);

    for (let i = 0; i < focaveis.length * 2; i++) {
      tecla('Tab');
      expect(caixa()!.contains(document.activeElement)).toBe(true);
    }
  });

  it('se o foco cair na página (botão desativado), Tab volta à caixa e Esc continua a fechar', () => {
    abrir();
    // O browser tira o foco a um botão que fica desativado; o jsdom não, por isso simula-se com blur().
    const confirmar = el().querySelector<HTMLButtonElement>('.confirmar')!;
    confirmar.focus();
    confirmar.blur();
    expect(document.activeElement).toBe(document.body);

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    expect(caixa()!.contains(document.activeElement)).toBe(true);

    (document.activeElement as HTMLElement).blur();
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.fechos).toBe(1);
  });

  it('o foco que vai para fora da caixa volta para ela', () => {
    abrir();

    botaoAbrir().focus();

    expect(caixa()!.contains(document.activeElement)).toBe(true);
  });

  it('fechado, ignora Esc e Tab na página', () => {
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(fixture.componentInstance.fechos).toBe(0);
  });

  it('clicar no fundo fecha; clicar dentro da caixa não', () => {
    abrir();

    caixa()!.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.fechos).toBe(0);

    el().querySelector<HTMLElement>('.modal-fundo')!.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.fechos).toBe(1);
  });

  it('com fecharComFundo a false, clicar no fundo não fecha', () => {
    fixture.componentInstance.fecharComFundo.set(false);
    abrir();

    el().querySelector<HTMLElement>('.modal-fundo')!.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.fechos).toBe(0);
  });

  it('projeta o conteúdo no corpo e as ações no rodapé', () => {
    abrir();

    expect(el().querySelector('.modal-corpo .campo')).not.toBeNull();
    expect(el().querySelector('.modal-rodape .confirmar')).not.toBeNull();
  });

  it('a variante normal mostra o subtítulo no cabeçalho e anuncia-o como descrição', () => {
    abrir();

    const subtitulo = el().querySelector('.modal-cabecalho .modal-subtitulo');
    expect(subtitulo?.textContent?.trim()).toBe('Verifique antes de avançar.');
    expect(caixa()?.getAttribute('aria-describedby')).toBe(subtitulo?.id);
    expect(el().querySelector('button[aria-label="Fechar"]')).not.toBeNull();
  });

  it('sem subtítulo, anuncia como descrição o elemento do conteúdo indicado em descritoPor', () => {
    fixture.componentInstance.subtitulo.set(undefined);
    fixture.componentInstance.descritoPor.set('descricao-anfitriao');
    abrir();

    expect(caixa()?.getAttribute('aria-describedby')).toBe('descricao-anfitriao');
  });

  it('com subtítulo e descritoPor, a descrição é o subtítulo', () => {
    fixture.componentInstance.descritoPor.set('descricao-anfitriao');
    abrir();

    expect(caixa()?.getAttribute('aria-describedby')).toBe(el().querySelector('.modal-subtitulo')?.id);
  });

  it('sem subtítulo nem descritoPor, o diálogo não tem descrição', () => {
    fixture.componentInstance.subtitulo.set(undefined);
    abrir();

    expect(caixa()?.hasAttribute('aria-describedby')).toBe(false);
  });

  it('a variante de confirmação tem o medalhão e o subtítulo, sem ×', () => {
    fixture.componentInstance.variante.set('confirmacao');
    abrir();

    expect(caixa()?.classList).toContain('modal-confirmacao');
    expect(el().querySelector('.modal-medalhao')).not.toBeNull();
    expect(el().querySelector(`#${caixa()!.getAttribute('aria-describedby')}`)?.textContent?.trim()).toBe(
      'Verifique antes de avançar.',
    );
    expect(el().querySelector('button[aria-label="Fechar"]')).toBeNull();
  });

  describe('corpo com scroll', () => {
    const corpo = (): HTMLElement => el().querySelector<HTMLElement>('.modal-corpo')!;

    beforeEach(prepararScroll);
    afterEach(limparScroll);

    it('com scroll, o corpo é uma região focável com o nome do título e o foco não sai da caixa', () => {
      alturaConteudo = 900;
      abrir();

      expect(corpo().getAttribute('tabindex')).toBe('0');
      expect(corpo().getAttribute('role')).toBe('region');
      expect(corpo().getAttribute('aria-labelledby')).toBe(caixa()!.getAttribute('aria-labelledby'));

      corpo().focus();
      expect(document.activeElement).toBe(corpo());
      for (let i = 0; i < 6; i++) {
        tecla('Tab');
        expect(caixa()!.contains(document.activeElement)).toBe(true);
      }
    });

    it('sem scroll, o corpo não entra no Tab', () => {
      alturaConteudo = 100;
      abrir();

      expect(corpo().hasAttribute('tabindex')).toBe(false);
      expect(corpo().hasAttribute('role')).toBe(false);
      expect(corpo().hasAttribute('aria-labelledby')).toBe(false);
      const antes = document.activeElement;
      corpo().focus();
      expect(document.activeElement).toBe(antes);
    });

    it('com scroll, o foco inicial continua no primeiro campo do corpo', () => {
      alturaConteudo = 900;
      abrir();

      expect(document.activeElement).toBe(el().querySelector('.campo'));
    });

    it('observa o corpo e o conteúdo e volta a medir quando mudam de tamanho', () => {
      abrir();
      const observador = ResizeObserverFalso.instancias.at(-1)!;
      expect([...observador.observados]).toEqual(
        expect.arrayContaining([corpo(), el().querySelector('.modal-corpo-conteudo')]),
      );
      expect(corpo().hasAttribute('tabindex')).toBe(false);

      alturaConteudo = 900;
      observador.disparar();
      fixture.detectChanges();
      expect(corpo().getAttribute('tabindex')).toBe('0');

      alturaConteudo = 100;
      observador.disparar();
      fixture.detectChanges();
      expect(corpo().hasAttribute('tabindex')).toBe(false);
    });

    it('ao fechar, deixa de observar', () => {
      abrir();
      const observador = ResizeObserverFalso.instancias.at(-1)!;

      tecla('Escape');

      expect(observador.observados.size).toBe(0);
    });
  });
});

describe('ModalComponent sem elementos focáveis', () => {
  let fixture: ComponentFixture<AnfitriaoSemFocaveisComponent>;
  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const caixa = (): HTMLElement => el().querySelector<HTMLElement>('[role="dialog"]')!;

  function abrir(): void {
    const botao = el().querySelector<HTMLButtonElement>('.abrir')!;
    botao.focus();
    botao.click();
    fixture.detectChanges();
    vi.runAllTimers();
  }

  beforeEach(() => {
    prepararScroll();
    vi.useFakeTimers();
    fixture = TestBed.createComponent(AnfitriaoSemFocaveisComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.nativeElement.remove();
    vi.useRealTimers();
    limparScroll();
  });

  it('não tem campos nem botões, nem no corpo nem no rodapé', () => {
    abrir();

    expect(caixa().querySelectorAll('a[href], button, input, select, textarea')).toHaveLength(0);
  });

  it('com scroll, o foco inicial vai para o corpo', () => {
    alturaConteudo = 900;
    abrir();

    expect(document.activeElement).toBe(el().querySelector('.modal-corpo'));
  });

  it('sem scroll, o foco inicial vai para a caixa', () => {
    alturaConteudo = 100;
    abrir();

    expect(document.activeElement).toBe(caixa());
  });
});

describe('ModalComponent em pilha', () => {
  let fixture: ComponentFixture<AnfitriaoEmPilhaComponent>;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    fixture = TestBed.createComponent(AnfitriaoEmPilhaComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const abrir = el().querySelector<HTMLButtonElement>('.abrir')!;
    abrir.focus();
    abrir.click();
    fixture.detectChanges();
    vi.runAllTimers();
    const abrirCima = el().querySelector<HTMLButtonElement>('.abrir-cima')!;
    abrirCima.focus();
    abrirCima.click();
    fixture.detectChanges();
    vi.runAllTimers();
  });

  afterEach(() => {
    fixture.nativeElement.remove();
    vi.useRealTimers();
  });

  it('o modal de cima fica com o foco e o de baixo não o puxa', () => {
    expect(document.activeElement).toBe(el().querySelector('.campo-cima'));
  });

  it('o Esc fecha só o de cima e o foco volta ao botão que o abriu, no de baixo', () => {
    document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.cima()).toBe(false);
    expect(fixture.componentInstance.baixo()).toBe(true);
    expect(document.activeElement).toBe(el().querySelector('.abrir-cima'));
    expect(document.body.style.overflow).toBe('hidden');
  });
});

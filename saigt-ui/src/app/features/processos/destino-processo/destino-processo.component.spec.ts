import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { DestinoProcesso } from '../../../models/processo.model';
import { AJUDA_DESTINO, AJUDA_DESTINO_COM_FICHEIROS, DestinoProcessoComponent } from './destino-processo.component';

describe('DestinoProcessoComponent', () => {
  let fixture: ComponentFixture<DestinoProcessoComponent>;
  let emitidos: DestinoProcesso[];

  function criar(entradas: Record<string, unknown>): void {
    fixture = TestBed.createComponent(DestinoProcessoComponent);
    for (const [nome, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(nome, valor);
    }
    fixture.componentInstance.alterado.subscribe((destino) => emitidos.push(destino));
    fixture.detectChanges();
  }

  function opcoes(): HTMLInputElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('input[type="radio"]'));
  }

  function opcao(valor: DestinoProcesso): HTMLInputElement {
    return opcoes().find((campo) => campo.value === valor)!;
  }

  function fieldset(): HTMLFieldSetElement {
    return fixture.nativeElement.querySelector('fieldset');
  }

  function texto(): string {
    return fixture.nativeElement.textContent;
  }

  beforeEach(() => {
    emitidos = [];
  });

  describe('editável', () => {
    it('mostra a legend e as duas opções, com a gravada marcada e a ajuda ligada ao grupo', () => {
      criar({ valor: 'PUBLICACAO_DEPOSITO', editavel: true });

      expect(fieldset().querySelector('legend')!.textContent).toBe('Destino do processo');
      expect(opcoes().map((campo) => campo.closest('label')!.textContent!.trim())).toEqual(['Publicação e depósito', 'Só depósito']);
      expect(opcao('PUBLICACAO_DEPOSITO').checked).toBe(true);
      expect(opcao('SO_DEPOSITO').checked).toBe(false);
      expect(new Set(opcoes().map((campo) => campo.name)).size).toBe(1);
      const ajuda = fixture.nativeElement.querySelector('.ajuda') as HTMLElement;
      expect(ajuda.textContent).toBe(AJUDA_DESTINO);
      expect(fieldset().getAttribute('aria-describedby')).toBe(ajuda.id);
    });

    it('escolher «Só depósito» emite alterado com SO_DEPOSITO e marca a opção', () => {
      criar({ valor: 'PUBLICACAO_DEPOSITO', editavel: true });

      opcao('SO_DEPOSITO').click();
      fixture.detectChanges();

      expect(emitidos).toEqual(['SO_DEPOSITO']);
      expect(opcao('SO_DEPOSITO').checked).toBe(true);
      expect(opcao('SO_DEPOSITO').closest('label')!.classList).toContain('opcao-escolhida');
    });

    it('escolher «Publicação e depósito» a partir de só depósito emite PUBLICACAO_DEPOSITO', () => {
      criar({ valor: 'SO_DEPOSITO', editavel: true });

      opcao('PUBLICACAO_DEPOSITO').click();

      expect(emitidos).toEqual(['PUBLICACAO_DEPOSITO']);
    });

    it('repetir a opção gravada não emite', () => {
      criar({ valor: 'SO_DEPOSITO', editavel: true });

      fixture.componentInstance.escolher('SO_DEPOSITO');

      expect(emitidos).toEqual([]);
    });

    it('a gravar, o grupo fica ocupado, outra escolha é ignorada e as opções continuam focáveis', () => {
      criar({ valor: 'PUBLICACAO_DEPOSITO', editavel: true, aGravar: true });

      expect(fieldset().getAttribute('aria-busy')).toBe('true');
      expect(opcoes().every((campo) => !campo.disabled)).toBe(true);
      opcao('SO_DEPOSITO').click();
      fixture.detectChanges();
      expect(emitidos).toEqual([]);
      expect(opcao('PUBLICACAO_DEPOSITO').checked).toBe(true);
    });

    it('com uma recusa, mostra o erro ligado ao grupo e volta à opção gravada', () => {
      criar({ valor: 'PUBLICACAO_DEPOSITO', editavel: true });
      opcao('SO_DEPOSITO').click();
      fixture.detectChanges();

      fixture.componentRef.setInput('erro', 'Este processo não pode ser só para depósito.');
      fixture.detectChanges();

      const erro = fixture.nativeElement.querySelector('.erro') as HTMLElement;
      expect(erro.textContent).toBe('Este processo não pode ser só para depósito.');
      expect(erro.getAttribute('role')).toBe('alert');
      expect(fieldset().getAttribute('aria-describedby')!.split(' ')).toContain(erro.id);
      expect(opcao('PUBLICACAO_DEPOSITO').checked).toBe(true);
      expect(opcao('SO_DEPOSITO').checked).toBe(false);
    });

    it('um valor novo vindo do ecrã passa a ser o marcado', () => {
      criar({ valor: 'PUBLICACAO_DEPOSITO', editavel: true });

      fixture.componentRef.setInput('valor', 'SO_DEPOSITO');
      fixture.detectChanges();

      expect(opcao('SO_DEPOSITO').checked).toBe(true);
    });
  });

  describe('só de leitura', () => {
    it('com ficheiros, mostra o valor sem opções e a indicação de remover os ficheiros', () => {
      criar({ valor: 'SO_DEPOSITO', editavel: false, comFicheiros: true });

      expect(opcoes()).toHaveLength(0);
      expect(fieldset().querySelector('legend')!.textContent).toBe('Destino do processo');
      expect(fixture.nativeElement.querySelector('.valor').textContent).toBe('Só depósito');
      const ajuda = fixture.nativeElement.querySelector('.ajuda') as HTMLElement;
      expect(ajuda.textContent).toBe(AJUDA_DESTINO_COM_FICHEIROS);
      expect(fieldset().getAttribute('aria-describedby')).toBe(ajuda.id);
    });

    it('sem ficheiros, mostra só o valor', () => {
      criar({ valor: 'PUBLICACAO_DEPOSITO', editavel: false, comFicheiros: false });

      expect(opcoes()).toHaveLength(0);
      expect(texto()).toContain('Publicação e depósito');
      expect(texto()).not.toContain(AJUDA_DESTINO_COM_FICHEIROS);
      expect(fieldset().getAttribute('aria-describedby')).toBeNull();
    });

    it('não emite alterado', () => {
      criar({ valor: 'PUBLICACAO_DEPOSITO', editavel: false });

      fixture.componentInstance.escolher('SO_DEPOSITO');

      expect(emitidos).toEqual([]);
    });
  });
});

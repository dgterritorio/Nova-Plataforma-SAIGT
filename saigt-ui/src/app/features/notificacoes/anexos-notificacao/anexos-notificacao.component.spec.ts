import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { NotificacaoService } from '../../../services/notificacao.service';
import { AnexosNotificacaoComponent, ResultadoAnexo, TAMANHO_MAXIMO_ANEXO, validarAnexo } from './anexos-notificacao.component';

function ficheiro(nome: string, conteudo = 'conteúdo'): File {
  return new File([conteudo], nome, { type: 'application/pdf' });
}

describe('AnexosNotificacaoComponent', () => {
  let fixture: ComponentFixture<AnexosNotificacaoComponent>;
  let anexar: ReturnType<typeof vi.fn>;

  const el = () => fixture.nativeElement as HTMLElement;
  const entrada = () => el().querySelector<HTMLInputElement>('input[type="file"]')!;

  function escolher(...ficheiros: File[]): void {
    Object.defineProperty(entrada(), 'files', { value: ficheiros, configurable: true });
    entrada().dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    anexar = vi.fn();
    TestBed.configureTestingModule({
      imports: [AnexosNotificacaoComponent],
      providers: [{ provide: NotificacaoService, useValue: { anexar } }],
    });
    fixture = TestBed.createComponent(AnexosNotificacaoComponent);
    fixture.detectChanges();
  });

  it('a seleção tem rótulo associado, aceita vários ficheiros e é alcançável por teclado', () => {
    const rotulo = el().querySelector<HTMLLabelElement>(`label[for="${entrada().id}"]`);

    expect(rotulo?.textContent).toContain('Clique para anexar documentos');
    expect(entrada().multiple).toBe(true);
    expect(entrada().tabIndex).toBe(0);
    expect(entrada().disabled).toBe(false);
    expect(entrada().accept).toContain('.pdf');
  });

  it('lista os ficheiros escolhidos com nome e tamanho e remove-os por «Remover {nome}»', () => {
    escolher(ficheiro('oficio.pdf'), ficheiro('mapa.xlsx'));

    const itens = [...el().querySelectorAll('.escolhido')].map((li) => li.querySelector('.escolhido-nome')?.textContent?.trim());
    expect(itens).toEqual(['oficio.pdf', 'mapa.xlsx']);
    expect(el().querySelector('.escolhido-tamanho')?.textContent?.trim()).toBe('1 KB');

    const remover = el().querySelector<HTMLButtonElement>('button[aria-label="Remover oficio.pdf"]')!;
    expect(remover.type).toBe('button');
    remover.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.ficheiros().map((f) => f.name)).toEqual(['mapa.xlsx']);
  });

  it('recusa no cliente a extensão não aceite e o ficheiro vazio, com mensagem', () => {
    escolher(ficheiro('programa.exe'), ficheiro('vazio.pdf', ''), ficheiro('bom.pdf'));

    expect(fixture.componentInstance.ficheiros().map((f) => f.name)).toEqual(['bom.pdf']);
    const erros = el().querySelector('[role="alert"]')?.textContent ?? '';
    expect(erros).toContain('programa.exe: extensão não aceite.');
    expect(erros).toContain('vazio.pdf: o ficheiro está vazio.');
  });

  it('recusa o ficheiro acima do tamanho máximo', () => {
    const grande = ficheiro('grande.pdf');
    Object.defineProperty(grande, 'size', { value: TAMANHO_MAXIMO_ANEXO + 1 });

    expect(validarAnexo(grande)).toBe('grande.pdf: o ficheiro excede o tamanho máximo permitido.');
  });

  it('desativada, mostra a ajuda ligada ao campo', () => {
    fixture.componentRef.setInput('desativado', true);
    fixture.componentRef.setInput('ajuda', 'Só com um destinatário.');
    fixture.detectChanges();

    expect(entrada().disabled).toBe(true);
    const ajuda = el().querySelector(`#${entrada().getAttribute('aria-describedby')}`);
    expect(ajuda?.textContent?.trim()).toBe('Só com um destinatário.');
  });

  it('envia os ficheiros um a um e devolve o resultado de cada um, mesmo com falhas', () => {
    const documento = { id: 'd1', nomeFicheiro: 'a.pdf', tamanhoBytes: 8, criadoEm: '2026-11-13T10:00:00Z' };
    anexar
      .mockReturnValueOnce(of(documento))
      .mockReturnValueOnce(throwError(() => new ApiError('NOTIF_FICHEIRO_EXCEDE_LIMITE', 'O ficheiro excede o tamanho máximo permitido.', 't', null, 400)));
    escolher(ficheiro('a.pdf'), ficheiro('b.pdf'));

    let resultados: ResultadoAnexo[] = [];
    fixture.componentInstance.enviar('n1').subscribe((r) => (resultados = r));

    expect(anexar.mock.calls.map((chamada) => [chamada[0], (chamada[1] as File).name])).toEqual([
      ['n1', 'a.pdf'],
      ['n1', 'b.pdf'],
    ]);
    expect(resultados.map((r) => [r.documento?.id ?? null, r.erro])).toEqual([
      ['d1', null],
      [null, 'b.pdf: O ficheiro excede o tamanho máximo permitido.'],
    ]);
  });
});

import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PageResponse } from '../../../core/models/page-response.model';
import { ApiError } from '../../../core/models/error-response.model';
import { DinamicaAssociavel, RespostaSimultaneoPmot } from '../../../models/processo.model';
import { ProcessoService } from '../../../services/processo.service';
import {
  AJUDA_DINAMICA_ASSOCIADA,
  DinamicaAssociadaComponent,
  MENSAGEM_DINAMICA_INDISPONIVEL,
  MENSAGEM_SEM_DINAMICAS,
  rotuloDinamicaAssociavel,
} from './dinamica-associada.component';

function dinamica(id: string, extra: Partial<DinamicaAssociavel> = {}): DinamicaAssociavel {
  return {
    dinamicaId: id,
    numeroDinamica: `D-${id}`,
    descritivo: `Alteração ${id}`,
    tipoDinamica: 'ALTERACAO',
    estado: 'EM_CURSO',
    instrumento: { id: `i-${id}`, designacao: `PDM ${id}`, tipoInstrumento: 'PDM' },
    ...extra,
  };
}

function pagina(content: DinamicaAssociavel[], page = 0, totalPages = 1): PageResponse<DinamicaAssociavel> {
  return { content, page, size: 20, totalElements: content.length, totalPages };
}

@Component({
  imports: [DinamicaAssociadaComponent, ReactiveFormsModule],
  template: `
    <app-dinamica-associada
      [formControl]="controlo"
      processoId="p1"
      [dinamicaInicial]="dinamicaInicial()"
      [respostaInicial]="respostaInicial()"
      [desativado]="desativado()"
      [erro]="erro()"
    />
  `,
})
class AnfitriaoComponent {
  readonly controlo = new FormControl<string | null>(null);
  readonly dinamicaInicial = signal<DinamicaAssociavel | null>(null);
  readonly respostaInicial = signal<RespostaSimultaneoPmot | null>(null);
  readonly desativado = signal(false);
  readonly erro = signal<string | null>(null);
}

describe('DinamicaAssociadaComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;
  let anfitriao: AnfitriaoComponent;
  let dinamicasAssociaveis: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    dinamicasAssociaveis = vi.fn((): Observable<PageResponse<DinamicaAssociavel>> => of(pagina([dinamica('a'), dinamica('b')])));
    TestBed.configureTestingModule({
      imports: [AnfitriaoComponent],
      providers: [{ provide: ProcessoService, useValue: { dinamicasAssociaveis } }],
    });
    fixture = TestBed.createComponent(AnfitriaoComponent);
    anfitriao = fixture.componentInstance;
  });

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function radio(valor: 'SIM' | 'NAO'): HTMLInputElement {
    return el().querySelector(`input[type="radio"][value="${valor}"]`) as HTMLInputElement;
  }

  function escolherResposta(valor: 'SIM' | 'NAO'): void {
    radio(valor).click();
    fixture.detectChanges();
  }

  function select(): HTMLSelectElement | null {
    return el().querySelector('select');
  }

  function opcoes(): string[] {
    return [...(select()?.querySelectorAll('option') ?? [])].slice(1).map((o) => o.textContent!.trim());
  }

  describe('valor inicial', () => {
    it('sem nada gravado, a pergunta vem com «Não», sem o seletor, sem pedir a lista e com a ajuda', () => {
      fixture.detectChanges();

      expect(el().querySelector('legend')!.textContent!.trim()).toBe('Processo em simultâneo com a formação de PMOT');
      expect(radio('NAO').checked).toBe(true);
      expect(radio('SIM').checked).toBe(false);
      expect(select()).toBeNull();
      expect(dinamicasAssociaveis).not.toHaveBeenCalled();
      expect(el().textContent).toContain(AJUDA_DINAMICA_ASSOCIADA);
      const ajuda = el().querySelector('.ajuda')!;
      expect(el().querySelector('fieldset')!.getAttribute('aria-describedby')).toContain(ajuda.id);
    });

    it('com uma Dinâmica gravada, vem «Sim» com a opção selecionada', () => {
      anfitriao.dinamicaInicial.set(dinamica('b'));
      anfitriao.controlo.setValue('b');
      fixture.detectChanges();

      expect(radio('SIM').checked).toBe(true);
      expect(dinamicasAssociaveis).toHaveBeenCalledWith('p1', 0, 20);
      expect(select()!.value).toBe('b');
    });

    it('a resposta «Sim» gravada sem Dinâmica abre o seletor vazio', () => {
      anfitriao.respostaInicial.set('SIM');
      fixture.detectChanges();

      expect(radio('SIM').checked).toBe(true);
      expect(select()!.value).toBe('');
      expect(anfitriao.controlo.value).toBeNull();
    });
  });

  describe('pergunta Sim/Não e escolha', () => {
    it('com «Sim» pede a primeira página e mostra o campo do IGT com as opções no formato combinado', () => {
      fixture.detectChanges();
      escolherResposta('SIM');

      expect(dinamicasAssociaveis).toHaveBeenCalledOnce();
      expect(dinamicasAssociaveis).toHaveBeenCalledWith('p1', 0, 20);
      const rotulo = el().querySelector('label.seletor-rotulo')!;
      expect(rotulo.textContent!.trim()).toBe('Instrumento de Gestão Territorial');
      expect(rotulo.getAttribute('for')).toBe(select()!.id);
      expect(select()!.querySelector('option')!.textContent!.trim()).toBe('Selecione a dinâmica do IGT');
      expect(opcoes()).toEqual(['D-a · ALTERACAO · Alteração a — PDM a (PDM)', 'D-b · ALTERACAO · Alteração b — PDM b (PDM)']);
      const ajuda = el().querySelector('.seletor-ajuda')!;
      expect(ajuda.textContent!.trim()).toBe(AJUDA_DINAMICA_ASSOCIADA);
      expect(select()!.getAttribute('aria-describedby')).toContain(ajuda.id);
    });

    it('uma Dinâmica caducada acaba em «(caducada)»', () => {
      expect(rotuloDinamicaAssociavel(dinamica('c', { estado: 'CADUCADA' }))).toBe('D-c · ALTERACAO · Alteração c — PDM c (PDM) (caducada)');
    });

    it('escolher uma opção passa o id ao formulário; «Sim» sem escolha vale null', () => {
      fixture.detectChanges();
      escolherResposta('SIM');
      expect(anfitriao.controlo.value).toBeNull();

      select()!.value = 'b';
      select()!.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(anfitriao.controlo.value).toBe('b');
    });

    it('passar a «Não» esconde o campo e limpa a Dinâmica escolhida', () => {
      anfitriao.controlo.setValue('a');
      anfitriao.dinamicaInicial.set(dinamica('a'));
      fixture.detectChanges();

      escolherResposta('NAO');

      expect(select()).toBeNull();
      expect(anfitriao.controlo.value).toBeNull();
    });

    it('anuncia o carregamento numa região de estado', () => {
      const resposta = new Subject<PageResponse<DinamicaAssociavel>>();
      dinamicasAssociaveis.mockReturnValue(resposta);
      fixture.detectChanges();
      escolherResposta('SIM');

      const estado = el().querySelector('[role="status"].carregamento')!;
      expect(estado.textContent!.trim()).toBe('A carregar dinâmicas…');

      resposta.next(pagina([dinamica('a')]));
      fixture.detectChanges();
      expect(estado.textContent!.trim()).toBe('');
    });
  });

  describe('Mostrar mais', () => {
    it('com duas páginas junta as opções da segunda às primeiras e o botão desaparece', () => {
      dinamicasAssociaveis.mockImplementation((_id: string, page: number) =>
        of(page === 0 ? pagina([dinamica('a'), dinamica('b')], 0, 2) : pagina([dinamica('c')], 1, 2)),
      );
      fixture.detectChanges();
      escolherResposta('SIM');

      const botao = [...el().querySelectorAll('button')].find((b) => b.textContent!.trim() === 'Mostrar mais')!;
      expect(botao).toBeTruthy();
      botao.click();
      fixture.detectChanges();

      expect(dinamicasAssociaveis).toHaveBeenLastCalledWith('p1', 1, 20);
      expect(opcoes().map((o) => o.split(' · ')[0])).toEqual(['D-a', 'D-b', 'D-c']);
      expect([...el().querySelectorAll('button')].some((b) => b.textContent!.trim() === 'Mostrar mais')).toBe(false);
    });
  });

  describe('lista vazia', () => {
    it('o campo fica sem opções e aparece o alerta informativo, ligado ao seletor', () => {
      dinamicasAssociaveis.mockReturnValue(of(pagina([], 0, 0)));
      fixture.detectChanges();
      escolherResposta('SIM');

      expect(opcoes()).toEqual([]);
      const alerta = el().querySelector('app-alerta.alerta-info')!;
      expect(alerta.textContent!.trim()).toBe(MENSAGEM_SEM_DINAMICAS);
      expect(alerta.getAttribute('role')).toBe('status');
      expect(select()!.getAttribute('aria-describedby')).toContain(alerta.id);
    });
  });

  describe('Dinâmica indisponível', () => {
    it('a gravada que já não vem na lista continua selecionada, com o aviso', () => {
      anfitriao.dinamicaInicial.set(dinamica('z', { estado: 'TERMINADA' }));
      anfitriao.controlo.setValue('z');
      fixture.detectChanges();

      expect(select()!.value).toBe('z');
      expect(opcoes()).toContain('D-z · ALTERACAO · Alteração z — PDM z (PDM)');
      const aviso = el().querySelector('app-alerta.alerta-aviso')!;
      expect(aviso.textContent!.trim()).toBe(MENSAGEM_DINAMICA_INDISPONIVEL);
      expect(select()!.getAttribute('aria-describedby')).toContain(aviso.id);
      expect(anfitriao.controlo.value).toBe('z');
    });

    it('não avisa enquanto há páginas por ler', () => {
      dinamicasAssociaveis.mockReturnValue(of(pagina([dinamica('a')], 0, 2)));
      anfitriao.dinamicaInicial.set(dinamica('z'));
      anfitriao.controlo.setValue('z');
      fixture.detectChanges();

      expect(el().querySelector('app-alerta.alerta-aviso')).toBeNull();
    });
  });

  describe('erro', () => {
    it('500 na lista: banner com a mensagem do envelope e «Tentar novamente», que volta a pedir', () => {
      dinamicasAssociaveis.mockReturnValueOnce(throwError(() => new ApiError('ERRO_INTERNO', 'Erro interno. Tente mais tarde.', 't1', null)));
      fixture.detectChanges();
      escolherResposta('SIM');

      const banner = el().querySelector('.error-banner')!;
      expect(banner.textContent!.trim()).toBe('Erro interno. Tente mais tarde.');
      expect(radio('NAO').disabled).toBe(false);
      expect(select()!.disabled).toBe(false);

      const tentar = [...el().querySelectorAll('button')].find((b) => b.textContent!.trim() === 'Tentar novamente')!;
      tentar.click();
      fixture.detectChanges();

      expect(dinamicasAssociaveis).toHaveBeenCalledTimes(2);
      expect(el().querySelector('.error-banner')).toBeNull();
      expect(opcoes()).toHaveLength(2);
    });

    it('a recusa do servidor aparece junto do seletor, por aria-describedby, e focar() leva-lhe o foco', () => {
      anfitriao.respostaInicial.set('SIM');
      anfitriao.erro.set('A dinâmica indicada não está disponível para associação.');
      fixture.detectChanges();

      const erro = el().querySelector('.seletor-erro')!;
      expect(erro.textContent!.trim()).toBe('A dinâmica indicada não está disponível para associação.');
      expect(select()!.getAttribute('aria-describedby')).toContain(erro.id);
      expect(select()!.getAttribute('aria-invalid')).toBe('true');

      const componente = fixture.debugElement.children[0].componentInstance as DinamicaAssociadaComponent;
      componente.focar();
      expect(document.activeElement).toBe(select());
    });
  });

  describe('desativado', () => {
    it('pela entrada: a pergunta e o seletor ficam desativados, com a associação gravada e sem pedir a lista', () => {
      anfitriao.desativado.set(true);
      anfitriao.dinamicaInicial.set(dinamica('a'));
      anfitriao.controlo.setValue('a');
      fixture.detectChanges();

      expect(radio('SIM').checked).toBe(true);
      expect(radio('SIM').disabled).toBe(true);
      expect(radio('NAO').disabled).toBe(true);
      expect(dinamicasAssociaveis).not.toHaveBeenCalled();
      expect(select()!.value).toBe('a');
      expect(opcoes()).toEqual(['D-a · ALTERACAO · Alteração a — PDM a (PDM)']);
    });

    it('pelo formulário (controlo desativado): o mesmo, e «Não» não limpa nada', () => {
      anfitriao.dinamicaInicial.set(dinamica('a'));
      anfitriao.controlo.setValue('a');
      anfitriao.controlo.disable();
      fixture.detectChanges();

      expect(radio('NAO').disabled).toBe(true);
      expect(select()!.disabled).toBe(true);
      expect(dinamicasAssociaveis).not.toHaveBeenCalled();
      const componente = fixture.debugElement.children[0].componentInstance as DinamicaAssociadaComponent;
      componente.responder('NAO');
      expect(anfitriao.controlo.value).toBe('a');
    });
  });
});

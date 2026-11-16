import { SimpleChange, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ElaboracaoNova } from '../../../models/instrumento.model';
import { DinamicaAssociavel, OpcaoLista, TerritorioInstrumento, TipoListaFormulario } from '../../../models/processo.model';
import { AuthService } from '../../../core/auth/auth.service';
import { JwtClaimsEntidade } from '../../../core/auth/auth.models';
import { EntidadeService } from '../../../services/entidade.service';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { ReferenciaSnitService } from '../../../services/referencia-snit.service';
import { ProcessoService } from '../../../services/processo.service';
import { ApiError } from '../../../core/models/error-response.model';
import { FormularioInicialValores, ProcessoFormularioInicialComponent, caminhoDoControlo, comErroNoTipoAto } from './processo-formulario-inicial.component';
import { pedeElementosAfetadosNoFormulario } from '../formulario-fixo';

describe('ProcessoFormularioInicialComponent', () => {
  let fixture: ComponentFixture<ProcessoFormularioInicialComponent>;
  let opcoesPorTipo: Partial<Record<TipoListaFormulario, OpcaoLista[]>>;
  let opcoes: ReturnType<typeof vi.fn>;
  let nutsII: ReturnType<typeof vi.fn>;
  let concelhos: ReturnType<typeof vi.fn>;
  let dinamicasAssociaveis: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    opcoesPorTipo = {};
    opcoes = vi.fn((tipo: TipoListaFormulario) => of(opcoesPorTipo[tipo] ?? []));
    nutsII = vi.fn(() => of([]));
    concelhos = vi.fn(() => of([]));
    dinamicasAssociaveis = vi.fn(() => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    TestBed.configureTestingModule({
      imports: [ProcessoFormularioInicialComponent],
      providers: [
        { provide: ListaValoresService, useValue: { opcoes } },
        { provide: ReferenciaSnitService, useValue: { nutsII, concelhos } },
        { provide: ProcessoService, useValue: { dinamicasAssociaveis } },
      ],
    });
    fixture = TestBed.createComponent(ProcessoFormularioInicialComponent);
  });

  function comSerie(serieDr: string | null): string {
    fixture.componentRef.setInput('serieDr', serieDr);
    fixture.detectChanges();
    return texto();
  }

  function texto(): string {
    return fixture.nativeElement.textContent as string;
  }

  function valoresGravados(valores: FormularioInicialValores): void {
    const componente = fixture.componentInstance;
    componente.valoresIniciais = valores;
    componente.ngOnChanges({ valoresIniciais: new SimpleChange(null, valores, true) });
  }

  function itensDoContexto(): Record<string, string> {
    const itens = fixture.nativeElement.querySelectorAll('dl.contexto > div') as NodeListOf<HTMLElement>;
    return Object.fromEntries(
      Array.from(itens).map((item) => [item.querySelector('dt')!.textContent!.trim(), item.querySelector('dd')!.textContent!.trim()]),
    );
  }

  describe('cartão Informação Geral numa Decisão de início de 2.ª série', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('tipoInstrumento', 'Plano Diretor Municipal');
      fixture.componentRef.setInput('tipoProcedimento', 'Decisão de início');
      fixture.componentRef.setInput('designacaoInstrumento', 'Alteração do PDM de Sintra');
      fixture.componentRef.setInput('tipoAtoNome', 'Despacho');
    });

    it('mostra os itens só de leitura e os campos Tipo de ato, Norma legal, Tipo de informação e Sumário', () => {
      const conteudo = comSerie('SEGUNDA_H');

      expect(fixture.nativeElement.querySelector('h2')!.textContent).toContain('Informação Geral');
      expect(itensDoContexto()).toEqual({
        'Tipo de instrumento': 'Plano Diretor Municipal',
        'Tipo de procedimento': 'Decisão de início',
        'NUT II': '—',
        Concelho: '—',
        Designação: 'Alteração do PDM de Sintra',
        'Parte do DR': '2.ª Série - H',
      });
      expect(fixture.nativeElement.querySelector('app-seletor.tipo-ato')).not.toBeNull();
      expect(conteudo).toContain('Norma legal ou mera conveniência');
      expect(conteudo).toContain('Tipo de informação');
      expect(conteudo).toContain('Sumário conteúdo do ato');
    });

    it('o Sumário tem a marca de obrigatório e o texto de exemplo do ato a publicar', () => {
      comSerie('SEGUNDA_H');

      const sumario = fixture.nativeElement.querySelector('textarea[formcontrolname="sumario"]') as HTMLTextAreaElement;
      const rotulo = sumario.closest('label')!.querySelector('.rotulo')!;
      expect(rotulo.querySelector('.marca-obrigatoria[aria-hidden="true"]')!.textContent).toBe('*');
      expect(sumario.getAttribute('aria-required')).toBe('true');
      expect(sumario.placeholder).toBe('Resumo do conteúdo do ato a publicar (até 2000 caracteres)');
      expect(sumario.placeholder).not.toContain('opcional');
    });

    it('lista os Instrumentos associados à Dinâmica só em leitura, com designação e tipo', () => {
      fixture.componentRef.setInput('instrumentosAbrangidos', [
        { id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' },
        { id: 'i2', designacao: 'PDM de Cascais', tipoInstrumento: 'PDM' },
      ]);
      comSerie('SEGUNDA_H');

      const itens = [...fixture.nativeElement.querySelectorAll('.instrumentos-associados li')].map((li: Element) =>
        li.textContent?.replace(/\s+/g, ' ').trim(),
      );
      expect(itens).toEqual(['PDM de Sintra (PDM)', 'PDM de Cascais (PDM)']);
      expect(fixture.nativeElement.querySelector('.instrumentos-associados input, .instrumentos-associados button')).toBeNull();
    });

    it('sem Instrumentos associados diz «Sem IGT associado»; sem detalhe não mostra a linha', () => {
      comSerie('SEGUNDA_H');
      expect(fixture.nativeElement.querySelector('.instrumentos-associados')).toBeNull();

      fixture.componentRef.setInput('instrumentosAbrangidos', []);
      fixture.detectChanges();
      expect(itensDoContexto()['Instrumentos associados']).toBe('Sem IGT associado');
    });

    it('não mostra a data de assinatura nem o cartão Publicação INCM', () => {
      const conteudo = comSerie('SEGUNDA_H');

      expect(conteudo).not.toContain('Data de assinatura');
      expect(conteudo).not.toContain('Publicação INCM');
      expect(fixture.nativeElement.querySelector('input[type="date"]')).toBeNull();
      expect('dataAssinatura' in fixture.componentInstance.form.controls).toBe(false);
    });

    it('os itens de leitura são texto, não campos editáveis', () => {
      comSerie('SEGUNDA_H');

      expect(fixture.nativeElement.querySelectorAll('dl.contexto input, dl.contexto select, dl.contexto textarea')).toHaveLength(0);
    });

    it('o cabeçalho recolhe e volta a abrir o cartão', () => {
      comSerie('SEGUNDA_H');
      const botao = fixture.nativeElement.querySelector('h2 button') as HTMLButtonElement;
      const corpo = fixture.nativeElement.querySelector('#informacao-geral-corpo') as HTMLElement;

      expect(botao.getAttribute('aria-expanded')).toBe('true');
      botao.click();
      fixture.detectChanges();
      expect(botao.getAttribute('aria-expanded')).toBe('false');
      expect(corpo.hidden).toBe(true);
      botao.click();
      fixture.detectChanges();
      expect(corpo.hidden).toBe(false);
    });
  });

  describe('Procedimento sem Informação Base do Texto do Ato (dataAssinaturaNoFormulario)', () => {
    const MENSAGEM = 'Indique a data de assinatura do ato.';

    function constituicaoComissao(valores: FormularioInicialValores = {}, extra: { leitura?: boolean; bloqueados?: string[] } = {}): void {
      fixture.componentRef.setInput('formulario', 'INFORMACAO_GERAL');
      fixture.componentRef.setInput('tipoProcedimento', 'Constituição da Comissão Consultiva');
      fixture.componentRef.setInput('pedeElementosAfetados', false);
      fixture.componentRef.setInput('dataAssinaturaNoFormulario', true);
      fixture.componentRef.setInput('leitura', extra.leitura ?? false);
      fixture.componentRef.setInput('camposBloqueados', extra.bloqueados ?? []);
      fixture.componentRef.setInput('valoresIniciais', valores);
      comSerie('SEGUNDA_C');
    }

    const campoData = (): HTMLInputElement | null => fixture.nativeElement.querySelector('input.data-assinatura');

    it('o cartão pede a data de assinatura do ato, obrigatória, a seguir ao Tipo de ato', () => {
      constituicaoComissao();

      const data = campoData()!;
      expect(data.type).toBe('date');
      expect(data.getAttribute('aria-required')).toBe('true');
      expect(data.closest('label')!.textContent).toContain('Data de Assinatura do ato');
      const tipoAto = fixture.nativeElement.querySelector('app-seletor.tipo-ato') as HTMLElement;
      expect(tipoAto.compareDocumentPosition(data) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('sem a data o «Seguinte» não avança e a mensagem fica junto do campo, que a descreve', () => {
      constituicaoComissao();

      expect(fixture.componentInstance.validarObrigatorios()).toBe(false);
      fixture.detectChanges();

      const erro = fixture.nativeElement.querySelector('#erro-dataAssinatura') as HTMLElement;
      expect(erro.textContent!.trim()).toBe(MENSAGEM);
      expect(erro.getAttribute('role')).toBe('alert');
      expect(campoData()!.getAttribute('aria-describedby')).toContain('erro-dataAssinatura');

      fixture.componentInstance.dataAssinatura.setValue('2026-11-10');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('#erro-dataAssinatura')).toBeNull();
      expect(fixture.componentInstance.validarObrigatorios()).toBe(true);
    });

    it('a gravação leva a data do cartão, mesmo sem bloco Informação Base', () => {
      constituicaoComissao({ dataAssinatura: '2026-11-10' });

      expect(campoData()!.value).toBe('2026-11-10');
      expect(fixture.componentInstance.dataAssinaturaPedido(null)).toBe('2026-11-10');
    });

    it('depois da primeira submissão a data fica bloqueada com «Desbloquear» e não trava o «Seguinte»', () => {
      constituicaoComissao({ dataAssinatura: '2026-11-10' }, { bloqueados: ['dataAssinatura'] });

      expect(campoData()!.disabled).toBe(true);
      expect(fixture.nativeElement.querySelector('#bloqueado-dataAssinatura')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('label.campo app-desbloquear-campo[chave="dataAssinatura"]')).not.toBeNull();
      expect(fixture.componentInstance.validarObrigatorios()).toBe(true);
      expect(fixture.componentInstance.dataAssinaturaPedido(null)).toBe('2026-11-10');
    });

    it('em leitura mostra a data de assinatura gravada', () => {
      constituicaoComissao({ dataAssinatura: '2026-11-10' }, { leitura: true });

      const leitura = fixture.nativeElement.querySelector('dl.leitura') as HTMLElement;
      expect(leitura.querySelectorAll('.data-assinatura')).toHaveLength(1);
      expect(leitura.querySelector('.data-assinatura')!.textContent!.trim()).toBe('10/11/2026');
    });

    it('sem dataAssinaturaNoFormulario o cartão continua sem a data e o «Seguinte» não a exige', () => {
      constituicaoComissao();
      fixture.componentRef.setInput('dataAssinaturaNoFormulario', false);
      fixture.detectChanges();

      expect(campoData()).toBeNull();
      expect(fixture.componentInstance.validarObrigatorios()).toBe(true);
      expect(fixture.componentInstance.dataAssinaturaPedido('2026-01-05')).toBe('2026-01-05');
    });

    it('na Ratificação a data aparece uma só vez, no cartão e em leitura', () => {
      constituicaoComissao({ dataAssinatura: '2026-11-10', publicarSuplemento: false });
      fixture.componentRef.setInput('formulario', 'RATIFICACAO');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('input.data-assinatura')).toHaveLength(1);

      fixture.componentRef.setInput('leitura', true);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('dl.leitura .data-assinatura')).toHaveLength(1);
    });
  });

  describe('em leitura', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('leitura', true);
    });

    it('mostra os valores gravados da 2.ª série em texto, sem campos editáveis', () => {
      valoresGravados({ sumario: 'Sumário gravado', normaLegal: 'Art. 89.º do RJIGT', tipoInformacao: 'EXTRATO' });
      const conteudo = comSerie('SEGUNDA_H');

      expect(conteudo).toContain('Art. 89.º do RJIGT');
      expect(conteudo).toContain('Extrato');
      expect(conteudo).toContain('Sumário gravado');
      expect(conteudo).not.toContain('Publicar suplemento');
      expect(fixture.nativeElement.querySelectorAll('input, textarea, select, form')).toHaveLength(0);
    });

    it('na 1.ª série mostra Publicar suplemento e não a Norma legal', () => {
      valoresGravados({ publicarSuplemento: true });
      const conteudo = comSerie('PRIMEIRA');

      expect(conteudo).toContain('Publicar suplemento');
      expect(fixture.nativeElement.querySelector('dl.leitura')!.textContent).toContain('Sim');
      expect(conteudo).not.toContain('Norma legal');
    });

    it('um valor por gravar aparece como traço', () => {
      comSerie('SEGUNDA_H');

      const valores = Array.from(fixture.nativeElement.querySelectorAll('dl.leitura dd') as NodeListOf<HTMLElement>).map((dd) =>
        dd.textContent!.trim(),
      );
      expect(valores).toEqual(['—', '—', '—', '—', '—', '—', '—']);
    });

    it('continua a mostrar os itens de contexto', () => {
      fixture.componentRef.setInput('tipoInstrumento', 'Plano Diretor Municipal');
      comSerie('SEGUNDA_H');

      expect(itensDoContexto()['Tipo de instrumento']).toBe('Plano Diretor Municipal');
    });
  });

  describe('Parte do DR', () => {
    it.each([
      ['SEGUNDA_H', '2.ª Série - H'],
      ['SEGUNDA_C', '2.ª Série - C'],
      ['PRIMEIRA', '1.ª Série'],
      [null, '—'],
    ])('%s mostra "%s"', (serieDr, esperado) => {
      comSerie(serieDr);

      expect(itensDoContexto()['Parte do DR']).toBe(esperado);
    });
  });

  describe('NUT II e Concelho do Instrumento', () => {
    const sintra: TerritorioInstrumento = {
      concelhos: [{ codigo: '1111', nome: 'SINTRA' }],
      nutsII: [{ codigo: 'AML', nome: 'Área Metropolitana de Lisboa' }],
    };

    it('mostra a NUT II e o Concelho antes da Designação, como no UI-051', () => {
      fixture.componentRef.setInput('territorio', sintra);
      comSerie('SEGUNDA_H');

      const rotulos = Object.keys(itensDoContexto());
      expect(rotulos.slice(0, 5)).toEqual(['Tipo de instrumento', 'Tipo de procedimento', 'NUT II', 'Concelho', 'Designação']);
      expect(itensDoContexto()['NUT II']).toBe('Área Metropolitana de Lisboa');
      expect(itensDoContexto()['Concelho']).toBe('Sintra');
    });

    it('separa vários concelhos por vírgula', () => {
      fixture.componentRef.setInput('territorio', {
        concelhos: [
          { codigo: '1105', nome: 'Cascais' },
          { codigo: '1111', nome: 'Sintra' },
        ],
        nutsII: [{ codigo: 'AML', nome: 'Área Metropolitana de Lisboa' }],
      });
      comSerie('SEGUNDA_H');

      expect(itensDoContexto()['Concelho']).toBe('Cascais, Sintra');
      expect(itensDoContexto()['NUT II']).toBe('Área Metropolitana de Lisboa');
    });

    it('mostra traço quando o Instrumento não tem concelhos', () => {
      fixture.componentRef.setInput('territorio', { concelhos: [], nutsII: [] });
      comSerie('SEGUNDA_H');

      expect(itensDoContexto()['NUT II']).toBe('—');
      expect(itensDoContexto()['Concelho']).toBe('—');
    });

    it('mostra o código de um concelho que ainda não está na réplica do SNIT', () => {
      fixture.componentRef.setInput('territorio', { concelhos: [{ codigo: '9999', nome: null }], nutsII: [] });
      comSerie('SEGUNDA_H');

      expect(itensDoContexto()['Concelho']).toBe('9999');
      expect(itensDoContexto()['NUT II']).toBe('—');
    });

    it.each([
      ['em edição', false],
      ['em leitura', true],
    ])('%s mostra os dois em texto, sem controlo editável', (_estado, leitura) => {
      fixture.componentRef.setInput('leitura', leitura);
      fixture.componentRef.setInput('territorio', sintra);
      comSerie('SEGUNDA_H');

      const itens = Array.from(fixture.nativeElement.querySelectorAll('dl.contexto > div') as NodeListOf<HTMLElement>).filter((item) =>
        ['NUT II', 'Concelho'].includes(item.querySelector('dt')!.textContent!.trim()),
      );
      expect(itens).toHaveLength(2);
      for (const item of itens) {
        expect(item.querySelectorAll('input, select, textarea, button')).toHaveLength(0);
      }
      expect(fixture.componentInstance.form.contains('territorio')).toBe(false);
    });
  });

  describe('Medidas Preventivas do município com e sem IGT', () => {
    const sintra: TerritorioInstrumento = {
      concelhos: [{ codigo: '1111', nome: 'Sintra' }],
      nutsII: [{ codigo: 'AML', nome: 'Área Metropolitana de Lisboa' }],
    };

    beforeEach(() => {
      fixture.componentRef.setInput('formulario', 'INFORMACAO_GERAL');
      fixture.componentRef.setInput('tipoInstrumento', 'Plano Diretor Municipal');
      fixture.componentRef.setInput('tipoProcedimento', 'Publicação das medidas preventivas');
      fixture.componentRef.setInput('tiposAtoDisponiveis', [{ id: 4, codigo: 'AVISO', nome: 'Aviso' }]);
    });

    it('sem IGT mostra «Sem IGT associado», o tipo de plano, a Parte do DR só de leitura e o Tipo de ato, sem designação nem território', () => {
      fixture.componentRef.setInput('designacaoInstrumento', 'MP Sintra');
      fixture.componentRef.setInput('instrumentosAbrangidos', []);
      fixture.componentRef.setInput('territorio', { concelhos: [], nutsII: [] });
      fixture.componentRef.setInput('pedeElementosAfetados', true);
      comSerie('SEGUNDA_H');

      expect(itensDoContexto()).toEqual({
        'Tipo de instrumento': 'Plano Diretor Municipal',
        'Tipo de procedimento': 'Publicação das medidas preventivas',
        'Instrumentos associados': 'Sem IGT associado',
        'Parte do DR': '2.ª Série - H',
      });
      expect(fixture.nativeElement.querySelectorAll('dl.contexto input, dl.contexto select')).toHaveLength(0);
      expect(fixture.nativeElement.querySelector('app-seletor.tipo-ato')).not.toBeNull();
      expect(texto()).toContain('A alteração incide sobre que elementos fundamentais?');
    });

    it('sem IGT, em leitura, também não mostra a designação nem o território', () => {
      fixture.componentRef.setInput('leitura', true);
      fixture.componentRef.setInput('designacaoInstrumento', 'MP Sintra');
      fixture.componentRef.setInput('instrumentosAbrangidos', []);
      comSerie('SEGUNDA_H');

      const rotulos = Object.keys(itensDoContexto());
      expect(rotulos).toContain('Instrumentos associados');
      expect(rotulos).not.toContain('Designação');
      expect(rotulos).not.toContain('NUT II');
      expect(rotulos).not.toContain('Concelho');
    });

    it('com o PDM mostra a designação e o território do Instrumento, como hoje', () => {
      fixture.componentRef.setInput('designacaoInstrumento', 'PDM de Sintra');
      fixture.componentRef.setInput('instrumentosAbrangidos', [{ id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' }]);
      fixture.componentRef.setInput('territorio', sintra);
      comSerie('SEGUNDA_H');

      const itens = itensDoContexto();
      expect(itens['NUT II']).toBe('Área Metropolitana de Lisboa');
      expect(itens['Concelho']).toBe('Sintra');
      expect(itens['Designação']).toBe('PDM de Sintra');
      expect(itens['Parte do DR']).toBe('2.ª Série - H');
      expect(fixture.nativeElement.querySelector('app-seletor.tipo-ato')).not.toBeNull();
    });

    it('a Suspensão da CCDR sem Instrumentos continua a mostrar a Designação da Dinâmica em leitura', () => {
      fixture.componentRef.setInput('formulario', 'SUSPENSAO_CCDR');
      fixture.componentRef.setInput('leitura', true);
      fixture.componentRef.setInput('designacaoInstrumento', 'Suspensão parcial');
      fixture.componentRef.setInput('instrumentosAbrangidos', []);
      comSerie('SEGUNDA_H');

      expect(itensDoContexto()['Designação']).toBe('Suspensão parcial');
    });
  });

  it('põe os elementos fundamentais num bloco à parte, com uma caixa por linha', () => {
    comSerie('SEGUNDA_H');

    const bloco = fixture.nativeElement.querySelector('fieldset.elementos') as HTMLElement;
    expect(bloco.querySelectorAll('label.campo-checkbox')).toHaveLength(3);
  });

  it('o rádio Tipo de informação tem Texto integral e Extrato, com os valores do contrato', () => {
    const conteudo = comSerie('SEGUNDA_H');

    const radios = Array.from(fixture.nativeElement.querySelectorAll('input[type="radio"]') as NodeListOf<HTMLInputElement>);
    expect(radios.map((radio) => radio.value)).toEqual(['TEXTO_INTEGRAL', 'EXTRATO']);
    expect(conteudo).toContain('Texto integral');
    expect(conteudo).toContain('Extrato');
    expect(fixture.nativeElement.querySelectorAll('select')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('app-seletor.tipo-ato select')).not.toBeNull();
  });

  it('a Norma legal e o Sumário respeitam os limites do contrato', () => {
    comSerie('SEGUNDA_H');

    expect((fixture.nativeElement.querySelector('input[type="text"]') as HTMLInputElement).maxLength).toBe(500);
    expect((fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement).maxLength).toBe(2000);
  });

  it('o texto de ajuda do Sumário indica o limite da coluna', () => {
    comSerie('SEGUNDA_H');

    expect((fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement).placeholder).toContain('até 2000 caracteres');
  });

  describe('1.ª série', () => {
    it('não mostra Norma legal nem Tipo de informação, e mostra Publicar suplemento', () => {
      const conteudo = comSerie('PRIMEIRA');

      expect(conteudo).not.toContain('Norma legal');
      expect(conteudo).not.toContain('Tipo de informação');
      expect(fixture.nativeElement.querySelector('input[type="radio"]')).toBeNull();
      expect(conteudo).toContain('Publicar suplemento');
      expect(conteudo).toContain('Sumário conteúdo do ato');
    });

    it('não envia os campos que a série esconde e mantém a data de suplemento gravada', () => {
      valoresGravados({ normaLegal: 'Gravada antes', publicarSuplemento: true, dataSuplemento: '2026-10-01' });
      comSerie('PRIMEIRA');

      expect(fixture.componentInstance.dados()).toEqual({
        sumario: null,
        normaLegal: null,
        tipoInformacao: null,
        publicarSuplemento: true,
        dataSuplemento: '2026-10-01',
        contactoTramitacao: null,
        entidadeInstrutora: null,
        entidadeEmitente: null,
        contactoPublicacao: null,
        elementosAfetados: [],
        fundamentacaoRjigt: [],
        localizacao: null,
        conformaHierarquiaSuperior: null,
        motivosAlteracao: [],
        dinamicaAssociadaId: null,
      });
    });
  });

  it('2.ª série não mostra Publicar suplemento e não envia suplemento', () => {
    valoresGravados({ publicarSuplemento: true, dataSuplemento: '2026-10-01' });
    const conteudo = comSerie('SEGUNDA_C');

    expect(conteudo).toContain('Norma legal');
    expect(conteudo).not.toContain('Publicar suplemento');
    expect(fixture.componentInstance.dados()).toMatchObject({ publicarSuplemento: null, dataSuplemento: null });
  });

  it('sem série mostra todos os campos', () => {
    const conteudo = comSerie(null);

    expect(conteudo).toContain('Norma legal');
    expect(conteudo).toContain('Tipo de informação');
    expect(conteudo).toContain('Publicar suplemento');
  });

  describe('estados', () => {
    it('rascunho novo: valores vazios e rádio em Texto integral', () => {
      comSerie('SEGUNDA_H');

      expect(fixture.componentInstance.form.getRawValue()).toEqual({
        sumario: '',
        normaLegal: '',
        tipoInformacao: 'TEXTO_INTEGRAL',
        publicarSuplemento: false,
        contactoTramitacao: '',
        entidadeInstrutora: '',
        entidadeEmitente: '',
        contactoPublicacao: '',
        elementosAfetados: { PECAS_ESCRITAS: false, PECAS_GRAFICAS: false, OUTROS: false },
        fundamentacaoRjigt: [],
        comGeoconform: null,
        introduzPlantaNova: null,
        localizacao: { aplicavelTodaArea: false, freguesia: '', toponimo: '' },
        conformaHierarquiaSuperior: null,
        motivosAlteracao: [],
        dinamicaAssociadaId: null,
        tipoAtoId: null,
        serieDr: null,
        redesDefesa: [],
        motivoProcedimentoId: null,
        diplomaCorrigido: '',
        regiaoCodigo: null,
        municipioCodigo: null,
        regiao: null,
        cimAm: null,
      });
      const marcado = fixture.nativeElement.querySelector('input[type="radio"]:checked') as HTMLInputElement;
      expect(marcado.value).toBe('TEXTO_INTEGRAL');
      expect(fixture.componentInstance.dados()).toEqual({
        sumario: null,
        normaLegal: null,
        tipoInformacao: 'TEXTO_INTEGRAL',
        publicarSuplemento: null,
        dataSuplemento: null,
        contactoTramitacao: null,
        entidadeInstrutora: null,
        entidadeEmitente: null,
        contactoPublicacao: null,
        elementosAfetados: [],
        fundamentacaoRjigt: [],
        localizacao: null,
        conformaHierarquiaSuperior: null,
        motivosAlteracao: [],
        dinamicaAssociadaId: null,
      });
    });

    it('rascunho retomado: os campos aparecem preenchidos com os valores gravados', () => {
      valoresGravados({ sumario: 'Sumário gravado', normaLegal: 'Art. 89.º do RJIGT', tipoInformacao: 'EXTRATO' });
      comSerie('SEGUNDA_H');

      expect((fixture.nativeElement.querySelector('input[formcontrolname="normaLegal"]') as HTMLInputElement).value).toBe('Art. 89.º do RJIGT');
      expect((fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement).value).toBe('Sumário gravado');
      expect((fixture.nativeElement.querySelector('input[type="radio"]:checked') as HTMLInputElement).value).toBe('EXTRATO');
      expect(fixture.componentInstance.dados()).toEqual({
        sumario: 'Sumário gravado',
        normaLegal: 'Art. 89.º do RJIGT',
        tipoInformacao: 'EXTRATO',
        publicarSuplemento: null,
        dataSuplemento: null,
        contactoTramitacao: null,
        entidadeInstrutora: null,
        entidadeEmitente: null,
        contactoPublicacao: null,
        elementosAfetados: [],
        fundamentacaoRjigt: [],
        localizacao: null,
        conformaHierarquiaSuperior: null,
        motivosAlteracao: [],
        dinamicaAssociadaId: null,
      });
    });

    it('erro de campo: a mensagem do servidor aparece junto do campo, pelo nome do campo do pedido', () => {
      comSerie('SEGUNDA_H');
      const { sumario, normaLegal, tipoInformacao } = fixture.componentInstance.form.controls;

      sumario.setErrors({ servidor: 'Demasiado longo.' });
      normaLegal.setErrors({ servidor: 'Norma inválida.' });
      tipoInformacao.setErrors({ servidor: 'Valor inválido.' });
      fixture.detectChanges();

      const erros = (campo: string): string[] =>
        Array.from(fixture.nativeElement.querySelectorAll(`${campo} .erro-campo`) as NodeListOf<HTMLElement>).map((e) => e.textContent!.trim());
      expect(erros('label:has(textarea)')).toEqual(['Demasiado longo.']);
      expect(erros('label:has(input[formcontrolname="normaLegal"])')).toEqual(['Norma inválida.']);
      expect(erros('fieldset')).toEqual(['Valor inválido.']);
    });

    it('a carregar: os campos ficam desativados e voltam a ficar ativos', () => {
      fixture.componentRef.setInput('aCarregar', true);
      comSerie('SEGUNDA_H');

      const campos = () =>
        Array.from(fixture.nativeElement.querySelectorAll('input, textarea') as NodeListOf<HTMLInputElement | HTMLTextAreaElement>);
      expect(campos().every((campo) => campo.disabled)).toBe(true);

      fixture.componentRef.setInput('aCarregar', false);
      fixture.detectChanges();

      expect(campos().some((campo) => campo.disabled)).toBe(false);
    });

    it('ao reativar mantém a mensagem do servidor posta enquanto os campos estavam desativados', () => {
      fixture.componentRef.setInput('aCarregar', true);
      comSerie('SEGUNDA_H');
      fixture.componentInstance.form.controls.sumario.setErrors({ servidor: 'Demasiado longo.' });

      fixture.componentRef.setInput('aCarregar', false);
      fixture.detectChanges();

      expect(fixture.componentInstance.form.controls.sumario.getError('servidor')).toBe('Demasiado longo.');
      expect(fixture.nativeElement.querySelector('label:has(textarea) .erro-campo')!.textContent).toContain('Demasiado longo.');
    });
  });

  describe('contacto de tramitação e elementos fundamentais afetados', () => {
    const caixa = (rotulo: string): HTMLInputElement => {
      const opcoes = Array.from(fixture.nativeElement.querySelectorAll('label.campo-checkbox') as NodeListOf<HTMLElement>);
      return opcoes.find((opcao) => opcao.textContent!.trim() === rotulo)!.querySelector('input')!;
    };

    it('mostra o campo de contacto e as três caixas com os rótulos do UI-051', () => {
      const conteudo = comSerie('SEGUNDA_H');

      expect(conteudo).toContain('Contato para efeitos de tramitação processual');
      expect(fixture.nativeElement.querySelector('input[type="email"]')).not.toBeNull();
      expect(conteudo).toContain('A alteração incide sobre que elementos fundamentais?');
      expect(caixa('Peças escritas (Regulamento)').checked).toBe(false);
      expect(caixa('Peças gráficas (Plantas)').checked).toBe(false);
      expect(caixa('Outros').checked).toBe(false);
    });

    it('marcar uma caixa e escrever o contacto entra no pedido de gravação', () => {
      comSerie('SEGUNDA_H');
      caixa('Peças escritas (Regulamento)').click();
      const contacto = fixture.nativeElement.querySelector('input[type="email"]') as HTMLInputElement;
      contacto.value = ' tramitacao@cm-sintra.pt ';
      contacto.dispatchEvent(new Event('input'));

      expect(fixture.componentInstance.dados()).toMatchObject({
        contactoTramitacao: 'tramitacao@cm-sintra.pt',
        elementosAfetados: ['PECAS_ESCRITAS'],
      });
    });

    it('ao retomar o rascunho as caixas gravadas vêm marcadas e o contacto preenchido', () => {
      valoresGravados({ contactoTramitacao: 'tramitacao@cm-sintra.pt', elementosAfetados: ['PECAS_ESCRITAS', 'OUTROS'] });
      comSerie('SEGUNDA_H');

      expect(caixa('Peças escritas (Regulamento)').checked).toBe(true);
      expect(caixa('Peças gráficas (Plantas)').checked).toBe(false);
      expect(caixa('Outros').checked).toBe(true);
      expect((fixture.nativeElement.querySelector('input[type="email"]') as HTMLInputElement).value).toBe('tramitacao@cm-sintra.pt');
      expect(fixture.componentInstance.dados()).toMatchObject({ elementosAfetados: ['PECAS_ESCRITAS', 'OUTROS'] });
    });

    it('mostra junto dos campos os erros do servidor de contactoTramitacao e elementosAfetados', () => {
      comSerie('SEGUNDA_H');
      const { contactoTramitacao, elementosAfetados } = fixture.componentInstance.form.controls;
      contactoTramitacao.setErrors({ servidor: 'Indique um email válido.' });
      elementosAfetados.setErrors({ servidor: 'Indique os elementos fundamentais afetados.' });
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('label:has(input[type="email"]) .erro-campo')!.textContent).toContain(
        'Indique um email válido.',
      );
      expect(fixture.nativeElement.querySelector('fieldset:has(input[type="checkbox"]) .erro-campo')!.textContent).toContain(
        'Indique os elementos fundamentais afetados.',
      );
    });

    it('avisa logo de um contacto sem formato de email depois de sair do campo', () => {
      comSerie('SEGUNDA_H');
      const contacto = fixture.nativeElement.querySelector('input[type="email"]') as HTMLInputElement;
      contacto.value = 'sem-arroba';
      contacto.dispatchEvent(new Event('input'));
      contacto.dispatchEvent(new Event('blur'));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('label:has(input[type="email"]) .erro-campo')!.textContent).toContain(
        'Indique um email válido.',
      );
    });

    it('em leitura mostra o contacto e os elementos gravados', () => {
      fixture.componentRef.setInput('leitura', true);
      valoresGravados({ contactoTramitacao: 'tramitacao@cm-sintra.pt', elementosAfetados: ['PECAS_GRAFICAS'] });
      const conteudo = comSerie('SEGUNDA_H');

      expect(conteudo).toContain('tramitacao@cm-sintra.pt');
      expect(conteudo).toContain('Peças gráficas (Plantas)');
      expect(fixture.nativeElement.querySelector('input')).toBeNull();
    });
  });

  describe('entidade emitente e contacto para publicação', () => {
    const campo = (nome: string): HTMLInputElement => fixture.nativeElement.querySelector(`input[formcontrolname="${nome}"]`) as HTMLInputElement;
    const rotulos = (): string[] =>
      Array.from(fixture.nativeElement.querySelectorAll('form.campos > label.campo > .rotulo') as NodeListOf<HTMLElement>).map((r) =>
        r.textContent!.trim(),
      );
    const escrever = (controlo: HTMLInputElement, valor: string): void => {
      controlo.value = valor;
      controlo.dispatchEvent(new Event('input'));
    };

    it('mostra os dois campos com os rótulos e a ordem do UI-051', () => {
      comSerie('SEGUNDA_H');

      expect(rotulos().slice(0, 4)).toEqual([
        'Identificação da entidade emitente',
        'Contato para efeitos de tramitação processual',
        'Contato para publicação',
        'Norma legal ou mera conveniência',
      ]);
    });

    it('mostra os dois campos também na 1.ª série', () => {
      comSerie('PRIMEIRA');

      expect(rotulos().slice(0, 3)).toEqual([
        'Identificação da entidade emitente',
        'Contato para efeitos de tramitação processual',
        'Contato para publicação',
      ]);
    });

    it('limita os dois campos a 500 caracteres', () => {
      comSerie('SEGUNDA_H');

      expect(campo('entidadeEmitente').maxLength).toBe(500);
      expect(campo('contactoPublicacao').maxLength).toBe(500);
    });

    it('escrever nos dois campos entra no pedido de gravação sem os espaços das pontas', () => {
      comSerie('SEGUNDA_H');
      escrever(campo('entidadeEmitente'), '  Câmara Municipal de Sintra ');
      escrever(campo('contactoPublicacao'), ' Gabinete de Planeamento ');

      expect(fixture.componentInstance.dados()).toMatchObject({
        entidadeEmitente: 'Câmara Municipal de Sintra',
        contactoPublicacao: 'Gabinete de Planeamento',
      });
    });

    it('um campo só com espaços vale nulo no pedido', () => {
      comSerie('SEGUNDA_H');
      escrever(campo('entidadeEmitente'), '   ');

      expect(fixture.componentInstance.dados()).toMatchObject({ entidadeEmitente: null, contactoPublicacao: null });
    });

    it('ao retomar o rascunho os dois campos vêm preenchidos', () => {
      valoresGravados({ entidadeEmitente: 'Câmara Municipal de Sintra', contactoPublicacao: 'Gabinete de Planeamento' });
      comSerie('SEGUNDA_H');

      expect(campo('entidadeEmitente').value).toBe('Câmara Municipal de Sintra');
      expect(campo('contactoPublicacao').value).toBe('Gabinete de Planeamento');
    });

    it('mostra junto de cada campo o erro do servidor e liga-o por aria-describedby', () => {
      comSerie('SEGUNDA_H');
      const { entidadeEmitente, contactoPublicacao } = fixture.componentInstance.form.controls;
      entidadeEmitente.setErrors({ servidor: 'Indique a identificação da entidade emitente.' });
      contactoPublicacao.setErrors({ servidor: 'Máximo de 500 caracteres.' });
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('label:has(input[formcontrolname="entidadeEmitente"]) .erro-campo')!.textContent).toContain(
        'Indique a identificação da entidade emitente.',
      );
      expect(fixture.nativeElement.querySelector('label:has(input[formcontrolname="contactoPublicacao"]) .erro-campo')!.textContent).toContain(
        'Máximo de 500 caracteres.',
      );
      expect(campo('entidadeEmitente').getAttribute('aria-describedby')).toBe('erro-entidadeEmitente');
      expect(campo('contactoPublicacao').getAttribute('aria-describedby')).toBe('erro-contactoPublicacao');
      expect(fixture.nativeElement.querySelector('#erro-entidadeEmitente')).not.toBeNull();
    });

    it('sem erro nem bloqueio os campos não têm aria-describedby', () => {
      comSerie('SEGUNDA_H');

      expect(campo('entidadeEmitente').hasAttribute('aria-describedby')).toBe(false);
      expect(campo('contactoPublicacao').hasAttribute('aria-describedby')).toBe(false);
    });

    it('em leitura mostra os valores gravados e traço no que está vazio', () => {
      fixture.componentRef.setInput('leitura', true);
      valoresGravados({ entidadeEmitente: 'Câmara Municipal de Sintra' });
      comSerie('SEGUNDA_H');

      const itens = Object.fromEntries(
        Array.from(fixture.nativeElement.querySelectorAll('dl.leitura > div') as NodeListOf<HTMLElement>).map((item) => [
          item.querySelector('dt')!.textContent!.trim(),
          item.querySelector('dd')!.textContent!.trim(),
        ]),
      );
      expect(itens['Identificação da entidade emitente']).toBe('Câmara Municipal de Sintra');
      expect(itens['Contato para publicação']).toBe('—');
      expect(fixture.nativeElement.querySelector('input')).toBeNull();
    });

    it('depois da primeira submissão desativa os campos, avisa e liga o aviso', () => {
      fixture.componentRef.setInput('valoresIniciais', {
        entidadeEmitente: 'Câmara Municipal de Sintra',
        contactoPublicacao: 'Gabinete de Planeamento',
      } satisfies FormularioInicialValores);
      fixture.componentRef.setInput('camposBloqueados', ['entidadeEmitente', 'contactoPublicacao']);
      comSerie('SEGUNDA_H');

      expect(campo('entidadeEmitente').disabled).toBe(true);
      expect(campo('contactoPublicacao').disabled).toBe(true);
      expect(campo('entidadeEmitente').getAttribute('aria-describedby')).toBe('bloqueado-entidadeEmitente');
      expect(fixture.nativeElement.querySelector('#bloqueado-contactoPublicacao').textContent).toContain('Bloqueado depois da primeira submissão');
      expect(fixture.componentInstance.dados()).toMatchObject({
        entidadeEmitente: 'Câmara Municipal de Sintra',
        contactoPublicacao: 'Gabinete de Planeamento',
      });
    });
  });

  describe('entidade instrutora dos Programas', () => {
    const campo = (): HTMLInputElement | null => fixture.nativeElement.querySelector('input[formcontrolname="entidadeInstrutora"]');
    const rotulos = (): string[] =>
      Array.from(fixture.nativeElement.querySelectorAll('form.campos > label.campo > .rotulo') as NodeListOf<HTMLElement>).map((r) =>
        r.textContent!.trim(),
      );
    const leitura = (): Record<string, string> =>
      Object.fromEntries(
        Array.from(fixture.nativeElement.querySelectorAll('dl.leitura > div') as NodeListOf<HTMLElement>).map((item) => [
          item.querySelector('dt')!.textContent!.trim(),
          item.querySelector('dd')!.textContent!.trim(),
        ]),
      );

    it('num Programa mostra «Entidade instrutora» antes da entidade emitente, obrigatório e com 500 caracteres', () => {
      fixture.componentRef.setInput('categoriaInstrumento', 'PROGRAMA');
      comSerie('PRIMEIRA');

      expect(rotulos().slice(0, 2)).toEqual(['Entidade instrutora', 'Identificação da entidade emitente']);
      expect(campo()!.getAttribute('aria-required')).toBe('true');
      expect(campo()!.maxLength).toBe(500);
      expect(campo()!.closest('label')!.querySelector('.rotulo')!.textContent).toContain('Entidade instrutora');
    });

    it('num Programa em que não é obrigatória (PS) mostra o campo sem aria-required', () => {
      fixture.componentRef.setInput('categoriaInstrumento', 'PROGRAMA');
      fixture.componentRef.setInput('entidadeInstrutoraObrigatoria', false);
      comSerie('PRIMEIRA');

      expect(rotulos()).toContain('Entidade instrutora');
      expect(campo()!.hasAttribute('aria-required')).toBe(false);
    });

    it.each(['PLANO', 'SRUP', null])('fora dos Programas (%s) o campo não aparece nem vai no pedido', (categoria) => {
      fixture.componentRef.setInput('categoriaInstrumento', categoria);
      valoresGravados({ entidadeInstrutora: 'Valor antigo' });
      comSerie('SEGUNDA_H');

      expect(campo()).toBeNull();
      expect(texto()).not.toContain('Entidade instrutora');
      expect(fixture.componentInstance.dados().entidadeInstrutora).toBeNull();
    });

    it('aparece quando a categoria passa a Programa, sem recriar o cartão', () => {
      fixture.componentRef.setInput('categoriaInstrumento', null);
      comSerie('PRIMEIRA');
      expect(campo()).toBeNull();

      fixture.componentRef.setInput('categoriaInstrumento', 'PROGRAMA');
      fixture.detectChanges();

      expect(campo()).not.toBeNull();
    });

    it('entra no pedido sem os espaços das pontas; só espaços vale nulo; ao retomar vem preenchido', () => {
      fixture.componentRef.setInput('categoriaInstrumento', 'PROGRAMA');
      valoresGravados({ entidadeInstrutora: 'CCDR de Lisboa' });
      comSerie('PRIMEIRA');
      expect(campo()!.value).toBe('CCDR de Lisboa');

      campo()!.value = '  Comissão de Coordenação ';
      campo()!.dispatchEvent(new Event('input'));
      expect(fixture.componentInstance.dados().entidadeInstrutora).toBe('Comissão de Coordenação');

      campo()!.value = '   ';
      campo()!.dispatchEvent(new Event('input'));
      expect(fixture.componentInstance.dados().entidadeInstrutora).toBeNull();
    });

    it('mostra o erro do servidor junto do campo, anunciado e ligado por aria-describedby', () => {
      fixture.componentRef.setInput('categoriaInstrumento', 'PROGRAMA');
      comSerie('PRIMEIRA');
      fixture.componentInstance.form.controls.entidadeInstrutora.setErrors({ servidor: 'Indique a entidade instrutora.' });
      fixture.detectChanges();

      const erro = fixture.nativeElement.querySelector('#erro-entidadeInstrutora') as HTMLElement;
      expect(erro.textContent).toContain('Indique a entidade instrutora.');
      expect(erro.getAttribute('role')).toBe('alert');
      expect(erro.closest('label')!.querySelector('input[formcontrolname="entidadeInstrutora"]')).not.toBeNull();
      expect(campo()!.getAttribute('aria-describedby')).toBe('erro-entidadeInstrutora');
      expect(campo()!.getAttribute('aria-invalid')).toBe('true');
    });

    it('aplica o erro recebido em errosServidor ao campo', async () => {
      fixture.componentRef.setInput('categoriaInstrumento', 'PROGRAMA');
      comSerie('PRIMEIRA');
      fixture.componentRef.setInput('errosServidor', [{ path: 'entidadeInstrutora', erro: 'Indique a entidade instrutora.' }]);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('#erro-entidadeInstrutora')?.textContent).toContain('Indique a entidade instrutora.');
    });

    it('em leitura mostra a linha num Programa, com traço quando vazia, e não noutras categorias', () => {
      fixture.componentRef.setInput('leitura', true);
      fixture.componentRef.setInput('categoriaInstrumento', 'PROGRAMA');
      comSerie('PRIMEIRA');
      expect(leitura()['Entidade instrutora']).toBe('—');
      expect(Object.keys(leitura()).indexOf('Entidade instrutora')).toBeLessThan(
        Object.keys(leitura()).indexOf('Identificação da entidade emitente'),
      );

      fixture.componentRef.setInput('categoriaInstrumento', 'PLANO');
      fixture.detectChanges();
      expect('Entidade instrutora' in leitura()).toBe(false);
    });

    it('depois da primeira submissão fica desativado com o aviso', () => {
      fixture.componentRef.setInput('categoriaInstrumento', 'PROGRAMA');
      fixture.componentRef.setInput('valoresIniciais', { entidadeInstrutora: 'CCDR de Lisboa' } satisfies FormularioInicialValores);
      fixture.componentRef.setInput('camposBloqueados', ['entidadeInstrutora']);
      comSerie('PRIMEIRA');

      expect(campo()!.disabled).toBe(true);
      expect(campo()!.getAttribute('aria-describedby')).toBe('bloqueado-entidadeInstrutora');
      expect(fixture.componentInstance.dados().entidadeInstrutora).toBe('CCDR de Lisboa');
    });
  });

  describe('depois da primeira submissão', () => {
    const TODAS =['sumario', 'normaLegal', 'tipoInformacao', 'contactoTramitacao', 'elementosAfetados'];
    const AVISO = 'Bloqueado depois da primeira submissão';

    function abrirBloqueado(camposBloqueados: string[]): void {
      fixture.componentRef.setInput('valoresIniciais', {
        sumario: 'Resumo gravado',
        normaLegal: 'Norma gravada',
        tipoInformacao: 'EXTRATO',
        contactoTramitacao: 'tramitacao@cm-sintra.pt',
        elementosAfetados: ['PECAS_ESCRITAS', 'OUTROS'],
      } satisfies FormularioInicialValores);
      fixture.componentRef.setInput('camposBloqueados', camposBloqueados);
      comSerie('SEGUNDA_H');
    }

    it('desativa os controlos das chaves bloqueadas', () => {
      abrirBloqueado(TODAS);

      const { sumario, normaLegal, tipoInformacao, contactoTramitacao, elementosAfetados } = fixture.componentInstance.form.controls;
      [sumario, normaLegal, tipoInformacao, contactoTramitacao, elementosAfetados].forEach((controlo) => expect(controlo.disabled).toBe(true));
      expect(fixture.nativeElement.querySelector('textarea').disabled).toBe(true);
      expect(fixture.nativeElement.querySelector('input[type="email"]').disabled).toBe(true);
      expect(Array.from(fixture.nativeElement.querySelectorAll('input[type="checkbox"]') as NodeListOf<HTMLInputElement>).every((c) => c.disabled)).toBe(true);
    });

    it('mostra o aviso por baixo de cada campo e liga-o por aria-describedby', () => {
      abrirBloqueado(TODAS);

      const avisos = Array.from(fixture.nativeElement.querySelectorAll('.aviso-bloqueado') as NodeListOf<HTMLElement>);
      expect(avisos).toHaveLength(TODAS.length);
      avisos.forEach((aviso) => expect(aviso.textContent!.trim()).toBe(AVISO));
      const sumario = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
      expect(sumario.getAttribute('aria-describedby')).toBe('bloqueado-sumario');
      expect(fixture.nativeElement.querySelector('#bloqueado-sumario').textContent).toContain(AVISO);
      expect(fixture.nativeElement.querySelector('fieldset.elementos').getAttribute('aria-describedby')).toBe('bloqueado-elementosAfetados');
    });

    it('dados() continua a devolver os valores gravados dos campos desativados', () => {
      abrirBloqueado(TODAS);

      expect(fixture.componentInstance.dados()).toMatchObject({
        sumario: 'Resumo gravado',
        normaLegal: 'Norma gravada',
        tipoInformacao: 'EXTRATO',
        contactoTramitacao: 'tramitacao@cm-sintra.pt',
        elementosAfetados: ['PECAS_ESCRITAS', 'OUTROS'],
      });
    });

    it('só desativa as chaves da lista', () => {
      abrirBloqueado(['sumario']);

      const { sumario, normaLegal } = fixture.componentInstance.form.controls;
      expect(sumario.disabled).toBe(true);
      expect(normaLegal.enabled).toBe(true);
      expect(fixture.nativeElement.querySelectorAll('.aviso-bloqueado')).toHaveLength(1);
    });

    it('sem camposBloqueados nada fica desativado nem avisado', () => {
      abrirBloqueado([]);

      expect(fixture.componentInstance.form.enabled).toBe(true);
      expect(fixture.nativeElement.querySelector('.aviso-bloqueado')).toBeNull();
    });

    it('depois de uma gravação os campos bloqueados continuam desativados e os outros reativam-se', () => {
      abrirBloqueado(['sumario']);
      fixture.componentRef.setInput('aCarregar', true);
      fixture.detectChanges();
      expect(fixture.componentInstance.form.disabled).toBe(true);

      fixture.componentRef.setInput('aCarregar', false);
      fixture.detectChanges();

      const { sumario, normaLegal, contactoTramitacao } = fixture.componentInstance.form.controls;
      expect(sumario.disabled).toBe(true);
      expect(normaLegal.enabled).toBe(true);
      expect(contactoTramitacao.enabled).toBe(true);
    });
  });

  describe('Fundamentação RJIGT', () => {
    const OPCOES = [
      { codigo: 'ART72A', descricao: 'Art. 72.º-A' },
      { codigo: 'ART72B', descricao: 'Art. 72.º-B' },
    ];

    function abrir(pede: boolean, opcoes = OPCOES, valores: FormularioInicialValores = {}, bloqueados: string[] = []): void {
      fixture.componentRef.setInput('pedeFundamentacaoRjigt', pede);
      fixture.componentRef.setInput('opcoesFundamentacaoRjigt', opcoes);
      fixture.componentRef.setInput('valoresIniciais', valores);
      fixture.componentRef.setInput('camposBloqueados', bloqueados);
      comSerie('SEGUNDA_H');
    }

    function grupo(): HTMLFieldSetElement | null {
      return fixture.nativeElement.querySelector('fieldset.fundamentacao');
    }

    function caixas(): HTMLInputElement[] {
      return Array.from(grupo()!.querySelectorAll('input[type="checkbox"]'));
    }

    it('mostra o grupo com uma caixa por opção, rotulada pela descrição', () => {
      abrir(true);

      expect(grupo()!.querySelector('legend')!.textContent!.trim()).toBe('Fundamentação RJIGT');
      const rotulos = Array.from(grupo()!.querySelectorAll('label.campo-checkbox')).map((l) => l.textContent!.trim());
      expect(rotulos).toEqual(['Art. 72.º-A', 'Art. 72.º-B']);
      expect(caixas().every((caixa) => caixa.closest('label') !== null)).toBe(true);
    });

    it('não mostra o grupo quando o Procedimento não pede o campo', () => {
      abrir(false);

      expect(grupo()).toBeNull();
      expect(texto()).not.toContain('Fundamentação RJIGT');
    });

    it('não mostra o grupo quando a lista não tem opções', () => {
      abrir(true, []);

      expect(grupo()).toBeNull();
    });

    it('marca o valor gravado', () => {
      abrir(true, OPCOES, { fundamentacaoRjigt: ['ART72B'] });

      expect(caixas().map((caixa) => caixa.checked)).toEqual([false, true]);
    });

    it('marcar duas caixas leva os dois códigos no pedido, pela ordem das opções', () => {
      abrir(true);

      caixas()[1].click();
      caixas()[0].click();
      fixture.detectChanges();

      expect(fixture.componentInstance.dados().fundamentacaoRjigt).toEqual(['ART72A', 'ART72B']);
      caixas()[1].click();
      expect(fixture.componentInstance.dados().fundamentacaoRjigt).toEqual(['ART72A']);
    });

    it('não reenvia um código gravado que já não está nas opções', () => {
      abrir(true, OPCOES, { fundamentacaoRjigt: ['ART72A', 'DESATIVADO'] });

      expect(fixture.componentInstance.dados().fundamentacaoRjigt).toEqual(['ART72A']);
    });

    it('envia lista vazia quando o grupo não aparece', () => {
      abrir(false, OPCOES, { fundamentacaoRjigt: ['ART72A'] });

      expect(fixture.componentInstance.dados().fundamentacaoRjigt).toEqual([]);
    });

    it('mostra o erro do servidor junto do grupo e liga-o por aria-describedby', () => {
      abrir(true);
      fixture.componentInstance.form.controls.fundamentacaoRjigt.setErrors({ servidor: 'Fundamentação RJIGT inválida.' });
      fixture.detectChanges();

      const erro = grupo()!.querySelector('#erro-fundamentacaoRjigt') as HTMLElement;
      expect(erro.textContent!.trim()).toBe('Fundamentação RJIGT inválida.');
      expect(erro.getAttribute('role')).toBe('alert');
      expect(grupo()!.getAttribute('aria-describedby')).toBe('erro-fundamentacaoRjigt');
    });

    it('sem erro nem bloqueio o grupo não tem aria-describedby', () => {
      abrir(true);

      expect(grupo()!.hasAttribute('aria-describedby')).toBe(false);
    });

    it('depois da primeira submissão desativa as caixas, avisa e reenvia o valor gravado', () => {
      abrir(true, OPCOES, { fundamentacaoRjigt: ['ART72B', 'DESATIVADO'] }, ['fundamentacaoRjigt']);

      expect(caixas().every((caixa) => caixa.disabled)).toBe(true);
      expect(grupo()!.querySelector('#bloqueado-fundamentacaoRjigt')).not.toBeNull();
      expect(grupo()!.getAttribute('aria-describedby')).toBe('bloqueado-fundamentacaoRjigt');
      expect(fixture.componentInstance.dados().fundamentacaoRjigt).toEqual(['ART72B', 'DESATIVADO']);
    });

    it('a carregar as caixas ficam desativadas e voltam a ficar ativas', () => {
      abrir(true);
      fixture.componentRef.setInput('aCarregar', true);
      fixture.detectChanges();
      expect(caixas().every((caixa) => caixa.disabled)).toBe(true);

      fixture.componentRef.setInput('aCarregar', false);
      fixture.detectChanges();
      expect(caixas().every((caixa) => !caixa.disabled)).toBe(true);
    });

    it('em leitura mostra as descrições gravadas, ou traço', () => {
      fixture.componentRef.setInput('leitura', true);
      abrir(true, OPCOES, { fundamentacaoRjigt: ['ART72B'] });
      expect(texto()).toContain('Art. 72.º-B');

      fixture.componentRef.setInput('valoresIniciais', { fundamentacaoRjigt: [] });
      fixture.detectChanges();
      const linha = Array.from(fixture.nativeElement.querySelectorAll('dl.leitura > div') as NodeListOf<HTMLElement>).find(
        (item) => item.querySelector('dt')!.textContent!.includes('Fundamentação RJIGT'),
      );
      expect(linha!.querySelector('dd')!.textContent!.trim()).toBe('—');
    });
  });

  describe('pergunta do GeoConform pela configuração do Procedimento', () => {
    const GRAFICAS = 'Peças gráficas (Plantas)';
    const ESCRITAS = 'Peças escritas (Regulamento)';
    const EM_FALTA = 'Indique se utiliza o GeoConform.';

    const caixa = (rotulo: string): HTMLInputElement => {
      const opcoes = Array.from(fixture.nativeElement.querySelectorAll('label.campo-checkbox') as NodeListOf<HTMLElement>);
      return opcoes.find((opcao) => opcao.textContent!.trim() === rotulo)!.querySelector('input')!;
    };
    const pergunta = (): HTMLElement | null => fixture.nativeElement.querySelector('fieldset.geoconform');
    const opcao = (rotulo: 'Sim' | 'Não'): HTMLInputElement => {
      const opcoes = Array.from(pergunta()!.querySelectorAll('label.opcao-geoconform') as NodeListOf<HTMLElement>);
      return opcoes.find((item) => item.textContent!.trim() === rotulo)!.querySelector('input')!;
    };
    const marcar = (controlo: HTMLInputElement): void => {
      controlo.click();
      fixture.detectChanges();
    };

    function abrir(perguntaGeoconform: 'NAO_APLICAVEL' | 'OBRIGATORIA' | 'SE_PECAS_GRAFICAS' | null, exigeOpcaoGeoconform = false): void {
      fixture.componentRef.setInput('formulario', 'INFORMACAO_GERAL');
      fixture.componentRef.setInput('perguntaGeoconform', perguntaGeoconform);
      fixture.componentRef.setInput('exigeOpcaoGeoconform', exigeOpcaoGeoconform);
      comSerie('SEGUNDA_H');
    }

    it('com SE_PECAS_GRAFICAS a pergunta aparece ao assinalar «Peças gráficas», sem gravar', () => {
      abrir('SE_PECAS_GRAFICAS');
      expect(pergunta()).toBeNull();

      marcar(caixa(GRAFICAS));

      expect(pergunta()!.querySelector('legend')!.textContent).toContain(
        'Vai fazer uso da plataforma Geoconform para a validação da informação e emissão das plantas?',
      );
      marcar(opcao('Não'));
      expect(fixture.componentInstance.dados()).toMatchObject({ elementosAfetados: ['PECAS_GRAFICAS'], comGeoconform: false });
    });

    it('ao desmarcar «Peças gráficas» a pergunta desaparece e a resposta deixa de ser enviada', () => {
      abrir('SE_PECAS_GRAFICAS');
      marcar(caixa(GRAFICAS));
      marcar(opcao('Sim'));

      marcar(caixa(GRAFICAS));

      expect(pergunta()).toBeNull();
      expect('comGeoconform' in fixture.componentInstance.dados()).toBe(false);
    });

    it('com a pergunta visível e sem resposta não deixa avançar: erro junto do grupo e foco na primeira opção', () => {
      abrir('SE_PECAS_GRAFICAS');
      marcar(caixa(GRAFICAS));

      expect(fixture.componentInstance.validarOpcaoGeoconform()).toBe(false);
      fixture.detectChanges();

      const erro = fixture.nativeElement.querySelector('#erro-comGeoconform') as HTMLElement;
      expect(erro.textContent!.trim()).toBe(EM_FALTA);
      expect(erro.getAttribute('role')).toBe('alert');
      expect(pergunta()!.getAttribute('aria-describedby')).toBe('erro-comGeoconform');
      expect(document.activeElement).toBe(opcao('Sim'));

      marcar(opcao('Não'));

      expect(fixture.nativeElement.querySelector('#erro-comGeoconform')).toBeNull();
      expect(fixture.componentInstance.validarOpcaoGeoconform()).toBe(true);
    });

    it('só com «Peças escritas» a pergunta não aparece, não é exigida nem enviada', () => {
      abrir('SE_PECAS_GRAFICAS');
      marcar(caixa(ESCRITAS));

      expect(pergunta()).toBeNull();
      expect(fixture.componentInstance.validarOpcaoGeoconform()).toBe(true);
      expect('comGeoconform' in fixture.componentInstance.dados()).toBe(false);
    });

    it('com OBRIGATORIA a pergunta aparece logo, sem depender dos elementos afetados', () => {
      abrir('OBRIGATORIA');

      expect(pergunta()).not.toBeNull();
      expect(fixture.componentInstance.validarOpcaoGeoconform()).toBe(false);
    });

    it('com NAO_APLICAVEL não pergunta, mesmo com «Peças gráficas»', () => {
      abrir('NAO_APLICAVEL');
      marcar(caixa(GRAFICAS));

      expect(pergunta()).toBeNull();
      expect('comGeoconform' in fixture.componentInstance.dados()).toBe(false);
    });

    it('sem a configuração (IGT novo antes do detalhe) segue `exigeOpcaoGeoconform`', () => {
      abrir(null, true);

      expect(pergunta()).not.toBeNull();
    });

    it('a resposta já gravada na Dinâmica vem escolhida', () => {
      fixture.componentRef.setInput('comGeoconform', false);
      fixture.componentRef.setInput('valoresIniciais', { elementosAfetados: ['PECAS_GRAFICAS'] });
      abrir('SE_PECAS_GRAFICAS', true);

      expect(opcao('Não').checked).toBe(true);
      expect(fixture.componentInstance.validarOpcaoGeoconform()).toBe(true);
    });
  });

  describe('Publicação da Alteração', () => {
    const GRAFICAS = 'Peças gráficas (Plantas)';
    const ESCRITAS = 'Peças escritas (Regulamento)';
    const AJUDA_GEOCONFORM = 'Numa dinâmica com GeoConform, a alteração afeta sempre as Peças Gráficas.';
    const AJUDA_PLANTA_NOVA = 'Introduzir uma planta nova exige alteração ao regulamento (Peças Escritas).';
    const ERRO_REGULAMENTO = 'Introduzir uma planta nova exige alteração ao regulamento: assinale as Peças Escritas.';
    const AVISO_BLOQUEADO = 'Bloqueado depois da primeira submissão';

    function abrir(
      opcoes: {
        comGeoconform?: boolean | null;
        valores?: FormularioInicialValores;
        camposBloqueados?: string[];
        formulario?: 'INFORMACAO_GERAL' | 'PUBLICACAO_ALTERACAO';
      } = {},
    ): void {
      fixture.componentRef.setInput('formulario', opcoes.formulario ?? 'PUBLICACAO_ALTERACAO');
      fixture.componentRef.setInput('comGeoconform', opcoes.comGeoconform ?? null);
      if (opcoes.valores) {
        fixture.componentRef.setInput('valoresIniciais', opcoes.valores);
      }
      if (opcoes.camposBloqueados) {
        fixture.componentRef.setInput('camposBloqueados', opcoes.camposBloqueados);
      }
      comSerie('SEGUNDA_H');
    }

    const caixa = (rotulo: string): HTMLInputElement => {
      const opcoes = Array.from(fixture.nativeElement.querySelectorAll('label.campo-checkbox') as NodeListOf<HTMLElement>);
      return opcoes.find((opcao) => opcao.textContent!.trim() === rotulo)!.querySelector('input')!;
    };
    const pergunta = (): HTMLElement | null => fixture.nativeElement.querySelector('fieldset.planta-nova');
    const opcao = (rotulo: 'Sim' | 'Não'): HTMLInputElement => {
      const opcoes = Array.from(pergunta()!.querySelectorAll('label.opcao-geoconform') as NodeListOf<HTMLElement>);
      return opcoes.find((item) => item.textContent!.trim() === rotulo)!.querySelector('input')!;
    };
    const marcar = (controlo: HTMLInputElement): void => {
      controlo.click();
      fixture.detectChanges();
    };

    describe('numa Dinâmica com GeoConform', () => {
      it('«Peças Gráficas» fica marcada e desativada, com a ajuda ligada por aria-describedby', () => {
        abrir({ comGeoconform: true });

        expect(caixa(GRAFICAS).checked).toBe(true);
        expect(caixa(GRAFICAS).disabled).toBe(true);
        expect(caixa(GRAFICAS).getAttribute('aria-describedby')).toBe('ajuda-pecasGraficas');
        expect(fixture.nativeElement.querySelector('#ajuda-pecasGraficas').textContent.trim()).toBe(AJUDA_GEOCONFORM);
        expect(caixa(ESCRITAS).disabled).toBe(false);
      });

      it('nem a pergunta da planta nova nem a do GeoConform aparecem', () => {
        abrir({ comGeoconform: true });

        expect(pergunta()).toBeNull();
        expect(fixture.nativeElement.querySelector('fieldset.geoconform')).toBeNull();
        expect(texto()).not.toContain('A alteração introduz alguma planta nova?');
      });

      it('o valor da opção fixa segue no pedido, com a resposta nula', () => {
        abrir({ comGeoconform: true });

        expect(fixture.componentInstance.dados()).toMatchObject({ elementosAfetados: ['PECAS_GRAFICAS'], introduzPlantaNova: null });
      });

      it('uma resposta gravada antes deixa de ir no pedido', () => {
        abrir({ comGeoconform: true, valores: { dadosFormulario: '{"introduzPlantaNova":true}' } });

        expect(fixture.componentInstance.form.controls.introduzPlantaNova.value).toBeNull();
        expect(fixture.componentInstance.dados().introduzPlantaNova).toBeNull();
      });

      it('depois de uma gravação a opção continua marcada e desativada', () => {
        abrir({ comGeoconform: true });
        fixture.componentRef.setInput('aCarregar', true);
        fixture.detectChanges();
        fixture.componentRef.setInput('aCarregar', false);
        fixture.detectChanges();

        expect(caixa(GRAFICAS).checked).toBe(true);
        expect(caixa(GRAFICAS).disabled).toBe(true);
        expect(caixa(ESCRITAS).disabled).toBe(false);
      });
    });

    describe('numa Dinâmica sem GeoConform', () => {
      it('a pergunta só aparece com «Peças Gráficas» assinalada e desaparece ao desmarcar, com o valor nulo', () => {
        abrir({ comGeoconform: false });
        expect(pergunta()).toBeNull();

        marcar(caixa(GRAFICAS));
        expect(pergunta()).not.toBeNull();
        marcar(opcao('Sim'));
        expect(fixture.componentInstance.dados().introduzPlantaNova).toBe(true);

        marcar(caixa(GRAFICAS));
        expect(pergunta()).toBeNull();
        expect(fixture.componentInstance.form.controls.introduzPlantaNova.value).toBeNull();
        expect(fixture.componentInstance.dados().introduzPlantaNova).toBeNull();
      });

      it('a pergunta também aparece com a resposta ao GeoConform ainda por dar', () => {
        abrir({ comGeoconform: null });

        marcar(caixa(GRAFICAS));

        expect(pergunta()).not.toBeNull();
      });

      it('a pergunta é um grupo com legenda e as opções Sim e Não, escolhidas pelo mesmo nome', () => {
        abrir({ comGeoconform: false, valores: { elementosAfetados: ['PECAS_GRAFICAS'] } });

        expect(pergunta()!.querySelector('legend')!.textContent!.trim()).toBe('A alteração introduz alguma planta nova?');
        const radios = Array.from(pergunta()!.querySelectorAll('input[type="radio"]') as NodeListOf<HTMLInputElement>);
        expect(radios.map((radio) => radio.name)).toEqual(['introduzPlantaNova', 'introduzPlantaNova']);
        expect(opcao('Sim')).toBeTruthy();
        expect(opcao('Não')).toBeTruthy();
      });

      it('a ajuda do regulamento é texto ligado ao grupo por aria-describedby', () => {
        abrir({ comGeoconform: false, valores: { elementosAfetados: ['PECAS_GRAFICAS'] } });

        expect(fixture.nativeElement.querySelector('#ajuda-introduzPlantaNova').textContent.trim()).toBe(AJUDA_PLANTA_NOVA);
        expect(pergunta()!.getAttribute('aria-describedby')).toBe('ajuda-introduzPlantaNova');
      });

      it('escolher Sim ou Não entra no pedido de gravação', () => {
        abrir({ comGeoconform: false, valores: { elementosAfetados: ['PECAS_GRAFICAS'] } });

        marcar(opcao('Não'));
        expect(fixture.componentInstance.dados().introduzPlantaNova).toBe(false);
        marcar(opcao('Sim'));
        expect(fixture.componentInstance.dados().introduzPlantaNova).toBe(true);
      });

      it('ao retomar o rascunho a resposta gravada em dadosFormulario vem marcada', () => {
        abrir({
          comGeoconform: false,
          valores: { elementosAfetados: ['PECAS_GRAFICAS', 'PECAS_ESCRITAS'], dadosFormulario: '{"introduzPlantaNova":true}' },
        });

        expect(opcao('Sim').checked).toBe(true);
        expect(opcao('Não').checked).toBe(false);
        expect(fixture.componentInstance.dados().introduzPlantaNova).toBe(true);
      });

      it('o que acabou de se gravar tem prioridade sobre dadosFormulario', () => {
        abrir({
          comGeoconform: false,
          valores: { elementosAfetados: ['PECAS_GRAFICAS'], introduzPlantaNova: false, dadosFormulario: '{"introduzPlantaNova":true}' },
        });

        expect(opcao('Não').checked).toBe(true);
      });
    });

    describe('validação local', () => {
      const erroRegulamento = (): HTMLElement | null =>
        fixture.nativeElement.querySelector('fieldset.elementos #erro-elementosAfetados-regulamento');

      it('«Sim» sem «Peças Escritas» mostra o erro junto dos elementos afetados e liga-o por aria-describedby', () => {
        abrir({ comGeoconform: false, valores: { elementosAfetados: ['PECAS_GRAFICAS'] } });

        marcar(opcao('Sim'));

        expect(erroRegulamento()!.textContent!.trim()).toBe(ERRO_REGULAMENTO);
        expect(fixture.nativeElement.querySelector('fieldset.elementos').getAttribute('aria-describedby')).toContain(
          'erro-elementosAfetados-regulamento',
        );
      });

      it('o erro desaparece ao assinalar «Peças Escritas» ou ao responder «Não»', () => {
        abrir({ comGeoconform: false, valores: { elementosAfetados: ['PECAS_GRAFICAS'] } });
        marcar(opcao('Sim'));

        marcar(caixa(ESCRITAS));
        expect(erroRegulamento()).toBeNull();
        expect(fixture.nativeElement.querySelector('fieldset.elementos').getAttribute('aria-describedby')).toBeNull();

        marcar(caixa(ESCRITAS));
        expect(erroRegulamento()).not.toBeNull();
        marcar(opcao('Não'));
        expect(erroRegulamento()).toBeNull();
      });

      it('não impede a gravação: o rascunho leva a resposta na mesma', () => {
        abrir({ comGeoconform: false, valores: { elementosAfetados: ['PECAS_GRAFICAS'] } });

        marcar(opcao('Sim'));

        expect(fixture.componentInstance.dados()).toMatchObject({ elementosAfetados: ['PECAS_GRAFICAS'], introduzPlantaNova: true });
      });
    });

    describe('noutros formulários', () => {
      it('o formulário Informação Geral não tem a pergunta e o pedido não leva o campo', () => {
        abrir({ formulario: 'INFORMACAO_GERAL', comGeoconform: false, valores: { elementosAfetados: ['PECAS_GRAFICAS'] } });

        expect(pergunta()).toBeNull();
        expect('introduzPlantaNova' in fixture.componentInstance.dados()).toBe(false);
      });

      it('com GeoConform «Peças Gráficas» não fica fixa fora da Publicação da Alteração', () => {
        abrir({ formulario: 'INFORMACAO_GERAL', comGeoconform: true });

        expect(caixa(GRAFICAS).checked).toBe(false);
        expect(caixa(GRAFICAS).disabled).toBe(false);
        expect(fixture.nativeElement.querySelector('#ajuda-pecasGraficas')).toBeNull();
      });
    });

    describe('depois da primeira submissão', () => {
      const valores: FormularioInicialValores = {
        elementosAfetados: ['PECAS_GRAFICAS', 'PECAS_ESCRITAS'],
        dadosFormulario: '{"introduzPlantaNova":true}',
      };

      it('desativa a pergunta, avisa e liga o aviso por aria-describedby', () => {
        abrir({ comGeoconform: false, valores, camposBloqueados: ['elementosAfetados', 'introduzPlantaNova'] });

        expect(opcao('Sim').disabled).toBe(true);
        expect(opcao('Não').disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('#bloqueado-introduzPlantaNova').textContent.trim()).toBe(AVISO_BLOQUEADO);
        expect(pergunta()!.getAttribute('aria-describedby')).toBe('ajuda-introduzPlantaNova bloqueado-introduzPlantaNova');
      });

      it('reenvia a resposta gravada, para o servidor não ver uma alteração', () => {
        abrir({ comGeoconform: false, valores, camposBloqueados: ['elementosAfetados', 'introduzPlantaNova'] });

        expect(fixture.componentInstance.dados().introduzPlantaNova).toBe(true);
      });

      it('com GeoConform a opção fixa continua marcada e desativada', () => {
        abrir({ comGeoconform: true, valores: { elementosAfetados: ['PECAS_GRAFICAS'] }, camposBloqueados: ['elementosAfetados'] });

        expect(caixa(GRAFICAS).checked).toBe(true);
        expect(caixa(GRAFICAS).disabled).toBe(true);
        expect(fixture.componentInstance.dados().elementosAfetados).toEqual(['PECAS_GRAFICAS']);
      });
    });

    describe('erros do servidor', () => {
      const mensagemEmFalta = 'Indique se a alteração introduz alguma planta nova.';

      it('mostra a mensagem junto da pergunta e liga-a por aria-describedby', () => {
        abrir({ comGeoconform: false, valores: { elementosAfetados: ['PECAS_GRAFICAS'] } });
        fixture.componentInstance.form.controls.introduzPlantaNova.setErrors({ servidor: mensagemEmFalta });
        fixture.detectChanges();

        const erro = pergunta()!.querySelector('#erro-introduzPlantaNova') as HTMLElement;
        expect(erro.textContent!.trim()).toBe(mensagemEmFalta);
        expect(pergunta()!.getAttribute('aria-describedby')).toBe('ajuda-introduzPlantaNova erro-introduzPlantaNova');
      });

      it('aplica os erros que chegam da submissão aos controlos e leva o foco ao primeiro campo com erro', () => {
        document.body.appendChild(fixture.nativeElement);
        try {
          fixture.componentRef.setInput('errosServidor', [
            { path: 'introduzPlantaNova', erro: mensagemEmFalta },
            { path: 'elementosAfetados', erro: ERRO_REGULAMENTO },
          ]);
          abrir({ comGeoconform: false, valores: { elementosAfetados: ['PECAS_GRAFICAS'] } });
          fixture.detectChanges();

          expect(fixture.nativeElement.querySelector('fieldset.elementos #erro-elementosAfetados').textContent.trim()).toBe(ERRO_REGULAMENTO);
          expect(pergunta()!.querySelector('#erro-introduzPlantaNova')!.textContent!.trim()).toBe(mensagemEmFalta);
          const ativo = document.activeElement as HTMLInputElement;
          expect(ativo.type).toBe('checkbox');
          expect(ativo.closest('fieldset')!.classList.contains('elementos')).toBe(true);
        } finally {
          fixture.nativeElement.remove();
        }
      });

      it('mostra o erro da planta nova junto dos elementos quando a pergunta não está visível', () => {
        abrir({ comGeoconform: false });
        fixture.componentInstance.form.controls.introduzPlantaNova.setErrors({
          servidor: 'Este procedimento não pergunta se a alteração introduz uma planta nova.',
        });
        fixture.detectChanges();

        expect(pergunta()).toBeNull();
        expect(fixture.nativeElement.querySelector('fieldset.elementos .erro-campo').textContent).toContain(
          'Este procedimento não pergunta se a alteração introduz uma planta nova.',
        );
      });
    });

    describe('em leitura', () => {
      it('mostra a resposta gravada, em texto, sem campos editáveis', () => {
        fixture.componentRef.setInput('leitura', true);
        fixture.componentRef.setInput('formulario', 'PUBLICACAO_ALTERACAO');
        fixture.componentRef.setInput('valoresIniciais', { dadosFormulario: '{"introduzPlantaNova":false}' });
        comSerie('SEGUNDA_H');

        const linha = Array.from(fixture.nativeElement.querySelectorAll('dl.leitura > div') as NodeListOf<HTMLElement>).find((item) =>
          item.querySelector('dt')!.textContent!.includes('planta nova'),
        );
        expect(linha!.querySelector('dd')!.textContent!.trim()).toBe('Não');
        expect(fixture.nativeElement.querySelectorAll('input, textarea, select, form')).toHaveLength(0);
      });

      it('não mostra a pergunta quando não há resposta nem noutros formulários', () => {
        fixture.componentRef.setInput('leitura', true);
        fixture.componentRef.setInput('formulario', 'PUBLICACAO_ALTERACAO');
        expect(comSerie('SEGUNDA_H')).not.toContain('planta nova');

        fixture.componentRef.setInput('formulario', 'INFORMACAO_GERAL');
        fixture.componentRef.setInput('valoresIniciais', { dadosFormulario: '{"introduzPlantaNova":true}' });
        fixture.detectChanges();
        expect(texto()).not.toContain('planta nova');
      });
    });
  });
  describe('Localização e motivo da alteração', () => {
    const FREGUESIAS: OpcaoLista[] = [
      { codigo: 'F01', descricao: 'Algueirão-Mem Martins' },
      { codigo: 'F02', descricao: 'Colares' },
    ];
    const TOPONIMOS: OpcaoLista[] = [{ codigo: 'T01', descricao: 'Bairro da Estação' }];
    const LEIS: OpcaoLista[] = [
      { codigo: 'L1', descricao: 'Lei de Bases' },
      { codigo: 'L2', descricao: 'Lei das Áreas Protegidas' },
    ];
    const PROGRAMAS: OpcaoLista[] = [{ codigo: 'G1', descricao: 'PROT-AML' }];
    const PLANOS: OpcaoLista[] = [{ codigo: 'P1', descricao: 'POOC Sintra-Sado' }];

    function todasAsListas(): void {
      opcoesPorTipo = {
        FREGUESIA: FREGUESIAS,
        TOPONIMO: TOPONIMOS,
        MOTIVO_ALTERACAO_LEI: LEIS,
        MOTIVO_ALTERACAO_PROGRAMA: PROGRAMAS,
        MOTIVO_ALTERACAO_PLANO: PLANOS,
      };
    }

    function abrir(valores: FormularioInicialValores = {}, opcoesExtra: { bloqueados?: string[] } = {}): void {
      fixture.componentRef.setInput('pedeLocalizacao', 'OBRIGATORIA');
      fixture.componentRef.setInput('pedeMotivoAlteracao', 'COM_PERGUNTA');
      fixture.componentRef.setInput('camposBloqueados', opcoesExtra.bloqueados ?? []);
      fixture.componentRef.setInput('valoresIniciais', valores);
      comSerie('SEGUNDA_H');
    }

    function localizacao(): HTMLFieldSetElement | null {
      return fixture.nativeElement.querySelector('fieldset.localizacao');
    }

    function conformacao(): HTMLFieldSetElement | null {
      return fixture.nativeElement.querySelector('fieldset.conformacao');
    }

    function motivos(): HTMLFieldSetElement | null {
      return fixture.nativeElement.querySelector('fieldset.motivos');
    }

    function responder(sim: boolean): void {
      const radio = conformacao()!.querySelectorAll('input[type="radio"]')[sim ? 0 : 1] as HTMLInputElement;
      radio.click();
      fixture.detectChanges();
    }

    it('lê as opções de cada lista uma vez, só dos tipos que o Procedimento pede', () => {
      todasAsListas();
      abrir();
      fixture.componentRef.setInput('valoresIniciais', { sumario: 'outro' });
      fixture.detectChanges();

      expect(opcoes.mock.calls.map(([tipo]) => tipo)).toEqual([
        'FREGUESIA',
        'TOPONIMO',
        'MOTIVO_ALTERACAO_LEI',
        'MOTIVO_ALTERACAO_PROGRAMA',
        'MOTIVO_ALTERACAO_PLANO',
      ]);
    });

    it('não lê nada nem mostra os grupos num Procedimento que não os pede', () => {
      todasAsListas();
      comSerie('SEGUNDA_H');

      expect(opcoes).not.toHaveBeenCalled();
      expect(localizacao()).toBeNull();
      expect(conformacao()).toBeNull();
      expect(motivos()).toBeNull();
    });

    it('com opções em todas as listas mostra freguesia, topónimo, toda a área, a pergunta e, depois de Sim, os motivos por tipo', () => {
      todasAsListas();
      abrir();

      const grupo = localizacao()!;
      const legenda = grupo.querySelector('legend')!;
      expect(legenda.textContent).toContain('Localização (obrigatório)');
      expect(legenda.classList.contains('so-leitor')).toBe(true);
      expect(grupo.querySelector('label .rotulo .marca-obrigatoria')!.closest('label')!.textContent).toContain('Freguesia');
      expect(Array.from(grupo.querySelectorAll('select option')).map((o) => o.textContent!.trim())).toEqual([
        'Selecione a freguesia',
        'Algueirão-Mem Martins',
        'Colares',
      ]);
      expect(grupo.querySelector('input[list="opcoes-toponimo"]')).not.toBeNull();
      expect(grupo.querySelector('datalist option')!.getAttribute('value')).toBe('Bairro da Estação');
      expect(grupo.textContent).toContain('Aplicável a toda a área do IGT');
      expect(conformacao()!.querySelector('legend')!.textContent).toContain('hierarquia superior');
      expect(motivos()).toBeNull();

      responder(true);
      const subgrupos = Array.from(motivos()!.querySelectorAll('fieldset.subgrupo-motivos legend')).map((l) => l.textContent!.trim());
      expect(subgrupos).toEqual(['Lei', 'Programa', 'Plano']);
    });

    it('com a lista FREGUESIA vazia, a localização não aparece mesmo com topónimos', () => {
      opcoesPorTipo = { TOPONIMO: TOPONIMOS, MOTIVO_ALTERACAO_LEI: LEIS };
      abrir();

      expect(localizacao()).toBeNull();
      expect(fixture.componentInstance.dados().localizacao).toBeNull();
    });

    it('com a lista TOPONIMO vazia, o topónimo aparece para texto livre e vai como texto', () => {
      opcoesPorTipo = { FREGUESIA: FREGUESIAS };
      abrir();

      const toponimo = localizacao()!.querySelector('input[list="opcoes-toponimo"]') as HTMLInputElement;
      expect(toponimo).not.toBeNull();
      expect(localizacao()!.querySelectorAll('datalist option').length).toBe(0);
      toponimo.value = 'Rua do Sol';
      toponimo.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(fixture.componentInstance.dados().localizacao).toEqual(expect.objectContaining({ toponimoTexto: 'Rua do Sol' }));
    });

    it('com as três listas de motivos vazias, Sim não mostra os motivos e a pergunta continua visível', () => {
      opcoesPorTipo = { FREGUESIA: FREGUESIAS };
      abrir();

      responder(true);

      expect(conformacao()).not.toBeNull();
      expect(motivos()).toBeNull();
      expect(fixture.componentInstance.dados().conformaHierarquiaSuperior).toBe(true);
    });

    it('com OPCIONAL a localização aparece sem marca de obrigatório', () => {
      todasAsListas();
      fixture.componentRef.setInput('pedeLocalizacao', 'OPCIONAL');
      comSerie('SEGUNDA_H');

      expect(localizacao()).not.toBeNull();
      expect(localizacao()!.querySelector('.marca-obrigatoria')).toBeNull();
    });

    it('com OPCIONAL a legenda não anuncia obrigatório e com a lista FREGUESIA vazia o bloco não aparece', () => {
      opcoesPorTipo = { FREGUESIA: FREGUESIAS, TOPONIMO: TOPONIMOS };
      fixture.componentRef.setInput('pedeLocalizacao', 'OPCIONAL');
      comSerie('SEGUNDA_H');

      expect(localizacao()!.querySelector('legend')!.textContent!.trim()).toBe('Localização');
      expect(localizacao()!.textContent).toContain('Aplicável a toda a área do IGT');

      opcoesPorTipo = { TOPONIMO: TOPONIMOS };
      fixture = TestBed.createComponent(ProcessoFormularioInicialComponent);
      fixture.componentRef.setInput('pedeLocalizacao', 'OPCIONAL');
      comSerie('SEGUNDA_H');

      expect(localizacao()).toBeNull();
      expect(fixture.componentInstance.dados().localizacao).toBeNull();
    });

    it('com OBRIGATORIO pede os motivos sem a pergunta', () => {
      todasAsListas();
      fixture.componentRef.setInput('pedeMotivoAlteracao', 'OBRIGATORIO');
      comSerie('SEGUNDA_H');

      expect(conformacao()).toBeNull();
      expect(motivos()).not.toBeNull();
      expect(fixture.componentInstance.dados().conformaHierarquiaSuperior).toBeNull();
    });

    it('na Alteração por Adaptação (OBRIGATORIO) o grupo «Motivo da Alteração» aparece logo, sem a pergunta, e some com as três listas vazias', () => {
      todasAsListas();
      fixture.componentRef.setInput('pedeLocalizacao', 'OBRIGATORIA');
      fixture.componentRef.setInput('pedeMotivoAlteracao', 'OBRIGATORIO');
      comSerie('SEGUNDA_H');

      expect(fixture.nativeElement.textContent).not.toContain('conformar o plano com uma lei');
      expect(motivos()!.querySelector('legend')!.textContent).toContain('Motivo da Alteração');
      expect([...motivos()!.querySelectorAll('.rotulo-subgrupo')].map((rotulo) => rotulo.textContent!.trim())).toEqual([
        'Lei',
        'Programa',
        'Plano',
      ]);
      (motivos()!.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
      fixture.detectChanges();
      expect(fixture.componentInstance.dados().motivosAlteracao).toEqual([{ tipo: 'MOTIVO_ALTERACAO_LEI', codigo: 'L1' }]);

      opcoesPorTipo = { FREGUESIA: FREGUESIAS };
      fixture = TestBed.createComponent(ProcessoFormularioInicialComponent);
      fixture.componentRef.setInput('pedeMotivoAlteracao', 'OBRIGATORIO');
      comSerie('SEGUNDA_H');

      expect(conformacao()).toBeNull();
      expect(motivos()).toBeNull();
    });

    it('na Correção Material (NAO) não há pergunta nem motivos, mesmo com opções nas listas', () => {
      todasAsListas();
      fixture.componentRef.setInput('pedeMotivoAlteracao', 'NAO');
      comSerie('SEGUNDA_H');

      expect(conformacao()).toBeNull();
      expect(motivos()).toBeNull();
      expect(fixture.componentInstance.dados().motivosAlteracao ?? []).toEqual([]);
      expect(fixture.componentInstance.dados().conformaHierarquiaSuperior).toBeNull();
    });

    it('Aplicável a toda a área do IGT limpa e desativa a freguesia e o topónimo', () => {
      todasAsListas();
      abrir({ localizacao: { aplicavelTodaArea: null, freguesia: 'F01', toponimoCodigo: null, toponimoTexto: 'Rua Nova' } });

      (localizacao()!.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
      fixture.detectChanges();

      const select = localizacao()!.querySelector('select') as HTMLSelectElement;
      const toponimo = localizacao()!.querySelector('input[list="opcoes-toponimo"]') as HTMLInputElement;
      expect(select.disabled).toBe(true);
      expect(select.value).toBe('');
      expect(toponimo.disabled).toBe(true);
      expect(toponimo.value).toBe('');
      expect(fixture.componentInstance.dados().localizacao).toEqual({
        aplicavelTodaArea: true,
        freguesia: null,
        toponimoCodigo: null,
        toponimoTexto: null,
      });
    });

    it('um topónimo igual à descrição de uma opção vai como código; outro texto vai como texto', () => {
      todasAsListas();
      abrir();
      const controlos = fixture.componentInstance.form.controls.localizacao.controls;
      controlos.freguesia.setValue('F02');

      controlos.toponimo.setValue('Bairro da Estação');
      expect(fixture.componentInstance.dados().localizacao).toEqual({
        aplicavelTodaArea: null,
        freguesia: 'F02',
        toponimoCodigo: 'T01',
        toponimoTexto: null,
      });

      controlos.toponimo.setValue('  Largo do Palácio ');
      expect(fixture.componentInstance.dados().localizacao).toEqual({
        aplicavelTodaArea: null,
        freguesia: 'F02',
        toponimoCodigo: null,
        toponimoTexto: 'Largo do Palácio',
      });
    });

    it('repõe a localização gravada, com a descrição do topónimo, tanto do detalhe como do que acabou de se gravar', () => {
      todasAsListas();
      abrir({
        localizacao: {
          aplicavelTodaArea: null,
          freguesia: { codigo: 'F01', descricao: 'Algueirão-Mem Martins' },
          toponimo: { codigo: 'T01', descricao: 'Bairro da Estação' },
          toponimoTexto: null,
        },
      });
      expect(fixture.componentInstance.form.controls.localizacao.getRawValue()).toEqual({
        aplicavelTodaArea: false,
        freguesia: 'F01',
        toponimo: 'Bairro da Estação',
      });

      fixture.componentRef.setInput('valoresIniciais', {
        localizacao: { aplicavelTodaArea: null, freguesia: 'F02', toponimoCodigo: 'T01', toponimoTexto: null },
      });
      fixture.detectChanges();
      expect(fixture.componentInstance.form.controls.localizacao.controls.toponimo.value).toBe('Bairro da Estação');
    });

    it('envia os motivos marcados com o tipo, pela ordem dos grupos; Não limpa-os e esconde o grupo', () => {
      todasAsListas();
      abrir();
      responder(true);
      const componente = fixture.componentInstance;
      componente.alternarMotivo('MOTIVO_ALTERACAO_PROGRAMA', 'G1');
      componente.alternarMotivo('MOTIVO_ALTERACAO_LEI', 'L2');
      componente.alternarMotivo('MOTIVO_ALTERACAO_LEI', 'L1');

      expect(componente.dados().motivosAlteracao).toEqual([
        { tipo: 'MOTIVO_ALTERACAO_LEI', codigo: 'L1' },
        { tipo: 'MOTIVO_ALTERACAO_LEI', codigo: 'L2' },
        { tipo: 'MOTIVO_ALTERACAO_PROGRAMA', codigo: 'G1' },
      ]);

      responder(false);
      expect(motivos()).toBeNull();
      expect(componente.form.controls.motivosAlteracao.value).toEqual([]);
      expect(componente.dados()).toMatchObject({ conformaHierarquiaSuperior: false, motivosAlteracao: [] });
    });

    it('mostra o erro do servidor nos motivos junto do grupo, anunciado e ligado por aria-describedby', () => {
      todasAsListas();
      fixture.componentRef.setInput('errosServidor', [
        { path: 'motivosAlteracao[0]', erro: 'Motivo inválido.' },
        { path: 'localizacao', erro: 'Indique a freguesia ou escolha "Aplicável a toda a área do IGT".' },
      ]);
      abrir({ conformaHierarquiaSuperior: true });
      fixture.detectChanges();

      const erro = motivos()!.querySelector('#erro-motivosAlteracao') as HTMLElement;
      expect(erro.textContent!.trim()).toBe('Motivo inválido.');
      expect(erro.getAttribute('role')).toBe('alert');
      expect(motivos()!.getAttribute('aria-describedby')).toBe('erro-motivosAlteracao');
      expect(localizacao()!.querySelector('#erro-localizacao')!.textContent).toContain('Indique a freguesia');
      expect(localizacao()!.getAttribute('aria-describedby')).toBe('erro-localizacao');
    });

    it('leva os erros do topónimo para o único campo do topónimo e os dos motivos para o grupo', () => {
      expect(caminhoDoControlo('localizacao.toponimoTexto')).toBe('localizacao.toponimo');
      expect(caminhoDoControlo('localizacao.toponimoCodigo')).toBe('localizacao.toponimo');
      expect(caminhoDoControlo('motivosAlteracao[2]')).toBe('motivosAlteracao');
      expect(caminhoDoControlo('localizacao.freguesia')).toBe('localizacao.freguesia');
    });

    it('depois da submissão os campos ficam desativados com as descrições, e reenviam o gravado', () => {
      opcoesPorTipo = { FREGUESIA: FREGUESIAS, MOTIVO_ALTERACAO_LEI: LEIS };
      const motivoDesativado = { tipo: 'MOTIVO_ALTERACAO_PLANO' as const, codigo: 'PX', descricao: 'Plano revogado' };
      abrir(
        {
          localizacao: {
            aplicavelTodaArea: null,
            freguesia: { codigo: 'F01', descricao: 'Algueirão-Mem Martins' },
            toponimo: null,
            toponimoTexto: 'Rua Nova',
          },
          conformaHierarquiaSuperior: true,
          motivosAlteracao: [motivoDesativado],
        },
        { bloqueados: ['localizacao', 'conformaHierarquiaSuperior', 'motivosAlteracao'] },
      );

      const select = localizacao()!.querySelector('select') as HTMLSelectElement;
      expect(select.disabled).toBe(true);
      expect(select.selectedOptions[0].textContent!.trim()).toBe('Algueirão-Mem Martins');
      expect((localizacao()!.querySelector('input[list="opcoes-toponimo"]') as HTMLInputElement).value).toBe('Rua Nova');
      expect(Array.from(conformacao()!.querySelectorAll('input')).every((r) => (r as HTMLInputElement).disabled)).toBe(true);
      const caixa = Array.from(motivos()!.querySelectorAll('label')).find((l) => l.textContent!.includes('Plano revogado'))!;
      expect((caixa.querySelector('input') as HTMLInputElement).checked).toBe(true);
      expect((caixa.querySelector('input') as HTMLInputElement).disabled).toBe(true);
      expect(fixture.componentInstance.dados()).toMatchObject({
        localizacao: { aplicavelTodaArea: null, freguesia: 'F01', toponimoCodigo: null, toponimoTexto: 'Rua Nova' },
        conformaHierarquiaSuperior: true,
        motivosAlteracao: [{ tipo: 'MOTIVO_ALTERACAO_PLANO', codigo: 'PX' }],
      });
    });

    it('cada controlo tem rótulo e os rádios e as caixas estão dentro de fieldset com legend', () => {
      todasAsListas();
      abrir({ conformaHierarquiaSuperior: true });

      for (const controlo of Array.from(fixture.nativeElement.querySelectorAll('fieldset.localizacao select, fieldset.localizacao input'))) {
        expect((controlo as HTMLElement).closest('label')).not.toBeNull();
      }
      for (const controlo of Array.from(fixture.nativeElement.querySelectorAll('fieldset.conformacao input, fieldset.motivos input'))) {
        expect((controlo as HTMLElement).closest('label')).not.toBeNull();
        expect((controlo as HTMLElement).closest('fieldset')!.querySelector('legend')).not.toBeNull();
      }
    });

    it('em leitura mostra a freguesia, o topónimo, a resposta e os motivos com descrição, sem pedir opções', () => {
      fixture.componentRef.setInput('leitura', true);
      fixture.componentRef.setInput('pedeLocalizacao', 'OBRIGATORIA');
      fixture.componentRef.setInput('pedeMotivoAlteracao', 'COM_PERGUNTA');
      fixture.componentRef.setInput('valoresIniciais', {
        localizacao: {
          aplicavelTodaArea: null,
          freguesia: { codigo: 'F01', descricao: 'Algueirão-Mem Martins' },
          toponimo: { codigo: 'T01', descricao: 'Bairro da Estação' },
          toponimoTexto: null,
        },
        conformaHierarquiaSuperior: true,
        motivosAlteracao: [
          { tipo: 'MOTIVO_ALTERACAO_LEI', codigo: 'L1', descricao: 'Lei de Bases' },
          { tipo: 'MOTIVO_ALTERACAO_PLANO', codigo: 'P1', descricao: 'POOC Sintra-Sado' },
        ],
      });
      comSerie('SEGUNDA_H');

      const linhas = Object.fromEntries(
        Array.from(fixture.nativeElement.querySelectorAll('dl.leitura > div') as NodeListOf<HTMLElement>).map((item) => [
          item.querySelector('dt')!.textContent!.trim(),
          item.querySelector('dd')!.textContent!.trim(),
        ]),
      );
      expect(linhas['Freguesia']).toBe('Algueirão-Mem Martins');
      expect(linhas['Topónimo']).toBe('Bairro da Estação');
      expect(linhas['Motivo da Alteração']).toBe('Lei: Lei de Bases; Plano: POOC Sintra-Sado');
      expect(Object.entries(linhas).find(([dt]) => dt.includes('hierarquia superior'))![1]).toBe('Sim');
      expect(opcoes).not.toHaveBeenCalled();
    });
  });

  describe('associação a um IGT em curso (REN)', () => {
    const alteracaoPdm: DinamicaAssociavel = {
      dinamicaId: 'd1',
      numeroDinamica: 'DIN-2026-0001',
      descritivo: 'Alteração do PDM de Sintra',
      tipoDinamica: 'ALTERACAO',
      estado: 'EM_CURSO',
      instrumento: { id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' },
    };

    function bloco(): HTMLElement | null {
      return fixture.nativeElement.querySelector('.associacao-igt');
    }

    function abrir(opcoesBloco: {
      aceita: boolean;
      valores?: FormularioInicialValores;
      dinamica?: DinamicaAssociavel | null;
      bloqueados?: string[];
    }): void {
      fixture.componentRef.setInput('aceitaDinamicaAssociada', opcoesBloco.aceita);
      fixture.componentRef.setInput('processoId', 'p1');
      fixture.componentRef.setInput('dinamicaAssociada', opcoesBloco.dinamica ?? null);
      fixture.componentRef.setInput('camposBloqueados', opcoesBloco.bloqueados ?? []);
      if (opcoesBloco.valores) {
        fixture.componentRef.setInput('valoresIniciais', opcoesBloco.valores);
      }
      comSerie('SEGUNDA_C');
    }

    it('com aceitaDinamicaAssociada mostra o bloco com título, a pergunta em «Não» e a ajuda, sem pedir a lista', () => {
      abrir({ aceita: true });

      expect(bloco()!.querySelector('h3')!.textContent!.trim()).toBe('Associação a um IGT em curso');
      expect(bloco()!.textContent).toContain('Processo em simultâneo com a formação de PMOT');
      expect((bloco()!.querySelector('input[value="NAO"]') as HTMLInputElement).checked).toBe(true);
      expect(bloco()!.querySelector('select')).toBeNull();
      expect(bloco()!.textContent).toContain('A REN deve ser publicada antes do IGT associado.');
      expect(dinamicasAssociaveis).not.toHaveBeenCalled();
      expect(fixture.componentInstance.dados().dinamicaAssociadaId).toBeNull();
      expect(fixture.componentInstance.dadosFormularioPedido()).toEqual({ dadosFormulario: { simultaneoComPmot: 'NAO' } });
    });

    it('sem aceitaDinamicaAssociada o bloco não aparece, nada é pedido e os dados levam dinamicaAssociadaId nulo', () => {
      abrir({ aceita: false, valores: { dinamicaAssociadaId: 'd1' } });

      expect(bloco()).toBeNull();
      expect(dinamicasAssociaveis).not.toHaveBeenCalled();
      expect(fixture.componentInstance.dados().dinamicaAssociadaId).toBeNull();
      expect(fixture.componentInstance.dadosFormularioPedido()).toEqual({});
    });

    it('com «Sim» e uma Dinâmica escolhida, os dados levam o id e a resposta SIM', () => {
      dinamicasAssociaveis.mockReturnValue(of({ content: [alteracaoPdm], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
      abrir({ aceita: true });

      (bloco()!.querySelector('input[value="SIM"]') as HTMLInputElement).click();
      fixture.detectChanges();
      const select = bloco()!.querySelector('select') as HTMLSelectElement;
      select.value = 'd1';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(fixture.componentInstance.dados().dinamicaAssociadaId).toBe('d1');
      expect(fixture.componentInstance.dadosFormularioPedido()).toEqual({ dadosFormulario: { simultaneoComPmot: 'SIM' } });
    });

    it('a resposta gravada em dadosFormulario e a Dinâmica gravada repõem «Sim» com a opção selecionada', () => {
      dinamicasAssociaveis.mockReturnValue(of({ content: [alteracaoPdm], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
      abrir({
        aceita: true,
        dinamica: alteracaoPdm,
        valores: { dinamicaAssociadaId: 'd1', dadosFormulario: JSON.stringify({ simultaneoComPmot: 'SIM' }) },
      });

      expect((bloco()!.querySelector('input[value="SIM"]') as HTMLInputElement).checked).toBe(true);
      expect((bloco()!.querySelector('select') as HTMLSelectElement).value).toBe('d1');
    });

    it('bloqueado depois da primeira submissão: pergunta e seletor desativados com a associação gravada', () => {
      abrir({ aceita: true, dinamica: alteracaoPdm, valores: { dinamicaAssociadaId: 'd1' }, bloqueados: ['dinamicaAssociadaId'] });

      expect((bloco()!.querySelector('input[value="SIM"]') as HTMLInputElement).disabled).toBe(true);
      expect((bloco()!.querySelector('input[value="NAO"]') as HTMLInputElement).disabled).toBe(true);
      const select = bloco()!.querySelector('select') as HTMLSelectElement;
      expect(select.disabled).toBe(true);
      expect(select.value).toBe('d1');
      expect(dinamicasAssociaveis).not.toHaveBeenCalled();
      expect(fixture.componentInstance.dados().dinamicaAssociadaId).toBe('d1');
    });

    it('a recusa DINAMICA_ASSOCIADA_INVALIDA da submissão aparece junto do seletor e leva-lhe o foco', async () => {
      document.body.appendChild(fixture.nativeElement);
      try {
        fixture.componentRef.setInput('errosServidor', [
          { path: 'dinamicaAssociadaId', erro: 'A dinâmica indicada não está disponível para associação.' },
        ]);
        abrir({ aceita: true, dinamica: { ...alteracaoPdm, estado: 'TERMINADA' }, valores: { dinamicaAssociadaId: 'd1' } });
        await fixture.whenStable();
        fixture.detectChanges();

        const select = bloco()!.querySelector('select') as HTMLSelectElement;
        const erro = bloco()!.querySelector('.seletor-erro')!;
        expect(erro.textContent!.trim()).toBe('A dinâmica indicada não está disponível para associação.');
        expect(select.getAttribute('aria-describedby')).toContain(erro.id);
        expect(document.activeElement).toBe(select);
      } finally {
        fixture.nativeElement.remove();
      }
    });

    it('em leitura mostra «IGT associado» com o número da Dinâmica e a designação, e nada sem associação', () => {
      fixture.componentRef.setInput('leitura', true);
      fixture.componentRef.setInput('dinamicaAssociada', alteracaoPdm);
      comSerie('SEGUNDA_C');
      const linha = fixture.nativeElement.querySelector('dl.leitura .igt-associado') as HTMLElement;
      expect(linha.querySelector('dt')!.textContent!.trim()).toBe('IGT associado');
      expect(linha.querySelector('dd')!.textContent!.trim()).toBe('DIN-2026-0001 · PDM de Sintra');

      fixture.componentRef.setInput('dinamicaAssociada', null);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.igt-associado')).toBeNull();
      expect(texto()).not.toContain('IGT associado');
    });
  });

  describe('tipo de ato no cabeçalho', () => {
    const TIPOS = [
      { id: 4, codigo: 'AVISO', nome: 'Aviso' },
      { id: 5, codigo: 'DESPACHO', nome: 'Despacho' },
    ];

    function seletorTipoAto(): HTMLSelectElement | null {
      return fixture.nativeElement.querySelector('app-seletor.tipo-ato select');
    }

    function abrir(opcoes: { valores?: FormularioInicialValores; bloqueados?: string[]; tipoAtoNome?: string | null } = {}): void {
      fixture.componentRef.setInput('tiposAtoDisponiveis', TIPOS);
      fixture.componentRef.setInput('tipoAtoNome', opcoes.tipoAtoNome ?? null);
      fixture.componentRef.setInput('valoresIniciais', opcoes.valores ?? {});
      fixture.componentRef.setInput('camposBloqueados', opcoes.bloqueados ?? []);
      comSerie('SEGUNDA_H');
    }

    it('num processo editável é um seletor obrigatório com as opções de tiposAtoDisponiveis', () => {
      abrir();

      const select = seletorTipoAto()!;
      const rotulo = fixture.nativeElement.querySelector('app-seletor.tipo-ato label') as HTMLLabelElement;
      expect(rotulo.textContent).toContain('Tipo de ato');
      expect(rotulo.querySelector('.seletor-obrigatorio')?.textContent).toBe('*');
      expect(rotulo.htmlFor).toBe(select.id);
      expect(select.required).toBe(true);
      expect(select.disabled).toBe(false);
      expect([...select.options].map((opcao) => opcao.textContent?.trim())).toEqual(['Selecionar...', 'Aviso', 'Despacho']);
      expect(fixture.componentInstance.dados().tipoAtoId).toBeUndefined();
    });

    it('escolher um tipo leva tipoAtoId no pedido e o gravado volta escolhido ao reabrir', () => {
      abrir();
      const select = seletorTipoAto()!;
      select.value = '5';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(fixture.componentInstance.dados().tipoAtoId).toBe(5);

      valoresGravados({ tipoAtoId: 4 });
      fixture.detectChanges();
      expect(seletorTipoAto()!.value).toBe('4');
      expect(fixture.componentInstance.dados().tipoAtoId).toBe(4);
    });

    it('mantém nas opções o tipo gravado que já não está na lista, pelo nome', () => {
      abrir({ valores: { tipoAtoId: 9 }, tipoAtoNome: 'Edital' });

      expect([...seletorTipoAto()!.options].map((opcao) => opcao.textContent?.trim())).toEqual(['Selecionar...', 'Aviso', 'Despacho', 'Edital']);
      expect(seletorTipoAto()!.value).toBe('9');
    });

    it('sem tipos disponíveis diz porquê junto do seletor', () => {
      fixture.componentRef.setInput('tiposAtoDisponiveis', []);
      comSerie(null);

      const select = seletorTipoAto()!;
      const ajuda = fixture.nativeElement.querySelector('app-seletor.tipo-ato .seletor-ajuda, app-seletor.tipo-ato [id$="-ajuda"]') as HTMLElement;
      expect(ajuda.textContent).toContain('Não há tipos de ato disponíveis');
      expect(select.getAttribute('aria-describedby')).toContain(ajuda.id);
    });

    it('bloqueado depois da primeira submissão mostra só o nome, com o aviso, e não vai no pedido', () => {
      abrir({ valores: { tipoAtoId: 5 }, tipoAtoNome: 'Despacho', bloqueados: ['tipoAtoId'] });

      expect(seletorTipoAto()).toBeNull();
      const nome = fixture.nativeElement.querySelector('.tipo-ato .tipo-ato-nome') as HTMLElement;
      expect(nome.textContent?.trim()).toBe('Despacho');
      expect(nome.getAttribute('aria-describedby')).toBe('bloqueado-tipoAtoId');
      expect(fixture.nativeElement.querySelector('#bloqueado-tipoAtoId').textContent).toContain('Bloqueado depois da primeira submissão');
      expect(fixture.nativeElement.querySelector('.tipo-ato app-desbloquear-campo')).not.toBeNull();
      expect('tipoAtoId' in fixture.componentInstance.dados()).toBe(false);
    });

    it('mostra junto do seletor o erro do servidor no campo tipoAtoId, ligado por aria-describedby', async () => {
      abrir();
      fixture.componentRef.setInput('errosServidor', [{ path: 'tipoAtoId', erro: 'Indique o tipo de ato.' }]);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const select = seletorTipoAto()!;
      const erro = fixture.nativeElement.querySelector('app-seletor.tipo-ato .seletor-erro, app-seletor.tipo-ato [id$="-erro"]') as HTMLElement;
      expect(erro.textContent).toContain('Indique o tipo de ato.');
      expect(select.getAttribute('aria-invalid')).toBe('true');
      expect(select.getAttribute('aria-describedby')).toContain(erro.id);
    });

    it('o 403 TIPO_ATO_NAO_AUTORIZADO, sem campos, passa a erro do campo tipoAtoId', () => {
      const recusa = new ApiError('TIPO_ATO_NAO_AUTORIZADO', 'Tipo de ato não autorizado ao utilizador pela INCM.', 't1', null);

      expect(comErroNoTipoAto(recusa).campos).toEqual([{ path: 'tipoAtoId', erro: 'Tipo de ato não autorizado ao utilizador pela INCM.' }]);
      const outro = new ApiError('VALIDACAO', 'Inválido.', 't2', [{ path: 'sumario', erro: 'x' }]);
      expect(comErroNoTipoAto(outro)).toBe(outro);
    });
  });

  describe('série escolhida no processo (restantes servidões)', () => {
    const SERIES = ['PRIMEIRA', 'SEGUNDA_H', 'SEGUNDA_C'] as const;

    function seletorSerie(): HTMLSelectElement | null {
      return fixture.nativeElement.querySelector('app-seletor.serie-dr select');
    }

    function abrir(opcoes: { valores?: FormularioInicialValores; bloqueados?: string[]; tipos?: { id: number; codigo: string; nome: string }[]; serie?: string | null } = {}): void {
      fixture.componentRef.setInput('formulario', 'SRUP');
      fixture.componentRef.setInput('seriesDrEscolhiveis', [...SERIES]);
      fixture.componentRef.setInput('tiposAtoDisponiveis', opcoes.tipos ?? []);
      fixture.componentRef.setInput('valoresIniciais', opcoes.valores ?? {});
      fixture.componentRef.setInput('camposBloqueados', opcoes.bloqueados ?? []);
      comSerie(opcoes.serie ?? null);
    }

    it('a Parte do DR passa a seletor obrigatório da série com as três opções e o tipo de ato fica vazio até haver série', () => {
      abrir();

      const select = seletorSerie()!;
      const rotulo = fixture.nativeElement.querySelector('app-seletor.serie-dr label') as HTMLLabelElement;
      expect(rotulo.textContent).toContain('Série do Diário da República');
      expect(rotulo.querySelector('.seletor-obrigatorio')?.textContent).toBe('*');
      expect(rotulo.htmlFor).toBe(select.id);
      expect(select.required).toBe(true);
      expect([...select.options].map((opcao) => opcao.textContent?.trim())).toEqual([
        'Selecionar...',
        '1.ª Série',
        '2.ª Série – H',
        '2.ª Série – C',
      ]);
      expect(itensDoContexto()['Parte do DR']).toBeUndefined();
      const tipoAto = fixture.nativeElement.querySelector('app-seletor.tipo-ato select') as HTMLSelectElement;
      expect([...tipoAto.options].map((opcao) => opcao.textContent?.trim())).toEqual(['Selecionar...']);
      expect(fixture.nativeElement.querySelector('app-seletor.tipo-ato').textContent).toContain(
        'Escolha primeiro a série do Diário da República.',
      );
      expect('serieDr' in fixture.componentInstance.dados()).toBe(false);
    });

    it('escolher a série avisa o ecrã uma só vez e leva serieDr no pedido; desativar e reativar não volta a avisar', () => {
      abrir();
      const escolhidas: string[] = [];
      fixture.componentInstance.serieEscolhida.subscribe((serie) => escolhidas.push(serie));

      const select = seletorSerie()!;
      select.value = 'PRIMEIRA';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      fixture.componentRef.setInput('aCarregar', true);
      fixture.detectChanges();
      fixture.componentRef.setInput('aCarregar', false);
      fixture.detectChanges();

      expect(escolhidas).toEqual(['PRIMEIRA']);
      expect(fixture.componentInstance.dados().serieDr).toBe('PRIMEIRA');
    });

    it('relido o detalhe, mostra a série gravada e só os tipos de ato dela', () => {
      const escolhidas: string[] = [];
      abrir({ valores: { serieDr: 'SEGUNDA_H' }, serie: 'SEGUNDA_H', tipos: [{ id: 9, codigo: 'EDITAL', nome: 'Edital' }] });
      fixture.componentInstance.serieEscolhida.subscribe((serie) => escolhidas.push(serie));

      expect(seletorSerie()!.value).toBe('SEGUNDA_H');
      const tipoAto = fixture.nativeElement.querySelector('app-seletor.tipo-ato select') as HTMLSelectElement;
      expect([...tipoAto.options].map((opcao) => opcao.textContent?.trim())).toEqual(['Selecionar...', 'Edital']);
      expect(escolhidas).toEqual([]);
      expect(fixture.componentInstance.dados().serieDr).toBe('SEGUNDA_H');
    });

    it('num Procedimento com série fixa a Parte do DR continua só de leitura e a série não vai no pedido', () => {
      fixture.componentRef.setInput('seriesDrEscolhiveis', null);
      comSerie('SEGUNDA_H');

      expect(seletorSerie()).toBeNull();
      expect(itensDoContexto()['Parte do DR']).toBe('2.ª Série - H');
      expect('serieDr' in fixture.componentInstance.dados()).toBe(false);
    });

    it('bloqueada depois da primeira submissão mostra só a série, com o aviso e o desbloqueio, e não vai no pedido', () => {
      abrir({ valores: { serieDr: 'SEGUNDA_C' }, serie: 'SEGUNDA_C', bloqueados: ['serieDr'] });

      expect(seletorSerie()).toBeNull();
      const nome = fixture.nativeElement.querySelector('.serie-dr .serie-dr-nome') as HTMLElement;
      expect(nome.textContent?.trim()).toBe('2.ª Série - C');
      expect(nome.getAttribute('aria-describedby')).toBe('bloqueado-serieDr');
      expect(fixture.nativeElement.querySelector('.serie-dr app-desbloquear-campo')).not.toBeNull();
      expect('serieDr' in fixture.componentInstance.dados()).toBe(false);
    });

    it('mostra junto do seletor o erro do servidor no campo serieDr, ligado por aria-describedby', async () => {
      abrir();
      fixture.componentRef.setInput('errosServidor', [{ path: 'serieDr', erro: 'Preencha os dados de publicação.' }]);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const select = seletorSerie()!;
      const erro = fixture.nativeElement.querySelector('app-seletor.serie-dr [id$="-erro"]') as HTMLElement;
      expect(erro.textContent).toContain('Preencha os dados de publicação.');
      expect(select.getAttribute('aria-invalid')).toBe('true');
      expect(select.getAttribute('aria-describedby')).toContain(erro.id);
    });
  });

  describe('campos obrigatórios no «Seguinte»', () => {
    const FREGUESIAS: OpcaoLista[] = [{ codigo: 'F01', descricao: 'Colares' }];
    const LEIS: OpcaoLista[] = [{ codigo: 'L1', descricao: 'Lei de Bases' }];
    const ELEMENTOS_EM_FALTA = 'Escolha pelo menos um elemento fundamental.';
    const LOCALIZACAO_EM_FALTA = 'Escolha "Aplicável a toda a área do IGT" ou indique a freguesia.';
    const CONFORMACAO_EM_FALTA = 'Indique se a alteração conforma o plano com uma lei, programa ou plano de hierarquia superior.';
    const MOTIVO_EM_FALTA = 'Escolha pelo menos um motivo da alteração.';
    const PLANTA_NOVA_EM_FALTA = 'Indique se a alteração introduz alguma planta nova.';
    const REGULAMENTO = 'Introduzir uma planta nova exige alteração ao regulamento: assinale as Peças Escritas.';
    const TODA_A_AREA = { aplicavelTodaArea: true, freguesia: null, toponimo: null, toponimoTexto: null };
    const COMPLETO: FormularioInicialValores = {
      elementosAfetados: ['PECAS_ESCRITAS'],
      localizacao: TODA_A_AREA,
      conformaHierarquiaSuperior: false,
    };

    const grupo = (classe: string): HTMLFieldSetElement => fixture.nativeElement.querySelector(`fieldset.${classe}`);
    const erro = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`#${id}`);
    const caixa = (rotulo: string): HTMLInputElement => {
      const opcoes = Array.from(fixture.nativeElement.querySelectorAll('label.campo-checkbox') as NodeListOf<HTMLElement>);
      return opcoes.find((opcao) => opcao.textContent!.trim() === rotulo)!.querySelector('input')!;
    };
    const radio = (classe: string, sim: boolean): HTMLInputElement =>
      grupo(classe).querySelectorAll('input[type="radio"]')[sim ? 0 : 1] as HTMLInputElement;
    const marcar = (controlo: HTMLInputElement): void => {
      controlo.click();
      fixture.detectChanges();
    };

    function seguinte(): boolean {
      const avanca = fixture.componentInstance.validarObrigatorios();
      fixture.detectChanges();
      return avanca;
    }

    /** Decisão de início de uma Alteração de PDM: localização obrigatória e pergunta da conformação. */
    function decisaoInicio(valores: FormularioInicialValores, extra: { bloqueados?: string[]; pedeMotivo?: 'COM_PERGUNTA' | 'OBRIGATORIO' } = {}): void {
      opcoesPorTipo = { FREGUESIA: FREGUESIAS, MOTIVO_ALTERACAO_LEI: LEIS };
      fixture.componentRef.setInput('formulario', 'INFORMACAO_GERAL');
      fixture.componentRef.setInput('pedeLocalizacao', 'OBRIGATORIA');
      fixture.componentRef.setInput('pedeMotivoAlteracao', extra.pedeMotivo ?? 'COM_PERGUNTA');
      fixture.componentRef.setInput('camposBloqueados', extra.bloqueados ?? []);
      fixture.componentRef.setInput('valoresIniciais', valores);
      comSerie('SEGUNDA_H');
    }

    function publicacaoAlteracao(valores: FormularioInicialValores): void {
      fixture.componentRef.setInput('formulario', 'PUBLICACAO_ALTERACAO');
      fixture.componentRef.setInput('comGeoconform', false);
      fixture.componentRef.setInput('valoresIniciais', valores);
      comSerie('SEGUNDA_H');
    }

    it('sem nenhum elemento afetado não avança: mensagem anunciada junto do grupo, ligada por aria-describedby e com o foco', () => {
      decisaoInicio({ ...COMPLETO, elementosAfetados: [] });

      expect(seguinte()).toBe(false);

      expect(erro('erro-elementosAfetados')!.textContent!.trim()).toBe(ELEMENTOS_EM_FALTA);
      expect(erro('erro-elementosAfetados')!.getAttribute('role')).toBe('alert');
      expect(grupo('elementos').getAttribute('aria-describedby')).toContain('erro-elementosAfetados');
      expect(grupo('elementos').querySelector('legend')).not.toBeNull();
      expect(document.activeElement).toBe(grupo('elementos').querySelector('input'));
      expect(fixture.componentInstance.form.controls.elementosAfetados.touched).toBe(true);

      marcar(caixa('Outros'));

      expect(erro('erro-elementosAfetados')).toBeNull();
      expect(seguinte()).toBe(true);
    });

    it('sem «toda a área» e sem freguesia não avança e mostra a mensagem da localização, com o foco na freguesia', () => {
      decisaoInicio({ ...COMPLETO, localizacao: null });

      expect(seguinte()).toBe(false);

      expect(erro('erro-localizacao')!.textContent!.trim()).toBe(LOCALIZACAO_EM_FALTA);
      expect(grupo('localizacao').getAttribute('aria-describedby')).toContain('erro-localizacao');
      expect(document.activeElement).toBe(grupo('localizacao').querySelector('select'));

      marcar(caixa('Aplicável a toda a área do IGT'));

      expect(erro('erro-localizacao')).toBeNull();
      expect(seguinte()).toBe(true);
    });

    it('com a freguesia escolhida a localização fica respondida', () => {
      decisaoInicio({ ...COMPLETO, localizacao: { aplicavelTodaArea: null, freguesia: { codigo: 'F01', descricao: 'Colares' }, toponimo: null, toponimoTexto: null } });

      expect(seguinte()).toBe(true);
    });

    it('sem a lista FREGUESIA a localização não aparece nem é exigida, como no servidor', () => {
      decisaoInicio({ ...COMPLETO, localizacao: null });
      fixture.componentInstance.opcoes.set({ MOTIVO_ALTERACAO_LEI: LEIS });
      fixture.detectChanges();

      expect(grupo('localizacao')).toBeNull();
      expect(seguinte()).toBe(true);
    });

    it('sem resposta à conformação não avança e mostra a mensagem junto da pergunta', () => {
      decisaoInicio({ ...COMPLETO, conformaHierarquiaSuperior: null });

      expect(seguinte()).toBe(false);

      expect(erro('erro-conformaHierarquiaSuperior')!.textContent!.trim()).toBe(CONFORMACAO_EM_FALTA);
      expect(erro('erro-conformaHierarquiaSuperior')!.getAttribute('role')).toBe('alert');
      expect(grupo('conformacao').getAttribute('aria-describedby')).toBe('erro-conformaHierarquiaSuperior');
      expect(document.activeElement).toBe(radio('conformacao', true));

      marcar(radio('conformacao', false));

      expect(erro('erro-conformaHierarquiaSuperior')).toBeNull();
      expect(seguinte()).toBe(true);
    });

    it('com a conformação «Sim» e sem motivo não avança e mostra «Escolha pelo menos um motivo da alteração.»', () => {
      decisaoInicio({ ...COMPLETO, conformaHierarquiaSuperior: true });

      expect(seguinte()).toBe(false);

      expect(erro('erro-motivosAlteracao')!.textContent!.trim()).toBe(MOTIVO_EM_FALTA);
      expect(grupo('motivos').getAttribute('aria-describedby')).toBe('erro-motivosAlteracao');
      expect(document.activeElement).toBe(grupo('motivos').querySelector('input'));

      marcar(grupo('motivos').querySelector('input') as HTMLInputElement);

      expect(erro('erro-motivosAlteracao')).toBeNull();
      expect(seguinte()).toBe(true);
    });

    it('com o motivo OBRIGATORIO pede o motivo sem perguntar pela conformação', () => {
      decisaoInicio({ ...COMPLETO, conformaHierarquiaSuperior: null }, { pedeMotivo: 'OBRIGATORIO' });

      expect(seguinte()).toBe(false);

      expect(erro('erro-conformaHierarquiaSuperior')).toBeNull();
      expect(erro('erro-motivosAlteracao')!.textContent!.trim()).toBe(MOTIVO_EM_FALTA);
    });

    it('com vários campos em falta mostra todos os erros e o foco vai para o primeiro', () => {
      decisaoInicio({});

      expect(seguinte()).toBe(false);

      expect(erro('erro-localizacao')!.textContent!.trim()).toBe(LOCALIZACAO_EM_FALTA);
      expect(erro('erro-conformaHierarquiaSuperior')!.textContent!.trim()).toBe(CONFORMACAO_EM_FALTA);
      expect(erro('erro-elementosAfetados')!.textContent!.trim()).toBe(ELEMENTOS_EM_FALTA);
      expect(document.activeElement).toBe(grupo('localizacao').querySelector('select'));
    });

    it('com todos os campos obrigatórios preenchidos avança, sem mensagens', () => {
      decisaoInicio(COMPLETO);

      expect(seguinte()).toBe(true);

      expect(fixture.nativeElement.querySelector('.erro-campo')).toBeNull();
    });

    it('o rascunho grava o formulário incompleto sem as mensagens destas regras', () => {
      decisaoInicio({});

      const dados = fixture.componentInstance.dados();

      expect(dados).toMatchObject({ elementosAfetados: [], localizacao: null, conformaHierarquiaSuperior: null });
      expect(fixture.nativeElement.querySelector('.erro-campo')).toBeNull();
      expect(fixture.componentInstance.form.controls.elementosAfetados.errors).toBeNull();
    });

    it('sem elementos afetados no Procedimento (Elaboração de um PDM novo) estas regras não se aplicam', () => {
      fixture.componentRef.setInput('pedeElementosAfetados', false);
      comSerie('SEGUNDA_H');

      expect(seguinte()).toBe(true);

      expect(grupo('elementos')).toBeNull();
      expect(fixture.nativeElement.querySelector('.erro-campo')).toBeNull();
    });

    it('os campos bloqueados depois da primeira submissão não são exigidos', () => {
      decisaoInicio({}, { bloqueados: ['elementosAfetados', 'localizacao', 'conformaHierarquiaSuperior'] });

      expect(seguinte()).toBe(true);
    });

    it('em leitura não valida', () => {
      fixture.componentRef.setInput('leitura', true);
      comSerie('SEGUNDA_H');

      expect(seguinte()).toBe(true);
    });

    it('na Publicação da Alteração, com «Peças gráficas» e a planta nova sem resposta, não avança', () => {
      publicacaoAlteracao({ elementosAfetados: ['PECAS_GRAFICAS'] });

      expect(seguinte()).toBe(false);

      expect(erro('erro-introduzPlantaNova')!.textContent!.trim()).toBe(PLANTA_NOVA_EM_FALTA);
      expect(grupo('planta-nova').getAttribute('aria-describedby')).toContain('erro-introduzPlantaNova');
      expect(document.activeElement).toBe(radio('planta-nova', true));

      marcar(radio('planta-nova', false));

      expect(erro('erro-introduzPlantaNova')).toBeNull();
      expect(seguinte()).toBe(true);
    });

    it('com a planta nova «Sim» e sem «Peças escritas» não avança e mostra a mensagem do regulamento', () => {
      publicacaoAlteracao({ elementosAfetados: ['PECAS_GRAFICAS'] });
      marcar(radio('planta-nova', true));

      expect(seguinte()).toBe(false);

      expect(erro('erro-elementosAfetados-regulamento')!.textContent!.trim()).toBe(REGULAMENTO);
      expect(erro('erro-elementosAfetados-regulamento')!.getAttribute('role')).toBe('alert');
      expect(document.activeElement).toBe(grupo('elementos').querySelector('input'));

      marcar(caixa('Peças escritas (Regulamento)'));

      expect(seguinte()).toBe(true);
    });

    it('os erros 400 da submissão continuam a aparecer nos campos respetivos', async () => {
      decisaoInicio(COMPLETO);
      fixture.componentRef.setInput('errosServidor', [
        { path: 'elementosAfetados', erro: 'Indique os elementos fundamentais afetados.' },
        { path: 'conformaHierarquiaSuperior', erro: 'Responda à conformação.' },
      ]);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(erro('erro-elementosAfetados')!.textContent!.trim()).toBe('Indique os elementos fundamentais afetados.');
      expect(erro('erro-conformaHierarquiaSuperior')!.textContent!.trim()).toBe('Responda à conformação.');
    });
  });

  describe('concelhos do IGT novo pela NUT II', () => {
    function elaboracao(overrides: Partial<ElaboracaoNova> = {}): ElaboracaoNova {
      return {
        instrumentoDinamicaConfigId: 103,
        nomeDinamica: 'Elaboração',
        procedimento: { tiposAtoDisponiveis: [] } as unknown as ElaboracaoNova['procedimento'],
        exigeOpcaoGeoconform: false,
        pedeLocalizacao: 'NAO',
        nutsII: [
          { codigo: 'NORTE', nome: 'NORTE' },
          { codigo: 'LISBOA E VALE DO TEJO', nome: 'LISBOA E VALE DO TEJO' },
        ],
        concelhos: [
          { codigo: '1105', nome: 'Cascais', nutIICodigo: 'LISBOA E VALE DO TEJO' },
          { codigo: '1111', nome: 'Sintra', nutIICodigo: 'LISBOA E VALE DO TEJO' },
          { codigo: '1113', nome: 'Torres Vedras', nutIICodigo: null },
          { codigo: '0412', nome: 'Vinhais', nutIICodigo: 'NORTE' },
        ],
        concelhoEntidade: null,
        ambitoTerritorial: 'MUNICIPAL',
        ...overrides,
      };
    }

    function abrir(nova: ElaboracaoNova): void {
      fixture.componentRef.setInput('elaboracaoNova', nova);
      fixture.detectChanges();
    }

    function seletor(rotulo: string): HTMLSelectElement {
      const seletores = [...fixture.nativeElement.querySelectorAll('app-seletor')] as HTMLElement[];
      const encontrado = seletores.find((elemento) => elemento.querySelector('label')?.textContent?.trim().startsWith(rotulo));
      return encontrado!.querySelector('select') as HTMLSelectElement;
    }

    function concelhosVisiveis(): string[] {
      return [...seletor('Concelho').options].slice(1).map((opcao) => opcao.textContent!.trim());
    }

    function escolherNutII(codigo: string): void {
      const select = seletor('NUT II');
      select.value = codigo;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    it('sem NUT II escolhida, a lista de concelhos está desativada com a ajuda', () => {
      abrir(elaboracao());

      expect(seletor('NUT II').value).toBe('');
      expect(seletor('Concelho').disabled).toBe(true);
      expect(texto()).toContain('Escolha primeiro a NUT II.');
    });

    it('sem NUT II escolhida, o concelho continua desativado depois de acabar de carregar', () => {
      fixture.componentRef.setInput('aCarregar', true);
      abrir(elaboracao({ concelhoEntidade: '1113' }));

      fixture.componentRef.setInput('aCarregar', false);
      fixture.detectChanges();

      expect(seletor('Concelho').disabled).toBe(true);
      expect(seletor('Concelho').value).toBe('1113');
      expect(seletor('NUT II').disabled).toBe(false);
      expect(texto()).toContain('Escolha primeiro a NUT II.');
    });

    it('ao escolher a NUT II mostra só os concelhos dessa NUT II e os sem NUT II conhecida', () => {
      abrir(elaboracao());

      escolherNutII('NORTE');

      expect(seletor('Concelho').disabled).toBe(false);
      expect(concelhosVisiveis()).toEqual(['Torres Vedras', 'Vinhais']);
      expect(texto()).not.toContain('Escolha primeiro a NUT II.');

      escolherNutII('LISBOA E VALE DO TEJO');

      expect(concelhosVisiveis()).toEqual(['Cascais', 'Sintra', 'Torres Vedras']);
    });

    it('a câmara com NUT II conhecida abre com o concelho e a NUT II pré-escolhidos e coerentes', () => {
      abrir(elaboracao({ concelhoEntidade: '1111' }));

      expect(seletor('NUT II').value).toBe('LISBOA E VALE DO TEJO');
      expect(seletor('Concelho').value).toBe('1111');
      expect(seletor('Concelho').disabled).toBe(false);
      expect(fixture.componentInstance.formElaboracao.getRawValue()).toEqual(
        expect.objectContaining({ nutIICodigo: 'LISBOA E VALE DO TEJO', concelho: '1111' }),
      );
    });

    it('mudar para uma NUT II a que o concelho não pertence limpa o concelho', () => {
      abrir(elaboracao({ concelhoEntidade: '1111' }));

      escolherNutII('NORTE');

      expect(fixture.componentInstance.formElaboracao.controls.concelho.value).toBeNull();
      expect(seletor('Concelho').value).toBe('');
    });

    it('um concelho sem NUT II conhecida fica escolhido com qualquer NUT II', () => {
      abrir(elaboracao({ concelhoEntidade: '1113' }));

      expect(seletor('NUT II').value).toBe('');
      expect(seletor('Concelho').value).toBe('1113');

      escolherNutII('NORTE');

      expect(fixture.componentInstance.formElaboracao.controls.concelho.value).toBe('1113');
    });

    it('no intermunicipal a lista de concelhos não é filtrada nem desativada', () => {
      abrir(elaboracao({ ambitoTerritorial: 'INTERMUNICIPAL' }));

      expect(seletor('Concelho').disabled).toBe(false);
      expect(concelhosVisiveis()).toEqual(['Cascais', 'Sintra', 'Torres Vedras', 'Vinhais']);

      escolherNutII('NORTE');

      expect(concelhosVisiveis()).toEqual(['Cascais', 'Sintra', 'Torres Vedras', 'Vinhais']);
    });
  });

  describe('formulário SGIFR', () => {
    const REDES_PSA = ['AEMGC', 'APPS', 'RPA', 'RSFGC', 'RVDI', 'RVF'].map((codigo) => ({ codigo, nome: `Rede ${codigo}` }));
    const REDE_PRA = [{ codigo: 'RPFGC', nome: 'Rede primária' }];

    beforeEach(() => {
      fixture.componentRef.setInput('formulario', 'SGIFR');
      fixture.componentRef.setInput('categoriaInstrumento', 'PROGRAMA');
      fixture.componentRef.setInput('pedeElementosAfetados', false);
    });

    function caixasDasRedes(): HTMLInputElement[] {
      return [...fixture.nativeElement.querySelectorAll('fieldset.redes-defesa input[type="checkbox"]')];
    }

    function marcar(codigo: string): void {
      const caixa = caixasDasRedes().find((c) => c.value === codigo)!;
      caixa.click();
      fixture.detectChanges();
    }

    it('no PSA mostra a classe só de leitura, o grupo «Tipos de servidão» com uma caixa por rede e o texto integral fixo', () => {
      fixture.componentRef.setInput('redesDefesaDisponiveis', REDES_PSA);
      const conteudo = comSerie('SEGUNDA_H');

      expect(itensDoContexto()['Classe de servidão']).toBe('Sistema de Gestão Integrada de Fogos Rurais');
      const grupo = fixture.nativeElement.querySelector('fieldset.redes-defesa') as HTMLFieldSetElement;
      expect(grupo.querySelector('legend')!.textContent!.trim()).toBe('Tipos de servidão');
      expect(caixasDasRedes()).toHaveLength(6);
      expect(grupo.textContent).toContain('Rede APPS (APPS)');
      expect(grupo.textContent).toContain('Cada uma é publicada numa folha própria da mesma publicação.');
      expect(itensDoContexto()['Tipo de informação']).toBe('Texto integral');
      expect(fixture.nativeElement.querySelector('input[type="radio"]')).toBeNull();
      expect(conteudo).not.toContain('elementos fundamentais');
      expect(fixture.nativeElement.querySelector('fieldset.elementos')).toBeNull();
    });

    it('no PRA mostra a rede primária só de leitura, sem caixas, e não a envia', () => {
      fixture.componentRef.setInput('redesDefesaDisponiveis', REDE_PRA);
      comSerie('SEGUNDA_C');

      expect(itensDoContexto()['Tipo de servidão']).toBe('Rede primária');
      expect(caixasDasRedes()).toHaveLength(0);
      expect(fixture.componentInstance.dados().redesDefesa).toBeUndefined();
      expect(fixture.componentInstance.dados().tipoInformacao).toBe('TEXTO_INTEGRAL');
    });

    it('as caixas marcadas vão em redesDefesa, pela ordem do catálogo', () => {
      fixture.componentRef.setInput('redesDefesaDisponiveis', REDES_PSA);
      comSerie('SEGUNDA_H');

      marcar('RVF');
      marcar('APPS');

      expect(fixture.componentInstance.dados().redesDefesa).toEqual(['APPS', 'RVF']);
    });

    it('sem nenhuma caixa marcada envia a lista vazia, porque o PSA não tem mínimo', () => {
      fixture.componentRef.setInput('redesDefesaDisponiveis', REDES_PSA);
      comSerie('SEGUNDA_H');

      expect(fixture.componentInstance.dados().redesDefesa).toEqual([]);
    });

    it('repõe as redes gravadas marcadas, venham do detalhe (com nome) ou do que acabou de se gravar (códigos)', () => {
      fixture.componentRef.setInput('redesDefesaDisponiveis', REDES_PSA);
      valoresGravados({ redesDefesa: [{ codigo: 'APPS', nome: 'Rede APPS' }, 'RPA'] });
      comSerie('SEGUNDA_H');

      expect(caixasDasRedes().filter((c) => c.checked).map((c) => c.value)).toEqual(['APPS', 'RPA']);
    });

    it('mostra o erro REDES_DEFESA_INVALIDAS no grupo, ligado por aria-describedby', async () => {
      fixture.componentRef.setInput('redesDefesaDisponiveis', REDES_PSA);
      comSerie('SEGUNDA_H');
      fixture.componentRef.setInput('errosServidor', [{ path: 'redesDefesa', erro: 'Redes de defesa inválidas para este programa.' }]);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const grupo = fixture.nativeElement.querySelector('fieldset.redes-defesa') as HTMLFieldSetElement;
      expect(grupo.querySelector('#erro-redesDefesa')!.textContent).toContain('Redes de defesa inválidas para este programa.');
      expect(grupo.getAttribute('aria-describedby')).toBe('ajuda-redesDefesa erro-redesDefesa');
    });

    it('as caixas ficam desativadas enquanto grava', () => {
      fixture.componentRef.setInput('redesDefesaDisponiveis', REDES_PSA);
      comSerie('SEGUNDA_H');

      fixture.componentRef.setInput('aCarregar', true);
      fixture.detectChanges();

      expect(caixasDasRedes().every((c) => c.disabled)).toBe(true);
    });

    it('em leitura lista os nomes das redes gravadas e a classe', () => {
      fixture.componentRef.setInput('leitura', true);
      fixture.componentRef.setInput('redesDefesaDisponiveis', REDES_PSA);
      valoresGravados({ redesDefesa: [{ codigo: 'APPS', nome: 'Rede APPS' }, { codigo: 'RVF', nome: 'Rede RVF' }] });
      comSerie('SEGUNDA_H');

      const itens = Object.fromEntries(
        [...fixture.nativeElement.querySelectorAll('dl.leitura > div')].map((item: Element) => [
          item.querySelector('dt')!.textContent!.trim(),
          item.querySelector('dd')!.textContent!.trim(),
        ]),
      );
      expect(itens['Classe de servidão']).toBe('Sistema de Gestão Integrada de Fogos Rurais');
      expect(itens['Tipos de servidão']).toBe('Rede APPS, Rede RVF');
    });

    it('os outros formulários ficam como estavam: radio do tipo de informação e sem bloco de servidão', () => {
      fixture.componentRef.setInput('formulario', 'INFORMACAO_GERAL');
      fixture.componentRef.setInput('pedeElementosAfetados', true);
      comSerie('SEGUNDA_H');

      expect(fixture.nativeElement.querySelectorAll('input[type="radio"]')).toHaveLength(2);
      expect(fixture.nativeElement.querySelector('fieldset.redes-defesa')).toBeNull();
      expect(texto()).not.toContain('Classe de servidão');
      expect(fixture.componentInstance.dados().redesDefesa).toBeUndefined();
    });

    describe('Região e CIM / AM', () => {
      const REGIOES = [
        { codigo: 'NORTE', descricao: 'Norte' },
        { codigo: 'CENTRO', descricao: 'Centro' },
      ];
      const CIM_AM = [
        { codigo: 'ALTO_MINHO', descricao: 'CIM do Alto Minho', regiao: 'NORTE' },
        { codigo: 'CAVADO', descricao: 'CIM do Cávado', regiao: 'NORTE' },
        { codigo: 'COIMBRA', descricao: 'CIM da Região de Coimbra', regiao: 'CENTRO' },
      ];

      function territorio(pedeRegiao: boolean, pedeCimAm: boolean): void {
        fixture.componentRef.setInput('redesDefesaDisponiveis', REDES_PSA);
        fixture.componentRef.setInput('pedeRegiao', pedeRegiao);
        fixture.componentRef.setInput('opcoesRegiao', pedeRegiao ? REGIOES : []);
        fixture.componentRef.setInput('pedeCimAm', pedeCimAm);
        fixture.componentRef.setInput('opcoesCimAm', pedeCimAm ? CIM_AM : []);
      }

      function seletor(classe: string): HTMLSelectElement | null {
        return fixture.nativeElement.querySelector(`app-seletor.${classe} select`);
      }

      function escolher(classe: string, valor: string): void {
        const select = seletor(classe)!;
        select.value = valor;
        select.dispatchEvent(new Event('change'));
        fixture.detectChanges();
      }

      function opcoesDe(classe: string): string[] {
        return [...seletor(classe)!.querySelectorAll('option')].map((opcao) => opcao.value).filter(Boolean);
      }

      it('no PSA em Delimitação mostra os dois campos e a CIM / AM só com as da Região escolhida', () => {
        territorio(true, true);
        comSerie('SEGUNDA_H');

        expect(fixture.nativeElement.querySelector('app-seletor.regiao label')!.textContent).toContain('Região');
        expect(fixture.nativeElement.querySelector('app-seletor.cim-am label')!.textContent).toContain('CIM / AM');
        expect(opcoesDe('cim-am')).toEqual([]);
        expect(texto()).toContain('Escolha primeiro a Região.');

        escolher('regiao', 'NORTE');
        expect(opcoesDe('cim-am')).toEqual(['ALTO_MINHO', 'CAVADO']);
        escolher('cim-am', 'CAVADO');

        expect(fixture.componentInstance.dados()).toMatchObject({ regiao: 'NORTE', cimAm: 'CAVADO' });
      });

      it('mudar a Região retira a CIM / AM que deixou de lhe pertencer', () => {
        territorio(true, true);
        comSerie('SEGUNDA_H');
        escolher('regiao', 'NORTE');
        escolher('cim-am', 'ALTO_MINHO');

        escolher('regiao', 'CENTRO');

        expect(opcoesDe('cim-am')).toEqual(['COIMBRA']);
        expect(fixture.componentInstance.dados()).toMatchObject({ regiao: 'CENTRO', cimAm: null });
      });

      it('no PSA em Alteração só há a CIM / AM, com a lista completa, e a Região não se envia', () => {
        territorio(false, true);
        comSerie('SEGUNDA_H');

        expect(seletor('regiao')).toBeNull();
        expect(opcoesDe('cim-am')).toEqual(['ALTO_MINHO', 'CAVADO', 'COIMBRA']);
        escolher('cim-am', 'COIMBRA');
        const dados = fixture.componentInstance.dados();
        expect(dados.cimAm).toBe('COIMBRA');
        expect('regiao' in dados).toBe(false);
      });

      it('no PRA em Alteração, ou com as listas vazias, não há nenhum dos campos nem se enviam', () => {
        territorio(false, false);
        comSerie('SEGUNDA_C');

        expect(seletor('regiao')).toBeNull();
        expect(seletor('cim-am')).toBeNull();
        const dados = fixture.componentInstance.dados();
        expect('regiao' in dados).toBe(false);
        expect('cimAm' in dados).toBe(false);
      });

      it('no PRA em Delimitação só há a Região', () => {
        territorio(true, false);
        comSerie('SEGUNDA_C');

        expect(seletor('regiao')).not.toBeNull();
        expect(seletor('cim-am')).toBeNull();
      });

      it('repõe os valores gravados ao voltar ao processo', () => {
        territorio(true, true);
        valoresGravados({ regiao: 'CENTRO', cimAm: 'COIMBRA' });
        comSerie('SEGUNDA_H');

        expect(seletor('regiao')!.value).toBe('CENTRO');
        expect(seletor('cim-am')!.value).toBe('COIMBRA');
        expect(fixture.componentInstance.dados()).toMatchObject({ regiao: 'CENTRO', cimAm: 'COIMBRA' });
      });

      it('mostra o erro do servidor junto da CIM / AM, ligado por aria-describedby', async () => {
        territorio(true, true);
        comSerie('SEGUNDA_H');
        fixture.componentRef.setInput('errosServidor', [{ path: 'cimAm', erro: 'A CIM / AM não pertence à Região escolhida.' }]);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const campo = fixture.nativeElement.querySelector('app-seletor.cim-am') as HTMLElement;
        const select = campo.querySelector('select')!;
        const erro = campo.querySelector('.seletor-erro')!;
        expect(erro.textContent).toContain('A CIM / AM não pertence à Região escolhida.');
        expect(select.getAttribute('aria-invalid')).toBe('true');
        expect(select.getAttribute('aria-describedby')).toContain(erro.id);
      });

      it('em leitura mostra a Região e a CIM / AM em texto, sem campos', () => {
        fixture.componentRef.setInput('leitura', true);
        territorio(true, true);
        valoresGravados({ regiao: 'NORTE', cimAm: 'INATIVA', cimAmDescricao: 'CIM desativada' });
        comSerie('SEGUNDA_H');

        expect(fixture.nativeElement.querySelector('.leitura-regiao dd')!.textContent.trim()).toBe('Norte');
        expect(fixture.nativeElement.querySelector('.leitura-cim-am dd')!.textContent.trim()).toBe('CIM desativada');
        expect(fixture.nativeElement.querySelector('select')).toBeNull();
      });
    });
  });
  describe('formulário REN', () => {
    const MOTIVOS = [
      { id: 11, codigo: 'M1', descricao: 'Adaptação a plano' },
      { id: 12, codigo: 'M2', descricao: 'Correção de limites' },
    ];
    const CONCELHOS: Record<string, { codigo: string; nome: string; nut2: string }[]> = {
      PT17: [
        { codigo: '1111', nome: 'Sintra', nut2: 'PT17' },
        { codigo: '1106', nome: 'Lisboa', nut2: 'PT17' },
      ],
      PT11: [{ codigo: '1312', nome: 'Porto', nut2: 'PT11' }],
    };

    beforeEach(() => {
      nutsII.mockReturnValue(of([{ codigo: 'PT17', nome: 'Área Metropolitana de Lisboa' }, { codigo: 'PT11', nome: 'Norte' }]));
      concelhos.mockImplementation((nut2: string) => of(CONCELHOS[nut2] ?? []));
      fixture.componentRef.setInput('formulario', 'REN');
      fixture.componentRef.setInput('categoriaInstrumento', 'SRUP');
      fixture.componentRef.setInput('pedeElementosAfetados', false);
      fixture.componentRef.setInput('tipoInstrumento', 'Reserva Ecológica Nacional');
      fixture.componentRef.setInput('tipoProcedimento', 'Alteração');
    });

    function seletorComRotulo(rotulo: string): HTMLElement | undefined {
      return [...fixture.nativeElement.querySelectorAll('app-seletor')].find((item: Element) =>
        item.querySelector('label')!.textContent!.trim().startsWith(rotulo),
      ) as HTMLElement | undefined;
    }

    function selectDe(rotulo: string): HTMLSelectElement | null {
      return seletorComRotulo(rotulo)?.querySelector('select') ?? null;
    }

    function opcoesDe(rotulo: string): string[] {
      return [...seletorComRotulo(rotulo)!.querySelectorAll('option')].slice(1).map((o) => (o as HTMLOptionElement).textContent!.trim());
    }

    it('mostra o tipo de instrumento e a Dinâmica só de leitura, sem elementos afetados, e o motivo obrigatório com as opções', () => {
      fixture.componentRef.setInput('motivosProcedimentoDisponiveis', MOTIVOS);
      comSerie('SEGUNDA_C');

      expect(itensDoContexto()['Tipo de instrumento']).toBe('Reserva Ecológica Nacional');
      expect(itensDoContexto()['Tipo de procedimento']).toBe('Alteração');
      expect(fixture.nativeElement.querySelector('fieldset.elementos')).toBeNull();
      expect(selectDe('Motivo do procedimento')!.required).toBe(true);
      expect(seletorComRotulo('Motivo do procedimento')!.querySelector('label')!.textContent).toContain('*');
      expect(opcoesDe('Motivo do procedimento')).toEqual(['Adaptação a plano', 'Correção de limites']);
    });

    it('repõe o motivo gravado e envia o escolhido como número', () => {
      fixture.componentRef.setInput('motivosProcedimentoDisponiveis', MOTIVOS);
      valoresGravados({ motivoProcedimento: MOTIVOS[1] });
      comSerie('SEGUNDA_C');

      expect(selectDe('Motivo do procedimento')!.value).toBe('12');
      expect(fixture.componentInstance.dados().motivoProcedimentoId).toBe(12);

      fixture.componentInstance.form.controls.motivoProcedimentoId.setValue('11');
      expect(fixture.componentInstance.dados().motivoProcedimentoId).toBe(11);
    });

    it('com a lista de motivos vazia não mostra o campo, não avisa, não o exige e não o envia', () => {
      fixture.componentRef.setInput('motivosProcedimentoDisponiveis', []);
      comSerie('SEGUNDA_C');

      expect(seletorComRotulo('Motivo do procedimento')).toBeUndefined();
      expect(texto()).not.toContain('Motivo do procedimento');
      expect(fixture.componentInstance.validarObrigatorios()).toBe(true);
      expect('motivoProcedimentoId' in fixture.componentInstance.dados()).toBe(false);
    });

    it('sem motivo escolhido, o «Seguinte» recusa e mostra a mensagem junto do campo', () => {
      fixture.componentRef.setInput('motivosProcedimentoDisponiveis', MOTIVOS);
      comSerie('SEGUNDA_C');

      expect(fixture.componentInstance.validarObrigatorios()).toBe(false);
      fixture.detectChanges();
      expect(seletorComRotulo('Motivo do procedimento')!.textContent).toContain('Indique o motivo do procedimento.');
    });

    it('associa o erro 400 do motivo ao seletor e põe o foco nele', async () => {
      fixture.componentRef.setInput('motivosProcedimentoDisponiveis', MOTIVOS);
      comSerie('SEGUNDA_C');
      fixture.componentRef.setInput('errosServidor', [{ path: 'motivoProcedimentoId', erro: 'Indique o motivo do procedimento.' }]);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const motivo = selectDe('Motivo do procedimento')!;
      const erro = seletorComRotulo('Motivo do procedimento')!.querySelector('.seletor-erro')!;
      expect(erro.textContent).toContain('Indique o motivo do procedimento.');
      expect(motivo.getAttribute('aria-describedby')).toContain(erro.id);
      expect(motivo.getAttribute('aria-invalid')).toBe('true');
      expect(document.activeElement).toBe(motivo);
    });

    it('mostra o diploma só quando a Dinâmica o pede e envia-o', () => {
      fixture.componentRef.setInput('pedeDiplomaCorrigido', true);
      valoresGravados({ diplomaCorrigido: 'Aviso n.º 1/2026' });
      comSerie('SEGUNDA_C');

      expect(texto()).toContain('Diploma que corrige ou retifica');
      expect(fixture.componentInstance.dados().diplomaCorrigido).toBe('Aviso n.º 1/2026');

      fixture.componentRef.setInput('pedeDiplomaCorrigido', false);
      fixture.detectChanges();
      expect(texto()).not.toContain('Diploma que corrige ou retifica');
      expect('diplomaCorrigido' in fixture.componentInstance.dados()).toBe(false);
    });

    it('com o tipo de informação fixo da Delimitação mostra «Texto integral» só de leitura; sem ele, a escolha', () => {
      fixture.componentRef.setInput('tipoInformacaoFixo', 'TEXTO_INTEGRAL');
      comSerie('SEGUNDA_C');

      expect(itensDoContexto()['Tipo de informação']).toBe('Texto integral');
      expect(fixture.nativeElement.querySelector('input[type="radio"][value="EXTRATO"]')).toBeNull();
      expect(fixture.componentInstance.dados().tipoInformacao).toBe('TEXTO_INTEGRAL');

      fixture.componentRef.setInput('tipoInformacaoFixo', null);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('input[type="radio"][value="EXTRATO"]')).not.toBeNull();
    });

    it('o município só oferece os concelhos da região, e sem região não tem opções', () => {
      comSerie('SEGUNDA_C');

      expect(opcoesDe('Região')).toEqual(['Área Metropolitana de Lisboa', 'Norte']);
      expect(opcoesDe('Município')).toEqual([]);
      expect(seletorComRotulo('Município')!.textContent).toContain('Escolha primeiro a região.');

      fixture.componentInstance.form.controls.regiaoCodigo.setValue('PT17');
      fixture.detectChanges();

      expect(concelhos).toHaveBeenCalledWith('PT17');
      expect(opcoesDe('Município')).toEqual(['Sintra', 'Lisboa']);
    });

    it('ao mudar de região, um município que deixou de lhe pertencer deixa de estar escolhido', () => {
      valoresGravados({ regiaoCodigo: 'PT17', municipioCodigo: '1111' });
      comSerie('SEGUNDA_C');
      expect(fixture.componentInstance.form.controls.municipioCodigo.value).toBe('1111');

      fixture.componentInstance.form.controls.regiaoCodigo.setValue('PT11');
      fixture.detectChanges();

      expect(fixture.componentInstance.form.controls.municipioCodigo.value).toBeNull();
      expect(opcoesDe('Município')).toEqual(['Porto']);
      expect(fixture.componentInstance.dados()).toMatchObject({ regiaoCodigo: 'PT11', municipioCodigo: '' });
    });

    it('a região e o município não têm asterisco: a obrigatoriedade está por definir', () => {
      comSerie('SEGUNDA_C');

      expect(seletorComRotulo('Região')!.querySelector('label')!.textContent).not.toContain('*');
      expect(seletorComRotulo('Município')!.querySelector('label')!.textContent).not.toContain('*');
    });

    it('em leitura mostra o motivo, o diploma, a região e o município gravados', () => {
      fixture.componentRef.setInput('leitura', true);
      valoresGravados({
        motivoProcedimento: MOTIVOS[0],
        diplomaCorrigido: 'Aviso n.º 1/2026',
        regiaoCodigo: 'PT17',
        municipioCodigo: '1106',
      });
      comSerie('SEGUNDA_C');

      const itens = Object.fromEntries(
        [...fixture.nativeElement.querySelectorAll('dl.leitura > div')].map((item: Element) => [
          item.querySelector('dt')!.textContent!.trim(),
          item.querySelector('dd')!.textContent!.trim(),
        ]),
      );
      expect(itens['Motivo do procedimento']).toBe('Adaptação a plano');
      expect(itens['Diploma que corrige ou retifica']).toBe('Aviso n.º 1/2026');
      expect(itens['Região']).toBe('Área Metropolitana de Lisboa');
      expect(itens['Município']).toBe('Lisboa');
    });

    it('os outros formulários não mostram nem enviam os campos da REN', () => {
      fixture.componentRef.setInput('formulario', 'INFORMACAO_GERAL');
      fixture.componentRef.setInput('motivosProcedimentoDisponiveis', MOTIVOS);
      comSerie('SEGUNDA_C');

      expect(texto()).not.toContain('Motivo do procedimento');
      expect(texto()).not.toContain('Região');
      const dados = fixture.componentInstance.dados();
      expect(['motivoProcedimentoId', 'diplomaCorrigido', 'regiaoCodigo', 'municipioCodigo'].some((chave) => chave in dados)).toBe(false);
    });
  });

});

describe('ProcessoFormularioInicialComponent na Suspensão da CCDR', () => {
  const LVT = 'Comissão de Coordenação e Desenvolvimento Regional de Lisboa e Vale do Tejo, IP';
  const NORTE = 'Comissão de Coordenação e Desenvolvimento Regional do Norte, IP';
  let fixture: ComponentFixture<ProcessoFormularioInicialComponent>;
  let emitentes: ReturnType<typeof vi.fn>;
  const entidadeDaSessao = signal<JwtClaimsEntidade | null>(null);

  beforeEach(() => {
    emitentes = vi.fn(() => of([{ id: 11, nome: LVT }, { id: 15, nome: NORTE }]));
    entidadeDaSessao.set({ id: 11, nipc: '517622610', nome: LVT, tipo_entidade: 'CCDR_DR' });
    TestBed.configureTestingModule({
      imports: [ProcessoFormularioInicialComponent],
      providers: [
        { provide: ListaValoresService, useValue: { opcoes: vi.fn(() => of([])) } },
        { provide: ProcessoService, useValue: { dinamicasAssociaveis: vi.fn() } },
        { provide: EntidadeService, useValue: { emitentes } },
        { provide: AuthService, useValue: { entidade: entidadeDaSessao } },
      ],
    });
    fixture = TestBed.createComponent(ProcessoFormularioInicialComponent);
  });

  function abrir(valores: FormularioInicialValores | null = null, leitura = false): void {
    fixture.componentRef.setInput('formulario', 'SUSPENSAO_CCDR');
    fixture.componentRef.setInput('serieDr', 'SEGUNDA_C');
    fixture.componentRef.setInput('leitura', leitura);
    fixture.componentRef.setInput('valoresIniciais', valores);
    fixture.detectChanges();
  }

  function seletorEmitente(): HTMLSelectElement {
    return fixture.nativeElement.querySelector('.entidade-emitente-lista select') as HTMLSelectElement;
  }

  it('a entidade emitente é a lista das CCDR, pré-escolhida a da sessão e anunciada como tal', () => {
    abrir();

    expect(emitentes).toHaveBeenCalledWith('CCDR_DR');
    expect(fixture.nativeElement.querySelector('input[formcontrolname="entidadeEmitente"]')).toBeNull();
    const select = seletorEmitente();
    const rotulo = fixture.nativeElement.querySelector(`label[for="${select.id}"]`) as HTMLElement;
    expect(rotulo.textContent).toContain('Identificação da entidade emitente');
    expect(select.required).toBe(true);
    expect(Array.from(select.options).slice(1).map((opcao) => opcao.textContent!.trim())).toEqual([LVT, NORTE]);
    expect(select.value).toBe(LVT);
    const ajuda = fixture.nativeElement.querySelector('#' + select.getAttribute('aria-describedby')) as HTMLElement;
    expect(ajuda.textContent).toContain('Pré-escolhida a entidade da sessão');
    expect(fixture.componentInstance.dados().entidadeEmitente).toBe(LVT);
  });

  it('pode mudar-se para outra entidade da lista, e o anúncio da pré-escolha sai', () => {
    abrir();
    const select = seletorEmitente();
    select.value = NORTE;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fixture.componentInstance.dados().entidadeEmitente).toBe(NORTE);
    expect(fixture.componentInstance.ajudaEmitente()).toBeNull();
    expect(seletorEmitente().getAttribute('aria-describedby')).toBeNull();
  });

  it('não pré-escolhe com um valor gravado nem numa sessão que não é CCDR', () => {
    abrir({ entidadeEmitente: NORTE });
    expect(seletorEmitente().value).toBe(NORTE);
    expect(fixture.componentInstance.ajudaEmitente()).toBeNull();

    entidadeDaSessao.set({ id: 5245, nipc: '505000000', nome: 'Câmara Municipal de Sintra', tipo_entidade: 'CM' });
    fixture = TestBed.createComponent(ProcessoFormularioInicialComponent);
    abrir();
    expect(fixture.componentInstance.dados().entidadeEmitente).toBeNull();
  });

  it('a recusa do servidor na entidade emitente fica junto da lista e descreve-a', async () => {
    abrir();
    fixture.componentRef.setInput('errosServidor', [{ path: 'entidadeEmitente', erro: 'Escolha a entidade emitente da lista das CCDR.' }]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const select = seletorEmitente();
    const erro = fixture.nativeElement.querySelector('.entidade-emitente-lista .seletor-erro') as HTMLElement;
    expect(erro.textContent!.trim()).toBe('Escolha a entidade emitente da lista das CCDR.');
    expect(select.getAttribute('aria-describedby')).toContain(erro.id);
    expect(select.getAttribute('aria-invalid')).toBe('true');
  });

  it('marca os campos obrigatórios, agrupa o Tipo de informação e pede a data de assinatura, sem a Publicação', () => {
    abrir();
    const el = fixture.nativeElement as HTMLElement;
    for (const nome of ['contactoTramitacao', 'contactoPublicacao', 'normaLegal']) {
      expect(el.querySelector(`input[formcontrolname="${nome}"]`)!.getAttribute('aria-required')).toBe('true');
    }
    expect(el.querySelector('textarea[formcontrolname="sumario"]')!.getAttribute('aria-required')).toBe('true');
    const tipoInformacao = el.querySelector('fieldset.tipo-informacao') as HTMLElement;
    expect(tipoInformacao.querySelector('legend')!.textContent!.trim()).toBe('Tipo de informação');
    expect(tipoInformacao.getAttribute('role')).toBe('radiogroup');
    expect(tipoInformacao.getAttribute('aria-required')).toBe('true');
    expect(el.querySelector('input.data-assinatura')!.getAttribute('aria-required')).toBe('true');
    expect(el.querySelector('fieldset.publicacao')).toBeNull();
    expect(el.textContent).toContain('2.ª Série');
    expect(el.querySelector('dl.contexto div dt')!.textContent!.trim()).toBe('Parte do DR');

    fixture.componentInstance.dataAssinatura.setValue('2026-11-10');
    expect(fixture.componentInstance.dataAssinaturaPedido(null)).toBe('2026-11-10');
  });

  it('os erros do servidor nos campos de texto e no tipo de informação são alertas que descrevem o campo', async () => {
    abrir();
    fixture.componentRef.setInput('errosServidor', [
      { path: 'normaLegal', erro: 'Indique a norma legal ou mera conveniência.' },
      { path: 'tipoInformacao', erro: 'Indique o tipo de informação.' },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const erroNorma = el.querySelector('#erro-normaLegal') as HTMLElement;
    expect(erroNorma.getAttribute('role')).toBe('alert');
    expect(el.querySelector('input[formcontrolname="normaLegal"]')!.getAttribute('aria-describedby')).toContain('erro-normaLegal');
    expect(el.querySelector('fieldset.tipo-informacao')!.getAttribute('aria-describedby')).toContain('erro-tipoInformacao');
  });

  it('em leitura mostra a entidade emitente e a data de assinatura sem pedir a lista', () => {
    abrir({ entidadeEmitente: LVT, dataAssinatura: '2026-11-10' }, true);
    const leitura = fixture.nativeElement.querySelector('dl.leitura') as HTMLElement;
    expect(leitura.textContent).toContain(LVT);
    expect(leitura.querySelector('.data-assinatura')!.textContent!.trim()).toBe('10/11/2026');
    expect(emitentes).not.toHaveBeenCalled();
  });
});

describe('ProcessoFormularioInicialComponent nos formulários do Governo', () => {
  let fixture: ComponentFixture<ProcessoFormularioInicialComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProcessoFormularioInicialComponent],
      providers: [
        { provide: ListaValoresService, useValue: { opcoes: vi.fn(() => of([])) } },
        { provide: ProcessoService, useValue: { dinamicasAssociaveis: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(ProcessoFormularioInicialComponent);
  });

  function abrir(
    formulario: 'SUSPENSAO_MP_GOVERNO' | 'PRORROGACAO_GOVERNO',
    valores: FormularioInicialValores | null = null,
    leitura = false,
  ): HTMLElement {
    fixture.componentRef.setInput('formulario', formulario);
    fixture.componentRef.setInput('serieDr', 'PRIMEIRA');
    fixture.componentRef.setInput('leitura', leitura);
    fixture.componentRef.setInput('tiposAtoDisponiveis', [{ id: 7, nome: 'Resolução do Conselho de Ministros' }]);
    // Como os ecrãs: pedeElementosAfetadosNoFormulario é falso nos formulários do Governo.
    fixture.componentRef.setInput('pedeElementosAfetados', pedeElementosAfetadosNoFormulario(formulario, true));
    fixture.componentRef.setInput('valoresIniciais', valores);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  /** Os rótulos dos campos e dos grupos, pela ordem do ecrã. */
  function rotulos(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('form.campos > *'))
      .map((campo) => (campo.querySelector('.rotulo, legend, dt, label') as HTMLElement | null)?.textContent?.replace(/\s+/g, ' ').trim() ?? '')
      .filter(Boolean);
  }

  it('na Publicação mostra os campos pela ordem da EF, com a Parte do DR só de leitura e a Publicação em botões de opção', () => {
    const el = abrir('SUSPENSAO_MP_GOVERNO');

    const ordem = [
      'Identificação da entidade emitente',
      'Contato para efeitos de tramitação processual',
      'Contato para publicação',
      'Norma legal ou mera conveniência',
      'Parte do DR',
      'Tipo de ato',
      'Data de Assinatura do ato',
      'Publicação',
      'Tipo de informação',
      'Sumário conteúdo do ato',
    ];
    const vistos = rotulos(el);
    expect(vistos.length, vistos.join(' | ')).toBe(ordem.length);
    ordem.forEach((rotulo, indice) => expect(vistos[indice]).toContain(rotulo));
    expect(el.querySelector('form.campos dl.contexto dd')!.textContent!.trim()).toBe('1.ª Série');
    const publicacao = el.querySelector('fieldset.publicacao') as HTMLElement;
    expect(publicacao.getAttribute('role')).toBe('radiogroup');
    expect(publicacao.getAttribute('aria-required')).toBe('true');
    expect(Array.from(publicacao.querySelectorAll('label')).map((opcao) => opcao.textContent!.trim())).toEqual(['Diário', 'Suplemento']);
    expect(el.querySelector('input[type="checkbox"][formcontrolname="publicarSuplemento"]')).toBeNull();
    // O contexto do processo está na secção própria por cima do cartão.
    expect(el.querySelector('.corpo > dl.contexto')).toBeNull();
    expect(el.querySelector('fieldset.elementos')).toBeNull();
  });

  it('marca todos os campos como obrigatórios e agrupa o Tipo de informação', () => {
    const el = abrir('SUSPENSAO_MP_GOVERNO');
    for (const nome of ['entidadeEmitente', 'contactoTramitacao', 'contactoPublicacao', 'normaLegal']) {
      expect(el.querySelector(`input[formcontrolname="${nome}"]`)!.getAttribute('aria-required')).toBe('true');
    }
    expect(el.querySelector('textarea[formcontrolname="sumario"]')!.getAttribute('aria-required')).toBe('true');
    expect(el.querySelector('input.data-assinatura')!.getAttribute('aria-required')).toBe('true');
    const tipoInformacao = el.querySelector('fieldset.tipo-informacao') as HTMLElement;
    expect(tipoInformacao.querySelector('legend')!.textContent!.trim()).toBe('Tipo de informação');
    expect(tipoInformacao.getAttribute('role')).toBe('radiogroup');
    expect(tipoInformacao.getAttribute('aria-required')).toBe('true');
    expect(el.querySelector('app-seletor.tipo-ato select')!.hasAttribute('required')).toBe(true);
  });

  it('com Suplemento pede a data do suplemento e grava os dois; com Diário a data não vai', () => {
    const el = abrir('SUSPENSAO_MP_GOVERNO', { publicarSuplemento: false });
    expect(el.querySelector('input[name="dataSuplemento"]')).toBeNull();
    expect(fixture.componentInstance.dados()).toMatchObject({ publicarSuplemento: false, dataSuplemento: null });

    (el.querySelectorAll('fieldset.publicacao input')[1] as HTMLInputElement).click();
    fixture.detectChanges();
    const data = el.querySelector('input[name="dataSuplemento"]') as HTMLInputElement;
    expect(data.getAttribute('aria-required')).toBe('true');
    expect(data.closest('label')!.textContent).toContain('Data do suplemento');
    fixture.componentInstance.dataSuplemento.setValue('2026-11-20');
    expect(fixture.componentInstance.dados()).toMatchObject({ publicarSuplemento: true, dataSuplemento: '2026-11-20' });
  });

  it('na Prorrogação não mostra a Publicação nem a caixa do suplemento e não os envia', () => {
    const el = abrir('PRORROGACAO_GOVERNO', { publicarSuplemento: false, dataSuplemento: '2026-11-20' });

    expect(el.querySelector('fieldset.publicacao')).toBeNull();
    expect(el.querySelector('input[formcontrolname="publicarSuplemento"]')).toBeNull();
    expect(el.textContent).not.toContain('Publicação');
    expect(el.querySelector('fieldset.tipo-informacao')).not.toBeNull();
    expect(el.querySelector('input[formcontrolname="normaLegal"]')).not.toBeNull();
    expect(fixture.componentInstance.dados()).toMatchObject({ publicarSuplemento: null, dataSuplemento: null, elementosAfetados: [] });
  });

  it('os erros do servidor na Publicação e no contacto de tramitação são alertas que descrevem o campo', async () => {
    abrir('SUSPENSAO_MP_GOVERNO');
    fixture.componentRef.setInput('errosServidor', [
      { path: 'publicarSuplemento', erro: 'Indique se a publicação é no Diário ou em Suplemento.' },
      { path: 'contactoTramitacao', erro: 'Indique um e-mail válido para a tramitação.' },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('#erro-publicarSuplemento')!.getAttribute('role')).toBe('alert');
    expect(el.querySelector('fieldset.publicacao')!.getAttribute('aria-describedby')).toContain('erro-publicarSuplemento');
    expect(el.querySelector('#erro-contactoTramitacao')!.textContent!.trim()).toBe('Indique um e-mail válido para a tramitação.');
    expect(el.querySelector('input[formcontrolname="contactoTramitacao"]')!.getAttribute('aria-describedby')).toContain('erro-contactoTramitacao');
  });

  it('depois da submissão os campos bloqueados ficam desativados com o aviso, incluindo a Publicação e a data do suplemento', () => {
    fixture.componentRef.setInput('camposBloqueados', ['sumario', 'publicarSuplemento', 'dataSuplemento', 'tipoInformacao']);
    const el = abrir('SUSPENSAO_MP_GOVERNO', { publicarSuplemento: true, dataSuplemento: '2026-11-20', sumario: 'Suspensão.' });

    expect(fixture.componentInstance.form.controls.publicarSuplemento.disabled).toBe(true);
    expect(fixture.componentInstance.dataSuplemento.disabled).toBe(true);
    expect(fixture.componentInstance.form.controls.sumario.disabled).toBe(true);
    expect(el.querySelector('#bloqueado-publicarSuplemento')).not.toBeNull();
    expect(el.querySelector('#bloqueado-dataSuplemento')).not.toBeNull();
    expect(fixture.componentInstance.dados()).toMatchObject({ publicarSuplemento: true, dataSuplemento: '2026-11-20' });
  });

  it('em leitura mostra a Publicação na Publicação e não na Prorrogação, com a norma legal e o tipo de informação', () => {
    let el = abrir(
      'SUSPENSAO_MP_GOVERNO',
      { publicarSuplemento: true, normaLegal: 'Artigo 126.º do RJIGT', tipoInformacao: 'EXTRATO', dataAssinatura: '2026-11-10' },
      true,
    );
    let leitura = el.querySelector('dl.leitura') as HTMLElement;
    expect(leitura.querySelector('.publicacao')!.textContent!.trim()).toBe('Suplemento');
    expect(leitura.textContent).toContain('Artigo 126.º do RJIGT');
    expect(leitura.textContent).toContain('Extrato');
    expect(leitura.querySelector('.data-assinatura')!.textContent!.trim()).toBe('10/11/2026');

    fixture = TestBed.createComponent(ProcessoFormularioInicialComponent);
    el = abrir('PRORROGACAO_GOVERNO', { publicarSuplemento: false, normaLegal: 'Artigo 126.º do RJIGT', dataAssinatura: '2026-11-10' }, true);
    leitura = el.querySelector('dl.leitura') as HTMLElement;
    expect(leitura.querySelector('.publicacao')).toBeNull();
    expect(leitura.textContent).not.toContain('Publicar suplemento');
    expect(leitura.querySelector('.data-assinatura')!.textContent!.trim()).toBe('10/11/2026');
  });
});

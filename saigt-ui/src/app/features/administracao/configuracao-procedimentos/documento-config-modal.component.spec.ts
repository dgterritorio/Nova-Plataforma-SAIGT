import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoConfig } from '../../../models/configuracao.model';
import { AJUDA_LEGENDA, AJUDA_SEPARADOR, DocumentoConfigModalComponent, ValorDocumentoConfig } from './documento-config-modal.component';

const PLANTA: DocumentoConfig = {
  id: 77,
  instrumentoProcedimentoConfigId: 209,
  codigo: 'PLANTA_ORD_DEP',
  nome: 'Planta de ordenamento',
  separador: 'Planta de ordenamento',
  descricao: null,
  categoria: 'DEPOSITO',
  tipoElemento: 'PECA_GRAFICA',
  formato: 'NAO_EDITAVEL',
  extensoesAceites: ['zip'],
  obrigatorio: true,
  ordem: 4,
  elementoInformacaoBase: null,
  tamanhoMaximoFicheiro: 524288000,
  tamanhoMaximoTotal: null,
  numeroMaximoFicheiros: 2,
  linkPublicoPermitido: true,
  ativo: true,
  regrasTiff: {
    sistemasReferencia: ['EPSG:3763'],
    compressoesAceites: ['NONE'],
    bandaAlfaTolerada: false,
    modeloCor: 'RGB',
    bitsPorBanda: 8,
    resolucaoDpi: 300,
    elementosLegenda: ['ID do plano', 'N.º de folha'],
  },
  regraNomenclatura: 'SGIFR',
};

@Component({
  imports: [DocumentoConfigModalComponent],
  template: `
    <app-documento-config-modal
      [aberto]="true"
      procedimento="PDM — Alteração — Decisão de início"
      [item]="item()"
      [erroServidor]="erro()"
      (guardar)="valores.push($event)"
    />
  `,
})
class AnfitriaoComponent {
  readonly item = signal<DocumentoConfig | null>(null);
  readonly erro = signal<ApiError | null>(null);
  readonly valores: ValorDocumentoConfig[] = [];
}

describe('DocumentoConfigModalComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  function criar(item: DocumentoConfig | null = null): AnfitriaoComponent {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.componentInstance.item.set(item);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const dialogo = (): HTMLElement => document.querySelector('[role="dialog"]') as HTMLElement;
  const procurarCampo = <T extends HTMLElement>(rotulo: string): T | null => {
    const label = [...dialogo().querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo));
    if (!label) {
      return null;
    }
    return (label.htmlFor ? document.getElementById(label.htmlFor) : label.querySelector('input')) as T;
  };
  const campo = <T extends HTMLElement>(rotulo: string): T => procurarCampo<T>(rotulo)!;
  const descricao = (entrada: HTMLElement): string =>
    (entrada.getAttribute('aria-describedby') ?? '')
      .split(' ')
      .filter(Boolean)
      .map((id) => document.getElementById(id)?.textContent?.trim())
      .join(' | ');
  const botaoGuardar = (): HTMLButtonElement =>
    [...dialogo().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'Guardar')!;
  const grupoTiff = (): HTMLFieldSetElement | undefined =>
    [...dialogo().querySelectorAll('fieldset')].find((f) => f.querySelector('legend')?.textContent?.trim() === 'Regras do GeoTIFF');

  function escolher(entrada: HTMLSelectElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function escrever(entrada: HTMLInputElement | HTMLTextAreaElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function clicar(entrada: HTMLElement): void {
    entrada.click();
    fixture.detectChanges();
  }

  function preencherPecaEscrita(): void {
    escrever(campo('Código'), 'RELATORIO_PONDERACAO');
    escrever(campo('Nome'), 'Relatório de Ponderação');
    escrever(campo('Separador'), 'Relatório de Ponderação');
    escolher(campo('Tipo de elemento'), 'PECA_ESCRITA');
    escolher(campo('Caixa'), 'DEPOSITO');
    escrever(campo('Extensões aceites'), '.PDF, jpg');
    escrever(campo('Ordem'), '7');
  }

  it('ao criar: título, código com ajuda, regra de nome Geral, ativo marcado e sem grupo TIFF até escolher o tipo', () => {
    criar();

    expect(dialogo().querySelector('h2')?.textContent).toContain('Novo item documental');
    expect(dialogo().textContent).toContain('PDM — Alteração — Decisão de início');
    expect(descricao(campo('Código'))).toContain('Ex.: TEXTO_ATO_PUB');
    expect(descricao(campo('Separador'))).toContain(AJUDA_SEPARADOR);
    expect(campo<HTMLSelectElement>('Regra de nome').value).toBe('GERAL');
    expect([...campo<HTMLSelectElement>('Regra de nome').options].map((o) => o.textContent?.trim())).toContain(
      'SGIFR (Nome_região_temática)',
    );
    expect(campo<HTMLInputElement>('Ativo').checked).toBe(true);
    expect(campo<HTMLInputElement>('Obrigatório').checked).toBe(false);
    expect(grupoTiff()).toBeUndefined();
    expect([...dialogo().querySelectorAll('fieldset legend')].map((l) => l.textContent?.trim())).toEqual(['Limites']);
  });

  it('peça escrita: a caixa propõe o formato do BL-DOC-023, a escolha fica livre, o pedido não leva regrasTiff e a Publicação leva link público', () => {
    const anfitriao = criar();
    preencherPecaEscrita();

    expect(campo<HTMLSelectElement>('Formato').value).toBe('NAO_EDITAVEL');
    escolher(campo('Caixa'), 'PUBLICACAO');
    expect(campo<HTMLSelectElement>('Formato').value).toBe('EDITAVEL');
    escolher(campo('Formato'), 'NAO_EDITAVEL');
    escolher(campo('Informação Base'), 'DELIBERACAO');
    clicar(campo('Obrigatório'));
    clicar(botaoGuardar());

    expect(anfitriao.valores).toHaveLength(1);
    const { codigo, pedido } = anfitriao.valores[0];
    expect(codigo).toBe('RELATORIO_PONDERACAO');
    expect(pedido).toEqual({
      nome: 'Relatório de Ponderação',
      separador: 'Relatório de Ponderação',
      descricao: null,
      categoria: 'PUBLICACAO',
      tipoElemento: 'PECA_ESCRITA',
      formato: 'NAO_EDITAVEL',
      extensoesAceites: ['.PDF', 'jpg'],
      obrigatorio: true,
      ordem: 7,
      elementoInformacaoBase: 'DELIBERACAO',
      tamanhoMaximoFicheiro: null,
      tamanhoMaximoTotal: null,
      numeroMaximoFicheiros: null,
      linkPublicoPermitido: true,
      ativo: true,
      regraNomenclatura: 'GERAL',
    });
    expect('regrasTiff' in pedido).toBe(false);
  });

  it('a ajuda das extensões mostra as da EF para o tipo e a caixa, numa região anunciada', () => {
    criar();
    const regiao = dialogo().querySelector('p.so-leitor[aria-live="polite"]');
    expect(regiao?.textContent).toContain('Escolha a caixa e o tipo de elemento');

    escolher(campo('Tipo de elemento'), 'PECA_ESCRITA');
    escolher(campo('Caixa'), 'DEPOSITO');
    expect(regiao?.textContent).toContain('pdf, jpg, png');
    expect(descricao(campo('Extensões aceites'))).toContain('pdf, jpg, png');

    escolher(campo('Tipo de elemento'), 'OUTROS');
    expect(regiao?.textContent).toContain('não restringe');
  });

  it('peça gráfica: o grupo TIFF aparece com a configuração inicial geral; ao mudar para peça escrita desaparece e não segue', () => {
    const anfitriao = criar();
    preencherPecaEscrita();
    escolher(campo('Tipo de elemento'), 'PECA_GRAFICA');
    escrever(campo('Extensões aceites'), 'zip');

    expect(grupoTiff()).toBeDefined();
    expect(campo<HTMLInputElement>('Sistemas de referência').value).toBe('EPSG:3763, EPSG:5014, EPSG:5015, EPSG:5016');
    expect(campo<HTMLInputElement>('Sem compressão').checked).toBe(true);
    expect(campo<HTMLInputElement>('LZW').checked).toBe(true);
    expect(campo<HTMLInputElement>('Banda alfa tolerada').checked).toBe(true);
    expect(campo<HTMLSelectElement>('Modelo de cor').value).toBe('');
    expect(campo<HTMLInputElement>('Resolução (dpi)').value).toBe('');
    expect(descricao(campo('Elementos da legenda'))).toContain(AJUDA_LEGENDA);

    clicar(botaoGuardar());
    expect(anfitriao.valores[0].pedido.regrasTiff).toEqual({
      sistemasReferencia: ['EPSG:3763', 'EPSG:5014', 'EPSG:5015', 'EPSG:5016'],
      compressoesAceites: ['NONE', 'LZW'],
      bandaAlfaTolerada: true,
      modeloCor: null,
      bitsPorBanda: null,
      resolucaoDpi: null,
      elementosLegenda: ['ID do plano', 'N.º de folha', 'Data de aprovação', 'Identificação do procedimento'],
    });

    escolher(campo('Tipo de elemento'), 'PECA_ESCRITA');
    escrever(campo('Extensões aceites'), 'pdf');
    expect(grupoTiff()).toBeUndefined();
    clicar(botaoGuardar());
    expect('regrasTiff' in anfitriao.valores[1].pedido).toBe(false);
  });

  it('dpi 0 ou sem compressão: o cliente assinala o campo, Guardar fica desativado e nada segue', () => {
    const anfitriao = criar();
    preencherPecaEscrita();
    escolher(campo('Tipo de elemento'), 'PECA_GRAFICA');
    escrever(campo('Extensões aceites'), 'zip');

    escrever(campo('Resolução (dpi)'), '0');
    expect(descricao(campo('Resolução (dpi)'))).toContain('Use um número inteiro igual ou superior a 1, ou deixe vazio.');
    expect(botaoGuardar().disabled).toBe(true);
    clicar(botaoGuardar());
    expect(anfitriao.valores).toHaveLength(0);

    escrever(campo('Resolução (dpi)'), '300');
    clicar(campo('Sem compressão'));
    clicar(campo('LZW'));
    const grupo = [...dialogo().querySelectorAll('fieldset')].find((f) => f.querySelector('legend')?.textContent?.trim() === 'Compressões aceites')!;
    expect(descricao(grupo)).toContain('Marque pelo menos uma compressão aceite.');
    expect(botaoGuardar().disabled).toBe(true);

    clicar(campo('LZW'));
    escrever(campo('Sistemas de referência'), '3763');
    expect(descricao(campo('Sistemas de referência'))).toContain('EPSG:<código>');
    escrever(campo('Sistemas de referência'), 'EPSG:3763');
    clicar(botaoGuardar());
    expect(anfitriao.valores[0].pedido.regrasTiff).toMatchObject({
      sistemasReferencia: ['EPSG:3763'],
      compressoesAceites: ['LZW'],
      resolucaoDpi: 300,
    });
  });

  it('código fora do padrão e extensões inválidas são assinalados no cliente', () => {
    criar();
    escrever(campo('Código'), 'texto ato');
    escrever(campo('Extensões aceites'), 'pdf, .tar.gz');

    expect(descricao(campo('Código'))).toContain('Use só maiúsculas, algarismos e «_».');
    expect(descricao(campo('Extensões aceites'))).toContain('Separe as extensões por vírgulas');
  });

  it('limites: 500 MB seguem como 524288000 bytes', () => {
    const anfitriao = criar();
    preencherPecaEscrita();
    escrever(campo('Tamanho máximo por ficheiro (MB)'), '500');
    escrever(campo('Tamanho máximo do item (MB)'), '1024');
    escrever(campo('N.º máximo de ficheiros'), '3');
    clicar(botaoGuardar());

    expect(anfitriao.valores[0].pedido).toMatchObject({
      tamanhoMaximoFicheiro: 524288000,
      tamanhoMaximoTotal: 1073741824,
      numeroMaximoFicheiros: 3,
    });
  });

  it('limites gravados em bytes aparecem em MB ao reabrir', () => {
    criar(PLANTA);
    expect(campo<HTMLInputElement>('Tamanho máximo por ficheiro (MB)').value).toBe('500');
    expect(campo<HTMLInputElement>('Tamanho máximo do item (MB)').value).toBe('');
    expect(campo<HTMLInputElement>('N.º máximo de ficheiros').value).toBe('2');
  });

  it('um limite gravado que não é um número inteiro de MB aparece sem arredondar e volta com os mesmos bytes', () => {
    const anfitriao = criar({ ...PLANTA, tamanhoMaximoFicheiro: 1000, tamanhoMaximoTotal: 1572864 });
    expect(campo<HTMLInputElement>('Tamanho máximo do item (MB)').value).toBe('1.5');
    clicar(botaoGuardar());

    expect(anfitriao.valores[0].pedido).toMatchObject({ tamanhoMaximoFicheiro: 1000, tamanhoMaximoTotal: 1572864 });
  });

  it('limites em MB: aceita casas decimais; 0 é assinalado e nada segue', () => {
    const anfitriao = criar();
    preencherPecaEscrita();
    escrever(campo('Tamanho máximo por ficheiro (MB)'), '0');
    clicar(botaoGuardar());
    expect(descricao(campo('Tamanho máximo por ficheiro (MB)'))).toContain('Use um número de MB superior a 0');
    expect(anfitriao.valores).toHaveLength(0);

    escrever(campo('Tamanho máximo por ficheiro (MB)'), '0.5');
    clicar(botaoGuardar());
    expect(anfitriao.valores[0].pedido.tamanhoMaximoFicheiro).toBe(524288);
  });

  it('link público: ao criar no Depósito vai falso', () => {
    const criacao = criar();
    preencherPecaEscrita();
    clicar(botaoGuardar());
    expect(criacao.valores[0].pedido.linkPublicoPermitido).toBe(false);
  });

  it('link público: ao editar mantém o gravado; ao mudar o item para a Publicação passa a verdadeiro', () => {
    const edicao = criar({ ...PLANTA, linkPublicoPermitido: false });
    clicar(botaoGuardar());
    expect(edicao.valores[0].pedido.linkPublicoPermitido).toBe(false);
    escolher(campo('Caixa'), 'PUBLICACAO');
    clicar(botaoGuardar());
    expect(edicao.valores.at(-1)!.pedido.linkPublicoPermitido).toBe(true);
  });

  it('edição: título com o nome, sem código, valores e regras gravadas; mantém o link público e emite sem código', () => {
    const anfitriao = criar(PLANTA);

    expect(dialogo().querySelector('h2')?.textContent).toContain('Editar Planta de ordenamento');
    expect(procurarCampo('Código')).toBeNull();
    expect(campo<HTMLSelectElement>('Regra de nome').value).toBe('SGIFR');
    expect(campo<HTMLInputElement>('Sistemas de referência').value).toBe('EPSG:3763');
    expect(campo<HTMLInputElement>('LZW').checked).toBe(false);
    expect(campo<HTMLSelectElement>('Modelo de cor').value).toBe('RGB');
    expect(campo<HTMLTextAreaElement>('Elementos da legenda').value).toBe('ID do plano\nN.º de folha');
    expect(campo<HTMLSelectElement>('Caixa').disabled).toBe(false);

    escolher(campo('Regra de nome'), 'GERAL');
    clicar(botaoGuardar());
    expect(anfitriao.valores[0].codigo).toBeNull();
    expect(anfitriao.valores[0].pedido).toMatchObject({
      linkPublicoPermitido: true,
      regraNomenclatura: 'GERAL',
      tamanhoMaximoFicheiro: 524288000,
      regrasTiff: PLANTA.regrasTiff!,
    });
  });

  it('erros do servidor ficam no campo do mesmo path, também os das regras TIFF; 409 EM_USO vai para o topo do modal', () => {
    const anfitriao = criar(PLANTA);
    anfitriao.erro.set(
      new ApiError('VALIDACAO', 'Item documental inválido.', 't', [
        { path: 'regrasTiff.sistemasReferencia', erro: 'Cada sistema de referência tem de ter o formato EPSG:<código>.' },
        { path: 'regrasTiff.compressoesAceites[0]', erro: 'Valor inválido.' },
        { path: 'extensoesAceites[0]', erro: 'Extensão inválida.' },
      ]),
    );
    fixture.detectChanges();
    expect(descricao(campo('Sistemas de referência'))).toContain('Cada sistema de referência tem de ter o formato EPSG:<código>.');
    expect(dialogo().textContent).toContain('Valor inválido.');
    expect(descricao(campo('Extensões aceites'))).toContain('Extensão inválida.');
    expect(dialogo().querySelector('form')?.firstElementChild?.tagName).not.toBe('APP-ALERTA');

    anfitriao.erro.set(new ApiError('CONFIG_FORMATO_INCOERENTE', 'Formato incoerente.', 't', [{ path: 'formato', erro: 'Numa peça escrita de depósito, o formato é não editável.' }]));
    fixture.detectChanges();
    expect(descricao(campo('Formato'))).toContain('Numa peça escrita de depósito, o formato é não editável.');

    anfitriao.erro.set(new ApiError('CONFIG_EXTENSAO_NAO_ACEITE', 'Extensão não aceite.', 't', [{ path: 'extensoesAceites', erro: 'Extensão fora da tabela da EF.' }]));
    fixture.detectChanges();
    expect(descricao(campo('Extensões aceites'))).toContain('Extensão fora da tabela da EF.');

    anfitriao.erro.set(new ApiError('EM_USO', 'O registo já está a ser usado e não pode mudar.', 't', null));
    fixture.detectChanges();
    const alerta = dialogo().querySelector('app-alerta[role="alert"]');
    expect(alerta?.textContent).toContain('O registo já está a ser usado e não pode mudar.');
    expect(dialogo().querySelector('form')?.firstElementChild).toBe(alerta);
  });

  it('um erro num path que o formulário não tem vai para o topo do modal, sem perder os dos campos', () => {
    const anfitriao = criar(PLANTA);
    anfitriao.erro.set(
      new ApiError('VALIDACAO', 'Item documental inválido.', 't', [
        { path: 'extensoesAceites', erro: 'Extensão fora da tabela da EF.' },
        { path: 'instrumentoProcedimentoConfigId', erro: 'O Procedimento não existe.' },
      ]),
    );
    fixture.detectChanges();

    expect(descricao(campo('Extensões aceites'))).toContain('Extensão fora da tabela da EF.');
    const alerta = dialogo().querySelector('app-alerta[role="alert"]');
    expect(alerta?.textContent).toContain('O Procedimento não existe.');
    expect(dialogo().querySelector('form')?.firstElementChild).toBe(alerta);
  });

  it('código repetido: a mensagem do servidor aparece no campo Código e o modal fica aberto', () => {
    const anfitriao = criar();
    preencherPecaEscrita();
    clicar(botaoGuardar());
    anfitriao.erro.set(new ApiError('VALIDACAO', 'Código duplicado.', 't', [{ path: 'codigo', erro: 'Já existe um item com este código neste Procedimento.' }]));
    fixture.detectChanges();

    expect(descricao(campo('Código'))).toContain('Já existe um item com este código neste Procedimento.');
    expect(campo<HTMLInputElement>('Nome').value).toBe('Relatório de Ponderação');

    escrever(campo('Código'), 'RELATORIO_PONDERACAO_2');
    expect(descricao(campo('Código'))).not.toContain('Já existe');
  });
});

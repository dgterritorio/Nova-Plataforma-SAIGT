import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../core/models/error-response.model';
import { DocumentoProcesso, HistoricoDocumental, ProcessoHistoricoDocumental } from '../../models/documento.model';
import { DescargaService } from '../../services/descarga.service';
import { DocumentoService } from '../../services/documento.service';
import { HistoricoDocumentalComponent } from './historico-documental.component';

function documento(parcial: Partial<DocumentoProcesso> = {}): DocumentoProcesso {
  return {
    id: 'd1',
    documentoConfigId: 1,
    nomeFicheiro: 'deliberacao.pdf',
    categoria: 'PUBLICACAO',
    extensao: 'pdf',
    origem: 'CARREGADO',
    bloqueadoEdicao: false,
    estadoValidacao: 'POR_VALIDAR',
    tamanhoBytes: 1024,
    criadoEm: '2026-10-20T09:30:00Z',
    criadoPorId: 'u1',
    criadoPorNome: 'Helena Marques',
    tags: [],
    justificacaoDesbloqueio: null,
    ...parcial,
  };
}

function processo(parcial: Partial<ProcessoHistoricoDocumental> = {}): ProcessoHistoricoDocumental {
  return {
    processoId: 'p1',
    numeroProcesso: 'PROC-1',
    ocorrencia: 1,
    estado: 'PUBLICADO',
    dataSubmissao: '2026-10-01T10:00:00Z',
    numeroDr: null,
    dataPublicacaoDr: null,
    linkDr: null,
    atual: false,
    documentos: [],
    ...parcial,
  };
}

const DOIS_PROCESSOS: HistoricoDocumental = {
  processos: [
    processo({
      processoId: 'p1',
      numeroProcesso: 'PROC-1',
      ocorrencia: 1,
      numeroDr: '123/2026',
      dataPublicacaoDr: '2026-10-15',
      linkDr: 'https://diariodarepublica.pt/dr/detalhe/aviso/123-2026',
      documentos: [documento({ id: 'd1' }), documento({ id: 'd2', nomeFicheiro: 'planta.dwg', extensao: 'dwg', categoria: 'DEPOSITO' })],
    }),
    processo({
      processoId: 'p2',
      numeroProcesso: 'PROC-2',
      ocorrencia: 2,
      estado: 'EM_SUBMISSAO',
      atual: true,
      documentos: [documento({ id: 'd3', nomeFicheiro: 'nova.pdf' })],
    }),
  ],
};

describe('HistoricoDocumentalComponent', () => {
  let fixture: ComponentFixture<HistoricoDocumentalComponent>;
  let historico: ReturnType<typeof vi.fn>;
  let descarregar: ReturnType<typeof vi.fn>;
  let erroDescarga: ReturnType<typeof vi.fn>;
  let limparErroDescarga: ReturnType<typeof vi.fn>;

  function abrir(): void {
    TestBed.configureTestingModule({
      providers: [
        { provide: DocumentoService, useValue: { historico } },
        { provide: DescargaService, useValue: { descarregar, erro: Object.assign(erroDescarga, { set: limparErroDescarga }) } },
      ],
    });
    fixture = TestBed.createComponent(HistoricoDocumentalComponent);
    fixture.componentRef.setInput('processoId', 'p2');
    fixture.detectChanges();
  }

  function secoes(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('section.submissao'));
  }

  function cabecalho(indice: number): HTMLButtonElement {
    return secoes()[indice].querySelector('button.submissao-cabecalho') as HTMLButtonElement;
  }

  beforeEach(() => {
    historico = vi.fn().mockReturnValue(of(DOIS_PROCESSOS));
    descarregar = vi.fn();
    erroDescarga = vi.fn().mockReturnValue(null);
    limparErroDescarga = vi.fn();
  });

  it('pede o histórico do processo ao abrir', () => {
    abrir();

    expect(historico).toHaveBeenCalledWith('p2');
  });

  it('mostra o cartão com um acordeão por submissão', () => {
    abrir();

    expect(fixture.nativeElement.textContent).toContain('Histórico documental');
    expect(secoes()).toHaveLength(2);
    expect(cabecalho(0).textContent).toContain('Submissão n.º 1 — Processo PROC-1');
    expect(cabecalho(0).textContent).toContain('Publicado');
    expect(cabecalho(1).textContent).toContain('Submissão n.º 2 — Processo PROC-2');
    expect(cabecalho(1).textContent).toContain('Em submissão');
  });

  it('marca o processo atual com «Este processo» e fecha-o por omissão; os outros ficam abertos', () => {
    abrir();

    expect(cabecalho(0).textContent).not.toContain('Este processo');
    expect(cabecalho(1).textContent).toContain('Este processo');
    expect(cabecalho(0).getAttribute('aria-expanded')).toBe('true');
    expect(cabecalho(1).getAttribute('aria-expanded')).toBe('false');
  });

  it('liga cada cabeçalho ao seu painel com aria-controls', () => {
    abrir();

    for (const indice of [0, 1]) {
      const painel = fixture.nativeElement.querySelector(`#${cabecalho(indice).getAttribute('aria-controls')}`) as HTMLElement;
      expect(painel).not.toBeNull();
      expect(painel.hidden).toBe(cabecalho(indice).getAttribute('aria-expanded') === 'false');
    }
  });

  it('abre e fecha o acordeão ao carregar no cabeçalho', () => {
    abrir();

    cabecalho(1).click();
    fixture.detectChanges();
    expect(cabecalho(1).getAttribute('aria-expanded')).toBe('true');

    cabecalho(0).click();
    fixture.detectChanges();
    expect(cabecalho(0).getAttribute('aria-expanded')).toBe('false');
  });

  it('mostra nome, tipo, caixa, data de envio e estado de validação de cada ficheiro', () => {
    abrir();

    const linhas = secoes()[0].querySelectorAll('tbody tr');
    const celulas = Array.from(linhas[1].querySelectorAll('td')).map((td) => (td as HTMLElement).textContent?.trim());
    expect(linhas).toHaveLength(2);
    expect(celulas.slice(0, 3)).toEqual(['planta.dwg', 'DWG', 'Depósito']);
    expect(celulas[3]).toMatch(/^20\/10\/2026 \d{2}:\d{2}$/);
    expect(celulas[4]).toBe('Por validar');
  });

  it('mostra o número, a data e a ligação ao Diário da República das submissões publicadas', () => {
    abrir();

    const publicacao = secoes()[0].querySelector('.publicacao') as HTMLElement;
    const ligacao = publicacao.querySelector('a') as HTMLAnchorElement;
    expect(publicacao.textContent).toContain('123/2026');
    expect(publicacao.textContent).toContain('15/10/2026');
    expect(ligacao.textContent).toContain('Ver no Diário da República');
    expect(ligacao.getAttribute('href')).toBe('https://diariodarepublica.pt/dr/detalhe/aviso/123-2026');
    expect(ligacao.getAttribute('target')).toBe('_blank');
    expect(ligacao.getAttribute('rel')).toContain('noopener');
    expect(ligacao.getAttribute('aria-label')).toContain('abre noutra janela');
    expect(secoes()[1].querySelector('.publicacao')).toBeNull();
  });

  it('não liga a um linkDr que não seja https', () => {
    historico.mockReturnValue(
      of({
        processos: [
          processo({ ocorrencia: 1, numeroDr: '1/2026', linkDr: 'javascript:alert(1)' }),
          processo({ processoId: 'p2', ocorrencia: 2, atual: true }),
        ],
      }),
    );
    abrir();

    expect(fixture.nativeElement.querySelector('.publicacao a')).toBeNull();
    expect(fixture.nativeElement.querySelector('.publicacao').textContent).toContain('1/2026');
  });

  it('pede a descarga ao processo a que o ficheiro pertence, não ao processo aberto', () => {
    abrir();

    const botao = secoes()[0].querySelector('tbody button') as HTMLButtonElement;
    botao.click();

    expect(descarregar).toHaveBeenCalledWith('p1', 'd1');
  });

  it('dá ao botão Descarregar um nome acessível com o ficheiro e a submissão', () => {
    abrir();

    const botao = secoes()[0].querySelector('tbody button') as HTMLButtonElement;
    expect(botao.getAttribute('aria-label')).toBe('Descarregar deliberacao.pdf, submissão n.º 1');
  });

  it('mostra «Sem ficheiros nesta submissão.» num processo sem ficheiros', () => {
    historico.mockReturnValue(
      of({ processos: [processo({ ocorrencia: 1 }), processo({ processoId: 'p2', ocorrencia: 2, atual: true })] }),
    );
    abrir();

    expect(secoes()[0].textContent).toContain('Sem ficheiros nesta submissão.');
    expect(secoes()[0].querySelector('table')).toBeNull();
  });

  it('mostra a mensagem de descarga quando o ticket falha', () => {
    erroDescarga.mockReturnValue('Não foi possível descarregar o ficheiro. Tente novamente.');
    abrir();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Não foi possível descarregar');
  });

  it('limpa o erro de uma descarga anterior ao abrir', () => {
    abrir();

    expect(limparErroDescarga).toHaveBeenCalledWith(null);
  });

  it('não mostra o cartão quando o Procedimento só tem um processo', () => {
    historico.mockReturnValue(of({ processos: [processo({ atual: true })] }));
    abrir();

    expect(fixture.nativeElement.querySelector('app-cartao')).toBeNull();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });

  it('mostra «A carregar…» enquanto espera pela resposta', () => {
    historico.mockReturnValue(NEVER);
    abrir();

    expect(fixture.nativeElement.textContent).toContain('A carregar…');
  });

  it('mostra o erro e «Tentar novamente» quando a API falha, e volta a pedir ao carregar', () => {
    historico.mockReturnValueOnce(throwError(() => new ApiError('ERRO_INTERNO', 'Erro interno.', 't1', null)));
    abrir();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    const tentar = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLElement).textContent?.trim() === 'Tentar novamente',
    ) as HTMLButtonElement;
    expect(tentar).toBeDefined();

    tentar.click();
    fixture.detectChanges();

    expect(historico).toHaveBeenCalledTimes(2);
    expect(secoes()).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('troca o texto técnico de um erro sem resposta do servidor por uma mensagem legível', () => {
    historico.mockReturnValue(throwError(() => new ApiError('ERRO_DESCONHECIDO', 'Http failure response', 't1', null)));
    abrir();

    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar o histórico documental.');
    expect(fixture.nativeElement.textContent).not.toContain('Http failure response');
  });
});

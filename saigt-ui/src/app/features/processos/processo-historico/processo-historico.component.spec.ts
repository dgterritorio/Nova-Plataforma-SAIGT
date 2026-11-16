import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProcessoHistoricoComponent } from './processo-historico.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { HistoricoProcessoEntrada } from '../../../models/processo.model';
import { ProcessoService } from '../../../services/processo.service';

function entrada(parcial: Partial<HistoricoProcessoEntrada> = {}): HistoricoProcessoEntrada {
  return {
    id: 1,
    acao: 'SUBMETER',
    estadoAntes: 'EM_SUBMISSAO',
    estadoDepois: 'ENVIADO_DGT',
    utilizadorId: 'u1',
    utilizadorNome: 'Helena Marques',
    dadosAlteracao: null,
    criadoEm: '2026-09-18T14:11:15Z',
    ...parcial,
  };
}

function pagina(content: HistoricoProcessoEntrada[], page = 0, totalPages = 1): PageResponse<HistoricoProcessoEntrada> {
  return { content, page, size: 20, totalElements: content.length, totalPages };
}

describe('ProcessoHistoricoComponent', () => {
  let fixture: ComponentFixture<ProcessoHistoricoComponent>;
  let historico: ReturnType<typeof vi.fn>;

  function configurar(): void {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ProcessoService, useValue: { historico } },
        {
          provide: AuthService,
          useValue: {
            nome: () => 'Helena Marques',
            origem: () => 'INCM',
            entidade: () => null,
            perfilInterno: () => null,
            utilizadorId: () => 'u1',
            ator: () => 'ENTIDADE',
          },
        },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ processoId: 'p1' }) } } },
      ],
    });
    fixture = TestBed.createComponent(ProcessoHistoricoComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    historico = vi.fn().mockReturnValue(of(pagina([entrada()])));
  });

  it('pede a primeira página do histórico do processo da rota', () => {
    configurar();

    expect(historico).toHaveBeenCalledWith('p1', 0, 20);
  });

  it('mostra a ação, quem e a transição com os rótulos atuais', () => {
    configurar();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Submeter');
    expect(texto).toContain('Helena Marques');
    expect(texto).toContain('Em submissão → Enviado DGT');
    expect(texto).toContain('18/09/2026');
  });

  it('traduz as entradas antigas SUBMETIDO e REJEITADO_INCM', () => {
    historico.mockReturnValue(
      of(
        pagina([
          entrada({ id: 2, estadoAntes: 'EM_SUBMISSAO', estadoDepois: 'SUBMETIDO' }),
          entrada({ id: 3, acao: 'ANULAR_INCM', estadoAntes: 'ENVIADO_INCM', estadoDepois: 'REJEITADO_INCM' }),
        ]),
      ),
    );
    configurar();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Em submissão → Enviado DGT');
    expect(texto).toContain('Enviado INCM → Anulado INCM');
    expect(texto).not.toContain('SUBMETIDO');
    expect(texto).not.toContain('REJEITADO_INCM');
  });

  it('mostra Sistema nas ações sem utilizador', () => {
    historico.mockReturnValue(of(pagina([entrada({ utilizadorId: null, utilizadorNome: null })])));
    configurar();

    expect(fixture.nativeElement.querySelector('.autor').textContent).toContain('Sistema');
  });

  it('mostra o detalhe da alteração quando é um objeto JSON', () => {
    historico.mockReturnValue(
      of(pagina([entrada({ acao: 'REJEITAR', estadoAntes: null, estadoDepois: null, dadosAlteracao: '{"motivoCodigo":"DOC_INCOMPLETA"}' })])),
    );
    configurar();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('motivoCodigo');
    expect(texto).toContain('DOC_INCOMPLETA');
    expect(fixture.nativeElement.querySelector('.transicao')).toBeNull();
  });

  it('ignora um dadosAlteracao que não seja JSON de objeto', () => {
    historico.mockReturnValue(of(pagina([entrada({ dadosAlteracao: 'texto solto' })])));
    configurar();

    expect(fixture.nativeElement.querySelector('.detalhes')).toBeNull();
  });

  it('só oferece Carregar mais quando há mais páginas e junta a página seguinte', () => {
    historico
      .mockReturnValueOnce(of(pagina([entrada({ id: 1 })], 0, 2)))
      .mockReturnValueOnce(of(pagina([entrada({ id: 2, acao: 'RESGATAR' })], 1, 2)));
    configurar();

    const botao = fixture.nativeElement.querySelector('button.btn-secundario') as HTMLButtonElement;
    expect(botao.textContent).toContain('Carregar mais');
    botao.click();
    fixture.detectChanges();

    expect(historico).toHaveBeenLastCalledWith('p1', 1, 20);
    expect(fixture.nativeElement.querySelectorAll('.entrada')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('button.btn-secundario')).toBeNull();
  });

  it('mostra Sem registos quando o histórico vem vazio', () => {
    historico.mockReturnValue(of(pagina([])));
    configurar();

    expect(fixture.nativeElement.textContent).toContain('Sem registos.');
  });

  it('mostra o erro devolvido pela API', () => {
    const erro = new ApiError('NAO_ENCONTRADO', 'Processo não encontrado.', 'trace-1', null);
    historico.mockReturnValue(throwError(() => erro));
    configurar();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});

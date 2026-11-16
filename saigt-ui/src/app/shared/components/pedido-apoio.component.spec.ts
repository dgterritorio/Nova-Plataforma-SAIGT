import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../core/models/error-response.model';
import { NotificacaoService } from '../../services/notificacao.service';
import { PedidoApoioComponent } from './pedido-apoio.component';

describe('PedidoApoioComponent', () => {
  let fixture: ComponentFixture<PedidoApoioComponent>;
  let criar: ReturnType<typeof vi.fn>;

  function configurar(processoId: string | null = null): PedidoApoioComponent {
    TestBed.configureTestingModule({
      imports: [PedidoApoioComponent],
      providers: [{ provide: NotificacaoService, useValue: { criar } }],
    });
    fixture = TestBed.createComponent(PedidoApoioComponent);
    fixture.componentRef.setInput('processoId', processoId);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    criar = vi.fn().mockReturnValue(of({ notificacoes: [], loteId: null }));
  });

  it('o botão abre o pedido', () => {
    configurar();
    expect(el().querySelector('[role="dialog"]')).toBeNull();

    el().querySelector<HTMLButtonElement>('.botao-pedido-apoio')!.click();
    fixture.detectChanges();

    expect(el().querySelector('[role="dialog"]')?.textContent).toContain('Pedido de Apoio');
  });

  it('sem assunto nem mensagem não envia e diz o que falta', () => {
    const componente = configurar();
    componente.abrir();

    componente.enviar();

    expect(criar).not.toHaveBeenCalled();
    expect(componente.erroAssunto()).toBe('Indique o assunto.');
    expect(componente.erroMensagem()).toBe('Escreva a mensagem.');
  });

  it('envia um PEDIDO_APOIO com o processo aberto e confirma o envio', () => {
    const componente = configurar('p1');
    componente.abrir();
    componente.assunto = '  Dúvida na submissão ';
    componente.mensagem = ' Não consigo carregar a planta. ';

    componente.enviar();
    fixture.detectChanges();

    expect(criar).toHaveBeenCalledWith({
      tipo: 'PEDIDO_APOIO',
      titulo: 'Dúvida na submissão',
      texto: 'Não consigo carregar a planta.',
      processoId: 'p1',
      destinatarios: [],
    });
    expect(el().textContent).toContain('O pedido foi enviado à DGT');
  });

  it('fora de um processo, o pedido vai sem processo', () => {
    const componente = configurar();
    componente.abrir();
    componente.assunto = 'Acesso';
    componente.mensagem = 'Não vejo a minha entidade.';

    componente.enviar();

    expect(criar).toHaveBeenCalledWith(expect.objectContaining({ processoId: undefined }));
  });

  it('um erro da API fica no pedido, que continua aberto', () => {
    criar.mockReturnValue(throwError(() => new ApiError('NOTIF_PERFIL_NAO_AUTORIZADO', 'Só um utilizador de entidade pode pedir apoio.', 't1', null)));
    const componente = configurar();
    componente.abrir();
    componente.assunto = 'Acesso';
    componente.mensagem = 'Texto.';

    componente.enviar();

    expect(componente.erro()).toBe('Só um utilizador de entidade pode pedir apoio.');
    expect(componente.aberto()).toBe(true);
    expect(componente.enviado()).toBe(false);
  });
});

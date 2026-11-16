import { describe, expect, it } from 'vitest';
import { ApiError } from '../../core/models/error-response.model';
import { ErrorBannerComponent } from './error-banner.component';

describe('ErrorBannerComponent', () => {
  function componenteCom(erro: ApiError | null): ErrorBannerComponent {
    const componente = new ErrorBannerComponent();
    componente.erro = erro;
    return componente;
  }

  it('mostra mensagem genérica de permissão para 403 (SEM_PERMISSAO)', () => {
    const componente = componenteCom(new ApiError('SEM_PERMISSAO', 'detalhe interno qualquer', 't1', null));
    expect(componente.mensagem()).toBe('Não tem permissão para executar esta ação.');
  });

  it('mostra mensagem genérica para 400 (VALIDACAO)', () => {
    const componente = componenteCom(
      new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'sumario', erro: 'Obrigatório.' }]),
    );
    expect(componente.mensagem()).toBe('Existem campos por corrigir.');
  });

  it('mostra a mensagem do backend para 400 (VALIDACAO) quando o ecrã o pede', () => {
    const componente = componenteCom(new ApiError('VALIDACAO', 'Grupo não aplicável ao perfil.', 't1', [{ path: 'grupo', erro: 'x' }]));
    componente.mensagemDoServidor = true;
    expect(componente.mensagem()).toBe('Grupo não aplicável ao perfil.');
  });

  it('lista o erro de cada item em falta quando a submissão é recusada por documentos obrigatórios', () => {
    const componente = componenteCom(
      new ApiError('DOCUMENTOS_OBRIGATORIOS_EM_FALTA', 'Faltam documentos obrigatórios.', 't1', [
        { path: 'documentos.A', erro: 'Falta carregar: A (Publicação).' },
        { path: 'documentos.B', erro: 'Falta carregar: B (Depósito).' },
      ]),
    );
    expect(componente.mensagem()).toBe('Faltam documentos obrigatórios.');
    expect(componente.itens()).toEqual(['Falta carregar: A (Publicação).', 'Falta carregar: B (Depósito).']);
  });

  it('não lista campos nos outros erros: o formulário assinala-os', () => {
    const componente = componenteCom(
      new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'sumario', erro: 'Obrigatório.' }]),
    );
    expect(componente.itens()).toEqual([]);
  });

  it('trata 404 (NAO_ENCONTRADO) como inexistente, sem sugerir que existe', () => {
    const componente = componenteCom(new ApiError('NAO_ENCONTRADO', 'Instrumento x não encontrado', 't1', null));
    expect(componente.mensagem()).toBe('Este recurso não existe ou não está disponível para si.');
  });

  /** Os dois casos de 409 partilham `codigo`: a distinção vem sempre do texto do backend. */
  it('mostra o texto do backend para 409 de conflito de versão (ESTADO_INVALIDO)', () => {
    const componente = componenteCom(
      new ApiError('ESTADO_INVALIDO', 'Esta tentativa foi alterada por outro pedido — recarregue antes de repetir.', 't1', null),
    );
    expect(componente.mensagem()).toBe('Esta tentativa foi alterada por outro pedido — recarregue antes de repetir.');
  });

  it('mostra uma mensagem distinta para 409 de ação indisponível no estado atual (mesmo codigo, texto diferente)', () => {
    const componente = componenteCom(
      new ApiError('ESTADO_INVALIDO', 'Transição não permitida a partir do estado atual (VALIDADO_DGT).', 't1', null),
    );
    expect(componente.mensagem()).toBe('Transição não permitida a partir do estado atual (VALIDADO_DGT).');
  });

  it.each([
    ['TENTATIVA_EM_CURSO', 'Já existe uma tentativa em curso para este Procedimento — só é possível criar uma nova depois de anulada.'],
    ['SEQUENCIAMENTO_NAO_CUMPRIDO', 'Procedimento indisponível — há Procedimentos anteriores obrigatórios por publicar.'],
    ['DINAMICA_NAO_ELEGIVEL', 'Dinâmica indisponível — tipo não configurado para este Instrumento ou regra de elegibilidade não cumprida.'],
    ['TIPO_ATO_NAO_AUTORIZADO', 'Tipo de ato não autorizado ao utilizador pela INCM.'],
    ['PARTE_DR_NAO_AUTORIZADA', 'Parte da adesão da entidade não coincide com a exigida pelo procedimento.'],
    ['DADOS_INCM_EM_FALTA', 'Dados insuficientes para submissão à INCM.'],
    ['INTEGRACAO_INDISPONIVEL', 'Serviço externo indisponível.'],
  ])('mostra o texto do backend para o código específico %s', (codigo, mensagem) => {
    const componente = componenteCom(new ApiError(codigo, mensagem, 't1', null));
    expect(componente.mensagem()).toBe(mensagem);
  });

  it('devolve string vazia quando não há erro', () => {
    const componente = componenteCom(null);
    expect(componente.mensagem()).toBe('');
  });
});

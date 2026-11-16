import { Component, Input } from '@angular/core';
import { ApiError } from '../../core/models/error-response.model';

/** Cobre 403/404/409, casos sem campo a assinalar. O 400 é tratado pelo próprio formulário (destaca `campos`). */
@Component({
  selector: 'app-error-banner',
  standalone: true,
  template: `
    @if (erro) {
      <div class="error-banner" role="alert">
        {{ mensagem() }}
        @if (itens().length > 0) {
          <ul>
            @for (item of itens(); track item) {
              <li>{{ item }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: [
    `
      .error-banner ul {
        margin: 6px 0 0;
        padding-left: 20px;
      }

      .error-banner {
        padding: 10px 14px;
        border-radius: 8px;
        background: #fef2f2;
        border: 1px solid #fca5a5;
        color: #991b1b;
        font-size: 13px;
      }
    `,
  ],
})
export class ErrorBannerComponent {
  @Input() erro: ApiError | null = null;

  /** Ecrãs sem formulário para assinalar campos: o 400 `VALIDACAO` mostra o texto do backend em vez do genérico. */
  @Input() mensagemDoServidor = false;

  /** Um por item documental em falta; nos outros erros de campo o próprio formulário assinala. */
  itens(): string[] {
    if (this.erro?.codigo !== 'DOCUMENTOS_OBRIGATORIOS_EM_FALTA') {
      return [];
    }
    return (this.erro.campos ?? []).map((campo) => campo.erro);
  }

  mensagem(): string {
    if (!this.erro) {
      return '';
    }
    switch (this.erro.codigo) {
      case 'SEM_PERMISSAO':
        return 'Não tem permissão para executar esta ação.';
      case 'NAO_ENCONTRADO':
        return 'Este recurso não existe ou não está disponível para si.';
      case 'VALIDACAO':
        return this.mensagemDoServidor ? this.erro.mensagem : 'Existem campos por corrigir.';
      default:
        // Estados e códigos específicos (ESTADO_INVALIDO, TENTATIVA_EM_CURSO, INTEGRACAO_INDISPONIVEL…): o texto do backend já distingue os casos, nunca reescrito aqui.
        return this.erro.mensagem;
    }
  }
}

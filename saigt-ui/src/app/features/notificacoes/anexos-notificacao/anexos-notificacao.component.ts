import { ChangeDetectionStrategy, Component, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { Observable, catchError, concatMap, from, map, of, toArray } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoNotificacao } from '../../../models/notificacao.model';
import { NotificacaoService } from '../../../services/notificacao.service';
import { tamanhoEmKb } from '../apresentacao-notificacao';

/** Os valores por omissão da API; servem só para avisar cedo, quem decide é o servidor. */
export const EXTENSOES_ANEXO = ['pdf', 'doc', 'docx', 'rtf', 'odf', 'odt', 'xls', 'xlsx', 'csv', 'ods', 'txt'] as const;
export const TAMANHO_MAXIMO_ANEXO = 500 * 1024 * 1024;
export const ACEITES_ANEXO = EXTENSOES_ANEXO.map((extensao) => `.${extensao}`).join(',');

export interface ResultadoAnexo {
  ficheiro: File;
  documento: DocumentoNotificacao | null;
  erro: string | null;
}

/** Nulo quando o ficheiro pode seguir; senão, a mensagem para o utilizador. */
export function validarAnexo(ficheiro: File): string | null {
  const ponto = ficheiro.name.lastIndexOf('.');
  const extensao = ponto < 0 ? '' : ficheiro.name.slice(ponto + 1).toLowerCase();
  if (!(EXTENSOES_ANEXO as readonly string[]).includes(extensao)) {
    return `${ficheiro.name}: extensão não aceite.`;
  }
  if (ficheiro.size === 0) {
    return `${ficheiro.name}: o ficheiro está vazio.`;
  }
  if (ficheiro.size > TAMANHO_MAXIMO_ANEXO) {
    return `${ficheiro.name}: o ficheiro excede o tamanho máximo permitido.`;
  }
  return null;
}

/** Sobe os ficheiros um a um; a falha de um não impede os seguintes. */
export function enviarAnexos(
  servico: NotificacaoService,
  notificacaoId: string,
  ficheiros: readonly File[],
): Observable<ResultadoAnexo[]> {
  return from(ficheiros).pipe(
    concatMap((ficheiro) =>
      servico.anexar(notificacaoId, ficheiro).pipe(
        map((documento): ResultadoAnexo => ({ ficheiro, documento, erro: null })),
        catchError((erro: unknown) =>
          of<ResultadoAnexo>({
            ficheiro,
            documento: null,
            erro: `${ficheiro.name}: ${erro instanceof ApiError ? erro.mensagem : 'não foi possível anexar o ficheiro.'}`,
          }),
        ),
      ),
    ),
    toArray(),
  );
}

let sequencia = 0;

@Component({
  selector: 'app-anexos-notificacao',
  templateUrl: './anexos-notificacao.component.html',
  styleUrl: './anexos-notificacao.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnexosNotificacaoComponent {
  private readonly notificacaoService = inject(NotificacaoService);
  private readonly seletor = viewChild.required<ElementRef<HTMLInputElement>>('seletor');

  readonly rotulo = input('Documentos (opcional)');
  readonly desativado = input(false);
  readonly ajuda = input<string | null>(null);

  readonly ficheiros = signal<File[]>([]);
  readonly erros = signal<string[]>([]);

  readonly idCampo = `anexos-notificacao-${++sequencia}`;
  readonly aceites = ACEITES_ANEXO;
  readonly tamanhoEmKb = tamanhoEmKb;

  escolher(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const escolhidos = [...(campo.files ?? [])];
    const erros: string[] = [];
    const validos: File[] = [];
    for (const ficheiro of escolhidos) {
      const erro = validarAnexo(ficheiro);
      if (erro) {
        erros.push(erro);
      } else {
        validos.push(ficheiro);
      }
    }
    this.erros.set(erros);
    this.ficheiros.update((atuais) => [
      ...atuais,
      ...validos.filter((novo) => !atuais.some((f) => f.name === novo.name && f.size === novo.size)),
    ]);
    // Permite voltar a escolher o mesmo ficheiro depois de o remover.
    campo.value = '';
  }

  remover(ficheiro: File): void {
    this.ficheiros.update((atuais) => atuais.filter((f) => f !== ficheiro));
    this.seletor().nativeElement.focus();
  }

  limpar(): void {
    this.ficheiros.set([]);
    this.erros.set([]);
  }

  enviar(notificacaoId: string): Observable<ResultadoAnexo[]> {
    return enviarAnexos(this.notificacaoService, notificacaoId, this.ficheiros());
  }
}

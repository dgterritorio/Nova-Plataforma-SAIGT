import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IgtSnit, Instrumento } from '../../../../models/instrumento.model';
import { formatarNomeConcelho } from '../../../../shared/nome-concelho';
import { ListaOpcoesComponent, OpcaoLista } from '../../../../shared/ui/lista-opcoes/lista-opcoes.component';

/** Valor da opção de um IGT do SNIT que ainda não existe na SAIGT; as dos Instrumentos são o `id` (UUID). */
export const PREFIXO_IGT_SNIT = 'snit:';

@Component({
  selector: 'app-pagina-igt-existente',
  imports: [ListaOpcoesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./pagina.css'],
  template: `
    <h2>Selecionar IGT Existente</h2>
    <p class="subtitulo">IGT do tipo <strong>{{ tipoCodigo() }}</strong> disponíveis.</p>

    @if (opcoes().length > 0) {
      <app-lista-opcoes legenda="IGT existente" [opcoes]="opcoes()" [valor]="valor()" (valorChange)="escolher.emit($event)" />
    } @else if (!aPesquisarSnit()) {
      <p class="vazio">Sem planos deste tipo.</p>
    }
    @if (aPesquisarSnit()) {
      <p class="estado" role="status">A consultar o SNIT…</p>
    }
    @if (erroSnit(); as erro) {
      <p class="estado" role="alert">{{ erro }}</p>
    }

    @if (aCarregar()) {
      <p class="estado">A carregar…</p>
    } @else if (idIgtSnit()) {
      <p class="estado">Este IGT está em vigor no SNIT e ainda não existe na SAIGT: «Criar IGT» cria-o com a designação e os concelhos do SNIT.</p>
    }
  `,
})
export class PaginaIgtExistenteComponent {
  readonly tipoCodigo = input('');
  readonly instrumentos = input.required<readonly Instrumento[]>();
  readonly instrumentoId = input<string | null>(null);
  /** Os IGT em vigor no SNIT que ainda não existem na SAIGT; os que já existem estão em `instrumentos`. */
  readonly igtSnit = input<readonly IgtSnit[]>([]);
  readonly idIgtSnit = input<string | null>(null);
  readonly aPesquisarSnit = input(false);
  readonly erroSnit = input<string | null>(null);
  readonly aCarregar = input(false);
  readonly escolher = output<string | null>();

  readonly valor = computed(() => {
    const idIgtSnit = this.idIgtSnit();
    return idIgtSnit ? PREFIXO_IGT_SNIT + idIgtSnit : this.instrumentoId();
  });

  readonly opcoes = computed<OpcaoLista[]>(() => [
    ...this.instrumentos().map((instrumento) => ({
      valor: instrumento.id,
      titulo: instrumento.designacao,
      descricao: instrumento.nomesConcelhos?.map(formatarNomeConcelho).join(', ') || undefined,
      etiqueta: this.tipoCodigo() || undefined,
      selo: instrumento.emVigor ? 'Em vigor' : 'Sem vigor',
    })),
    ...this.igtSnit().map((igt) => ({
      valor: PREFIXO_IGT_SNIT + igt.idIgtSnit,
      titulo: igt.designacao,
      descricao: 'No SNIT, ainda não existe na SAIGT',
      etiqueta: igt.tipoInstrumento || this.tipoCodigo() || undefined,
      selo: 'Em vigor',
    })),
  ]);
}

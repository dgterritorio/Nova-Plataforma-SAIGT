import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export type TipoAlerta = 'info' | 'sucesso' | 'aviso' | 'erro';

/** O erro interrompe o leitor de ecrã (`role="alert"`); os outros tipos esperam pela vez (`role="status"`). */
@Component({
  selector: 'app-alerta',
  templateUrl: './alerta.component.html',
  styleUrl: './alerta.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': "'alerta alerta-' + tipo()",
    '[attr.role]': 'papel()',
  },
})
export class AlertaComponent {
  readonly tipo = input<TipoAlerta>('info');
  readonly titulo = input<string | null>(null);
  readonly fechavel = input(false);
  /** Um aviso que bloqueia a ação seguinte também interrompe o leitor de ecrã. */
  readonly urgente = input(false);
  readonly fechar = output<void>();

  readonly papel = computed(() => (this.tipo() === 'erro' || this.urgente() ? 'alert' : 'status'));
}
